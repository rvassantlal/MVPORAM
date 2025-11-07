package oram.benchmark.tvd;

import java.math.BigDecimal;
import java.util.Arrays;

public class TVDBenchmarkClient {
	public static void main(String[] args) {

		String input = "(0.90436, 0.8) (0.90436, 0.8) (0.90436, 0.8) (0.90436,0.8)";
		input = input.trim();
		input = input.substring(1, input.length() - 1);
		input = input.replaceAll(" ", "");
		String[] tokens = input.split("\\)\\(");
		double[] zipfParameters = new double[tokens.length];
		double[] accessThresholds = new double[tokens.length];
		for (int i = 0; i < tokens.length; i++) {
			String[] pair = tokens[i].split(",");
			zipfParameters[i] = Double.parseDouble(pair[0]);
			accessThresholds[i] = Double.parseDouble(pair[1]);

		}

		TVDBenchmarkClient tvdClient2 = new TVDBenchmarkClient();
		int steps1 = 11;
		int[] x1 = new int[steps1];
		for (int i = 0; i < steps1; i++) {
			x1[i] = Math.max(1, i * 5);
		}
		int h = 17;

		for (int i = 0; i < zipfParameters.length; i++) {
			double a = zipfParameters[i];
			double t = accessThresholds[i];
			BigDecimal[] y = tvdClient2.calculateHeightTVD(x1, h, a, t);
			for (int j = 0; j < x1.length; j++) {
				System.out.println("Zipf: " + a + ", Threshold: " + t + ", Clients: " + x1[j] + ", TVD: " + y[j]);
			}
		}


		if (args.length != 6) {
			System.out.println("Usage: oram.benchmark.statisticalDistance.StatisticalDistanceBenchmarkClient " +
					"<treeHeight> <zipfian parameter> <access threshold> <nClients> <data point steps> <sigmas(comma separated)>");
			return;
		}
		int treeHeight = Integer.parseInt(args[0]);
		double alpha = Double.parseDouble(args[1]);
		double threshold = Double.parseDouble(args[2]);
		int nClients = Integer.parseInt(args[3]);
		int dataSteps = Integer.parseInt(args[4]);
		int[] sigmas = Arrays.stream(args[5].split(",")).mapToInt(Integer::parseInt).toArray();
		int steps = nClients / dataSteps + 1;
		int[] x = new int[steps];
		for (int i = 0; i < steps; i++) {
			x[i] = Math.max(1, i * dataSteps);
		}

		TVDBenchmarkClient tvdClient = new TVDBenchmarkClient();
		BigDecimal[][] tvd = tvdClient.calculateStrongMVPORAMTVD(x,
				treeHeight, alpha, threshold, sigmas);
		for (int i = 0; i < sigmas.length; i++) {
			int sigma = sigmas[i];
			System.out.println("Sigma: " + sigma);
			for (int j = 0; j < x.length; j++) {
				int clients = x[j];
				BigDecimal distance = tvd[i][j];
				System.out.println("Clients: " + clients + ", TVD: " + distance);
			}
		}
	}

	public BigDecimal[][] calculateStrongMVPORAMTVD(int[] x, int treeHeight, double zipfian,
													double threshold, int[] sigmas) {
		int nBlocks = (1 << (treeHeight + 1)) - 1;
		double harmonicNumber = computeGeneralizedHarmonicNumber(nBlocks, zipfian);
		double distinctItems = calculateDistinctItems(nBlocks, zipfian, harmonicNumber, threshold);
		//double distinctItemsPercentage = distinctItems / nBlocks * 100.0;
		//double thresholdPercentage = threshold * 100.0;
		BigDecimal[] zipfianPMFPerDepth = precomputeZipfianPMFPerDepth(treeHeight, zipfian, harmonicNumber);
		return experimentWithSigma(treeHeight, x, zipfianPMFPerDepth, sigmas);
	}

	public BigDecimal[] calculateHeightTVD(int[] x, int height, double zipfian, double threshold) {
		int nNodes = (1 << (height + 1)) - 1;
		double harmonicNumber = computeGeneralizedHarmonicNumber(nNodes, zipfian);
		BigDecimal[] zipfianPMFPerDepth = precomputeZipfianPMFPerDepth(height, zipfian, harmonicNumber);
		return experimentWithZipfian(height, x, zipfianPMFPerDepth);
	}

	private BigDecimal[] experimentWithZipfian(int height, int[] x, BigDecimal[] zipfianPMFPerDepth) {
		BigDecimal[] y = new BigDecimal[x.length];
		for (int i = 0; i < x.length; i++) {
			int nClients = x[i];
			BigDecimal tvd = calculateTVD(height, nClients, zipfianPMFPerDepth);
			y[i] = tvd;
		}

		return y;
	}

	private BigDecimal calculateTVD(int height, int nClients, BigDecimal[] zipfianPMFPerDepth) {
		BigDecimal tvd = BigDecimal.ZERO;
		for (int i = 1; i <= nClients; i++) {
			BigDecimal root = kLeavesUsageProbabilityForRandom(height, nClients, i);
			BigDecimal mvp = kLeavesUsageProbabilityForMVP(height, nClients, i, zipfianPMFPerDepth);
			tvd = tvd.add(root.subtract(mvp).abs());
		}
		tvd = tvd.divide(BigDecimal.valueOf(2));
		return tvd;
	}


	private BigDecimal[][] experimentWithSigma(int height, int[] x, BigDecimal[] zipfianPMFPerDepth,
												  int[] sigmas) {
		BigDecimal[][] ys = new BigDecimal[sigmas.length][];
		for (int i = 0; i < sigmas.length; i++) {
			int sigma = sigmas[i];
			BigDecimal[] y = new BigDecimal[x.length];
			for (int j = 0; j < x.length; j++) {
				int newC = x[j] - sigma;
				BigDecimal tvd = calculateTVDWithSigma(height, newC, zipfianPMFPerDepth);
				y[j] = tvd;
			}
			ys[i] = y;
		}
		return ys;
	}

	private BigDecimal calculateTVDWithSigma(int height, int nClients, BigDecimal[] zipfianPMFPerDepth) {
		BigDecimal tvd = BigDecimal.ZERO;
		for (int i = 1; i <= nClients; i++) {
			BigDecimal root = kLeavesUsageProbabilityForRandom(height, nClients, i);
			BigDecimal mvp = kLeavesUsageProbabilityForMVP(height, nClients, i, zipfianPMFPerDepth);
			tvd = tvd.add(root.subtract(mvp).abs());
		}
		tvd = tvd.divide(BigDecimal.valueOf(2));
		return tvd;
	}

	private BigDecimal kLeavesUsageProbabilityForMVP(int height, int nClients, int i, BigDecimal[] zipfianPMFPerDepth) {
		BigDecimal result = BigDecimal.ZERO;
		for (int d = 0; d <= height; d++) {
			int nLeavesFromDepth = 1 << (height - d);
			BigDecimal pmfAtDepth = zipfianPMFPerDepth[d];
			BigDecimal kLeavesUsageProb = kLeavesUsageProbability(nLeavesFromDepth, nClients, i);
			result = result.add(pmfAtDepth.multiply(kLeavesUsageProb));
		}
		return result;
	}

	private BigDecimal kLeavesUsageProbabilityForRandom(int height, int nClients, int i) {
		int nLeaves = 1 << height;
		return kLeavesUsageProbability(nLeaves, nClients, i);
	}

	private BigDecimal kLeavesUsageProbability(int nLeaves, int nClients, int i) {
		BigDecimal part1 = permutation(nLeaves, i);
		BigDecimal part21 = stirlingSecondKind(nClients, i);
		BigDecimal part22 = BigDecimal.valueOf(nLeaves).pow(nClients);
		BigDecimal part2 = part21.divide(part22);
		return part1.multiply(part2);
	}

	private BigDecimal permutation(int nLeaves, int i) {
		BigDecimal result = BigDecimal.ONE;
		for (int j = 0; j < i; j++) {
			result = result.multiply(BigDecimal.valueOf(nLeaves - j));
		}
		return result;
	}

	private BigDecimal stirlingSecondKind(int nClients, int i) {
		BigDecimal sum = BigDecimal.ZERO;
		for (int j = 0; j <= i; j++) {
			BigDecimal part1 = combination(i, j);
			BigDecimal base = BigDecimal.valueOf(i - j);
			BigDecimal part2 = base.pow(nClients);
			BigDecimal term = part1.multiply(part2);
			if (j % 2 == 0) {
				sum = sum.add(term);
			} else {
				sum = sum.subtract(term);
			}
		}
		return sum.divide(factorial(i));
	}

	private BigDecimal combination(int n, int k) {
		return factorial(n).divide(factorial(k).multiply(factorial(n - k)));
	}

	private BigDecimal factorial(int n) {
		BigDecimal result = BigDecimal.ONE;
		for (int i = 2; i <= n; i++) {
			result = result.multiply(BigDecimal.valueOf(i));
		}
		return result;
	}

	private BigDecimal[] precomputeZipfianPMFPerDepth(int treeHeight, double alpha, double harmonicNumber) {
		BigDecimal[] pmfPerDepth = new BigDecimal[treeHeight + 1];
		for (int depth = 0; depth <= treeHeight; depth++) {
			int nNodesAtDepth = 1 << depth;
			int nNodesUpToDepth = (1 << (depth + 1)) - 1;
			pmfPerDepth[depth] = BigDecimal.valueOf(zipfianPMF(alpha, nNodesAtDepth, nNodesUpToDepth, harmonicNumber));
		}

		return pmfPerDepth;
	}

	private double zipfianPMF(double alpha, int start, int end, double harmonicNumber) {
		double zipfian = 0.0;
		for (int i = start; i <= end; i++) {
			zipfian += 1.0 / Math.pow(i, alpha);
		}
		return zipfian / harmonicNumber;
	}

	private int calculateDistinctItems(int nBlocks, double alpha, double harmonicNumber, double threshold) {
		double cumulativeProbability = 0.0;

		for (int i = 1; i <= nBlocks; i++) {
			double denominator = harmonicNumber * Math.pow(i, alpha);
			double pi = 1.0 / denominator;
			cumulativeProbability += pi;
			if (cumulativeProbability >= threshold) {
				return i;
			}
		}
		throw new IllegalStateException("Threshold not reached within the number of blocks.");
	}

	private double computeGeneralizedHarmonicNumber(int nBlocks, double alpha) {
		double harmonicNumber = 0.0;
		for (int i = 1; i <= nBlocks; i++) {
			harmonicNumber += 1.0 / Math.pow(i, alpha);
		}
		return harmonicNumber;
	}
}
