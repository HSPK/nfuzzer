package nfuzzer;


import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.net.URI;
import java.net.URISyntaxException;

public class HTTPClient {
  private static void sendPostReq() {
    String host = "";
    try {
      URI uri = new URIBuilder()
          .setScheme("http")
          .setHost(host)
          .build();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    CloseableHttpClient httpClient = HttpClients.createDefault();
    
  }
}
