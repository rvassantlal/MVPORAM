# --- Check for required parameters ---
if (!exists("O") || !exists("L") || !exists("Z") || !exists("B") || !exists("c_max") || !exists("D")) {
	print "Usage: gnuplot -e \"O=\'<path to output folder>\'; L=\'<tree height>\'; Z=\'<bucket size>\'; B=\'<block size>\'; c_max=\'<max concurrent clients>\'; D=<experiment duration in next whole minute>" stash_analyses_plot.gp"
	exit 1
}

data_dir = O . "/output/processed_data/"

stash_zipf_1_data = sprintf("%sf_0_height_%s_bucket_%s_block_%s_zipf_0.000001_c_max_%s_clients_15_stashes.dat", data_dir, L, Z, B, c_max)
stash_zipf_2_data = sprintf("%sf_0_height_%s_bucket_%s_block_%s_zipf_1.0_c_max_%s_clients_15_stashes.dat", data_dir, L, Z, B, c_max)
stash_zipf_3_data = sprintf("%sf_0_height_%s_bucket_%s_block_%s_zipf_2.0_c_max_%s_clients_15_stashes.dat", data_dir, L, Z, B, c_max)
stash_clients_1_data = sprintf("%sf_0_height_%s_bucket_%s_block_%s_zipf_1.0_c_max_%s_clients_1_stashes.dat", data_dir, L, Z, B, c_max)
stash_clients_2_data = sprintf("%sf_0_height_%s_bucket_%s_block_%s_zipf_1.0_c_max_%s_clients_25_stashes.dat", data_dir, L, Z, B, c_max)
stash_clients_3_data = sprintf("%sf_0_height_%s_bucket_%s_block_%s_zipf_1.0_c_max_%s_clients_50_stashes.dat", data_dir, L, Z, B, c_max)

output_dir = O . "/output/plots"
system "mkdir -p " . output_dir

set terminal pdf size 15cm, 6cm enhanced
set output output_dir . "/E3.pdf"

set grid
set multiplot
set xlabel "Time (min)"
set ylabel "#Blocks"
set yrange [0:250]
set xrange [0:D]


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