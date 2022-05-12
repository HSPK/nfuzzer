cur_dir=$(pwd)
indir=in_dir
outdir=out_dir

 cd ../fuzzer
 ./afl-fuzz -t 1000000 -m none -l -d -i $cur_dir/$indir -o $cur_dir/$outdir -w peach -g $cur_dir/pit.xml -- $cur_dir/testAfl
