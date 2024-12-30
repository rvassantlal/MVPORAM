package oram.testers;

import oram.utils.ORAMUtils;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.commons.math3.distribution.ZipfDistribution;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DistributionTest {
	public static void main(String[] args) {
		int nClients = 5;
		long duration = 30 * 24 * 60 * 60;
		System.out.println(duration);
		long nTests = 1000 * duration;
		System.out.println(nTests);
		int height = 15;
		int bucketSize = 4;

		int poissonDistributionMean = 1;
		PoissonDistribution poissonDistribution = new PoissonDistribution(poissonDistributionMean, nClients);
		System.out.println(poissonDistribution.getMean());
		System.out.println(poissonDistribution.getNumericalMean());
		System.out.println(poissonDistribution.getSupportLowerBound());
		System.out.println(poissonDistribution.getSupportUpperBound());
		Set<Integer> samples = new HashSet<>();
		for (long i = 0; i < nTests; i++) {
			samples.add(poissonDistribution.sample());
		}
		System.out.println(samples.size());
		System.out.println(samples);

		/*int treeSize = ORAMUtils.computeTreeSize(height, bucketSize);
		System.out.println("Tree size: " + treeSize);
		System.out.println("Tree size / 2: " + treeSize / 2);
		double[] zipfParameters = {
				0.01,
				0.1,
				0.5,
				1,
				2,
				4
		};

		for (int i = 0; i < nClients; i++) {
			System.out.println("Client " + i);
			for (double zipfParameter : zipfParameters) {
				ZipfDistribution distribution = new ZipfDistribution(treeSize, zipfParameter);
				Set<Integer> samples = new HashSet<>(nTests);
				for (int i1 = 0; i1 < nTests; i1++) {
					samples.add(distribution.sample() - 1);
				}
				System.out.println("\tZipf: " + zipfParameter + " -> " + samples.size());

			}
		}*/

	}
}
