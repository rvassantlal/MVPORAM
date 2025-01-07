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


		int treeSize = ORAMUtils.computeNumberOfNodes(height);
		System.out.println("Tree size: " + treeSize);
		System.out.println("Tree size / 2: " + treeSize / 2);
		double[] zipfParameters = {
				0.000001,
				0.1,
				0.5,
				0.9,
				1,
				2,
				4
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

	}
}
