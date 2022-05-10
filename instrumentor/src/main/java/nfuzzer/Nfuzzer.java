package nfuzzer;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.*;

class Nfuzzer {

    private static final int maxQueue = 10;
    private static final Queue<FuzzRequest> requestQueue = new ConcurrentLinkedQueue<>();

    public static final byte STATUS_SUCCESS = 0;
    public static final byte STATUS_TIMEOUT = 1;
    public static final byte STATUS_CRASH = 2;
    public static final byte STATUS_QUEUE_FULL = 3;
    public static final byte STATUS_COMM_ERROR = 4;

    public static final long DEFAULT_TIMEOUT = 300000L;
    private static long timeout;

    public static final int DEFAULT_VERBOSITY = 2;

    public static final int DEFAULT_PORT = 7007;
    private static int port;

    public static final int DEFAULT_COVPORT = 60020;
    private static int covPort;

    public static final String DEFAULT_HOST = "127.0.0.1";
    private static String host;

    public static final byte DEFAULT_MODE = 0;
    public static final byte LOCAL_MODE = 1;

    private static Method targetMain;
    private static String[] targetArgs;

    private static File tmpfile;
    static String getstate;

    private static final Logger logger = LogManager.getLogger(Nfuzzer.class);

    private static class FuzzRequest {
        Socket clientSocket;

        FuzzRequest(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }
    }

    // 以线程运行，接受通过TCP传输的请求，并放入队列中
    // server, receive fuzz request
    private static void runServer() {

        try (ServerSocket ss = new ServerSocket(port)) {
            logger.debug("Server listening on port " + port);

            while (true) {
                Socket s = ss.accept();
                logger.debug("Connection established.");

                boolean status = false;
                if (requestQueue.size() < maxQueue) {
                    status = requestQueue.offer(new FuzzRequest(s));
                    logger.debug("Request added to queue: " + status);
                }
                // queue full
                if (!status) {
                    logger.debug("Queue full.");
                    // send status and close socket
                    OutputStream os = s.getOutputStream();
                    os.write(STATUS_QUEUE_FULL);
                    os.flush();
                    s.shutdownOutput();
                    s.shutdownInput();
                    s.close();
                    logger.debug("Connection closed");
                }
            }
        } catch (BindException be) {
            logger.error("Unable to bind to port " + port);
            System.exit(1);
        } catch (Exception e) {
            logger.error("Exception in request server");
            System.exit(1);
        }
    }

    // 用提供的文件名启动 main(), 并用文件名替换 @@
    private static long runApplication(String filename) {
        long runtime = -1L;

        String[] args = Arrays.copyOf(targetArgs, targetArgs.length);
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("@@")) {
                args[i] = filename;
            }
        }
        logger.debug("running in app call, invoke main method...");
        long pre = System.nanoTime();
        try {
            targetMain.invoke(null, (Object) args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
            throw new RuntimeException("Error invoking target main method");
        }
        runtime = System.nanoTime() - pre;
        return runtime;
    }

    /**
     * 将提供的输入写入文件，并调用 main().
     * 用写好的文件名替换 @@
     *
     * @param input 内容为字节数组的文件
     */
    private static long runApplication(byte[] input) {
        try (FileOutputStream stream = new FileOutputStream(tmpfile)) {
            stream.write(input);
            stream.close();
            return runApplication(tmpfile.getAbsolutePath());
        } catch (IOException ioe) {
            throw new RuntimeException("Error writing to tmp file");
        }
    }

    // 目标程序的运行线程
    private static class ApplicationCall implements Callable<Long> {
        byte[] input;
        String path = null;

        ApplicationCall(byte[] input) {
            this.input = input;
        }

        ApplicationCall(String path) {
            this.path = path;
        }

        @Override
        public Long call() {
            if (path != null)
                return runApplication(path);
            return runApplication(input);
        }
    }


    private static void handleFuzzRequest(FuzzRequest request) throws IOException {
        logger.debug("Handling request 1 of " + (requestQueue.size() + 1));

        InputStream is = request.clientSocket.getInputStream();
        OutputStream os = request.clientSocket.getOutputStream();

        byte result = STATUS_CRASH;
        ApplicationCall appCall = null;

        // 读取 mode (local 或 default)
        byte mode = (byte) is.read();

        // local_mode
        if (mode == LOCAL_MODE) {
            logger.debug("Handling request in LOCAL MODE.");

            // 读取路径的长度 (Integer)
            int pathLen = is.read() | is.read() << 8 | is.read() << 16 | is.read() << 24;
            logger.debug("Path len = " + pathLen);

            if (pathLen < 0) {
                logger.error("Failed to read path length");
                result = STATUS_COMM_ERROR;
            } else {
                // 读取路径
                byte[] input = new byte[pathLen];
                int read = 0;
                while (read < pathLen) {
                    if (is.available() > 0) {
                        input[read++] = (byte) is.read();
                    } else {
                        logger.error("No input available from stream, strangely, breaking.");
                        result = STATUS_COMM_ERROR;
                        break;
                    }
                }
                String path = new String(input);
                logger.debug("Received path: " + path);
                // make a call using path as arg
                appCall = new ApplicationCall(path);
            }
            // default_mode
        } else {
            logger.debug("Handling request in DEFAULT MODE.");

            // 读取输入文件的大小 (Integer)
            int fileSize = is.read() | is.read() << 8 | is.read() << 16 | is.read() << 24;
            logger.debug("File size = " + fileSize);

            if (fileSize < 0) {
                logger.error("Failed to read file size");
                result = STATUS_COMM_ERROR;
            } else {
                // 读取输入文件
                byte[] input = new byte[fileSize];
                int read = 0;
                while (read < fileSize) {
                    if (is.available() > 0) {
                        input[read++] = (byte) is.read();
                    } else {
                        logger.error("No input available from stream, strangely, Appending a 0");
                        input[read++] = 0;
                    }
                }
                appCall = new ApplicationCall(input);
            }
        }

        if (result != STATUS_COMM_ERROR) {
            // 运行程序
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Long> future = executor.submit(appCall);

            try {
                logger.debug("starting app call...");

                future.get(timeout, TimeUnit.MILLISECONDS);
                result = STATUS_SUCCESS;

                logger.debug("app call finished...");
            } catch (TimeoutException te) {
                future.cancel(true);
                logger.error("app call timeout...");
                result = STATUS_TIMEOUT;
            } catch (Throwable e) {
                future.cancel(true);
                logger.error("app call error..." + e.getMessage());
                logger.error(Arrays.toString(e.getStackTrace()));
            }
            executor.shutdownNow();
        }

        // 建立socket获取位图监控端的覆盖信息
        logger.debug("creating socket to bitmap server: " + host);
        Socket cov_socket = new Socket(host, covPort);
        logger.debug("bitmap server connected");
        // 发送请求
        OutputStream cov_quest = cov_socket.getOutputStream();
        cov_quest.write(1);
        cov_quest.flush();
        cov_socket.shutdownOutput();

        // 获取边缘覆盖结果
        InputStream cov_result = cov_socket.getInputStream();

        byte[] mem = new byte[8192];

        int read_size = cov_result.read(mem);
        cov_socket.shutdownInput();
        cov_socket.close();
        logger.debug("read bitmap bytes: " + read_size);
        logger.debug("result: " + result);

        // 将程序运行结果发送给AFL端
        os.write(result);

        // 将边缘覆盖发送给AFL端
        os.write(mem, 0, mem.length);

        // 关闭socket连接
        os.flush();
        request.clientSocket.shutdownOutput();
        request.clientSocket.shutdownInput();
        request.clientSocket.setSoLinger(true, 100000);
        request.clientSocket.close();
        logger.debug("afl connection closed");
    }
    /**
     * 启动线程，一次处理队列中的一个请求
     * <p>
     * local_mode :   只获得文件的路径
     * default_mode : 获得文件的内容 (字节)
     */
    private static void doFuzzerRuns() {
        logger.debug("Fuzzer runs handler thread started.");

        while (true) {
            try {
                FuzzRequest request = requestQueue.poll();
                if (request != null) {
                   handleFuzzRequest(request);
                } else {
                    // 没有结果
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                throw new RuntimeException("Exception running fuzzed input");
            }
        }
    }

    public static void main(String[] args) {
        logger.debug("nfuzzer started...");
        // command line options
        // java nfuzzer.Nfuzzer [-p port] [-cp covPort] [-t timeout] [-v verbosity] [-h host] className <args>
        Options options = new Options();
        options.addOption("p", true, "port");
        options.addOption("cp",true, "covSend port");
        options.addOption("t", true, "timeout");
        options.addOption("h", true, "host");
        options.addOption("v", true, "verbosity");
        options.addOption("help", false, "print this message");

        // parse command line args
        CommandLineParser parser = new DefaultParser();
        String loadClassName;

        try {
            CommandLine cmd = parser.parse(options, args);

            // print help message
            if (cmd.hasOption("help")) {
                HelpFormatter helpFormatter = new HelpFormatter();
                helpFormatter.setSyntaxPrefix("java nfuzzer.Nfuzzer ");
                helpFormatter.printHelp("[options] className [args]", options);
                return;
            }

            // get option value
            port = Integer.parseInt(cmd.getOptionValue("p", String.valueOf(DEFAULT_PORT)));
            timeout = Integer.parseInt(cmd.getOptionValue("t", String.valueOf(DEFAULT_TIMEOUT)));
            host = cmd.getOptionValue("h", DEFAULT_HOST);
            covPort = Integer.parseInt(cmd.getOptionValue("cp", String.valueOf(DEFAULT_COVPORT)));

            // get args
            String[] loadClassArgs = cmd.getArgs();
            if (loadClassArgs.length < 1) {
                logger.error("a external className is needed!");
                return;
            }

            loadClassName = loadClassArgs[0];
            targetArgs = Arrays.copyOfRange(loadClassArgs, 1, args.length);

            // must have @@ param, replaced by filename later
            if (!Arrays.asList(targetArgs).contains("@@")) {
                logger.error("A @@ param is needed!");
                return;
            }
            logger.debug("target args: " + Arrays.toString(targetArgs));
        } catch (ParseException exp) {
            logger.error("Parse failed reason: " + exp.getMessage());
            return;
        }

        // ThreadState Thread_monitor_state = new ThreadState();
        // Thread_monitor_state.start();

        // load external target class
        logger.debug("load external class: " + loadClassName);
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> target = classloader.loadClass(loadClassName);
            // load main(String[] args) method
            targetMain = target.getMethod("main", String[].class);
        } catch (ClassNotFoundException e) {
            logger.error("class not found: " + loadClassName);
            logger.error("classpath: " + System.getProperty("java.class.path"));
            return;
        } catch (NoSuchMethodException e) {
            logger.error("No main method found in class: " + loadClassName);
            return;
        } catch (SecurityException e) {
            logger.error("Main method in class not accessible: " + loadClassName);
            return;
        }
        logger.debug("external class loaded");

        // 创建 tmp 文件作为程序的输入文件
        try {
            tmpfile = File.createTempFile("Nfuzzer-input", "");
            tmpfile.deleteOnExit();
        } catch (IOException ioe) {
            throw new RuntimeException("Error creating tmp file");
        }

        // 启动服务器线程
        logger.debug("starting server thread...");
        Thread server = new Thread(Nfuzzer::runServer);
        server.start();

        // 在单独的线程中处理 fuzzer 运行的请求
        logger.debug("starting fuzzer request handle thread...");
        Thread fuzzerRuns = new Thread(Nfuzzer::doFuzzerRuns);
        fuzzerRuns.start();
    }
}
