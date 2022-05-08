#!bin/bash -e
cur_dir=$(pwd)

shmdir=shm_dir
if [ ! -d $shmdir ]; then
  mkdir $shmdir
fi
nfuzzer=../instrumentor/build/libs/nfuzzer.jar
java -cp $nfuzzer nfuzzer.socket.CovSend -sp $cur_dir/shm_dir -cp 23333
