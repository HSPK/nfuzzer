nfuzzer=../instrumentor/build/libs/nfuzzer.jar
java -classpath .:$nfuzzer nfuzzer.webclient.NacosWebClient $@
