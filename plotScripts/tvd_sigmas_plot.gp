# --- Check for required parameters ---
if (!exists("O") || !exists("L") || !exists("T")) {
	print "Usage: gnuplot -e \"O=\'<path to output folder>\'\; L=\'<tree height>\'; T=\'<access threshold>\'" tvd_sigma_plot.gp"
	exit 1
}

data_dir = O . "/output/processed_data/"

uniform_data = sprintf("%stvd_sigma_height_%s_zipf_0.0000001_threshold_%s.dat", data_dir, L, T)
skewed_data = sprintf("%stvd_sigma_height_%s_zipf_1.537_threshold_%s.dat", data_dir, L, T)

output_dir = O . "/output/plots"
system "mkdir -p " . output_dir

set terminal pdf size 15cm, 6.5cm enhanced
set output output_dir . "/tvd_sigmas.pdf"

set grid
set multiplot
set xlabel "#Clients"
set ylabel "Statistical Distance"
#set yrange [0:1]
#set xrange [0:60]


set size 0.5,0.96
set key box outside center top maxrows 2

# Graph analyzing heights
set origin 0,0.06
set label "(a) Uniform Distribution" font "15" at graph 0.20, graph -0.3
plot uniform_data using 1:2 with linespoints title "{/Symbol s} = 0" dt 1 lw 2 pt 5 ps 0.5, \
	'' using 1:3 with linespoints title "{/Symbol s} = 10" dt 1 lw 2 pt 9 ps 0.5, \
	'' using 1:4 with linespoints title "{/Symbol s} = 20" dt 1 lw 2 pt 7 ps 0.5, \
	'' using 1:5 with linespoints title "{/Symbol s} = 30" dt 1 lw 2 pt 3 ps 0.5, \
	'' using 1:6 with linespoints title "{/Symbol s} = 40" dt 1 lw 2 pt 1 ps 0.5, \
	'' using 1:7 with linespoints title "{/Symbol s} = 50" dt 1 lw 2 pt 2 ps 0.5


unset label
#set yrange [0:0.3]
# Graph analyzing Zipfian
set origin 0.475,0.06
set label "(b) Skewed Distribution" font "15" at graph 0.20, graph -0.3
plot skewed_data using 1:2 with linespoints title "{/Symbol s} = 0" dt 1 lw 2 pt 5 ps 0.5, \
	'' using 1:3 with linespoints title "{/Symbol s} = 10" dt 1 lw 2 pt 9 ps 0.5, \
	'' using 1:4 with linespoints title "{/Symbol s} = 20" dt 1 lw 2 pt 7 ps 0.5, \
	'' using 1:5 with linespoints title "{/Symbol s} = 30" dt 1 lw 2 pt 3 ps 0.5, \
	'' using 1:6 with linespoints title "{/Symbol s} = 40" dt 1 lw 2 pt 1 ps 0.5, \
	'' using 1:7 with linespoints title "{/Symbol s} = 50" dt 1 lw 2 pt 2 ps 0.5

unset multiplot