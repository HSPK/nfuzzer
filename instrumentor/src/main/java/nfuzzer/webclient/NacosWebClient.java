package nfuzzer.webclient;


import org.apache.commons.cli.*;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

public class NacosWebClient implements WebClientInterface {
  private static final Logger logger = LogManager.getLogger(NacosWebClient.class);
  private final String host;
  private String token;
  private final int port;

  public NacosWebClient(String host, int port, String[] args) throws ParseException {
    this.host = host;
    this.port = port;
    parse(args);
  }

  public NacosWebClient(String host, int port) {
    this.host = host;
    this.port = port;
  }

  @Override
  public int sendPostReq(String body) throws Exception {
    // setup uri
    URI uri;
    try {
      uri = new URIBuilder()
          .setScheme("http")
          .setHost(host)
          .setPort(port)
          .setPath("/nacos/v1/cs/configs")
          .addParameter("accessToken", token)
          .build();
    } catch (URISyntaxException e) {
      logger.error("uri syntax error: " + e.getMessage());
      throw e;
    }

    // setup post request
    HttpPost post = new HttpPost(uri);

    // set post body
    try {
      HttpEntity entity = new StringEntity(body);
      post.setEntity(entity);
    } catch (UnsupportedEncodingException e) {
      logger.error(e.getMessage());
      throw e;
    }

    // set post header
    HttpHead head = new HttpHead();
    head.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/101.0.4951.54 Safari/537.36 Edg/101.0.1210.39");
    head.addHeader("Content-Type", "application/x-www-form-urlencoded");
    head.addHeader("Connection", "keep-alive");
    post.setHeaders(head.getAllHeaders());

    // make a post request
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      logger.debug("request headers: " + Arrays.toString(post.getAllHeaders()));
      logger.debug("request body entity: " + EntityUtils.toString(post.getEntity()));
      logger.debug("request uri: " + post.getURI());

      // get response
      CloseableHttpResponse response = httpClient.execute(post);
      logger.debug("request response: " + response.toString());
      if (response.getStatusLine().getStatusCode() >= 500) {
        logger.error("request error: " + response);
        response.close();
        return 1;
      }
      response.close();
    } catch (IOException e) {
      logger.error("request error: " + e.getMessage());
      throw e;
    }
    return 0;
  }

  @Override
  public void parse(String[] args) throws ParseException {
    if (args.length < 2)
      throw new ParseException("token is needed!");
    token = args[1];
  }

  public static void main(String[] args) {
    logger.debug("http client started...");

    // command line options
    Options options = new Options();
    options.addOption("h", true, "web server host");
    options.addOption("p", true, "web server port");
    options.addOption("token", true, "session token");
    options.addOption("body", true, "post body");

    String host, token, body;
    int port;
    // parse command line
    CommandLineParser parser = new DefaultParser();
    try {
      CommandLine cmd = parser.parse(options, args);

      // get option values
      host = cmd.getOptionValue("h", "127.0.0.1");
      body = cmd.getOptionValue("body", "");
      port = Integer.parseInt(cmd.getOptionValue("p", "8848"));
    } catch (ParseException e) {
      logger.error("parse error: " + e.getMessage());
      return;
    }

    NacosWebClient webClient = new NacosWebClient(host, port);
    try {
      webClient.sendPostReq(body);
    } catch (Exception e) {
      logger.error("send post req failed: " + e.getMessage());
    }
  }
}
