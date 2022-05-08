package nfuzzer;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class Nfuzzer {

    private static final int maxQueue = 10;
    private static Queue<FuzzRequest> requestQueue = new ConcurrentLinkedQueue<>();

    public static final byte STATUS_SUCCESS = 0;
    public static final byte STATUS_TIMEOUT = 1;
    public static final byte STATUS_CRASH = 2;
    public static final byte STATUS_QUEUE_FULL = 3;
    public static final byte STATUS_COMM_ERROR = 4;
    public static final byte STATUS_DONE = 5;

    public static final long DEFAULT_TIMEOUT = 300000L;
    private static long timeout;

    public static final int DEFAULT_VERBOSITY = 2;
    private static int verbosity;

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

    private static class FuzzRequest {
        Socket clientSocket;

        FuzzRequest(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }
    }

    // 以线程运行，接受通过TCP传输的请求，并放入队列中
    private static void runServer() {

        try (ServerSocket ss = new ServerSocket(port)) {
            if (verbosity > 1)
                System.out.println("Server listening on port " + port);

            while (true) {
                Socket s = ss.accept();
                if (verbosity > 1)
                    System.out.println("Connection established.");

                boolean status = false;
                if (requestQueue.size() < maxQueue) {
                    status = requestQueue.offer(new FuzzRequest(s));
                    if (verbosity > 1)
                        System.out.println("Request added to queue: " + status);
                }
                if (!status) {
                    if (verbosity > 1)
                        System.out.println("Queue full.");
                    OutputStream os = s.getOutputStream();
                    os.write(STATUS_QUEUE_FULL);
                    os.flush();
                    s.shutdownOutput();
                    s.shutdownInput();
                    s.close();
                    if (verbosity > 1)
                        System.out.println("Connection closed.");
                }
            }
        } catch (BindException be) {
            System.err.println("Unable to bind to port " + port);
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Exception in request server");
            e.printStackTrace();
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
        String path;

        ApplicationCall(byte[] input) {
            this.input = input;
        }

        ApplicationCall(String path) {
            this.path = path;
        }

        @Override
        public Long call() throws Exception {
            if (path != null)
                return runApplication(path);
            return runApplication(input);
        }
    }


    /**
     * 启动线程，一次处理队列中的一个请求
     * <p>
     * local_mode :   只获得文件的路径
     * default_mode : 获得文件的内容 (字节)
     */
    private static void doFuzzerRuns() {
        if (verbosity > 1)
            System.out.println("Fuzzer runs handler thread started.");

        while (true) {
            try {
                ThreadState.setState("Fuzzing is running");
                FuzzRequest request = requestQueue.poll();
                if (request != null) {
                    if (verbosity > 1)
                        System.out.println("Handling request 1 of " + (requestQueue.size() + 1));

                    InputStream is = request.clientSocket.getInputStream();
                    OutputStream os = request.clientSocket.getOutputStream();

                    byte result = STATUS_CRASH;
                    ApplicationCall appCall = null;

                    // 读取 mode (local 或 default)
                    byte mode = (byte) is.read();

                    // local_mode
                    if (mode == LOCAL_MODE) {
                        if (verbosity > 1)
                            System.out.println("Handling request in LOCAL MODE.");

                        // 读取路径的长度 (Integer)
                        int pathlen = is.read() | is.read() << 8 | is.read() << 16 | is.read() << 24;
                        if (verbosity > 2)
                            System.out.println("Path len = " + pathlen);

                        if (pathlen < 0) {
                            if (verbosity > 1)
                                System.err.println("Failed to read path length");
                            result = STATUS_COMM_ERROR;
                        } else {

                            // 读取路径
                            byte[] input = new byte[pathlen];
                            int read = 0;
                            while (read < pathlen) {
                                if (is.available() > 0) {
                                    input[read++] = (byte) is.read();
                                } else {
                                    if (verbosity > 1) {
                                        System.err.println("No input available from stream, strangely, breaking.");
                                        result = STATUS_COMM_ERROR;
                                        break;
                                    }
                                }
                            }
                            String path = new String(input);
                            if (verbosity > 1)
                                System.out.println("Received path: " + path);

                            appCall = new ApplicationCall(path);
                        }

                        // default_mode
                    } else {
                        if (verbosity > 1)
                            System.out.println("Handling request in DEFAULT MODE.");

                        // 读取输入文件的大小 (Integer)
                        int filesize = is.read() | is.read() << 8 | is.read() << 16 | is.read() << 24;
                        if (verbosity > 2)
                            System.out.println("File size = " + filesize);

                        if (filesize < 0) {
                            if (verbosity > 1)
                                System.err.println("Failed to read file size");
                            result = STATUS_COMM_ERROR;
                        } else {

                            // 读取输入文件
                            byte[] input = new byte[filesize];
                            int read = 0;
                            while (read < filesize) {
                                if (is.available() > 0) {
                                    input[read++] = (byte) is.read();
                                } else {
                                    if (verbosity > 1) {
                                        System.err.println("No input available from stream, strangely");
                                        System.err.println("Appending a 0");
                                    }
                                    input[read++] = 0;
                                }
                            }

                            appCall = new ApplicationCall(input);
                        }
                    }

                    if (result != STATUS_COMM_ERROR && appCall != null) {

                        // 运行程序
                        ExecutorService executor = Executors.newSingleThreadExecutor();
                        Future<Long> future = executor.submit(appCall);

                        try {
                            if (verbosity > 1)
                                System.out.println("Started...");

                            future.get(timeout, TimeUnit.MILLISECONDS);
                            result = STATUS_SUCCESS;

                            if (verbosity > 1)
                                System.out.println("Finished!");
                        } catch (TimeoutException te) {
                            future.cancel(true);
                            if (verbosity > 1)
                                System.out.println("Time-out!");
                            result = STATUS_TIMEOUT;
                        } catch (Throwable e) {
                            future.cancel(true);
                            if (e.getCause() instanceof RuntimeException) {
                                if (verbosity > 1)
                                    System.out.println("RuntimeException thrown!");
                            } else if (e.getCause() instanceof Error) {
                                if (verbosity > 1)
                                    System.out.println("Error thrown!");
                            } else {
                                if (verbosity > 1)
                                    System.out.println("Uncaught throwable!");
                            }
                            e.printStackTrace();
                        }
                        executor.shutdownNow();
                    }

                    // 建立socket获取位图监控端的覆盖信息
                    Socket cov_socket = new Socket(host, covPort);

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
                    System.out.println(read_size);

                    // 测试边缘覆盖结果
                    for (int i = 0; i < 8192; i++) {
                        if (mem[i] != 0) {
                            System.out.print(i);
                            System.out.print(": ");
                            System.out.println(mem[i]);
                        }
                    }

                    if (verbosity > 1)
                        System.out.println("Result: " + result);

                    if (verbosity > 2) {

                        for (int i = 0; i < 8192; i++) {
                            if (mem[i] != 0) {
                                System.out.println(i + " -> " + mem[i]);
                            }
                        }
                    }

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
                    if (verbosity > 1)
                        System.out.println("Connection closed.");

                } else {
                    // 没有结果
                    Thread.sleep(100);
                }
            } catch (SocketException se) {
                // 可能是AFL端将进程kill了，导致连接被重置
                ThreadState.setState("Connection reset");
                if (verbosity > 1)
                    System.out.println("Connection reset.");
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Exception running fuzzed input");
            }
        }
    }

    public static class ThreadState extends Thread {
        public static String State = "Fuzzer is starting;";

        //创建线程,只运行一次监控函数
        public static int flag = 0;

        public void run() {
            if (flag == 0) {
                try {
                    monitorQuery();
                    flag = 1;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public static void setState(String s) {
            State = s;
        }

        public static void monitorQuery() throws Exception {
            System.out.println("监控线程启动；");
            while (true) {
                int port = 60043;
                ServerSocket server = new ServerSocket(port);

                Socket socket = server.accept();
                //跟客户端建立好连接之后，我们就可以获取socket的InputStream，并从中读取客户端发过来的信息了。
                BufferedReader bReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                //读取客户端发送来的消息

                String sb = bReader.readLine();
                if (sb.equals("query the state of fuzzer")) {
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    bw.write(State + "\n");
                    bw.flush();
                }

                socket.close();
                server.close();
            }
        }
    }

    public static void main(String[] args) {

        ThreadState Thread_monitor_state = new ThreadState();
        Thread_monitor_state.start();


        // 处理命令行输入的参数
        if (args.length < 1) {
            System.err.println("Usage: java nfuzzer.Nfuzzer [-p N] [-cp N] [-t N] [-v N] [-h N] package.ExampleMain <args>");
            return;
        }

        port = DEFAULT_PORT;
        timeout = DEFAULT_TIMEOUT;
        verbosity = DEFAULT_VERBOSITY;
        host = DEFAULT_HOST;
        covPort = DEFAULT_COVPORT;

        int curArg = 0;
        while (args.length > curArg) {
            if (args[curArg].equals("-p") || args[curArg].equals("-port")) {
                port = Integer.parseInt(args[curArg + 1]);
                curArg += 2;
            } else if (args[curArg].equals("-v") || args[curArg].equals("-verbosity")) {
                verbosity = Integer.parseInt(args[curArg + 1]);
                curArg += 2;
            } else if (args[curArg].equals("-cp") || args[curArg].equals("-covport")) {
                covPort = Integer.parseInt(args[curArg + 1]);
                curArg += 2;
            } else if (args[curArg].equals("-t") || args[curArg].equals("-timeout")) {
                timeout = Long.parseLong(args[curArg + 1]);
                curArg += 2;
            } else if (args[curArg].equals("-h") || args[curArg].equals("-host")) {
                host = args[curArg + 1];
                curArg += 2;
            } else {
                break;
            }
        }
        String mainClass = args[curArg];
        targetArgs = Arrays.copyOfRange(args, curArg + 1, args.length);

        // 判断是否有 @@ 参数 (必要)
        boolean present = false;
        for (String targetArg : targetArgs) {
            if (targetArg.equals("@@")) {
                present = true;
                break;
            }
        }
        if (!present) {
            System.err.println("Error: none of the target application parameters is @@");
            System.exit(1);
        }

        // 如果设置了 verbosity ，将程序输出重定向到 /dev/null
        if (verbosity <= 0) {
            PrintStream nullStream = new PrintStream(new NullOutputStream());
            System.setOut(nullStream);
            System.setErr(nullStream);
        }

        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> target = classloader.loadClass(mainClass);
            targetMain = target.getMethod("main", String[].class);
        } catch (ClassNotFoundException e) {
            System.err.println("Main class not found: " + mainClass);
            return;
        } catch (NoSuchMethodException e) {
            System.err.println("No main method found in class: " + mainClass);
            return;
        } catch (SecurityException e) {
            System.err.println("Main method in class not accessible: " + mainClass);
            return;
        }

        // 创建 tmp 文件作为程序的输入文件
        try {
            tmpfile = File.createTempFile("Nfuzzer-input", "");
            tmpfile.deleteOnExit();
        } catch (IOException ioe) {
            throw new RuntimeException("Error creating tmp file");
        }

        // 启动服务器线程
        Thread server = new Thread(new Runnable() {
            @Override
            public void run() {
                runServer();
            }
        });
        server.start();

        // 在单独的线程中处理 fuzzer 运行的请求

        Thread fuzzerRuns = new Thread(new Runnable() {
            @Override
            public void run() {
                doFuzzerRuns();
            }
        });
        fuzzerRuns.start();


    }

    // 重定向目标程序的输出 (到 /dev/null 的流)
    private static class NullOutputStream extends ByteArrayOutputStream {

        @Override
        public void write(int b) {
        }

        @Override
        public void write(byte[] b, int off, int len) {
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
        }
    }
}
