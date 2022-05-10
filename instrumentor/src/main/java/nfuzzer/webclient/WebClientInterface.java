package nfuzzer.webclient;


import org.apache.commons.cli.ParseException;

public interface WebClientInterface {
  int sendPostReq(String body) throws Exception;

  void parse(String[] args) throws ParseException;
}
