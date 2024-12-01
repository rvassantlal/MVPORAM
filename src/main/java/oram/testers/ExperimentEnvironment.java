package oram.testers;

import oram.client.metadata.*;
import oram.client.structure.Block;
import oram.client.structure.EvictionMap;
import oram.client.structure.PositionMap;
import oram.client.structure.Stash;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import oram.utils.PositionMapType;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class ExperimentEnvironment {
	private static PositionMap positionMap;
	private static ORAMContext context;
	private static Map<Integer, EvictionMap> evictionMaps;
	private static Map<Integer, int[]> outstandingVersions;
	private static Map<Integer, Map<Integer, ArrayList<Pair<Block, Integer>>>> trees;
	private static Map<Integer, Map<Integer, Stash>> stashes;
	private static Map<Integer, Integer> accessedPathsHistory;
	private static Set<Integer> lastVersions;

	private static void loadData() {
		String dataFolder = "C:\\Users\\robin\\Desktop\\oram\\";
		String traceErrorFilename1 = dataFolder + "oramErrorTrace_1730991971295_client_100.txt";
		String traceErrorFilename2 = dataFolder + "oramErrorTrace_1730991980737_client_200.txt";
		lastVersions = new HashSet<>(2);
		deserializeTraceError(traceErrorFilename1);
		deserializeTraceError(traceErrorFilename2);

		accessedPathsHistory.put(1, -1);
		stashes.put(1, new HashMap<>());
	}

	public static void main(String[] args) {
		StringBuilder messageBuilder = new StringBuilder();

		loadData();

		int blockOfInterest = 2;
		int problematicVersion = Collections.min(lastVersions);

		BlockMetadataManager metadataManager = new BlockMetadataManager();
		metadataManager.setBlockOfInterest(blockOfInterest);
		metadataManager.processMetadata(evictionMaps, outstandingVersions, problematicVersion, messageBuilder);
		System.out.println(messageBuilder);

		if (true) {
			return;
		}

		Map<Integer, Stash> problematicVersionStashes = ExperimentEnvironment.stashes.get(problematicVersion);
		Map<Integer, Block> stash = mergeStashes(problematicVersion,
				positionMap, problematicVersionStashes, metadataManager);

		Map<Integer, BlockMetadata> metadata = metadataManager.getMetadata(problematicVersion);
		Map<Integer, ArrayList<Pair<Block, Integer>>> blocksGroupedByAddress = trees.get(problematicVersion);
		Set<Integer> unknownBlocks = new HashSet<>();
		Set<Integer> strangeBlocks = new HashSet<>();

		for (int address = 0; address < context.getTreeSize(); address++) {
			int pmLocation = positionMap.getLocationOf(address);
			if (pmLocation == ORAMUtils.DUMMY_LOCATION) {
				continue;
			}
			ArrayList<Pair<Block, Integer>> blocksInTree = blocksGroupedByAddress.get(address);
			Block blockInStash = stash.get(address);
			if (blocksInTree == null && blockInStash == null) {
				unknownBlocks.add(address);
				continue;
			}

			int pmContentVersion = positionMap.getContentVersionOf(address);
			int pmLocationVersion = positionMap.getLocationVersionOf(address);
			BlockMetadata blockMetadata = metadata.get(address);
			System.out.println(address);
			int mBlockLocation = blockMetadata.getHighestLocation();
			int mBlockContentVersion = blockMetadata.getContentVersion();
			int mBlockLocationVersion = blockMetadata.getLocationVersion();
			int mBlockHighestLocationVersion = blockMetadata.getHighestLocationVersion();

			if ((blocksInTree != null && blockInStash != null)
					|| (blocksInTree != null && mBlockLocation == ORAMUtils.DUMMY_LOCATION)) {
				strangeBlocks.add(address);
			}

			boolean isInStash = blocksInTree == null && mBlockLocation == ORAMUtils.DUMMY_LOCATION
					&& mBlockContentVersion == pmContentVersion && mBlockLocationVersion == pmLocationVersion
					&& blockInStash.getContentVersion() == mBlockContentVersion
					&& blockInStash.getLocationVersion() == mBlockLocationVersion;
			boolean isSingleInTree = blockInStash == null
					&& blocksInTree.size() == 1 && blocksInTree.get(0).getRight() == mBlockLocation
					&& mBlockContentVersion == pmContentVersion && mBlockLocationVersion == pmLocationVersion
					&& blocksInTree.get(0).getLeft().getContentVersion() == mBlockContentVersion
					&& blocksInTree.get(0).getLeft().getLocationVersion() == mBlockLocationVersion;
			//if (!isInStash && !isSingleInTree) {
			printBlockInformation("", address, positionMap, blocksGroupedByAddress, stash, metadata);
			//}
		}

		System.out.printf("Strange blocks: %d\n", strangeBlocks.size());
		strangeBlocks.stream().sorted().forEach(address
				-> printBlockInformation("\t", address, positionMap, blocksGroupedByAddress, stash, metadata));

		System.out.printf("Unknown blocks: %d\n", unknownBlocks.size());
		unknownBlocks.stream().sorted().forEach(address
				-> printBlockInformation("\t", address, positionMap, blocksGroupedByAddress, stash, metadata));

		//printing stash
		System.out.printf("Stash: %d\n\t", stash.size());
		stash.keySet().stream().sorted().forEach(address -> {
			Block block = stash.get(address);
			System.out.print(block + " ");
		});
		System.out.println();

		//printing merged position map
		//printPositionMap();

		System.out.println(accessedPathsHistory);

		int minVersion = 200;//Math.max(positionMap.getContentVersionOf(blockOfInterest),
				//positionMap.getLocationVersionOf(blockOfInterest));

		studyBlock(blockOfInterest, problematicVersion, minVersion);

		Set<Integer> problematicBlocks = new HashSet<>();
		problematicBlocks.addAll(strangeBlocks);
		problematicBlocks.addAll(unknownBlocks);
		System.out.printf("Problematic blocks: %s\n", problematicBlocks);
		System.out.printf("Last versions: %s\n", lastVersions);

	}

	private static void studyBlock(int blockOfInterest, int problematicVersion, int minVersion) {
		evictionMaps.keySet().stream().filter(v -> v >= minVersion).sorted().forEach(version -> {
			EvictionMap map = evictionMaps.get(version);
			Map<Integer, ArrayList<Pair<Block, Integer>>> tree = trees.get(version);
			System.out.printf("Version %d:\n", version);
			System.out.printf("\tOV: %s\n", Arrays.toString(outstandingVersions.get(version)));
			System.out.printf("\tPath: %d\n", accessedPathsHistory.get(version));

			System.out.printf("\tBlock %d in tree:\n", blockOfInterest);
			if (tree != null) {
				System.out.printf("\t\t %s\n", tree.get(blockOfInterest));
			}

			Map<Integer, Stash> currentVersionStashes = stashes.get(version);
			if (currentVersionStashes == null) {
				System.out.printf("\tBlock %d in stash: there are no stashes\n", blockOfInterest);
			} else {
				Map<Integer, Block> stash = simpleMergeStashes(currentVersionStashes);
				System.out.printf("\tBlock %d in stash: %s\n", blockOfInterest, stash.get(blockOfInterest));
			}

			PartialTreeWithDuplicatedBlocks blocksRemovedFromPath = map.getBlocksRemovedFromPath();
			PartialTree blocksMovedToPath = map.getBlocksMovedToPath();

			System.out.print("\tBlocks removed from path:\n");
			System.out.printf("\t\t L: %s\n", blocksRemovedFromPath.getLocations().get(blockOfInterest));
			System.out.printf("\t\tCV: %s\n", blocksRemovedFromPath.getContentVersions().get(blockOfInterest));
			System.out.printf("\t\tLV: %s\n", blocksRemovedFromPath.getLocationVersions().get(blockOfInterest));
			System.out.print("\tBlocks moved to path:\n");
			System.out.printf("\t\t L: %s\n", blocksMovedToPath.getLocations().get(blockOfInterest));
			System.out.printf("\t\tCV: %s\n", blocksMovedToPath.getContentVersions().get(blockOfInterest));
			System.out.printf("\t\tLV: %s\n", blocksMovedToPath.getLocationVersions().get(blockOfInterest));
		});

		System.out.printf("Path accessed by problematic version %d: %d\n", problematicVersion,
				accessedPathsHistory.get(problematicVersion));
		System.out.printf("Problematic version %d tree:\n", problematicVersion);
		Map<Integer, ArrayList<Pair<Block, Integer>>> problematicTree = trees.get(problematicVersion);
		problematicTree.keySet().stream().sorted().forEach(address
				-> System.out.printf("\t%s\n", problematicTree.get(address)));

		Map<Integer, Stash> currentVersionStashes = stashes.get(problematicVersion);
		System.out.printf("Problematic version %d stash:\n\t", problematicVersion);
		if (currentVersionStashes == null) {
			System.out.print("There are no stashes\n");
		} else {
			Map<Integer, Block> stash = simpleMergeStashes(currentVersionStashes);
			stash.keySet().stream().sorted().forEach(address -> {
				Block block = stash.get(address);
				System.out.print(block + " ");
			});
			System.out.println();
		}
	}

	private static Map<Integer, Block> simpleMergeStashes(Map<Integer, Stash> stashes) {
		Map<Integer, Block> mergedStash = new HashMap<>();

		for (Map.Entry<Integer, Stash> entry : stashes.entrySet()) {
			Stash stash = entry.getValue();
			if (stash == null) {
				throw new IllegalStateException("Stash is null");
			}
			for (Block block : stash.getBlocks()) {
				int address = block.getAddress();
				int contentVersion = block.getContentVersion();
				int locationVersion = block.getLocationVersion();
				Block previousBlock = mergedStash.get(address);
				if (previousBlock == null || contentVersion > previousBlock.getContentVersion()
						|| (contentVersion == previousBlock.getContentVersion() && locationVersion > previousBlock.getLocationVersion())) {
					mergedStash.put(address, block);
				}
			}
		}

		return mergedStash;
	}

	private static Map<Integer, Block> mergeStashes(int version, PositionMap positionMap,
													Map<Integer, Stash> stashes,
													BlockMetadataManager metadataManager) {
		Map<Integer, Block> mergedStash = new HashMap<>();
		for (Map.Entry<Integer, Stash> entry : stashes.entrySet()) {
			Stash stash = entry.getValue();
			if (stash == null) {
				throw new IllegalStateException("Stash is null");
			}
			for (Block block : stash.getBlocks()) {
				int address = block.getAddress();
				int contentVersion = block.getContentVersion();
				int pmContentVersion = positionMap.getContentVersionOf(address);
				int pmLocationVersion = positionMap.getLocationVersionOf(address);
				if (contentVersion > pmContentVersion
						|| (contentVersion == pmContentVersion
						&& block.getLocationVersion() > pmLocationVersion)) {
					throw new IllegalStateException("Block in stash has a higher version than the position map");
				}


				if (pmContentVersion == contentVersion
						&& pmLocationVersion == block.getLocationVersion()
						&& !metadataManager.isInTree(version, block.getAddress())) {
					mergedStash.put(block.getAddress(), block);
				}
			}
		}
		return mergedStash;
	}

	private static void printBlockInformation(String initialTabs, int address, PositionMap positionMap,
											  Map<Integer, ArrayList<Pair<Block, Integer>>> blocksInTree,
											  Map<Integer, Block> stash, Map<Integer, BlockMetadata> metadata) {
		int pmLocation = positionMap.getLocationOf(address);
		int pmContentVersion = positionMap.getContentVersionOf(address);
		int pmLocationVersion = positionMap.getLocationVersionOf(address);
		System.out.printf("%sBlock %d\n", initialTabs, address);
		System.out.printf("%s\tVersion in PM -> L: %d | CV: %d | LV: %d\n", initialTabs,
				pmLocation, pmContentVersion, pmLocationVersion);
		System.out.printf("%s\tVersions in tree -> %s\n", initialTabs, blocksInTree.get(address));
		System.out.printf("%s\tVersion in stash -> %s\n", initialTabs, stash.get(address));
		System.out.printf("%s\tBlock location -> %s\n", initialTabs, metadata.get(address));
	}

	private static void printPositionMap() {
		System.out.println("Merged position map:");
		for (int address = 0; address < context.getTreeSize(); address++) {
			int location = positionMap.getLocationOf(address);
			if (location == ORAMUtils.DUMMY_LOCATION) {
				continue;
			}
			int contentVersion = positionMap.getContentVersionOf(address);
			int locationVersion = positionMap.getLocationVersionOf(address);
			System.out.printf("\tA: %d | L: %d | CV: %d | LV: %d\n", address, location, contentVersion, locationVersion);
		}
	}

	private static void deserializeTraceError(String positionMapFilename) {
		try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(Paths.get(positionMapFilename)));
			 ObjectInput in = new ObjectInputStream(bis)) {
			int treeHeight = in.readInt();
			int blockSize = in.readInt();
			int bucketSize = in.readInt();
			int versionNumber = in.readInt();
			lastVersions.add(versionNumber);
			int treeSize = ORAMUtils.computeTreeSize(treeHeight, bucketSize);
			context = new ORAMContext(PositionMapType.TRIPLE_POSITION_MAP, treeHeight, treeSize, bucketSize, blockSize);

			//deserialize position map
			if (positionMap == null) {
				positionMap = new PositionMap(treeSize);
			}
			for (int address = 0; address < treeSize; address++) {
				int location = in.readInt();
				int contentVersion = in.readInt();
				int locationVersion = in.readInt();
				positionMap.setLocationOf(address, location);
				positionMap.setContentVersionOf(address, contentVersion);
				positionMap.setLocationVersionOf(address, locationVersion);
			}

			//deserialize eviction maps
			int evictionMapSize = in.readInt();
			if (evictionMaps == null) {
				evictionMaps = new HashMap<>(evictionMapSize);
			}
			while (evictionMapSize-- > 0) {
				int version = in.readInt();
				int size = in.readInt();
				byte[] serializedEvictionMap = new byte[size];
				in.readFully(serializedEvictionMap);
				EvictionMap map = new EvictionMap();
				map.readExternal(serializedEvictionMap, 0);
				evictionMaps.put(version, map);
			}

			//deserialize outstanding versions
			int outstandingVersionsSize = in.readInt();
			if (outstandingVersions == null) {
				outstandingVersions = new HashMap<>(outstandingVersionsSize);
			}
			while (outstandingVersionsSize--> 0) {
				int version = in.readInt();
				int size = in.readInt();
				int[] ov = new int[size];
				for (int i = 0; i < size; i++) {
					ov[i] = in.readInt();
				}
				outstandingVersions.put(version, ov);
			}

			//deserialize tree
			int nTrees = in.readInt();
			if (trees == null) {
				trees = new HashMap<>(nTrees);
			}
			while (nTrees-- > 0) {
				int version = in.readInt();
				int nTreeBlocks = in.readInt();
				Map<Integer, ArrayList<Pair<Block, Integer>>> blocksGroupedByAddress = new HashMap<>(nTreeBlocks);

				while (nTreeBlocks-- > 0) {
					int address = in.readInt();
					int nBlocks = in.readInt();
					ArrayList<Pair<Block, Integer>> blocksAndLocations = new ArrayList<>(nBlocks);

					while (nBlocks-- > 0) {
						int location = in.readInt();
						int size = in.readInt();
						byte[] serializedBlock = new byte[size];
						in.readFully(serializedBlock);
						Block block = new Block(blockSize);
						block.readExternal(serializedBlock, 0);
						blocksAndLocations.add(Pair.of(block, location));
					}

					blocksGroupedByAddress.put(address, blocksAndLocations);
				}
				trees.put(version, blocksGroupedByAddress);
			}

			//deserialize stash
			int nV = in.readInt();
			if (stashes == null) {
				stashes = new HashMap<>(nV);
			}
			while (nV--> 0) {
				int v = in.readInt();
				int nStashes = in.readInt();
				Map<Integer, Stash> vStashes = new HashMap<>(nStashes);
				while (nStashes-- > 0) {
					int version = in.readInt();
					int size = in.readInt();
					byte[] serializedStash = new byte[size];
					in.readFully(serializedStash);
					Stash stash = new Stash(blockSize);
					stash.readExternal(serializedStash, 0);
					vStashes.put(version, stash);
				}
				stashes.put(v, vStashes);
			}

			//deserialize paths
			int nPaths = in.readInt();
			if (accessedPathsHistory == null) {
				accessedPathsHistory = new HashMap<>(nPaths);
			}
			while (nPaths-- > 0) {
				int version = in.readInt();
				int path = in.readInt();
				accessedPathsHistory.put(version, path);
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
