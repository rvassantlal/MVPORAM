package oram.benchmark;

import oram.client.structure.Block;
import oram.client.structure.Bucket;
import oram.client.structure.Stash;
import oram.utils.ORAMUtils;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PathAllocationTest {
	public static void main(String[] args) {
		int treeHeight = 15;
		int bucketSize = 4;
		int blockSize = 16;
		int treeLevels = treeHeight + 1;
		int pathId = 3;
		int nBlocks = treeHeight * 4;
		System.out.println("========= Bucket Strategy =========");
		bucketStrategy(treeHeight, bucketSize, blockSize, treeLevels, pathId, nBlocks);
		System.out.println("========= Path Strategy =========");
		pathStrategy(treeHeight, bucketSize, blockSize, treeLevels, pathId, nBlocks);
	}

	private static void bucketStrategy(int treeHeight, int bucketSize, int blockSize, int treeLevels, int pathId, int nBlocks) {
		SecureRandom rndGenerator = new SecureRandom();
		int[] pathLocations = ORAMUtils.computePathLocations(pathId, treeHeight);
		Map<Integer, Bucket> path = new HashMap<>(treeLevels);
		for (int pathLocation : pathLocations) {
			path.put(pathLocation, new Bucket(bucketSize, blockSize));
		}

		Stash remainingBlocks = new Stash(blockSize);
		for (int j = 0; j < nBlocks; j++) {
			byte[] data = String.valueOf(j).getBytes();

			Block block = new Block(blockSize, j, 1, data);
			int bucketAddress = pathLocations[rndGenerator.nextInt(pathLocations.length)];

			boolean isPathEmpty = false;
			for (int pathLocation : pathLocations) {
				if (bucketAddress < pathLocation)
					continue;
				Bucket bucket = path.get(pathLocation);
				if (bucket.putBlock(block)) {
					isPathEmpty = true;
					break;
				} else {
					System.out.println("Bucket " + pathLocation + " is full, for block " + block);
				}
			}
			if (!isPathEmpty) {
				remainingBlocks.putBlock(block);
			}
		}

		System.out.println("Remaining blocks: " + remainingBlocks.getBlocks());
		for (int pathLocation : pathLocations) {
			Bucket bucket = path.get(pathLocation);
			System.out.println("Path location: " + pathLocation + ", blocks: " + bucket.toString());
		}
	}

	private static void pathStrategy(int treeHeight, int bucketSize, int blockSize, int treeLevels, int pathId, int nBlocks) {
		SecureRandom rndGenerator = new SecureRandom();
		int[] pathLocations = ORAMUtils.computePathLocations(pathId, treeHeight);
		Map<Integer, Bucket> path = new HashMap<>(treeLevels);
		for (int pathLocation : pathLocations) {
			path.put(pathLocation, new Bucket(bucketSize, blockSize));
		}


		Stash remainingBlocks = new Stash(blockSize);
		Map<Integer, List<Integer>> commonPaths = new HashMap<>();
		for (int j = 0; j < nBlocks; j++) {
			byte[] data = String.valueOf(j).getBytes();

			Block block = new Block(blockSize, j, 1, data);
			int newPathId = rndGenerator.nextInt(1 << treeHeight);
			List<Integer> commonPath = commonPaths.get(newPathId);
			if (commonPath == null) {
				int[] newPathLocations = ORAMUtils.computePathLocations(newPathId, treeHeight);
				commonPath = ORAMUtils.computePathIntersection(treeLevels, pathLocations, newPathLocations);
				commonPaths.put(newPathId, commonPath);
			}

			boolean isPathEmpty = false;
			for (int pathLocation : commonPath) {
				Bucket bucket = path.get(pathLocation);
				if (bucket.putBlock(block)) {
					isPathEmpty = true;
					break;
				} else {
					System.out.println("Bucket " + pathLocation + " is full, for block " + block);
				}
			}
			if (!isPathEmpty) {
				remainingBlocks.putBlock(block);
			}
		}

		System.out.println("Remaining blocks: " + remainingBlocks.getBlocks());
		for (int pathLocation : pathLocations) {
			Bucket bucket = path.get(pathLocation);
			System.out.println("Path location: " + pathLocation + ", blocks: " + bucket.toString());
		}
	}
}
