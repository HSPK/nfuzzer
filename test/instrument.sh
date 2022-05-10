#!/bin/sh

if [ $# -ne 1 ]; then
	echo "usage: $0 [jar name]"
	exit 1
fi
src_jar=$1
if [ ! -f $src_jar ]; then
	echo file not found: $src_jar
	exit 1
fi
unzip_src_jar_dir=${src_jar%.*}
if [ -d $unzip_src_jar_dir ]; then
	echo $unzip_src_jar_dir is not empty, cleaning it...
	rm -rf $unzip_src_jar_dir
fi
echo "unzipping to "$unzip_src_jar_dir"..."
unzip -o $src_jar -d $unzip_src_jar_dir >/dev/null 2>&1
echo "unzipping finished"

echo "instrumenting..."
ins_dir=$unzip_src_jar_dir/BOOT-INF
ins_out_dir=ins_out
shm_dir=shm
if [ -d $ins_out_dir ]; then
	echo "instrument output directory not empty, cleaning..."
	rm -rf $ins_out_dir
fi
if [ ! -d $shm_dir ]; then
  mkdir $shm_dir
fi
nfuzzer=../instrumentor/build/libs/nfuzzer.jar
java -classpath .:$nfuzzer nfuzzer.instrumentor.Instrumentor -i $ins_dir -o $ins_out_dir -sp $shm_dir >/dev/null 2>&1
echo "instrument finished"
if [ ! -d $ins_out_dir ]; then
	echo "instument failed, see logs/instrument.log"
	exit 1
fi

echo "copying $ins_out_dir to $unzip_src_jar_dir"
cp -r $ins_out_dir/* $unzip_src_jar_dir

echo "packing to $src_jar.pak"
jar -cvfM0 $src_jar.pak -C $unzip_src_jar_dir/ . >/dev/null 2>&1
echo "pack finished"

echo "cleaning..."
rm -rf $unzip_src_jar_dir $ins_out_dir
echo "finished"
