package oram.testers;

import oram.client.metadata.OutstandingGraph;
import oram.client.structure.Block;
import oram.client.structure.PathMap;
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
import java.util.stream.Collectors;

public class ExperimentEnvironment {
	private static Map<Integer, PathMap> pathMaps;
	private static ORAMContext context;
	private static Map<Integer, int[]> outstandingVersionsHistory;
	private static Map<Integer, Map<Integer, ArrayList<Pair<Block, Integer>>>> trees;
	private static Map<Integer, Map<Integer, Stash>> stashes;
	private static Map<Integer, Integer> accessedPathsHistory;
	private static Map<Integer, Set<Integer>> unknownBlocksHistory;
	private static Map<Integer, Set<Integer>> versionsPerClient;
	private static Set<Integer> lastVersions;
	private static Map<Integer, PositionMap> positionMapHistory = new HashMap<>();

	public static void main(String[] args) {
		StringBuilder messageBuilder = new StringBuilder();
		int nClients = 50;

		loadData(nClients);

		boolean isAllOf = true;
		for (Map.Entry<Integer, Set<Integer>> entry : unknownBlocksHistory.entrySet()) {
			if (!entry.getValue().isEmpty()) {
				isAllOf = false;
				System.out.println("Access " + entry.getKey() + " found " + entry.getValue().size() + " unknown block");
			}
		}

		int maxStashSize = 0;
		for (Map.Entry<Integer, Map<Integer, Stash>> stashes : stashes.entrySet()) {
			for (Stash stash : stashes.getValue().values()) {
				if (stash.getBlocks().size() > maxStashSize) {
					maxStashSize = stash.getBlocks().size();
				}
			}
		}
		System.out.println("Max stash size: " + maxStashSize);

		if (isAllOf) {
			System.out.println("Every thing is perfect!!");
			System.exit(0);
		}

		System.out.println("Tree height: " + context.getTreeHeight());
		positionMapHistory.put(1, new PositionMap(context.getTreeSize()));

		int problematicVersion = Collections.min(lastVersions);
		System.out.println("Last versions: " + lastVersions);
		System.out.println("Problematic version: " + problematicVersion);

		versionsPerClient.keySet().stream().sorted().forEach(c -> {
			Set<Integer> version = versionsPerClient.get(c);
			System.out.printf("Client %d versions:", c);
			version.stream().sorted().forEach(v -> System.out.printf(" %d", v));
			System.out.println();
		});

		removeNonOutstandingVersionsOfProblematicVersion(problematicVersion);

		OutstandingGraph outstandingGraph = new OutstandingGraph();
		outstandingGraph.addOutstandingVersions(outstandingVersionsHistory);

		/*outstandingVersionsHistory.keySet().stream().filter(v -> v <= problematicVersion).sorted().forEach(v -> {
			int c = 0;
			for (Map.Entry<Integer, Set<Integer>> entry : versionsPerClient.entrySet()) {
				if (entry.getValue().contains(v)) {
					c = entry.getKey();
					break;
				}
			}
			int[] ovs = outstandingVersionsHistory.get(v);
			PathMap pathMap = pathMaps.get(v);
			System.out.println("OP sequence " + v);
			System.out.println("\tClient: " + c);
			System.out.println("\tPath: " + accessedPathsHistory.get(v));
			System.out.print("\tPM: " + (pathMap == null || pathMap.getStoredAddresses().isEmpty() ? "\n" : pathMap));
			System.out.println("\tOV: " + Arrays.toString(ovs));
		});*/

		int checkVersion = 86;
		PositionMap positionMap = getPositionMap(checkVersion, outstandingGraph);
		int[] ov = outstandingVersionsHistory.get(checkVersion);
		for (int v : ov) {
			System.out.println("Version " + v);
			printTreeMap(positionMapHistory.get(v));
		}

		printTreeMap(positionMap);
		if (true) {
			return;
		}
		PositionMap treeMap = buildTreeMap(messageBuilder);
		printTreeMap(treeMap);

		Stash stash = mergeStashes(treeMap, stashes.get(problematicVersion));

		System.out.println(messageBuilder);

		Map<Integer, ArrayList<Pair<Block, Integer>>> blocksGroupedByAddress = trees.get(problematicVersion);
		Set<Integer> unknownBlocks = new HashSet<>();
		Set<Integer> strangeBlocks = new HashSet<>();

		for (int address = 0; address < context.getTreeSize(); address++) {
			int tmAccessVersion = treeMap.getAccessVersionOf(address);
			if (tmAccessVersion == ORAMUtils.DUMMY_VERSION) {
				continue;
			}
			ArrayList<Pair<Block, Integer>> blocksInTree = blocksGroupedByAddress.get(address);
			Block blockInStash = stash.getBlock(address);
			if (blocksInTree == null && blockInStash == null) {
				unknownBlocks.add(address);
			}

			if (blocksInTree != null && blockInStash != null) {
				strangeBlocks.add(address);
			}

			printBlockInformation("", address, treeMap, blocksGroupedByAddress, stash);
		}

		System.out.println("Strange blocks: " + strangeBlocks);
		System.out.println("Unknown  blocks: " + unknownBlocks);
	}

	public static PositionMap getPositionMap(int version, OutstandingGraph outstandingGraph) {
		PositionMap positionMap = positionMapHistory.get(version);
		if (positionMap != null) {
			return positionMap;
		}

		positionMap = new PositionMap(context.getTreeSize());
		int[] outstandingVersions = outstandingVersionsHistory.get(version);
		PositionMap[] outstandingPositionMaps = new PositionMap[outstandingVersions.length];
		for (int i = 0; i < outstandingVersions.length; i++) {
			int outstandingVersion = outstandingVersions[i];
			PositionMap outstandingPositionMap = getPositionMap(outstandingVersion, outstandingGraph);
			outstandingPositionMaps[i] = outstandingPositionMap;
		}

		Map<Integer, int[]> updates = new HashMap<>();
		System.out.println("Building version " + version);
		System.out.println("\tOVs: " + Arrays.toString(outstandingVersions));

		for (int address = 0; address < context.getTreeSize(); address++) {
			System.out.println("\tAddress: " + address);
			updates.clear();
			for (int i = 0; i < outstandingVersions.length; i++) {
				int outstandingVersion = outstandingVersions[i];
				System.out.println("\t\tOV: " + outstandingVersion);
				PositionMap outstandingPositionMap = outstandingPositionMaps[i];
				int[] outstandingVersionsOfOV = outstandingVersionsHistory.get(outstandingVersion);
				Set<Integer> concurrentVersions = findConcurrentVersions(version, outstandingVersion,
						outstandingVersionsOfOV, outstandingVersionsHistory, outstandingGraph);
				System.out.println("\t\t\tCV: " + concurrentVersions);

				int[] outstandingUpdate = consolidateUpdateFor(address, outstandingGraph, concurrentVersions, outstandingPositionMap);

				if (outstandingUpdate != null) {
					updates.put(outstandingUpdate[3], outstandingUpdate);
					System.out.println("\t\t\tOutstanding update: " + Arrays.toString(outstandingUpdate));
				}
			}

			System.out.println("\t\tConsolidate");

			//Remove older versions
			removeOlderVersions(updates);
			System.out.println("\t\t\tRecent versions: " + updates.keySet());

			//Remove overridden updates and keep concurrent updates
			removeOverriddenUpdates(outstandingGraph, updates);
			System.out.println("\t\t\tConcurrent updates: " + updates.keySet());

			//Select deepest and recent update
			selectDeepestAndRecentUpdates(updates);
			System.out.println("\t\t\tDeepest updates: " + updates.keySet());

			if (!updates.isEmpty()) {
				int[] bestUpdate = updates.values().iterator().next();
				System.out.println("\t\t\tBest update " + Arrays.toString(bestUpdate));
				positionMap.update(address, bestUpdate[0], bestUpdate[1], bestUpdate[2], bestUpdate[3]);
			}
		}

		positionMapHistory.put(version, positionMap);
		return positionMap;
	}

	private static int[] consolidateUpdates(OutstandingGraph outstandingGraph, int[] updateA, int[] updateB) {
		if (updateA[2] == ORAMUtils.DUMMY_VERSION) {
			return updateB;
		}
		if (updateB[2] == ORAMUtils.DUMMY_VERSION) {
			return updateA;
		}
		if (updateA[1] > updateB[1] || (updateA[1] == updateB[1] && updateA[2] > updateB[2])) {
			return updateA;
		}
		if (outstandingGraph.doesOverrides(updateA[3], updateB[3])) {
			System.out.println("\t\t\t\t" + Arrays.toString(updateA) + " overrides " + Arrays.toString(updateB));
			return updateA;
		}

		if (outstandingGraph.doesOverrides(updateB[3], updateA[3])) {
			System.out.println("\t\t\t\t" + Arrays.toString(updateB) + " overrides " + Arrays.toString(updateA));
			return updateB;
		}

		if (updateA[0] > updateB[0] || (updateA[0] == updateB[0] && updateA[3] > updateB[3])) {
			System.out.println("\t\t\t\t" + Arrays.toString(updateA) + " is deeper than " + Arrays.toString(updateB));
			return updateA;
		}
		System.out.println("\t\t\t\t" + Arrays.toString(updateB) + " is deeper than " + Arrays.toString(updateA));
		return updateB;
	}

	private static int[] consolidateUpdateFor(int address, OutstandingGraph outstandingGraph,
											  Set<Integer> concurrentPathMaps, PositionMap outstandingPositionMap) {
		//Collect updates
		Map<Integer, int[]> updates = collectConcurrentUpdatesFor(address, concurrentPathMaps);
		int positionMapAccessVersion = outstandingPositionMap.getAccessVersionOf(address);
		if (positionMapAccessVersion != ORAMUtils.DUMMY_VERSION) {
			int positionMapLocationUpdateVersion = outstandingPositionMap.getLocationUpdateVersion(address);
			int[] pmUpdate = {
					outstandingPositionMap.getLocationOf(address),
					outstandingPositionMap.getWriteVersionOf(address),
					positionMapAccessVersion,
					positionMapLocationUpdateVersion
			};
			updates.put(positionMapLocationUpdateVersion, pmUpdate);
		}
		System.out.println("\t\t\tConcurrent updates: " + updates.keySet());

		//Remove updates with older versions
		removeOlderVersions(updates);
		System.out.println("\t\t\tRecent versions: " + updates.keySet());

		//Remove overridden updates and keep concurrent updates
		removeOverriddenUpdates(outstandingGraph, updates);
		System.out.println("\t\t\tConcurrent updates: " + updates.keySet());

		//Select deepest and recent update
		selectDeepestAndRecentUpdates(updates);
		System.out.println("\t\t\tDeepest updates: " + updates.keySet());

		if (updates.isEmpty()) {
			return null;
		}
		return updates.values().iterator().next();
	}

	private static Map<Integer, int[]> collectConcurrentUpdatesFor(int address, Set<Integer> concurrentVersions) {
		Map<Integer, int[]> updates = new HashMap<>();
		for (int concurrentVersion : concurrentVersions) {
			PathMap concurrentPathMap = pathMaps.get(concurrentVersion);
			if (concurrentPathMap.getAccessVersionOf(address) == ORAMUtils.DUMMY_VERSION) {
				continue;
			}
			int[] update = {
					concurrentPathMap.getLocationOf(address),
					concurrentPathMap.getWriteVersionOf(address),
					concurrentPathMap.getAccessVersionOf(address),
					concurrentVersion
			};
			updates.put(concurrentVersion, update);
		}
		return updates;
	}

	private static void removeOlderVersions(Map<Integer, int[]> updates) {
		//Remove older versions
		Set<Integer> currentUpdates = new HashSet<>(updates.keySet());
		for (int ovA : currentUpdates) {
			int[] updateA = updates.get(ovA);
			if (updateA == null) {
				continue;
			}
			for (int ovB : currentUpdates) {
				int[] updateB = updates.get(ovB);
				if (ovA == ovB || updateB == null) {
					continue;
				}
				if (updateA[1] > updateB[1] || (updateA[1] == updateB[1] && updateA[2] > updateB[2])) {
					updates.remove(ovB);
				}
			}
		}
	}

	private static void removeOverriddenUpdates(OutstandingGraph outstandingGraph, Map<Integer, int[]> updates) {
		Set<Integer> currentUpdates = new HashSet<>(updates.keySet());
		for (int ovA : currentUpdates) {
			int[] updateA = updates.get(ovA);
			if (updateA == null) {
				continue;
			}
			for (int ovB : currentUpdates) {
				int[] updateB = updates.get(ovB);
				if (ovA == ovB || updateB == null) {
					continue;
				}
				if (outstandingGraph.doesOverrides(updateA[3], updateB[3])) {
					updates.remove(ovB);
					System.out.println("\t\t\t\t" + Arrays.toString(updateA) + " overrides " + Arrays.toString(updateB));
				}
			}
		}
	}

	private static void selectDeepestAndRecentUpdates(Map<Integer, int[]> updates) {
		Set<Integer> currentUpdates = new HashSet<>(updates.keySet());
		for (int ovA : currentUpdates) {
			int[] updateA = updates.get(ovA);
			if (updateA == null) {
				continue;
			}
			for (int ovB : currentUpdates) {
				int[] updateB = updates.get(ovB);
				if (ovA == ovB || updateB == null) {
					continue;
				}
				if (updateA[0] > updateB[0] || (updateA[0] == updateB[0] && updateA[3] > updateB[3])) {
					System.out.println("\t\t\t\t" + Arrays.toString(updateA) + " is deeper than " + Arrays.toString(updateB));
					updates.remove(ovB);
				}
			}
		}
	}

	private static Set<Integer> findConcurrentVersions(int startVersion, int ovVersion, int[] limitOutstandingVersions,
													   Map<Integer, int[]> outstandingVersionsHistory,
													   OutstandingGraph outstandingGraph) {
		Set<Integer> visitedOutstandingVersions = new HashSet<>();
		Queue<int[]> queue = new ArrayDeque<>();
		queue.add(outstandingVersionsHistory.get(startVersion));
		Set<Integer> limitOVSet = new HashSet<>(limitOutstandingVersions.length);
		int minLimitVersion = Integer.MAX_VALUE;
		for (int limitOutstandingVersion : limitOutstandingVersions) {
			limitOVSet.add(limitOutstandingVersion);
			if (limitOutstandingVersion < minLimitVersion) {
				minLimitVersion = limitOutstandingVersion;
			}
		}
		Set<Integer> concurrentVersions = new HashSet<>();
		while (!queue.isEmpty()) {
			int[] versions = queue.poll();
			for (int version : versions) {
				if (outstandingGraph.doesOverrides(version, limitOVSet)) {
					concurrentVersions.add(version);
				}
				if (version > minLimitVersion && version != ovVersion) {
					int[] newVersions = outstandingVersionsHistory.get(version);
					int hash = ORAMUtils.computeHashCode(newVersions);
					if (!visitedOutstandingVersions.contains(hash)) {
						queue.add(newVersions);
						visitedOutstandingVersions.add(hash);
					}
				}
			}
		}

		return concurrentVersions;
	}

	private static void removeNonOutstandingVersionsOfProblematicVersion(int problematicVersion) {
		OutstandingGraph outstandingGraph = new OutstandingGraph();
		outstandingGraph.addOutstandingVersions(outstandingVersionsHistory);
		Set<Integer> allVersions = new HashSet<>(outstandingVersionsHistory.keySet());
		for (int version : allVersions) {
			if (!outstandingGraph.doesOverrides(problematicVersion, version) && version != problematicVersion) {
				outstandingVersionsHistory.remove(version);
				pathMaps.remove(version);
			}
		}
	}

	private static PositionMap buildTreeMap(StringBuilder debugInformation) {
		OutstandingGraph outstandingGraph = new OutstandingGraph();
		outstandingGraph.addOutstandingVersions(outstandingVersionsHistory);

		Set<Integer> updatedAddresses = new HashSet<>();
		for (Map.Entry<Integer, PathMap> entry : pathMaps.entrySet()) {
			PathMap currentPM = entry.getValue();
			updatedAddresses.addAll(currentPM.getStoredAddresses());
		}

		return buildTreeMap(updatedAddresses, pathMaps, outstandingGraph, debugInformation);
	}

	private static PositionMap buildTreeMap(Set<Integer> updatedAddresses, Map<Integer, PathMap> pathMaps,
											OutstandingGraph outstandingGraph, StringBuilder debugInformation) {
		debugInformation.append("Updated addresses: ").append(updatedAddresses).append("\n");
		PositionMap treeMap = new PositionMap(context.getTreeSize());
		Set<Integer> concurrentVersions = new HashSet<>();
		for (int updatedAddress : updatedAddresses) {
			int tmLocation = treeMap.getLocationOf(updatedAddress);
			int tmWriteVersion = treeMap.getWriteVersionOf(updatedAddress);
			int tmAccessVersion = treeMap.getAccessVersionOf(updatedAddress);
			int tmLocationUpdateVersion = treeMap.getLocationUpdateVersion(updatedAddress);
			debugInformation.append("\tUpdated address: ").append(updatedAddress)
					.append(" (L: ").append(tmLocation)
					.append(" WV: ").append(tmWriteVersion)
					.append(" AV: ").append(tmAccessVersion)
					.append(" LUV: ").append(tmLocationUpdateVersion).append(")\n");
			for (Map.Entry<Integer, PathMap> entry : pathMaps.entrySet()) {
				int pmLocationUpdateVersion = entry.getKey();
				PathMap pathMap = entry.getValue();
				int pmLocation = pathMap.getLocationOf(updatedAddress);
				int pmWriteVersion = pathMap.getWriteVersionOf(updatedAddress);
				int pmAccessVersion = pathMap.getAccessVersionOf(updatedAddress);
				if (pmAccessVersion == ORAMUtils.DUMMY_VERSION) {
					continue;
				}
				debugInformation.append("\t\tUpdated op sequence: ").append(pmLocationUpdateVersion)
						.append(" (L: ").append(pmLocation)
						.append(" WV: ").append(pmWriteVersion)
						.append(" AV: ").append(pmAccessVersion)
						.append(" LUV: ").append(pmLocationUpdateVersion).append(")\n");
				if (pmWriteVersion > tmWriteVersion ||
						(pmWriteVersion == tmWriteVersion && pmAccessVersion > tmAccessVersion)) {
					tmWriteVersion = pmWriteVersion;
					tmAccessVersion = pmAccessVersion;
					tmLocationUpdateVersion = pmLocationUpdateVersion;
					tmLocation = pmLocation;
					concurrentVersions.clear();
					concurrentVersions.add(pmLocationUpdateVersion);
				} else if (pmWriteVersion == tmWriteVersion && pmAccessVersion == tmAccessVersion)  {
					if (outstandingGraph.doesOverrides(pmLocationUpdateVersion, tmLocationUpdateVersion)) {
						tmLocationUpdateVersion = pmLocationUpdateVersion;
						tmLocation = pmLocation;
					} else if (!outstandingGraph.doesOverrides(tmLocationUpdateVersion, pmLocationUpdateVersion)) {//concurrent version
						concurrentVersions.add(pmLocationUpdateVersion);
						if (pmLocation > tmLocation || (pmLocation == tmLocation && pmLocationUpdateVersion > tmLocationUpdateVersion)) {//TODO have to compute and compare heights
							tmLocationUpdateVersion = pmLocationUpdateVersion;
							tmLocation = pmLocation;
						}
					}
				}
			}

			treeMap.setLocationOf(updatedAddress, tmLocation);
			treeMap.setWriteVersionOf(updatedAddress, tmWriteVersion);
			treeMap.setAccessVersionOf(updatedAddress, tmAccessVersion);
			treeMap.setLocationUpdateVersions(updatedAddress, tmLocationUpdateVersion);
			debugInformation.append("\t\tResult: (A: ").append(updatedAddress)
					.append(", L: ").append(tmLocation)
					.append(", WV: ").append(tmWriteVersion)
					.append(", AV: ").append(tmAccessVersion)
					.append(", LUV: ").append(tmLocationUpdateVersion)
					.append(")\n");
		}

		return treeMap;
	}

	private static Stash mergeStashes(PositionMap treeMap,
									  Map<Integer, Stash> stashes) {
		Stash mergedStash = new Stash(context.getBlockSize());
		for (Map.Entry<Integer, Stash> stashEntry : stashes.entrySet()) {
			Stash stash = stashEntry.getValue();
			if (stash == null) {
				continue;
			}

			for (Map.Entry<Integer, Block> blockEntry : stash.getBlocks().entrySet()) {
				int address = blockEntry.getKey();
				Block block =  blockEntry.getValue();
				int writeVersion = block.getWriteVersion();
				int accessVersion = block.getAccessVersion();
				int tmLocation = treeMap.getLocationOf(address);
				int tmWriteVersion = treeMap.getWriteVersionOf(address);
				int tmAccessVersion = treeMap.getAccessVersionOf(address);
				if (writeVersion > tmWriteVersion
						|| (writeVersion == tmWriteVersion && accessVersion > tmAccessVersion)) {
					throw new IllegalStateException("Block in stash has a higher version than the tree map");
				}
				if (tmLocation == ORAMUtils.DUMMY_LOCATION && tmWriteVersion == writeVersion
						&& tmAccessVersion == accessVersion) {
					mergedStash.putBlock(block);
				}
			}
		}
		return mergedStash;
	}

	private static void printBlockInformation(String initialTabs, int address, PositionMap positionMap,
											  Map<Integer, ArrayList<Pair<Block, Integer>>> blocksInTree,
											  Stash stash) {
		int pmLocation = positionMap.getLocationOf(address);
		int pmWriteVersion = positionMap.getWriteVersionOf(address);
		int pmAccessVersion = positionMap.getAccessVersionOf(address);
		int pmLocationUpdateVersion = positionMap.getLocationUpdateVersion(address);
		System.out.printf("%sBlock %d\n", initialTabs, address);
		System.out.printf("%s\tVersion in PM -> L: %d | WV: %d | AV: %d | LUV: %d\n", initialTabs,
				pmLocation, pmWriteVersion, pmAccessVersion, pmLocationUpdateVersion);
		System.out.printf("%s\tVersions in tree -> %s\n", initialTabs, blocksInTree.get(address));
		System.out.printf("%s\tVersion in stash -> %s\n", initialTabs, stash.getBlock(address));
	}

	private static void printTreeMap(PositionMap treeMap) {
		System.out.println("Tree map:");
		for (int address = 0; address < context.getTreeSize(); address++) {
			int location = treeMap.getLocationOf(address);
			int writeVersion = treeMap.getWriteVersionOf(address);
			int accessVersion = treeMap.getAccessVersionOf(address);
			int locationUpdateVersion = treeMap.getLocationUpdateVersion(address);
			if (accessVersion == ORAMUtils.DUMMY_VERSION) {
				continue;
			}
			System.out.printf("\tA: %d | L: %d | WV: %d | AV: %d | LUV: %d\n", address, location, writeVersion,
					accessVersion, locationUpdateVersion);
		}
	}

	private static void loadData(int numberOfRecentFiles) {
		String dataFolder = "C:\\Users\\robin\\Desktop\\oram\\";
		lastVersions = new HashSet<>(numberOfRecentFiles);

		File folder = new File(dataFolder);
		File[] files = folder.listFiles();
		if (files == null) {
			throw new RuntimeException("There are no files");
		}
		Arrays.sort(files, Comparator.comparingLong(File::lastModified));

		for (int i = files.length - 1, c = 0; i >= 0 && c < numberOfRecentFiles; i--, c++) {
			File file = files[i];
			System.out.println(file);
			deserializeTraceError(file);
		}

		accessedPathsHistory.put(1, -1);
		stashes.put(1, new HashMap<>());
	}

	private static void deserializeTraceError(File traceFilename) {
		try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(Paths.get(traceFilename.toURI())));
			 ObjectInput in = new ObjectInputStream(bis)) {
			int treeHeight = in.readInt();
			int blockSize = in.readInt();
			int bucketSize = in.readInt();
			int versionNumber = in.readInt();
			lastVersions.add(versionNumber);
			int treeSize = ORAMUtils.computeTreeSize(treeHeight, bucketSize);
			context = new ORAMContext(PositionMapType.TRIPLE_POSITION_MAP, treeHeight, treeSize, bucketSize, blockSize);

			//deserialize version history
			if (versionsPerClient == null) {
				versionsPerClient = new HashMap<>();
			}
			int clientId = in.readInt();
			int nVersions = in.readInt();
			Set<Integer> versions = new HashSet<>(nVersions);
			while (nVersions-- > 0) {
				int version = in.readInt();
				versions.add(version);
			}
			versionsPerClient.put(clientId, versions);

			//deserialize path map
			int nPathMaps = in.readInt();
			if (pathMaps == null) {
				pathMaps = new HashMap<>(nPathMaps);
			}
			while (nPathMaps--> 0) {
				int version = in.readInt();
				int pathMapSize = in.readInt();
				byte[] serializedPathMap = new byte[pathMapSize];
				in.readFully(serializedPathMap);
				PathMap pathMap = new PathMap();
				pathMap.readExternal(serializedPathMap, 0);
				pathMaps.put(version, pathMap);
			}

			//deserialize outstanding versions
			int outstandingVersionsSize = in.readInt();
			if (outstandingVersionsHistory == null) {
				outstandingVersionsHistory = new HashMap<>(outstandingVersionsSize);
			}
			while (outstandingVersionsSize--> 0) {
				int version = in.readInt();
				int size = in.readInt();
				int[] ov = new int[size];
				for (int i = 0; i < size; i++) {
					ov[i] = in.readInt();
				}
				outstandingVersionsHistory.put(version, ov);
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

			//deserialize unknown blocks history
			int nSets = in.readInt();
			if (unknownBlocksHistory == null) {
				unknownBlocksHistory = new HashMap<>(nSets);
			}
			while (nSets-- > 0) {
				int v = in.readInt();
				int size = in.readInt();
				Set<Integer> vs = new HashSet<>(size);
				while (size--> 0) {
					vs.add(in.readInt());
				}
				unknownBlocksHistory.put(v, vs);
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
