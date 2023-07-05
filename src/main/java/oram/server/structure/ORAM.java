package oram.server.structure;

import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ORAM {
	private final Logger logger = LoggerFactory.getLogger("oram");
	private final int oramId;
	private final ORAMContext oramContext;
	private final List<OramSnapshot> outstandingTrees; //all versions that are not previous of any other version
	private final HashMap<Integer, ORAMClientContext> oramClientContexts;
	private final List<OramSnapshot> allTrees;
	private int sequenceNumber = 0;

	public ORAM(int oramId, int treeHeight, int bucketSize, int blockSize,
				EncryptedPositionMap encryptedPositionMap, EncryptedStash encryptedStash) {
		this.oramId = oramId;
		this.allTrees = new ArrayList<>();
		int treeSize = ORAMUtils.computeNumberOfNodes(treeHeight) * bucketSize;
		this.oramContext = new ORAMContext(treeHeight, treeSize, bucketSize, blockSize);
		logger.debug("Total number of blocks: {}", treeSize);
		this.outstandingTrees = new LinkedList<>();
		sequenceNumber++;
		double versionId = sequenceNumber;

		OramSnapshot[] previous = new OramSnapshot[0];
		OramSnapshot snap = new OramSnapshot(versionId, previous,
				encryptedPositionMap, encryptedStash);

		outstandingTrees.add(snap);
		allTrees.add(snap);
		oramClientContexts = new HashMap<>();
	}

	public ORAMContext getOramContext() {
		return oramContext;
	}

	public EncryptedPositionMaps getPositionMaps(int clientId) {
		EncryptedPositionMap[] encryptedPositionMaps = new EncryptedPositionMap[outstandingTrees.size()];
		double[] outstandingVersionIds = new double[outstandingTrees.size()];
		OramSnapshot[] currentOutstandingVersions = new OramSnapshot[outstandingTrees.size()];
		int i = 0;
		for (OramSnapshot snapshot : outstandingTrees) {
			encryptedPositionMaps[i] = snapshot.getPositionMap();
			currentOutstandingVersions[i] = snapshot;
			outstandingVersionIds[i] = snapshot.getVersionId();
			i++;
		}
		double newVersionId = sequenceNumber++;
		ORAMClientContext oramClientContext = new ORAMClientContext(currentOutstandingVersions, newVersionId);

		oramClientContexts.put(clientId, oramClientContext);
		return new EncryptedPositionMaps(newVersionId, outstandingVersionIds, encryptedPositionMaps);
	}

	public EncryptedStashesAndPaths getStashesAndPaths(byte pathId, int clientId) {
		ORAMClientContext oramClientContext = oramClientContexts.get(clientId);
		if (oramClientContext == null) {
			return null;
		}
		OramSnapshot[] outstandingTrees = oramClientContext.getOutstandingVersions();
		Map<Double, Set<Double>> versionPaths = new HashMap<>(allTrees.size());// Map<Version id, Set<OutStanding id>>

		List<Integer> pathLocations = ORAMUtils.computePathLocationsList(pathId, oramContext.getTreeHeight());
		Map<Double, EncryptedStash> encryptedStashes = new HashMap<>(allTrees.size());
		Map<Double, Map<Integer, EncryptedBucket>> pathContents = new TreeMap<>();
		Set<Double> visitedVersions = new HashSet<>(allTrees.size());
		for (OramSnapshot outstandingTree : outstandingTrees) {
			Set<Double> traversedVersions = traverseVersions(outstandingTree, pathLocations, encryptedStashes,
					pathContents, visitedVersions);
			for (double traversedVersion : traversedVersions) {
				Set<Double> outstandingTreeIds = versionPaths.computeIfAbsent(traversedVersion,
						k -> new HashSet<>(outstandingTrees.length));
				outstandingTreeIds.add(outstandingTree.getVersionId());
			}
		}

		Map<Double, EncryptedBucket[]> compactedPaths = new HashMap<>(pathContents.size());
		for (Map.Entry<Double, Map<Integer, EncryptedBucket>> entry : pathContents.entrySet()) {
			EncryptedBucket[] buckets = new EncryptedBucket[entry.getValue().size()];
			int i = 0;
			for (EncryptedBucket value : entry.getValue().values()) {
				buckets[i++] = value;
			}
			compactedPaths.put(entry.getKey(), buckets);
		}

		return new EncryptedStashesAndPaths(encryptedStashes, compactedPaths, versionPaths);
	}

	private Set<Double> traverseVersions(OramSnapshot outstanding, List<Integer> pathLocations,
										 Map<Double, EncryptedStash> encryptedStashes,
										 Map<Double, Map<Integer, EncryptedBucket>> pathContents,
										 Set<Double> visitedVersions) {
		Queue<OramSnapshot> queue = new ArrayDeque<>();
		queue.add(outstanding);
		Set<Double> visitedVersionsPerSnapshot = new HashSet<>(allTrees.size());
		visitedVersionsPerSnapshot.add(outstanding.getVersionId());
		while (!queue.isEmpty()) {
			OramSnapshot version = queue.poll();
			if (!visitedVersions.contains(version.getVersionId())) {
				encryptedStashes.put(version.getVersionId(), version.getStash());
				for (Integer pathLocation : pathLocations) {
					EncryptedBucket bucket = version.getFromLocation(pathLocation);
					if (bucket != null) {
						Map<Integer, EncryptedBucket> encryptedBuckets = pathContents.computeIfAbsent(
								version.getVersionId(), k -> new HashMap<>(oramContext.getTreeLevels()));
						encryptedBuckets.put(pathLocation, bucket);
					}
				}
			}
			List<OramSnapshot> previous = version.getPrevious();
			synchronized (previous) {
				for (OramSnapshot oramSnapshot : previous) {
					if (!visitedVersionsPerSnapshot.contains(oramSnapshot.getVersionId())) {
						visitedVersionsPerSnapshot.add(oramSnapshot.getVersionId());
						queue.add(oramSnapshot);
					}
				}
			}
		}
		visitedVersions.addAll(visitedVersionsPerSnapshot);
		return visitedVersionsPerSnapshot;
	}

	public boolean performEviction(EncryptedStash encryptedStash, EncryptedPositionMap encryptedPositionMap,
								   Map<Integer, EncryptedBucket> encryptedPath, int clientId) {
		ORAMClientContext oramClientContext = oramClientContexts.remove(clientId);
		if (oramClientContext == null) {
			return false;
		}
		OramSnapshot[] outstandingVersions = oramClientContext.getOutstandingVersions();

		double newVersionId = oramClientContext.getNewVersionId();

		OramSnapshot newVersion = new OramSnapshot(newVersionId,
				outstandingVersions, encryptedPositionMap, encryptedStash);
		for (Map.Entry<Integer, EncryptedBucket> entry : encryptedPath.entrySet()) {
			newVersion.setToLocation(entry.getKey(), entry.getValue());
		}
		for (OramSnapshot outstandingVersion : outstandingVersions) {
			outstandingTrees.remove(outstandingVersion);
		}
		outstandingTrees.add(newVersion);
		allTrees.add(newVersion);
		garbageCollect(newVersion);
		return true;
	}


	private void garbageCollect(OramSnapshot newVersion) {
		TreeSet<OramSnapshot> versions = new TreeSet<>();
		for (ORAMClientContext oramClientContext : oramClientContexts.values()) {
			Collections.addAll(versions, oramClientContext.getOutstandingVersions());
		}
		versions.add(newVersion);
		for (OramSnapshot version : versions) {
			BitSet locationsMarker = new BitSet(oramContext.getTreeSize());
			HashSet<Double> visitedVersions = new HashSet<>(allTrees.size());
			version.garbageCollect(locationsMarker, oramContext.getTreeSize(), visitedVersions);
		}

		ArrayList<OramSnapshot> treesToRemove = new ArrayList<>();
		for (OramSnapshot tree : allTrees) {
			if (tree.removeNonTainted()) {
				treesToRemove.add(tree);
			}
		}
		allTrees.removeAll(treesToRemove);
		removeOldVersions();
	}

	private void removeOldVersions() {
		Queue<OramSnapshot> queue = new ArrayDeque<>(allTrees);
		while (!queue.isEmpty()) {
			OramSnapshot version = queue.poll();
			List<OramSnapshot> previousToAdd = new ArrayList<>();
			List<OramSnapshot> previousToRemove = new ArrayList<>();
			for (OramSnapshot previousVersion : version.getPrevious()) {
				if (!allTrees.contains(previousVersion)) {
					for (OramSnapshot previous : previousVersion.getPrevious()) {
						if(allTrees.contains(previous))
							previousToAdd.add(previous);
					}
					previousToRemove.add(previousVersion);
				}
			}
			version.addPrevious(previousToAdd);
			version.removePrevious(previousToRemove);
		}
	}

	@Override
	public String toString() {
		return String.valueOf(oramId);
	}

	public int getOramId() {
		return oramId;
	}

	public int getOutstandingNumber() {
		return outstandingTrees.size();
	}

	public int getAllVersionNumber() {
		return allTrees.size();
	}

	public String printORAM() {
		Queue<OramSnapshot> snapshots = new LinkedList<>(outstandingTrees);
		int[] bucketSize = new int[oramContext.getTreeSize()];
		int[] numberOfNodesByLocation = new int[oramContext.getTreeSize()];
		while (!snapshots.isEmpty()) {
			OramSnapshot snapshot = snapshots.poll();
			for (int i = 0; i < oramContext.getTreeSize(); i++) {
				EncryptedBucket bucket = snapshot.getFromLocation(i);
				if (bucket != null) {
					numberOfNodesByLocation[i]++;
				}
				if (bucket != null) {
					bucketSize[i] = bucket.getBlocks().length;
				}
			}
			snapshots.addAll(snapshot.getPrevious());
		}
		StringBuilder sb = new StringBuilder();
		sb.append("Printing ORAM ").append(oramId).append("\n");
		sb.append("(Location, Is Empty?, Number of versions)\n");
		for (int i = 0; i < oramContext.getTreeSize(); i++) {
			sb.append("(").append(i).append(", ").append(bucketSize[i] == 0).append(", ")
					.append(numberOfNodesByLocation[i]).append(") ");
			if (i % 7 == 0 && i != 0)
				sb.append("\n");
		}
		return sb.toString();
	}
}
