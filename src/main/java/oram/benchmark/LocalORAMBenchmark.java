package oram.benchmark;

public class LocalORAMBenchmark {
	/*private static EncryptionManager encryptionManager;
	private static ORAMContext oramContext;
	private static SecureRandom rndGenerator;

	public static void main(String[] args) {
		int oramId = 0;
		int initClientId = 1;
		int treeHeight = 3;
		int bucketSize = 4;
		int blockSize = 32;
		int treeSize = ORAMUtils.computeNumberOfNodes(treeHeight);
		int nClients = 4;
		int nOperations = 100;
		oramContext = new ORAMContext(treeHeight, treeSize, bucketSize, blockSize);
		encryptionManager = new EncryptionManager();
		rndGenerator = new SecureRandom("oram".getBytes());
		EncryptedPositionMap initialEncryptedPositionMap = initializeEmptyPositionMap();
		EncryptedStash emptyEncryptedStash = initializeEmptyStash(oramContext.getBlockSize());
		ORAM oram = new ORAM(oramId, oramContext.getTreeHeight(), oramContext.getBucketSize(),
				oramContext.getBlockSize(), initialEncryptedPositionMap, emptyEncryptedStash);

		int[] clientOperations = new int[nOperations * nClients];
		for (int i = 0; i < clientOperations.length; i++) {
			clientOperations[i] = rndGenerator.nextInt(nClients);
		}

		byte[] newContent = new byte[blockSize];
		Arrays.fill(newContent, (byte) 'a');

		for (int clientId : clientOperations) {
			access(oram, clientId, newContent);
		}
	}

	private static void access(ORAM oram, int clientId, byte[] newContent) {
		int address = rndGenerator.nextInt(oramContext.getTreeSize());
		Operation op = Operation.WRITE;

		byte[] oldContent = null;

		PositionMaps oldPositionMaps = getPositionsMaps(oram, clientId);
		PositionMap mergedPositionMap = mergePositionMaps(oldPositionMaps.getPositionMaps());

		int pathId = mergedPositionMap.getPathAt(address);
		if (pathId == ORAMUtils.DUMMY_PATH) {
			pathId = generateRandomPathId();
		}
		Stash mergedStash = getPS(oram, clientId, pathId, oldPositionMaps, mergedPositionMap);

		Block block = mergedStash.getBlock(address);

		if (block == null) {
			block = new Block(oramContext.getBlockSize(), address, newContent);
			mergedStash.putBlock(block);
		} else {
			oldContent = block.getContent();
			block.setContent(newContent);
		}

		boolean isEvicted = evict(oram, clientId, mergedPositionMap, mergedStash, pathId,op, address, oldPositionMaps.getNewVersionId());

		if (!isEvicted) {
			System.err.println("Failed to do eviction");
		}

		//System.out.println("Old content: " + Arrays.toString(oldContent));
	}

	public static boolean evict(ORAM oram, int clientId, PositionMap positionMap, Stash stash, int oldPathId,
								Operation op, int changedAddress, int newVersionId) {
		byte newPathId = generateRandomPathId();
		if (op == Operation.WRITE){
			positionMap.setVersionIdAt(changedAddress, newVersionId);
		}
		if (op == Operation.WRITE || (op == Operation.READ && oldPathId != ORAMUtils.DUMMY_PATH)) {
			positionMap.setPathAt(changedAddress, newPathId);
		}
		int[] oldPathLocations = ORAMUtils.computePathLocations(oldPathId, oramContext.getTreeHeight());
		Map<Integer, List<Integer>> commonPaths = new HashMap<>();
		Map<Integer, Bucket> path = new HashMap<>(oramContext.getTreeLevels());
		for (int pathLocation : oldPathLocations) {
			path.put(pathLocation, new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize()));
		}
		Stash remainingBlocks = new Stash(oramContext.getBlockSize());
		for (Block block : stash.getBlocks()) {
			int address = block.getAddress();
			int pathId = positionMap.getPathAt(address);
			List<Integer> commonPath = commonPaths.get(pathId);
			if (commonPath == null) {
				int[] pathLocations = ORAMUtils.computePathLocations(pathId, oramContext.getTreeHeight());
				commonPath = ORAMUtils.computePathIntersection(oramContext.getTreeLevels(), oldPathLocations, pathLocations);
				commonPaths.put(pathId, commonPath);
			}
			boolean isPathEmpty = false;
			for (int pathLocation : commonPath) {
				Bucket bucket = path.get(pathLocation);
				if (bucket.putBlock(block)) {
					isPathEmpty = true;
					break;
				}
			}
			if (!isPathEmpty) {
				remainingBlocks.putBlock(block);
			}
		}
		EncryptedStash encryptedStash = encryptionManager.encryptStash(remainingBlocks);
		EncryptedPositionMap encryptedPositionMap = encryptionManager.encryptPositionMap(positionMap);
		Map<Integer, EncryptedBucket> encryptedPath = encryptionManager.encryptPath(oramContext, path);
		return oram.performEviction(encryptedStash, encryptedPositionMap, encryptedPath, clientId);
	}

	public static Stash getPS(ORAM oram, int clientId, int pathId, PositionMaps positionMaps,
							  PositionMap mergedPositionMap) {
		EncryptedStashesAndPaths encryptedStashesAndPaths = oram.getStashesAndPaths(pathId, clientId);
		StashesAndPaths stashesAndPaths = encryptionManager.decryptStashesAndPaths(oramContext,
				encryptedStashesAndPaths);

		return mergeStashesAndPaths(stashesAndPaths.getStashes(), stashesAndPaths.getPaths(),
				stashesAndPaths.getVersionPaths(), positionMaps, mergedPositionMap);
	}

	private static Stash mergeStashesAndPaths(Map<Integer, Stash> stashes, Map<Integer, Bucket[]> paths, Map<Integer, Set<Integer>> versionPaths, PositionMaps positionMaps, PositionMap mergedPositionMap) {
		Map<Integer, Block> recentBlocks = new HashMap<>();
		Map<Integer, Double> recentVersionIds = new HashMap<>();
		PositionMap[] positionMapsArray = positionMaps.getPositionMaps();
		int[] outstandingIds = positionMaps.getOutstandingVersionIds();
		Map<Integer, PositionMap> positionMapPerVersion = new HashMap<>(outstandingIds.length);
		for (int i = 0; i < outstandingIds.length; i++) {
			positionMapPerVersion.put(outstandingIds[i], positionMapsArray[i]);
		}

		mergeStashes(recentBlocks, recentVersionIds, stashes, versionPaths, positionMapPerVersion, mergedPositionMap);
		mergePaths(recentBlocks, recentVersionIds, paths, versionPaths, positionMapPerVersion, mergedPositionMap);

		Stash mergedStash = new Stash(oramContext.getBlockSize());
		for (Block recentBlock : recentBlocks.values()) {
			mergedStash.putBlock(recentBlock);
		}
		return mergedStash;
	}

	private static void mergePaths(Map<Integer, Block> recentBlocks, Map<Integer, Double> recentVersionIds,
								   Map<Integer, Bucket[]> paths, Map<Integer, Set<Integer>> versionPaths,
								   Map<Integer, PositionMap> positionMaps, PositionMap mergedPositionMap) {
		Map<Integer, List<Block>> blocksToMerge = new HashMap<>();
		for (Map.Entry<Integer, Bucket[]> entry : paths.entrySet()) {
			List<Block> blocks = new LinkedList<>();
			for (Bucket bucket : entry.getValue()) {
				for (Block block : bucket.readBucket()) {
					if (block != null) {
						blocks.add(block);
					}
				}
			}
			blocksToMerge.put(entry.getKey(), blocks);
		}
		selectRecentBlocks(recentBlocks, recentVersionIds, versionPaths, positionMaps, mergedPositionMap, blocksToMerge);
	}

	private static void mergeStashes(Map<Integer, Block> recentBlocks, Map<Integer, Double> recentVersionIds,
									 Map<Integer, Stash> stashes, Map<Integer, Set<Integer>> versionPaths,
									 Map<Integer, PositionMap> positionMaps, PositionMap mergedPositionMap) {
		Map<Integer, List<Block>> blocksToMerge = new HashMap<>();
		for (Map.Entry<Integer, Stash> entry : stashes.entrySet()) {
			blocksToMerge.put(entry.getKey(), entry.getValue().getBlocks());
		}
		selectRecentBlocks(recentBlocks, recentVersionIds, versionPaths, positionMaps, mergedPositionMap, blocksToMerge);
	}

	private static void selectRecentBlocks(Map<Integer, Block> recentBlocks, Map<Integer, Double> recentVersionIds,
										   Map<Integer, Set<Integer>> versionPaths, Map<Integer, PositionMap> positionMaps,
										   PositionMap mergedPositionMap, Map<Integer, List<Block>> blocksToMerge) {
		for (Map.Entry<Integer, List<Block>> entry : blocksToMerge.entrySet()) {
			Set<Integer> outstandingTreeIds = versionPaths.get(entry.getKey());
			for (Block block : entry.getValue()) {
				for (double outstandingTreeId : outstandingTreeIds) {
					PositionMap outstandingPositionMap = positionMaps.get(outstandingTreeId);
					int blockAddress = block.getAddress();
					double blockVersionIdInOutstandingTree = outstandingPositionMap.getVersionIdAt(blockAddress);
					if (blockVersionIdInOutstandingTree == mergedPositionMap.getVersionIdAt(blockAddress)) {
						recentBlocks.put(blockAddress, block);
						recentVersionIds.put(blockAddress, blockVersionIdInOutstandingTree);
					}
				}
			}
		}
	}

	private static byte generateRandomPathId() {
		return (byte) rndGenerator.nextInt(1 << oramContext.getTreeHeight()); //2^height
	}

	private static PositionMaps getPositionsMaps(ORAM oram, int clientId) {
		EncryptedPositionMaps encryptedPositionMaps = oram.getPositionMaps(clientId, request.getLastVersion());
		return encryptionManager.decryptPositionMaps(encryptedPositionMaps);
	}

	private static PositionMap mergePositionMaps(PositionMap[] positionMaps) {
		int treeSize = oramContext.getTreeSize();
		int[] pathIds = new int[treeSize];
		int[] versionIds = new int[treeSize];

		for (int address = 0; address < treeSize; address++) {
			int recentPathId = ORAMUtils.DUMMY_PATH;
			int recentVersionId = ORAMUtils.DUMMY_VERSION;
			for (PositionMap positionMap : positionMaps) {
				if (positionMap.getPathIds().length == 0)
					continue;
				int pathId = positionMap.getPathAt(address);
				int versionId = positionMap.getVersionIdAt(address);
				if (versionId != ORAMUtils.DUMMY_VERSION && versionId > recentVersionId) {
					recentVersionId = versionId;
					recentPathId = pathId;
				}
			}
			pathIds[address] = recentPathId;
			versionIds[address] = recentVersionId;
		}
		return new PositionMap(versionIds, pathIds, address);
	}

	private static EncryptedStash initializeEmptyStash(int blockSize) {
		Stash stash = new Stash(blockSize);
		return encryptionManager.encryptStash(stash);
	}

	private static EncryptedPositionMap initializeEmptyPositionMap() {
		int[] positionMap = new int[0];
		int[] versionIds = new int[0];
		PositionMap pm = new PositionMap(versionIds, positionMap, address);
		return encryptionManager.encryptPositionMap(pm);
	}*/
}
