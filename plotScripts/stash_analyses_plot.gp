stash_zipf_1_data = "data/height_4_bucket_2_zipf_0.000001_clients_15_stashes.dat"
stash_zipf_2_data = "data/height_4_bucket_2_zipf_1.0_clients_15_stashes.dat"
stash_zipf_3_data = "data/height_4_bucket_2_zipf_2.0_clients_15_stashes.dat"
stash_clients_1_data = "data/height_4_bucket_2_zipf_1.0_clients_1_stashes.dat"
stash_clients_2_data = "data/height_4_bucket_2_zipf_1.0_clients_25_stashes.dat"
stash_clients_3_data = "data/height_4_bucket_2_zipf_1.0_clients_50_stashes.dat"

output_dir = "plots"
system "mkdir -p " . output_dir

set terminal pdf size 15cm, 6cm enhanced
set output output_dir . "/E1.pdf"

set grid
set multiplot
set xlabel "Time (min)"
set ylabel "#Blocks"
set yrange [0:250]
set xrange [0:60]


set size 0.5,1
set key box outside center top maxrows 2

# Graph analyzing Zipf
set origin 0,0
set label "(b) Impact of Zipfian distribution" font "15" at graph 0.15, graph -0.3
plot stash_zipf_1_data using 1:2 with linespoints title "{/Symbol a} = 10^{-6}" dt 1 lw 2 pt 5 ps 0.5, \
	stash_zipf_2_data using 1:2 with linespoints title "{/Symbol a} = 1.0" dt 2 lw 2 pt 9 ps 0.5, \
	stash_zipf_3_data using 1:2 with linespoints title "{/Symbol a} = 2.0" dt 4 lw 2 pt 7 ps 0.5

unset label

# Graph analyzing clients
set origin 0.5,0
set label "(c) Impact of concurrent clients" font "15" at graph 0.18, graph -0.3
plot stash_clients_1_data using 1:2 with linespoints title "c = 1" dt 1 lw 2 pt 5 ps 0.5, \
	stash_clients_2_data using 1:2 with linespoints title "c = 25" dt 2 lw 2 pt 9 ps 0.5, \
	stash_clients_3_data using 1:2 with linespoints title "c = 50" dt 4 lw 2 pt 7 ps 0.5

unset multiplot