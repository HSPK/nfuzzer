nacos_jar_dir=./nacos/target/nacos-server.jar
cp $nacos_jar_dir .
bash ins.sh
cp nacos-server.jar $nacos_jar_dir
bash ./nacos/bin/startup.sh -m standalone
