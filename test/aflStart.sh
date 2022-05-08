#!/bin/bash

ip=172.22.196.26
port=8848
expand="nacos/v1/cs/configs?&accessToken=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJuYWNvcyIsImV4cCI6MTY1MjAyMzY1Mn0.j-TZPrkWja53L4EHmB8V4W92td3IJVJXCnQlGMVzgqM"
username=nacos
password=nacos
communicationMthod=post
messageBody="dataId=123&group=DEFAULT_GROUP&content=123&desc=123&config_tags=123&type=text&appName=123&tenant=&namespaceId="
serverIp=172.22.196.26
serverPort=23333
montiorPort=null
cur_dir=$(pwd)
while getopts "i:p:e:n:w:M:B:I:P:m:" arg
do
	case $arg in
		i)
			ip=$OPTARG
			#echo ip : $ip
			;;
		p)
			port=$OPTARG
			;;
		e)
			expand=$OPTARG
			;;
		n)
			username=$OPTARG
			;;
		w)
			password=$OPTARG
			;;
		M)
			communicationMthod=$OPTARG
			;;
		B)
			messageBody=$OPTARG
			
			;;
		I)
			serverIp=$OPTARG
			#echo serverIp : $serverIp
			;;
		P)
			serverPort=$OPTARG
			#echo $OPTARG
			;;
		m)
			montiorPort=$OPTARG
			;;
		?)
			echo parameterError
			return parameterError
			;;
	esac
done
#echo MP:$montiorPort
#echo ServerIp: $serverIp    ServerPort : $serverPort
if [ $ip = null ] || [ $port = null ] || [ $expand = null ] || [ $communicationMthod = null ] || [ $messageBody = null ] || [ $serverIp = null ] || [ $serverPort = null ];
then
	echo parameterError
	return parameterError
fi
echo $messageBody > ./in_dir/example

nfuzzer=../instrumentor/build/libs/nfuzzer.jar
java -cp $nfuzzer nfuzzer.buildpit.BuildPit in_dir/example

#nohup java -cp $nfuzzer nfuzzer.Nfuzzer -h $serverIp -cp $serverPort -p 7007 StartSendMess @@ $ip $port $expand $username $password $communicationMthod > Log.log &

nohup java -cp $nfuzzer nfuzzer.Nfuzzer -h $serverIp -cp $serverPort -p 7007 testURLconnect @@ $ip $port $expand $communicationMthod > Log.log &
#p=`/usr/sbin/lsof -i :$montiorPort | awk '{print $1 " "  $2}'`
#if [[ $p != "" ]];
#then
#	echo 1
#	exit
#        return 1
#else
#	nohup ./Montior $montior &
#fi

indir=in_dir
outdir=out_dir
if [ ! -d $indir ]; then
  mkdir $indir
fi

if [ ! -d $outdir ]; then
  mkdir $outdir
fi

cd ../fuzzer
./afl-fuzz -t 1000000 -m none -h -d -i $cur_dir/$indir -o $cur_dir/$outdir -w peach -g $cur_dir/pit.xml -- $cur_dir/interface -p 7007 @@
