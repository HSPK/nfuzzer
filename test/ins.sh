src_jar=nacos-server.jar
unzip_src_jar_dir=${src_jar%.*}
echo "unzipping to "$unzip_src_jar_dir"..."
unzip -o $src_jar -d ./$unzip_src_jar_dir >/dev/null 2>&1
echo "unzipping finished"

echo "instrumenting..."
ins_dir=$unzip_src_jar_dir/BOOT-INF/classes/com
ins_out_dir=$unzip_src_jar_dir/BOOT-INF/classes/com_ins
shm_dir=shm_dir
echo "saving share memory bitmap to "$shm_dir
if [ ! -d $shm_dir ]; then
  mkdir $shm_dir
fi
nfuzzer=../instrumentor/build/libs/nfuzzer.jar
java -cp $nfuzzer nfuzzer.instrumentor.Instrumentor -i $ins_dir -o $ins_out_dir -sp $shm_dir >/dev/null 2>&1
rm -rf $ins_dir
mv $ins_out_dir $ins_dir
echo "instrument finished"

echo "packing..."
ins_nfuzzer_dir=$ins_dir/nfuzzer
mv $ins_nfuzzer_dir $unzip_src_jar_dir
jar -cvfM0 $src_jar -C $unzip_src_jar_dir/ . >/dev/null 2>&1
echo "pack finished"

echo "cleaning..."
rm -rf *.log* $unzip_src_jar_dir
echo "finished"
