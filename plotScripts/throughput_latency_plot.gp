f_1_data = "data/f_1_throughput_latency_results.dat"
f_2_data = "data/f_2_throughput_latency_results.dat"
f_3_data = "data/f_3_throughput_latency_results.dat"

output_dir = "plots"
system "mkdir -p " . output_dir

set terminal pdf  size 15cm, 6cm enhanced
set output output_dir . "/E3.pdf"

set grid
set multiplot
set xlabel "#Clients"
set yrange [0:*]

# Legends
set key at graph 2,1.13
set key box
set key maxrows 1
set key Left reverse

set size 0.5,0.96

### Throughput
set origin 0,0
set ylabel "Throughput (ops/s)"
plot f_1_data using 1:2:3 with errorlines title "n = 1" dt 1 lw 2 pt 4 ps 0.5, \
	f_1_data using 1:2:3 with errorlines title "n = 4" dt 2 lw 2 pt 5 ps 0.5, \
	f_2_data using 1:2:3 with errorlines title "n = 7" dt 4 lw 2 pt 9 ps 0.5, \
	f_3_data using 1:2:3 with errorlines title "n = 10" dt 5 lw 2 pt 7 ps 0.5

unset label

### Latency
set origin 0.5,0
set key off
set title ""
set ylabel "Latency (ms)"
plot f_1_data using 1:4:5 with errorlines title "n = 1" dt 1 lw 2 pt 4 ps 0.5, \
	f_1_data using 1:4:5 with errorlines title "n = 4" dt 2 lw 2 pt 5 ps 0.5, \
	f_2_data using 1:4:5 with errorlines title "n = 7" dt 4 lw 2 pt 9 ps 0.5, \
	f_3_data using 1:4:5 with errorlines title "n = 10" dt 5 lw 2 pt 7 ps 0.5

unset multiplot