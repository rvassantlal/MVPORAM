package oram.testers;

import oram.client.structure.*;
import oram.utils.ORAMUtils;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PathDistributionTest {
	private static HashMap<Integer, int[]> allPaths;
	private static Map<Integer, int[]> bucketToPaths;
	private static final int height = 10;
	private static final int bucketSize = 4;
	private static final int blockSize = 10;

	public static void main(String[] args) {
		SecureRandom rndGenerator = new SecureRandom();
		preComputeTreeLocations(height);
		int nPaths = 1 << height;
		int pathId = rndGenerator.nextInt(nPaths);
		int N = ORAMUtils.computeNumberOfNodes(height);
		System.out.println("Height: " + height);
		System.out.println("Bucket size: " + bucketSize);
		System.out.println("Populating path: " + pathId);
		System.out.println("N: " + pathId);
		int pathLength = height + 1;
		int pathCapacity = (height + 1) * bucketSize;
		System.out.println("Path length: " + pathLength);
		System.out.println("Path capacity: " + pathCapacity);

		//Generate random blocks
		int numBlocksToGenerate = rndGenerator.nextInt(pathCapacity);
		System.out.println("Number of blocks to generate: " + numBlocksToGenerate);
		Stash stash = new Stash(blockSize);
		PositionMap positionMap = new PositionMap(N);
		int[] accessedPathLocations = allPaths.get(pathId);
		for (int i = 0; i < numBlocksToGenerate; i++) {
			if (i == 0) {
				positionMap.update(i, 0, 1, 1, 1);
			} else {
				positionMap.update(i, accessedPathLocations[rndGenerator.nextInt(accessedPathLocations.length)], 1, 1, 1);
			}
			Block newBlock = new Block(blockSize, i, 1, "test".getBytes());
			stash.putBlock(newBlock);
		}

		PathMap pathMap = new PathMap(pathCapacity);
		HashMap<Integer, Bucket> pathToPopulate = new HashMap<>(pathLength);
		populatePathAccessedAndNewBlockToRoot(pathId, 0, stash, positionMap, pathToPopulate, pathMap);

		pathToPopulate.keySet().stream().sorted().forEach( key -> {
			Bucket bucket = pathToPopulate.get(key);
			System.out.println(key + ": " + bucket);
		});
		System.out.println(stash);
		System.out.println(pathMap);
	}

	private static void populatePathAccessedAndNewBlockToRoot(int pathId, int accessedAddress, Stash stash, PositionMap positionMap,
													   Map<Integer, Bucket> pathToPopulate, PathMap pathMap) {
		int[] accessedPathLocations = allPaths.get(pathId);
		Set<Integer> bucketWithAvailableCapacity = new HashSet<>(accessedPathLocations.length);
		for (int pathLocation : accessedPathLocations) {
			bucketWithAvailableCapacity.add(pathLocation);
			pathToPopulate.put(pathLocation, new Bucket(bucketSize, blockSize, pathLocation));
		}

		//Move accessed block to the root bucket
		Block accessedBlock = stash.getAndRemoveBlock(accessedAddress);
		if (accessedBlock != null) {
			pathToPopulate.get(0).putBlock(accessedBlock);
			pathMap.setLocation(accessedAddress, 0, accessedBlock.getVersion(), accessedBlock.getAccess());
			if (pathToPopulate.get(0).isFull()) {
				bucketWithAvailableCapacity.remove(0);
			}
		}

		Set<Integer> blocksToEvict = new HashSet<>(stash.getBlocks().keySet());

		for (int address : blocksToEvict) {
			if (bucketWithAvailableCapacity.isEmpty()) {
				break;
			}
			Block block = stash.getAndRemoveBlock(address);
			int bucketId = positionMap.getLocation(address);

			//if it was not allocated to root bucket or the root bucket does not have space
			if (bucketId != 0 || !bucketWithAvailableCapacity.contains(0)) {
				bucketId = bucketWithAvailableCapacity.iterator().next();
			}
			Bucket bucket = pathToPopulate.get(bucketId);
			bucket.putBlock(block);
			if (bucket.isFull()) {
				bucketWithAvailableCapacity.remove(bucketId);
			}
			pathMap.setLocation(address, bucketId, block.getVersion(), block.getAccess());
		}

		for (Block value : stash.getBlocks().values()) {
			pathMap.setLocation(value.getAddress(), ORAMUtils.DUMMY_LOCATION, value.getVersion(), value.getAccess());
		}
	}

	private static void preComputeTreeLocations(int treeHeight) {
		int nPaths = 1 << treeHeight;
		int treeSize = ORAMUtils.computeNumberOfNodes(treeHeight);
		allPaths = new HashMap<>(nPaths);
		Map<Integer, Set<Integer>> tempBucketToPaths = new HashMap<>(treeSize);
		for (int pathId = 0; pathId < nPaths; pathId++) {
			int[] pathLocations = ORAMUtils.computePathLocations(pathId, treeHeight);
			allPaths.put(pathId, pathLocations);
			for (int pathLocation : pathLocations) {
				tempBucketToPaths.computeIfAbsent(pathLocation, k -> new HashSet<>()).add(pathId);
			}
		}
		bucketToPaths = new HashMap<>(treeSize);
		for (int i = 0; i < treeSize; i++) {
			Set<Integer> possiblePaths = tempBucketToPaths.get(i);
			int[] possiblePathsArray = new int[possiblePaths.size()];
			int j = 0;
			for (int possiblePath : possiblePaths) {
				possiblePathsArray[j++] = possiblePath;
			}
			bucketToPaths.put(i, possiblePathsArray);
		}
	}
}
