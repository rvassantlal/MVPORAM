package oram.testers;

import oram.utils.ORAMUtils;
import org.apache.commons.math3.distribution.ZipfDistribution;

import java.util.HashSet;
import java.util.Set;

public class DistributionTest {
	public static void main(String[] args) {
		int nClients = 5;
		long duration = 30 * 24 * 60 * 60;
		System.out.println(duration);
		long nTests = 10000;
		System.out.println(nTests);
		int height = 17;
		int bucketSize = 4;
		int pathCapacity = bucketSize * (height + 1);
		int k = bucketSize;
		System.out.println("Path capacity: " + pathCapacity);

		int treeSize = ORAMUtils.computeNumberOfNodes(height);
		System.out.println("Tree size: " + treeSize);
		double[] zipfParameters = {
				0.000001,
				0.9,
				1,
				2,
				1.5,
		};

		for (int i = 0; i < nClients; i++) {
			System.out.println("Client " + i);
			for (double zipfParameter : zipfParameters) {
				ZipfDistribution distribution = new ZipfDistribution(treeSize, zipfParameter);
				Set<Integer> samples = new HashSet<>((int)nTests);
				for (int i1 = 0; i1 < nTests; i1++) {
					samples.add(distribution.sample() - 1);
				}
				System.out.println("\tZipf: " + zipfParameter + " -> " + samples.size());

			}
		}

		System.out.println("Accumulative");
		System.out.println("Tree size: " + treeSize);
		double[] percentage = {
				0.0001,
				0.00015,
				0.0002,
				0.00027,
				0.000276,
				0.27,
				0.46,
				0.9,
		};

		for (double zipfParameter : zipfParameters) {
			ZipfDistribution distribution = new ZipfDistribution(treeSize, zipfParameter);
			System.out.println("Zipf: " + zipfParameter);
			for (double p : percentage) {
				int values = (int) (treeSize * p);
				double v = distribution.cumulativeProbability(values);
				System.out.println("\t" + (Math.round(p*100)) + "% -> " + values + " -> " + Math.round(v*100) + "%");
			}
		}
		System.out.println(72.0/treeSize);
	}
}
