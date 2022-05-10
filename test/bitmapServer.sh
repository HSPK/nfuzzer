#!bin/bash -e
cur_dir=$(pwd)

shmdir=shm
if [ ! -d $shmdir ]; then
  mkdir $shmdir
fi
nfuzzer=../instrumentor/build/libs/nfuzzer.jar
java -classpath .:$nfuzzer nfuzzer.socket.CovSend -sp $shmdir -cp 23333
