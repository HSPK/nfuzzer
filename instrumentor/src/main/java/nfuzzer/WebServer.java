package nfuzzer;

import java.net.*;
import java.io.*;
import java.util.*;

public class WebServer {
  public static void main(String args[]) throws Exception {


    StringBuilder result = new StringBuilder();
    HttpURLConnection conn = null;

    //TODO 设置ip、port、expand为传参，使用args[]
    String ip = args[1];
    String port = args[2];
    String expand = args[3];


    if (args[4].toLowerCase().equals("post")) {
      OutputStreamWriter out = null;
      BufferedReader in = null;

      URL url = new URL("http://" + ip + ":" + port + "/" + expand);

      System.out.println("url:" + url);

      conn = (HttpURLConnection) url.openConnection();

      // conn.addRequestProperty("Accept", "application/json, text/javascript, * /*; q=0.01");
      conn.addRequestProperty("Accept-Encoding", "gzip, deflate");
      conn.addRequestProperty("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
      conn.addRequestProperty("Cookie", "JSESSIONID=683A6BF8ADDF0EC1ADC671069670AD8C");
      //conn.addRequestProperty("Authorization", "{'accessToken':'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJuYWNvcyIsImV4cCI6MTYxNTA1MDYxNH0.Y3Y7ocnPgg8zANj5xY9GB6b6u8QcJTszLpK0Sk4mkTU','tokenTtl':18000,'globalAdmin':true}");
      conn.addRequestProperty("Connection", "keep-alive");
      conn.addRequestProperty("Content-Length", "101");
      conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      conn.addRequestProperty("Host", ip + ":" + port);
      conn.addRequestProperty("Origin", "http://" + ip + ":" + port);
      conn.addRequestProperty("Referer", "http://" + ip + ":" + port);
      conn.addRequestProperty("Sec-Fetch-Dest", "empty");
      conn.addRequestProperty("Sec-Fetch-Mode", "cors");
      conn.addRequestProperty("Sec-Fetch-Site", "same-origin");
      conn.addRequestProperty("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:86.0) Gecko/20100101 Firefox/86.0");
      //conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");


      conn.setRequestMethod("POST");
      //发送POST请求必须设置为true
      conn.setDoOutput(true);
      conn.setDoInput(true);
      //设置连接超时时间和读取超时时间
      conn.setConnectTimeout(30000);
      conn.setReadTimeout(10000);


      String jsonStr = "";
      out = new OutputStreamWriter(conn.getOutputStream());
      //获取输出流

      out = new OutputStreamWriter(conn.getOutputStream());

      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(args[0])));
      String data = null;
      while ((data = br.readLine()) != null) {
        jsonStr = jsonStr + data;
      }

//        jsonStr="dataId=12345&group=DEFAULT_GROUP&content=123&desc=123&config_tags=&type=text&appName=&tenant=&namespaceId=";
      out.write(jsonStr);
      System.out.println("发送信息:\t" + jsonStr);
      out.flush();
      out.close();

      //取得输入流，并使用Reader读取
      if (200 == conn.getResponseCode()) {
        in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        String line;
        while ((line = in.readLine()) != null) {
          result.append(line);
          System.out.println(line);
        }
      } else if (conn.getResponseCode() >= 500) {
        BufferedWriter out_ = null;
        try {
          out_ = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("results.txt", true)));
          out_.write(url + "\n" + jsonStr + "\r\n\n");
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          try {
            out_.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
        throw new Exception();
      } else {
        System.out.println("ResponseCode is an error code:" + conn.getResponseCode());
      }
    } else if (args[4].toLowerCase().equals("get")) {
      InputStream is = null;
      BufferedReader br = null;

      String getStr = "";
      BufferedReader br_input = new BufferedReader(new InputStreamReader(new FileInputStream(args[0])));
      String data = null;
      while ((data = br_input.readLine()) != null) {
        getStr = getStr + data;
      }
      URL url = new URL("http://" + ip + ":" + port + "/" + expand + '?' + getStr);

      System.out.println("url:" + url);


      conn = (HttpURLConnection) url.openConnection();

      // conn.addRequestProperty("Accept", "application/json, text/javascript, * /*; q=0.01");
      conn.addRequestProperty("Accept-Encoding", "gzip, deflate");
      conn.addRequestProperty("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
      conn.addRequestProperty("Cookie", "JSESSIONID=683A6BF8ADDF0EC1ADC671069670AD8C");
      //conn.addRequestProperty("Authorization", "{'accessToken':'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJuYWNvcyIsImV4cCI6MTYxNTA1MDYxNH0.Y3Y7ocnPgg8zANj5xY9GB6b6u8QcJTszLpK0Sk4mkTU','tokenTtl':18000,'globalAdmin':true}");
      conn.addRequestProperty("Connection", "keep-alive");
      conn.addRequestProperty("Content-Length", "101");
      conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      conn.addRequestProperty("Host", ip + ":" + port);
      conn.addRequestProperty("Origin", "http://" + ip + ":" + port);
      conn.addRequestProperty("Referer", "http://" + ip + ":" + port);
      conn.addRequestProperty("Sec-Fetch-Dest", "empty");
      conn.addRequestProperty("Sec-Fetch-Mode", "cors");
      conn.addRequestProperty("Sec-Fetch-Site", "same-origin");
      conn.addRequestProperty("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:86.0) Gecko/20100101 Firefox/86.0");
      //conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");


      conn.setRequestMethod("GET");
      //set the connect-timeout time and read-timeout time
      conn.setConnectTimeout(15000);
      conn.setReadTimeout(60000);
      conn.connect();

      if (200 == conn.getResponseCode()) {
        is = conn.getInputStream();
        br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
          result.append(line);
          System.out.println(line);
        }
      } else if (conn.getResponseCode() >= 500) {
        BufferedWriter out_ = null;
        try {
          out_ = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("results.txt", true)));
          out_.write(url + "\r\n\n");
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          try {
            out_.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
        throw new Exception();
      } else {
        System.out.println("ResponseCode is an error code:" + conn.getResponseCode());
      }
    }
  }
}
