#!/bin/bash

# params
webAppHost=`ip addr show eth0 | grep 'inet ' | awk '{print $2}' | sed 's/\/.*//'`
webAppPort=8848
token="eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJuYWNvcyIsImV4cCI6MTY1MjEyMjkxMn0.A5nXJhrN1pV_KTZFiKyMp_kjBaklYfKE6NbosWCELd8"
body="dataId=123&group=DEFAULT_GROUP&content=123&desc=123&config_tags=123&type=text&appName=123&tenant=&namespaceId="

bitmapServerHost=$webAppHost
# serverIp=127.0.0.1
bitmapServerPort=23333
cur_dir=$(pwd)

# nfuzzer.jar path
nfuzzer=../instrumentor/build/libs/nfuzzer.jar
echo $body > ./in_dir/example

# build pit file
echo building pit file...
nfuzzer=../instrumentor/build/libs/nfuzzer.jar
java -classpath .:$nfuzzer nfuzzer.buildpit.BuildPit in_dir/example >/dev/null 2>&1

# start nfuzzer communication
echo starting nfuzzer.Nfuzzer...
java -classpath .:$nfuzzer nfuzzer.Nfuzzer -hb $bitmapServerHost -pb $bitmapServerPort -pf 7007 -hw $webAppHost -pw $webAppPort nacos $token >/dev/null 2>&1 &
# mkdir
indir=in_dir
outdir=out_dir
if [ ! -d $indir ]; then
  mkdir $indir
fi

if [ ! -d $outdir ]; then
  mkdir $outdir
fi

echo "stating afl"
# start nfuzzer
cd ../fuzzer
./afl-fuzz -t 1000000 -m none -h -d -i $cur_dir/$indir -o $cur_dir/$outdir -w peach -g $cur_dir/pit.xml -- $cur_dir/interface -p 7007 @@
