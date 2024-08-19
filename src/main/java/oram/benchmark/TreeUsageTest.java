package oram.benchmark;

import oram.client.structure.Block;
import oram.client.structure.Bucket;
import oram.client.structure.PositionMap;
import oram.client.structure.Stash;
import oram.utils.ORAMUtils;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;

public class TreeUsageTest {
	private static Map<Integer, int[]> bucketToPaths;
	private static Map<Integer, int[]> allPaths;
	private static Map<Integer, int[]> orderedAllPaths;
	private static int treeHeight;
	private static int treeSize;
	private static int treeCapacity;
	private static int bucketSize;
	private static int blockSize;
	private static int nPaths;

	public static void main(String[] args) throws IOException {
		treeHeight = 10;
		bucketSize = 4;
		blockSize = 32 / 8;
		treeSize = ORAMUtils.computeNumberOfNodes(treeHeight);
		treeCapacity = ORAMUtils.computeTreeSize(treeHeight, bucketSize);
		nPaths = 1 << treeHeight;
		int nTests = 5;
		//int nItems = nPaths * bucketSize;
		int nItems = treeSize * bucketSize;

		preComputeAllPaths();

		System.out.println("Tree height: " + treeHeight);
		System.out.println("Number of buckets: " + bucketSize);
		System.out.println("Tree size: " + treeSize);
		System.out.println("Tree capacity: " + treeCapacity);
		System.out.println("Number of paths: " + nPaths);
		System.out.println("Block size: " + blockSize);
		System.out.println("Number of accesses to the same item: " + nTests);
		System.out.println("Number of items: " + nItems);

		System.out.println("\n========= Load Tree =========");
		Bucket[] originalTree = new Bucket[treeSize];
		PositionMap originalBucketPM = new PositionMap(treeCapacity);
		PositionMap originalPathPM = new PositionMap(treeCapacity);
		Stash originalStash = new Stash(blockSize);
		for (int i = 0; i < treeSize; i++) {
			originalTree[i] = new Bucket(bucketSize, blockSize);
		}
		loadTree(nItems, originalTree, originalStash, originalBucketPM, originalPathPM);
		printBucketUsage(originalTree, originalStash);

		Bucket[] tree;
		PositionMap positionMap;
		Stash stash;

		System.out.println("\n========= PathORAM Tree Access =========");
		tree = cloneTree(originalTree);
		positionMap = clonePositionMap(originalPathPM);
		stash = cloneStash(originalStash);
		normalTreeAccess(nTests, nItems, tree, positionMap, stash);

		System.out.println("\n========= Probabilistic Ascend with Substitution Tree Access V2 =========");
		tree = cloneTree(originalTree);
		positionMap = clonePositionMap(originalBucketPM);
		stash = cloneStash(originalStash);
		weightedTreeAccessWithSubstitutionV2(nTests, nItems, tree, positionMap, stash);

		System.out.println("\n========= Probabilistic Ascend with Substitution Tree Access =========");
		tree = cloneTree(originalTree);
		positionMap = clonePositionMap(originalBucketPM);
		stash = cloneStash(originalStash);
		weightedTreeAccessWithSubstitution(nTests, nItems, tree, positionMap, stash);

		/*System.out.println("\n========= Probabilistic Ascend Tree Access =========");
		tree = cloneTree(originalTree);
		positionMap = clonePositionMap(originalBucketPM);
		stash = cloneStash(originalStash);
		weightedTreeAccess(nTests, nItems, tree, positionMap, stash);*/
	}

	private static Bucket[] cloneTree(Bucket[] originalTree) {
		Bucket[] newTree = new Bucket[originalTree.length];
		for (int i = 0; i < originalTree.length; i++) {
			newTree[i] = new Bucket(bucketSize, blockSize);
			Block[] blocks = originalTree[i].readBucket();
			for (Block block : blocks) {
				if (block != null) {
					Block newBlock = new Block(blockSize, block.getAddress(), block.getVersionId(),
							Arrays.copyOf(block.getContent(), block.getContent().length));
					newTree[i].putBlock(newBlock);
				}
			}
		}
		return newTree;
	}

	private static PositionMap clonePositionMap(PositionMap originalPositionMap) {
		PositionMap newPositionMap = new PositionMap(treeCapacity);
		for (int i = 0; i < treeCapacity; i++) {
			newPositionMap.setPathAt(i, originalPositionMap.getPathAt(i));
			newPositionMap.setVersionIdAt(i, originalPositionMap.getVersionIdAt(i));
		}
		return newPositionMap;
	}

	private static Stash cloneStash(Stash originalStash) throws IOException {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream(originalStash.getSerializedSize());
			 DataOutputStream dos = new DataOutputStream(baos)) {
			originalStash.writeExternal(dos);
			dos.flush();
			baos.flush();
			byte[] serializedStash = baos.toByteArray();
			try (ByteArrayInputStream bais = new ByteArrayInputStream(serializedStash);
				 DataInputStream dis = new DataInputStream(bais)) {
				Stash newStash = new Stash(blockSize);
				newStash.readExternal(dis);
				return newStash;
			}
		}
	}

	private static void loadTree(int nItems, Bucket[] tree, Stash stash, PositionMap bucketPM, PositionMap pathPM) {
		SecureRandom rndGenerator = new SecureRandom();
		for (int i = 0; i < nItems; i++) {
			int pathId = rndGenerator.nextInt(nPaths);
			int[] pathLocations = allPaths.get(pathId);
			boolean isPathFull = true;
			Block block = new Block(blockSize, i, 1, String.valueOf(i).getBytes());
			pathPM.setPathAt(i, pathId);
			for (int pathLocation : pathLocations) {
				boolean wasAdded = tree[pathLocation].putBlock(block);
				if (wasAdded) {
					bucketPM.setPathAt(i, pathLocation);
					isPathFull = false;
					break;
				}
			}
			if (isPathFull) {
				stash.putBlock(block);
				bucketPM.setPathAt(i, 0);
			}
		}
	}

	private static void normalTreeAccess(int nTests, int nItems, Bucket[] tree, PositionMap positionMap, Stash stash) {
		SecureRandom rndGenerator = new SecureRandom();
		for (int itemAddress = 0; itemAddress < nItems; itemAddress++) {
			for (int t = 0; t < nTests; t++) {
				int itemLocation = positionMap.getPathAt(itemAddress);
				if (itemLocation == ORAMUtils.DUMMY_PATH) {
					itemLocation = rndGenerator.nextInt(nPaths);
				}

				int[] pathLocations = ORAMUtils.computePathLocations(itemLocation, treeHeight);

				//Reading path
				for (int pathLocation : pathLocations) {
					if (tree[pathLocation] != null) {
						Block[] blocks = tree[pathLocation].readBucket();
						for (Block block : blocks) {
							if (block != null) {
								stash.putBlock(block);
							}
						}
					}
				}

				//Accessing item
				Block block = stash.getBlock(itemAddress);
				if (block == null) {
					throw new IllegalStateException("Block not found in stash");
				}
				int newItemLocation = rndGenerator.nextInt(nPaths);

				// System.out.printf("Item %d moved from %d to %d\n", itemAddress, itemLocation, newItemLocation);
				positionMap.setPathAt(itemAddress, newItemLocation);

				//Writing item
				stash = populatePath(tree, positionMap, stash, pathLocations);
			}

		}

		//printTreeUsage(tree, treeHeight, stash);
		printBucketUsage(tree, stash);
	}

	private static void weightedTreeAccess(int nTests, int nItems, Bucket[] tree, PositionMap positionMap, Stash stash) {
		SecureRandom rndGenerator = new SecureRandom();

		for (int itemAddress = 0; itemAddress < nItems; itemAddress++) {
			if (itemAddress % 1000 == 0) {
				System.out.printf("Item %d out of %d\n", itemAddress, nItems);
			}
			for (int t = 0; t < nTests; t++) {
				int itemLocation = positionMap.getPathAt(itemAddress);
				if (itemLocation == ORAMUtils.DUMMY_PATH) {
					itemLocation = rndGenerator.nextInt(treeSize);
				}
				// System.out.printf("Item %d accessed at %d\n", itemAddress, itemLocation);
				int pathId = getRandomPathForBucket(itemLocation, rndGenerator);
				// System.out.printf("Item %d accessed at %d, path %d\n", itemAddress, itemLocation, pathId);
				int[] pathLocations = allPaths.get(pathId);
				int[] orderedPathLocations = orderedAllPaths.get(pathId);
				// System.out.printf("Path %d: %s\n", pathId, Arrays.toString(pathLocations));
				//Reading path
				for (int pathLocation : pathLocations) {
					if (tree[pathLocation] != null) {
						Block[] blocks = tree[pathLocation].readBucket();
						for (Block block : blocks) {
							if (block != null) {
								stash.putBlock(block);
							}
						}
					}
				}

				//Accessing item
				Block block = stash.getBlock(itemAddress);
				if (block == null) {
					throw new IllegalStateException("Block not found in stash");
				}
				int newItemLocation = selectNewRandomUpperLocationBasedOnWeight(orderedPathLocations, itemLocation);
				//int newItemLocation = selectNewRandomLocationUniformly(pathLocations, itemLocation, rndGenerator);
				if (newItemLocation == -1) {
					throw new IllegalStateException("New item location not found");
				}
				// System.out.printf("Item %d moved from %d to %d\n", itemAddress, itemLocation, newItemLocation);
				positionMap.setPathAt(itemAddress, newItemLocation);

				//Writing item
				stash = populateBuckets(tree, positionMap, stash, pathLocations);
			}

		}
		printBucketUsage(tree, stash);
	}

	private static void weightedTreeAccessWithSubstitution(int nTests, int nItems, Bucket[] tree,
														   PositionMap positionMap, Stash stash) {
		SecureRandom rndGenerator = new SecureRandom();

		for (int itemAddress = 0; itemAddress < nItems; itemAddress++) {
			if (itemAddress % 1000 == 0) {
				System.out.printf("Item %d out of %d\n", itemAddress, nItems);
			}
			for (int t = 0; t < nTests; t++) {
				int itemLocation = positionMap.getPathAt(itemAddress);
				if (itemLocation == ORAMUtils.DUMMY_PATH) {
					itemLocation = rndGenerator.nextInt(treeSize);
					// System.out.printf("Reading dummy bucket for item %d\n", itemAddress);
				}
				// System.out.printf("Item %d accessed at %d\n", itemAddress, itemLocation);
				int pathId = getRandomPathForBucket(itemLocation, rndGenerator);
				int[] pathLocations = allPaths.get(pathId);
				int[] orderedPathLocations = orderedAllPaths.get(pathId);
				// System.out.printf("Item %d accessed at %d, path %d (%s)\n", itemAddress, itemLocation, pathId, Arrays.toString(pathLocations));
				// System.out.printf("Path %d: %s\n", pathId, Arrays.toString(pathLocations));
				//Reading path
				for (int pathLocation : pathLocations) {
					if (tree[pathLocation] != null) {
						Block[] blocks = tree[pathLocation].readBucket();
						for (Block block : blocks) {
							if (block != null) {
								stash.putBlock(block);
							}
						}
					}
				}

				//Accessing item
				Block block = stash.getBlock(itemAddress);
				if (block == null) {
					System.out.println("====== Debugging ======");
					System.out.println("Stash: " + stash.getBlocks());
					System.out.printf("Accessing location %d at path %d\n", itemLocation, pathId);
					System.out.printf("Accessing path %d and locations %s\n", pathId, Arrays.toString(pathLocations));
					//find the missing block in the tree
					for (int i = 0; i < tree.length; i++) {
						for (Block b : tree[i].readBucket()) {
							if (b != null && b.getAddress() == itemAddress) {
								System.out.printf("Block %d found in bucket %d\n", itemAddress, i);
							}
						}
					}
					throw new IllegalStateException("Block " + itemAddress + " not found in stash");
				}
				int newItemLocation = selectNewRandomUpperLocationBasedOnWeight(orderedPathLocations, itemLocation);
				//int newItemLocation = selectNewRandomLocationUniformly(pathLocations, itemLocation, rndGenerator);
				if (newItemLocation == -1) {
					throw new IllegalStateException("New item location not found");
				}
				// System.out.printf("Item %d moved up from %d to %d\n", itemAddress, itemLocation, newItemLocation);
				positionMap.setPathAt(itemAddress, newItemLocation);

				//Move an item from location newItemLocation to location below
				int newDownLocation = selectNewRandomDownLocationBasedOnWeight(orderedPathLocations, newItemLocation);
				// System.out.printf("New down location: %d\n", newDownLocation);
				if (newDownLocation == -1) {
					throw new IllegalStateException("New down location not found");
				}


				for (Block stashBlock : stash.getBlocks()) {
					if (positionMap.getPathAt(stashBlock.getAddress()) == newItemLocation && stashBlock.getAddress() != itemAddress) {
						// System.out.printf("Item %d moved down from %d to %d\n", stashBlock.getAddress(), newItemLocation, newDownLocation);
						positionMap.setPathAt(stashBlock.getAddress(), newDownLocation);
						break;
					}
				}

				//Writing item
				stash = populateBuckets(tree, positionMap, stash, pathLocations);
			}

		}
		printBucketUsage(tree, stash);
	}

	private static void weightedTreeAccessWithSubstitutionV2(int nTests, int nItems, Bucket[] tree,
															 PositionMap positionMap, Stash stash) {
		SecureRandom rndGenerator = new SecureRandom();

		for (int itemAddress = 0; itemAddress < nItems; itemAddress++) {
			if (itemAddress % 1000 == 0) {
				System.out.printf("Item %d out of %d\n", itemAddress, nItems);
			}
			for (int t = 0; t < nTests; t++) {
				int itemLocation = positionMap.getPathAt(itemAddress);
				if (itemLocation == ORAMUtils.DUMMY_PATH) {
					itemLocation = rndGenerator.nextInt(treeSize);
					// System.out.printf("Reading dummy bucket for item %d\n", itemAddress);
				}
				// System.out.printf("Item %d accessed at %d\n", itemAddress, itemLocation);
				int pathId = getRandomPathForBucket(itemLocation, rndGenerator);
				int[] pathLocations = allPaths.get(pathId);
				int[] orderedPathLocations = orderedAllPaths.get(pathId);
				// System.out.printf("Item %d accessed at %d, path %d (%s)\n", itemAddress, itemLocation, pathId, Arrays.toString(pathLocations));
				// System.out.printf("Path %d: %s\n", pathId, Arrays.toString(pathLocations));
				//Reading path
				for (int pathLocation : pathLocations) {
					if (tree[pathLocation] != null) {
						Block[] blocks = tree[pathLocation].readBucket();
						for (Block block : blocks) {
							if (block != null) {
								stash.putBlock(block);
							}
						}
					}
				}

				//Accessing item
				Block block = stash.getBlock(itemAddress);
				if (block == null) {
					System.out.println("====== Debugging ======");
					System.out.println("Stash: " + stash.getBlocks());
					System.out.printf("Accessing location %d at path %d\n", itemLocation, pathId);
					System.out.printf("Accessing path %d and locations %s\n", pathId, Arrays.toString(pathLocations));
					//find the missing block in the tree
					for (int i = 0; i < tree.length; i++) {
						for (Block b : tree[i].readBucket()) {
							if (b != null && b.getAddress() == itemAddress) {
								System.out.printf("Block %d found in bucket %d\n", itemAddress, i);
							}
						}
					}
					throw new IllegalStateException("Block " + itemAddress + " not found in stash");
				}

				int newItemLocation = selectNewRandomUpperLocationBasedOnWeight(orderedPathLocations, itemLocation);
				//int newItemLocation = selectNewRandomLocationUniformly(orderedPathLocations, itemLocation, rndGenerator);
				//int newItemLocation = rndGenerator.nextInt(itemLocation + 1);

				if (newItemLocation == -1) {
					throw new IllegalStateException("New item location not found");
				}
				// System.out.printf("Item %d moved up from %d to %d\n", itemAddress, itemLocation, newItemLocation);
				positionMap.setPathAt(itemAddress, newItemLocation);

				//Move an item from location newItemLocation to location below
				//int newDownLocation = selectNewRandomDownLocationBasedOnWeight(orderedPathLocations, newItemLocation);
				int newDownLocation = rndGenerator.nextInt(treeSize - newItemLocation) + newItemLocation;

				// System.out.printf("New down location: %d\n", newDownLocation);
				if (newDownLocation == -1) {
					throw new IllegalStateException("New down location not found");
				}


				for (Block stashBlock : stash.getBlocks()) {
					if (positionMap.getPathAt(stashBlock.getAddress()) == newItemLocation && stashBlock.getAddress() != itemAddress) {
						// System.out.printf("Item %d moved down from %d to %d\n", stashBlock.getAddress(), newItemLocation, newDownLocation);
						positionMap.setPathAt(stashBlock.getAddress(), newDownLocation);
						break;
					}
				}

				//Writing item
				stash = populateBuckets(tree, positionMap, stash, pathLocations);
			}

		}
		printBucketUsage(tree, stash);
	}

	private static int selectNewRandomLocationUniformly(int[] sortedPathLocations, int itemLocation, SecureRandom rndGenerator) {
		int indexOfOldItemLocation = Arrays.binarySearch(sortedPathLocations, itemLocation);
		return sortedPathLocations[rndGenerator.nextInt(indexOfOldItemLocation + 1)];
	}

	private static int selectNewRandomUpperLocationBasedOnWeight(int[] sortedPathLocations, int itemLocation) {
		//Compute new item location based on probability. The item will go up in the path with higher probability

		int indexOfOldItemLocation = Arrays.binarySearch(sortedPathLocations, itemLocation);
		//System.out.printf("Sorted path locations: %s\n", Arrays.toString(sortedPathLocations));
		//System.out.printf("Index of old item in sorted locations: %d\n", indexOfOldItemLocation);
		int[] prefixLocations = new int[indexOfOldItemLocation + 1];

		int[] weights = new int[indexOfOldItemLocation + 1];
		int totalWeight = 0;
		for (int i = 0; i <= indexOfOldItemLocation; i++) {
			prefixLocations[i] = sortedPathLocations[i];
			weights[i] = indexOfOldItemLocation - i + 1;
			totalWeight += weights[i];
			//System.out.printf("Location %d, weight %d\n", prefixLocations[i], weights[i]);
		}

		double r = Math.random() * totalWeight;
		double countWeight = 0;
		for (int i = 0; i < prefixLocations.length; i++) {
			countWeight += weights[i];
			if (countWeight >= r) {
				return prefixLocations[i];
			}
		}
		return -1;
	}

	private static int selectNewRandomDownLocationBasedOnWeight(int[] sortedPathLocations, int itemLocation) {
		//Compute new item location based on probability. The item will go up in the path with higher probability

		int indexOfOldItemLocation = Arrays.binarySearch(sortedPathLocations, itemLocation);
		//System.out.printf("Sorted path locations: %s\n", Arrays.toString(sortedPathLocations));
		//System.out.printf("Index of old item in sorted locations: %d\n", indexOfOldItemLocation);
		int[] suffixLocations = new int[sortedPathLocations.length - indexOfOldItemLocation];

		int[] weights = new int[sortedPathLocations.length - indexOfOldItemLocation];
		int totalWeight = 0;
		for (int i = indexOfOldItemLocation, j = 0; i < sortedPathLocations.length; i++, j++) {
			suffixLocations[j] = sortedPathLocations[i];
			weights[j] = i + 1;
			totalWeight += weights[j];
		}
		//System.out.printf("Suffix locations: %s\n", Arrays.toString(suffixLocations));
		//System.out.printf("Weights: %s\n", Arrays.toString(weights));
		double r = Math.random() * totalWeight;
		double countWeight = 0;
		for (int i = 0; i < suffixLocations.length; i++) {
			countWeight += weights[i];
			if (countWeight >= r) {
				return suffixLocations[i];
			}
		}
		return -1;
	}

	private static void printBucketUsage(Bucket[] tree, Stash stash) {
		printBinaryTree(tree);
		/*double[] normalizedTreeUsage = new double[tree.length];
		for (int i = 0; i < tree.length; i++) {
			long realBlocks = Arrays.stream(tree[i].readBucket()).filter(Objects::nonNull).count();
			normalizedTreeUsage[i] = (double) realBlocks / bucketSize * 100;
		}
		for (int i = 0; i <= treeHeight; i++) {
			int levelSize = 1 << i;
			int levelStartIndex = (1 << i) - 1;
			for (int j = 0; j < levelSize; j++) {
				System.out.printf("%.0f\t", normalizedTreeUsage[levelStartIndex + j]);
			}
			System.out.println();
		}*/
		System.out.printf("Stash usage: %d\n", stash.getBlocks().size());
	}

	private static void printTreeUsage(Bucket[] tree, Stash stash) {
		for (int i = 0; i <= treeHeight; i++) {
			int levelSize = 1 << i;
			int levelStartIndex = (1 << i) - 1;
			for (int j = 0; j < levelSize; j++) {
				System.out.printf("%d\t", Arrays.stream(tree[levelStartIndex + j].readBucket()).filter(Objects::nonNull).count());
			}
			System.out.println();
		}
		//System.out.printf("Stash usage: %.0f\n", (double) stash.getBlocks().size() / nTests * 100);
		System.out.printf("Stash usage: %d\n", stash.getBlocks().size());
	}

	private static int getRandomPathForBucket(int bucketId, SecureRandom rndGenerator) {

		int[] possiblePathsArray = bucketToPaths.get(bucketId);
		// System.out.printf("Possible paths for bucket %d: %s\n", bucketId, Arrays.toString(possiblePathsArray));
		return possiblePathsArray[rndGenerator.nextInt(possiblePathsArray.length)];
	}

	private static Stash populateBuckets(Bucket[] tree, PositionMap positionMap, Stash stash, int[] oldPathLocations) {

		for (int pathLocation : oldPathLocations) {
			tree[pathLocation] = new Bucket(bucketSize, blockSize);
		}
		Stash remainingBlocks = new Stash(blockSize);
		for (Block block : stash.getBlocks()) {
			int address = block.getAddress();
			int bucketId = positionMap.getPathAt(address);

			int pathIdForBucket = bucketToPaths.get(bucketId)[0];
			int[] pathForBucket = allPaths.get(pathIdForBucket);

			boolean isPathEmpty = false;
			for (int i = 0; i <= treeHeight; i++) {
				if (oldPathLocations[i] == pathForBucket[i] && bucketId >= oldPathLocations[i]) {
					Bucket bucket = tree[oldPathLocations[i]];
					// System.out.printf("Block %d stored in bucket %d (buckedId: %d)\n", address, oldPathLocations[i], bucketId);
					if (bucket.putBlock(block)) {
						isPathEmpty = true;
						break;
					}
				}
			}

			if (!isPathEmpty) {
				remainingBlocks.putBlock(block);
			}
		}
		return remainingBlocks;
	}

	private static Stash populatePath(Bucket[] tree, PositionMap positionMap, Stash stash, int[] oldPathLocations) {
		for (int pathLocation : oldPathLocations) {
			tree[pathLocation] = new Bucket(bucketSize, blockSize);
		}
		Map<Integer, List<Integer>> commonPaths = new HashMap<>();
		Stash remainingBlocks = new Stash(blockSize);

		for (Block block : stash.getBlocks()) {
			int address = block.getAddress();
			int pathId = positionMap.getPathAt(address);
			List<Integer> commonPath = commonPaths.get(pathId);
			if (commonPath == null) {
				int[] pathLocations = allPaths.get(pathId);
				commonPath = ORAMUtils.computePathIntersection(treeHeight + 1, oldPathLocations,
						pathLocations);
				commonPaths.put(pathId, commonPath);
			}
			boolean isPathEmpty = false;
			for (int pathLocation : commonPath) {
				Bucket bucket = tree[pathLocation];
				if (bucket.putBlock(block)) {
					isPathEmpty = true;
					break;
				}
			}
			if (!isPathEmpty) {
				remainingBlocks.putBlock(block);
			}
		}
		return remainingBlocks;
	}

	private static void preComputeAllPaths() {
		allPaths = new HashMap<>(nPaths);
		orderedAllPaths = new HashMap<>(nPaths);
		Map<Integer, Set<Integer>> bucketToPathsTemp = new HashMap<>(treeSize);
		for (int i = 0; i < nPaths; i++) {
			int[] path = ORAMUtils.computePathLocations(i, treeHeight);
			int[] orderedPath = Arrays.copyOf(path, path.length);
			Arrays.sort(orderedPath);

			allPaths.put(i, path);
			orderedAllPaths.put(i, orderedPath);

			for (int pathLocation : path) {
				if (!bucketToPathsTemp.containsKey(pathLocation)) {
					bucketToPathsTemp.put(pathLocation, new HashSet<>());
				}
				bucketToPathsTemp.get(pathLocation).add(i);
			}
		}
		bucketToPaths = new HashMap<>(treeSize);
		for (int i = 0; i < treeSize; i++) {
			Set<Integer> possiblePaths = bucketToPathsTemp.get(i);
			int[] possiblePathsArray = new int[possiblePaths.size()];
			int j = 0;
			for (int possiblePath : possiblePaths) {
				possiblePathsArray[j++] = possiblePath;
			}
			bucketToPaths.put(i, possiblePathsArray);
		}
	}

	public static void printBinaryTree(Bucket[] array) {
		int n = array.length;
		int index = 0;
		for (int level = 0; level <= treeHeight; level++) {
			int levelCount = (int) Math.pow(2, level);
			int spaceCount = (int) Math.pow(2, treeHeight - level) - 1;
			printSpaces(spaceCount);
			for (int i = 0; i < levelCount && index < n; i++) {
				System.out.print(Arrays.stream(array[index++].readBucket()).filter(Objects::nonNull).count());
				if (i < levelCount - 1) {
					printSpaces(2 * spaceCount + 1);
				}
			}
			System.out.println();
		}
	}

	private static void printSpaces(int count) {
		for (int i = 0; i < count; i++) {
			System.out.print(" ");
		}
	}
}
