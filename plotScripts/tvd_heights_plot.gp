# --- Check for required parameters ---
if (!exists("O") || !exists("L1") || !exists("L2")) {
	print "Usage: gnuplot -e \"O=\'<path to output folder>\'\; L1=\'<tree height 1>\'; L2=\'<tree height 2>\'\" tvd_heights_plot.gp"
	exit 1
}

data_dir = O . "/output/processed_data/"

height_1_data = sprintf("%stvd_heights_height_%s.dat", data_dir, L1)
height_2_data = sprintf("%stvd_heights_height_%s.dat", data_dir, L2)

output_dir = O . "/output/plots"
system "mkdir -p " . output_dir

set terminal pdf size 15cm, 6.5cm enhanced
set output output_dir . "/tvd_heights.pdf"

set grid
set multiplot
set xlabel "#Clients"
set ylabel "Statistical Distance"
set yrange [0:1]
#set xrange [0:60]


set size 0.5,0.96
set key box outside center top maxrows 2

# Graph analyzing heights
set origin 0,0.06
set label "(a) Impact of {/Symbol a} (L = ". L1 .")" font "15" at graph 0.20, graph -0.3
plot height_1_data using 1:2 with linespoints title "80/20" dt 1 lw 2 pt 5 ps 0.5, \
	'' using 1:3 with linespoints title "90/10" dt 1 lw 2 pt 9 ps 0.5, \
	'' using 1:4 with linespoints title "95/5" dt 1 lw 2 pt 7 ps 0.5, \
	'' using 1:5 with linespoints title "99/1" dt 1 lw 2 pt 2 ps 0.5


unset label
#set yrange [0:0.3]
# Graph analyzing Zipfian
set origin 0.5,0.06
set label "(b) Impact of {/Symbol a} (L = " . L2 . ")" font "15" at graph 0.20, graph -0.3
plot height_2_data using 1:2 with linespoints title "80/20" dt 1 lw 2 pt 5 ps 0.5, \
	'' using 1:3 with linespoints title "90/10" dt 1 lw 2 pt 9 ps 0.5, \
	'' using 1:4 with linespoints title "95/5" dt 1 lw 2 pt 7 ps 0.5, \
	'' using 1:5 with linespoints title "99/1" dt 1 lw 2 pt 2 ps 0.5

unset multiplot