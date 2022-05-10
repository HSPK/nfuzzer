package nfuzzer;

import nfuzzer.webclient.NacosWebClient;
import nfuzzer.webclient.WebClientInterface;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.*;

class Nfuzzer {

    private static final int maxQueue = 10;
    private static final Queue<FuzzRequest> requestQueue = new ConcurrentLinkedQueue<>();

    public static final byte STATUS_SUCCESS = 0;
    public static final byte STATUS_CRASH = 2;
    public static final byte STATUS_QUEUE_FULL = 3;
    public static final byte STATUS_COMM_ERROR = 4;

    public static final int DEFAULT_FUZZER_PORT = 7007;
    private static int portFuzzer;

    public static final String DEFAULT_BITMAP_HOST = "127.0.0.1";
    public static final int DEFAULT_BITMAP_PORT = 60020;
    private static String hostBitmap;
    private static int portBitmap;

    // web app args
    private static final String DEFAULT_WEB_HOST = "127.0.1.1";
    private static final int DEFAULT_WEB_PORT = 8848;

    // web client
    private static WebClientInterface webClient = null;

    public static final byte DEFAULT_MODE = 0;
    public static final byte FILE_MODE = 1;

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

        try (ServerSocket ss = new ServerSocket(portFuzzer)) {
            logger.debug("Server listening on port " + portFuzzer);

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
            logger.error("Unable to bind to port " + portFuzzer);
            System.exit(1);
        } catch (Exception e) {
            logger.error("Exception in request server");
            System.exit(1);
        }
    }

    private static byte[] getBitmapInfo() throws IOException {
        logger.debug("creating socket to bitmap server: " + hostBitmap);
        Socket cov_socket = new Socket(hostBitmap, portBitmap);
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
        return mem;
    }

    private static void handleFuzzRequest(FuzzRequest request) throws IOException {
        logger.debug("Handling request 1 of " + (requestQueue.size() + 1));

        InputStream is = request.clientSocket.getInputStream();
        OutputStream os = request.clientSocket.getOutputStream();

        int result = STATUS_CRASH;

        // 读取 mode (local 或 default)
        byte mode = (byte) is.read();

        int pathLen = is.read() | is.read() << 8 | is.read() << 16 | is.read() << 24;
        logger.debug("Path len = " + pathLen);
        // fuzz seed(body)
        String body = "";

        // receive seed path or data
        if (pathLen < 0) {
            logger.error("Failed to read path length");
            result = STATUS_COMM_ERROR;
        } else {
            byte[] bytes = new byte[pathLen];
            int readBytes = is.read(bytes);
            if (readBytes != pathLen) {
                logger.error("no enough input path, read: " + readBytes);
                result = STATUS_COMM_ERROR;
            } else {
                body = new String(bytes);
                logger.debug("received: " + body);
                if (mode == FILE_MODE)
                    body = new String(Files.readAllBytes(Paths.get(body)));
            }
        }

        if (result != STATUS_COMM_ERROR) {
            // send seed to web app
            try {
                // web app crashed
                if (webClient.sendPostReq(body) == 0)
                    result = STATUS_SUCCESS;
                logger.debug("post request finished, result: " + result);
            } catch (Exception e) {
                logger.error("send post req failed: " + e.getMessage());
            }
        }

        // get bitmap info
        byte[] mem = getBitmapInfo();
        logger.debug("result: " + result);

        // send result to afl
        os.write(result);
        os.write(mem, 0, mem.length);

        // close socket
        os.flush();
        request.clientSocket.shutdownOutput();
        request.clientSocket.shutdownInput();
        request.clientSocket.setSoLinger(true, 100000);
        request.clientSocket.close();
        logger.debug("afl connection closed");
    }
    /**
     * 启动线程，一次处理队列中的一个请求
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
                logger.error("while handling fuzz request: " + e.getMessage());
                throw new RuntimeException("Exception running fuzzed input");
            }
        }
    }

    /*
    * nfuzzer work flow:
    *
    *   afl -> nfuzzer, fuzz request (seed file or data)
    *
    *   nfuzzer -> web server, send seed to web server
    *   web server -> nfuzzer, status code
    *
    *   nfuzzer -> bitmap server, bitmap request
    *   bitmap server -> nfuzzer, bitmap info
    *
    *   nfuzzer -> afl, status code & bitmap info
    * */
    public static void main(String[] args) {
        logger.debug("nfuzzer started...");
        // command line options
        Options options = new Options();
        // fuzzer request server options
        options.addOption("pf", true, "fuzzer request port");
        // bitmap server options
        options.addOption("hb", true, "bitmap server host");
        options.addOption("pb",true, "bitmap server port");
        // web server options
        options.addOption("hw", true, "web server host");
        options.addOption("pw", true, "web server port");
        options.addOption("t", true, "http request timeout");
        // others
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
                helpFormatter.printHelp("[options] WebApp [WebApp args]", options);
                return;
            }

            // get option value
            portFuzzer = Integer.parseInt(cmd.getOptionValue("pf", String.valueOf(DEFAULT_FUZZER_PORT)));
            // bitmap option values
            hostBitmap = cmd.getOptionValue("hb", DEFAULT_BITMAP_HOST);
            portBitmap = Integer.parseInt(cmd.getOptionValue("pb", String.valueOf(DEFAULT_BITMAP_PORT)));
            // web server option values
            String hostWeb = cmd.getOptionValue("hw", DEFAULT_WEB_HOST);
            int portWeb = Integer.parseInt(cmd.getOptionValue("pw", String.valueOf(DEFAULT_WEB_PORT)));

            // get args
            logger.debug("nfuzzer args: " + Arrays.toString(args));
            String[] webAppArgs = cmd.getArgs();
            if (webAppArgs.length < 1) {
                logger.error("a WebApp name is needed!");
                return;
            }

            loadClassName = webAppArgs[0];
            if (loadClassName.equalsIgnoreCase("nacos")) {
                webClient = new NacosWebClient(hostWeb, portWeb, webAppArgs);
                logger.debug("web app args: " + Arrays.toString(webAppArgs));
            } else {
                logger.error("unsupported web app: " + loadClassName);
                return;
            }
        } catch (ParseException exp) {
            logger.error("Parse failed reason: " + exp.getMessage());
            return;
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
