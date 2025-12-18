# --- Check for required parameters ---
if (!exists("O") || !exists("L") || !exists("Z") || !exists("B") || !exists("A") || !exists("c_max")) {
	print "Usage: gnuplot -e \"O=\'<path to output folder>\'; L=\'<tree height>\'; Z=\'<bucket size>\'; B=\'<block size>\'; A=\'<zipfian parameter>\'; c_max=\'<max concurrent clients>\'" mvp_oram_vs_quoram_plot.gp"
	exit 1
}

data_dir = O . "/output/processed_data/"


mvp_f_1_data = sprintf("%sf_1_height_%s_bucket_%s_block_%s_zipf_%s_c_max_%s_throughput_latency_results.dat", data_dir, L, Z, B, A, c_max)
mvp_f_2_data = sprintf("%sf_2_height_%s_bucket_%s_block_%s_zipf_%s_c_max_%s_throughput_latency_results.dat", data_dir, L, Z, B, A, c_max)

cobra_f_1_data = sprintf("%sf_1_0_%s_0_0_bytes_ordered_request_full_response_throughput_latency_results.dat", data_dir, B)
cobra_f_2_data = sprintf("%sf_2_0_%s_0_0_bytes_ordered_request_full_response_throughput_latency_results.dat", data_dir, B)


output_dir = O . "/output/plots"
system "mkdir -p " . output_dir


set terminal pdf  size 15cm, 6cm enhanced
set output output_dir . "/cobra.pdf"

set grid
set multiplot
set xlabel "#Clients"
set yrange [0:*]

# Legends
set key at graph 1.8,1.22
set key box
set key maxrows 2
set key Left reverse

set size 0.5,0.90

### Throughput
set origin 0,0
set ylabel "Throughput (ops/s)"
plot mvp_f_1_data using 1:2 with linespoints title "n = 4 (MVP-ORAM)" dt 1 lw 2 pt 4 ps 0.5, \
	mvp_f_2_data using 1:2 with linespoints title "n = 7 (MVP-ORAM)" dt 2 lw 2 pt 5 ps 0.5, \
	cobra_f_1_data using 1:2 with linespoints title "n = 4 (QuORAM)" dt 4 lw 2 pt 9 ps 0.5, \
	cobra_f_2_data using 1:2 with linespoints title "n = 7 (QuORAM)" dt 5 lw 2 pt 7 ps 0.5

unset label

### Latency
set origin 0.5,0
set key off
set title ""
set ylabel "Latency (ms)"
plot mvp_f_1_data using 1:4 with linespoints title "n = 4 (MVP-ORAM)" dt 1 lw 2 pt 4 ps 0.5, \
	mvp_f_2_data using 1:4 with linespoints title "n = 7 (MVP-ORAM)" dt 2 lw 2 pt 5 ps 0.5, \
	cobra_f_1_data using 1:3 with linespoints title "n = 4 (QuORAM)" dt 4 lw 2 pt 9 ps 0.5, \
	cobra_f_2_data using 1:3 with linespoints title "n = 7 (QuORAM)" dt 5 lw 2 pt 7 ps 0.5

unset multiplot