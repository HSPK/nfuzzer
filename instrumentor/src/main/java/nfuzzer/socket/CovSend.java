package nfuzzer.socket;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import nfuzzer.Mem;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CovSend {

  private static final Logger logger = LogManager.getLogger(CovSend.class);

  public static void main(String[] args) {
    // command line options
    Options options = new Options();
    options.addOption("sp", true, "share memory path");
    options.addOption("cp", true, "cov send port");
    // parse command line args
    CommandLineParser parser = new DefaultParser();

    final String DEFAULT_SHM_PATH = "shm";
    final int DEFAULT_COV_PORT = 60020;
    String sp = null;
    int cp = 0;

    try {
      CommandLine cmd = parser.parse(options, args);
      sp = cmd.getOptionValue("sp", DEFAULT_SHM_PATH);
      cp = Integer.parseInt(cmd.getOptionValue("cp", String.valueOf(DEFAULT_COV_PORT)));
    } catch (ParseException e) {
      logger.error("parse error: " + e.getMessage());
      return;
    }

    // 获取分支总数，清空分支覆盖存储区域
    int branchSum = Mem.getInstance(sp).read_TwoBytes(131074);
    Mem.getInstance(sp).write_all(65538);

    try {
      ServerSocket serverSocket = new ServerSocket(cp);
      Socket socket;
      int count = 0;
//            testlog.writeLog("Bitmap monitor started");
      System.out.println("Bitmap monitor started");

      // 建立线程处理请求
      while (true) {
        socket = serverSocket.accept();
        InetAddress inetAddress = socket.getInetAddress();
        CovSendThread thread = new CovSendThread(socket, inetAddress, sp, branchSum);
        thread.start();
        count++;
//                testlog.writeLog("Test " + count + " : ");
        System.out.println("Test " + count + " : ");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}

class CovSendThread extends Thread {

  Socket socket;
  InetAddress inetAddress;
  String sp;
  int branchSum;

  public CovSendThread(Socket socket, InetAddress inetAddress, String sp, Integer branchSum) {
    this.socket = socket;
    this.inetAddress = inetAddress;
    this.sp = sp;
    this.branchSum = branchSum;
  }

  @Override
  public void run() {
    InputStream inputStream = null;
    OutputStream outputStream = null;
    // OutputStreamWriter writer = null;

    try {

      // 收到请求后开始处理
      inputStream = socket.getInputStream();
      socket.shutdownInput();

      byte[] data = Mem.getInstance(sp).read_all(0);

      // 清空边缘覆盖
      Mem.getInstance(sp).write_all(0);
      Mem.getInstance(sp).write_TwoBytes(65536, 0);

      // 将一个字节压缩为一位 (因为未知原因导致无法发送64kb数据，只能压缩为8kb)
      int edgeCount = 0;
      byte[] sendPackage = new byte[8192];
      for (int i = 0; i < 65536; i++) {
        if (data[i] > 0) {
          edgeCount++;
          int a = i / 8, b = i % 8;
          sendPackage[a] = (byte) (sendPackage[a] | (1 << b));
        }
      }

      // 发送边缘覆盖
      outputStream = socket.getOutputStream();
      outputStream.write(sendPackage);
      outputStream.flush();
      // testlog.writeLog("单次边缘覆盖 : " + edgeCount);
      System.out.println("单次边缘覆盖 : " + edgeCount);

      // 计算分支覆盖，控制台显示分支覆盖
      byte[] branch = Mem.getInstance(sp).read_all(65538);
      int branchCount = 0;
      for (int i = 0; i < 65536; i++) {
        if (branch[i] != 0) {
          branchCount++;
        }
      }
//            testlog.writeLog("总体分支覆盖 : " + branchCount + " / " + branchSum);
//            testlog.writeLog("===========================");
      System.out.println("总体分支覆盖 : " + branchCount + " / " + branchSum);
      System.out.println("===========================");

    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        if (outputStream != null) {
          outputStream.close();
        }
        if (inputStream != null) {
          inputStream.close();
        }
        if (socket != null) {
          socket.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

  }
}
