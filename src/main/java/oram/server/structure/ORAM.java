package oram.server.structure;

import oram.messages.GetPositionMap;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import oram.utils.PositionMapType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public abstract class ORAM {
	private final Logger logger = LoggerFactory.getLogger("oram");
	private final int oramId;
	private final ORAMContext oramContext;
	protected final List<OramSnapshot> outstandingTrees; //all versions that are not previous of any other version
	protected final Map<Integer, EncryptedPositionMap> positionMaps;
	protected final HashMap<Integer, ORAMClientContext> oramClientContexts;
	protected int sequenceNumber = 0;
	private int currentNumberOfSnapshots;
	private final int numberOfBuckets;
	private final Set<Integer> visitedOutstandingVersions;
	private final Set<Integer> taintedVersions;
	private final List<OramSnapshot> taintedSnapshots;

	public ORAM(int oramId, PositionMapType positionMapType, int treeHeight, int bucketSize, int blockSize,
				EncryptedPositionMap encryptedPositionMap, EncryptedStash encryptedStash) {
		this.oramId = oramId;
		int treeSize = ORAMUtils.computeTreeSize(treeHeight, bucketSize);
		this.oramContext = new ORAMContext(positionMapType, treeHeight, treeSize, bucketSize, blockSize);
		logger.debug("Total number of blocks: {}", treeSize);
		this.outstandingTrees = new LinkedList<>();
		this.positionMaps = new HashMap<>();
		this.numberOfBuckets = ORAMUtils.computeNumberOfNodes(treeHeight);
		this.visitedOutstandingVersions = new HashSet<>();
		this.taintedVersions = new HashSet<>();
		this.taintedSnapshots = new LinkedList<>();
		int versionId = ++sequenceNumber;
		this.positionMaps.put(versionId, encryptedPositionMap);

		OramSnapshot[] previous = new OramSnapshot[0];
		OramSnapshot snap = new OramSnapshot(versionId, previous, encryptedStash);

		outstandingTrees.add(snap);
		oramClientContexts = new HashMap<>();
	}

	public ORAMContext getOramContext() {
		return oramContext;
	}

	public abstract EncryptedPositionMaps getPositionMaps(int clientId, GetPositionMap request);

	public EncryptedStashesAndPaths getStashesAndPaths(int pathId, int clientId) {
		ORAMClientContext oramClientContext = oramClientContexts.get(clientId);
		if (oramClientContext == null) {
			return null;
		}
		OramSnapshot[] outstandingTrees = oramClientContext.getOutstandingVersions();

		List<Integer> pathLocations = ORAMUtils.computePathLocationsList(pathId, oramContext.getTreeHeight());
		Map<Integer, EncryptedStash> encryptedStashes = new HashMap<>();//Make it global and reuse it
		Map<Integer, Map<Integer, EncryptedBucket>> pathContents = new HashMap<>();
		Set<Integer> visitedVersions = new HashSet<>();
		for (OramSnapshot outstandingTree : outstandingTrees) {
			traverseVersions(outstandingTree, pathLocations, encryptedStashes, pathContents, visitedVersions);
		}

		Map<Integer, EncryptedBucket[]> compactedPaths = new HashMap<>(pathContents.size());
		for (Map.Entry<Integer, Map<Integer, EncryptedBucket>> entry : pathContents.entrySet()) {
			EncryptedBucket[] buckets = new EncryptedBucket[entry.getValue().size()];
			int i = 0;
			for (EncryptedBucket value : entry.getValue().values()) {
				buckets[i++] = value;
			}
			compactedPaths.put(entry.getKey(), buckets);
		}

		return new EncryptedStashesAndPaths(encryptedStashes, compactedPaths);
	}

	private void traverseVersions(OramSnapshot outstanding, List<Integer> pathLocations,
								  Map<Integer, EncryptedStash> encryptedStashes,
								  Map<Integer, Map<Integer, EncryptedBucket>> pathContents,
								  Set<Integer> visitedVersions) {
		Queue<OramSnapshot> queue = new ArrayDeque<>();
		queue.add(outstanding);
		Set<Integer> visitedVersionsPerSnapshot = new HashSet<>();
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
			Set<OramSnapshot> previous = version.getPrevious();
			for (OramSnapshot oramSnapshot : previous) {
				if (!visitedVersionsPerSnapshot.contains(oramSnapshot.getVersionId())) {
					visitedVersionsPerSnapshot.add(oramSnapshot.getVersionId());
					queue.add(oramSnapshot);
				}
			}
		}
		visitedVersions.addAll(visitedVersionsPerSnapshot);
	}

	public boolean performEviction(EncryptedStash encryptedStash, EncryptedPositionMap encryptedPositionMap,
								   Map<Integer, EncryptedBucket> encryptedPath, int clientId) {
		ORAMClientContext oramClientContext = oramClientContexts.remove(clientId);
		if (oramClientContext == null) {
			logger.debug("Client {} doesn't have any client context", clientId);
			return false;
		}
		OramSnapshot[] outstandingVersions = oramClientContext.getOutstandingVersions();
		int newVersionId = oramClientContext.getNewVersionId();

		positionMaps.put(newVersionId, encryptedPositionMap);
		OramSnapshot newVersion = new OramSnapshot(newVersionId,
				outstandingVersions, encryptedStash);

		for (Map.Entry<Integer, EncryptedBucket> entry : encryptedPath.entrySet()) {
			newVersion.setToLocation(entry.getKey(), entry.getValue());
		}

		cleanOutstandingTrees(outstandingVersions);

		outstandingTrees.add(newVersion);

		garbageCollect();
		return true;
	}

	protected void cleanOutstandingTrees(OramSnapshot[] outstandingVersions) {
		for (OramSnapshot outstandingVersion : outstandingVersions) {
			outstandingTrees.remove(outstandingVersion);
		}
	}

	private void garbageCollect() {
		long start, end;
		start = System.nanoTime();
		garbageCollectSnapshot();
		end = System.nanoTime();
		long delta = end - start;
		logger.info("garbageCollection[ns]: {}", delta);
	}

	private void garbageCollectSnapshot() {
		visitedOutstandingVersions.clear();
		taintedVersions.clear();
		taintedSnapshots.clear();


		//New and old outstanding snapshots
		List<OramSnapshot> outstandingVersions = new ArrayList<>(outstandingTrees);
		for (ORAMClientContext clientContext : oramClientContexts.values()) {
			Collections.addAll(outstandingVersions, clientContext.getOutstandingVersions());
		}

		//Taint versions
		Set<Integer> visitedVersions = new HashSet<>();
		for (OramSnapshot outstandingVersion : outstandingVersions) {
			if (visitedOutstandingVersions.contains(outstandingVersion.getVersionId())) {
				continue;
			}
			visitedOutstandingVersions.add(outstandingVersion.getVersionId());
			BitSet locationsMarker = new BitSet(numberOfBuckets);
			visitedVersions.clear();
			taintVersions(outstandingVersion, locationsMarker, visitedVersions, taintedSnapshots, taintedVersions);
			taintedVersions.addAll(visitedVersions);
		}

		//Remove not tainted versions
		logger.info("Number of tainted snapshots: {}", taintedSnapshots.size());
		logger.info("Number of distinct tainted snapshots: {}", taintedVersions.size());
		List<OramSnapshot> previousRemove = new ArrayList<>();
		for (OramSnapshot taintedSnapshot : taintedSnapshots) {
			previousRemove.clear();
			for (OramSnapshot previous : taintedSnapshot.getPrevious()) {
				if (!taintedVersions.contains(previous.getVersionId())) {
					previousRemove.add(previous);
				}
			}
			if (!previousRemove.isEmpty()) {
				logger.info("\n\n\n============>>>>  Number of previous to remove: {}\n\n\n", previousRemove.size());
			}
			taintedSnapshot.removePrevious(previousRemove);
		}
		currentNumberOfSnapshots = taintedSnapshots.size();
	}

	private void taintVersions(OramSnapshot currentSnapshot, BitSet locationsMarker, Set<Integer> visitedVersions,
							   List<OramSnapshot> taintedSnapshots, Set<Integer> taintedVersions) {
		BitSet currentLocationMarker = null;
		if (!visitedVersions.contains(currentSnapshot.getVersionId())) {
			//Check is this snapshot has needed path
			for (Map.Entry<Integer, EncryptedBucket> entry : currentSnapshot.getDifTree().entrySet()) {
				if (!locationsMarker.get(entry.getKey())) {
					if (currentLocationMarker == null) {
						long[] longArray = locationsMarker.toLongArray();
						currentLocationMarker = BitSet.valueOf(longArray);
					}
					currentLocationMarker.set(entry.getKey(), true); // Mark the location as tainted
				}
			}

			visitedVersions.add(currentSnapshot.getVersionId());
			if (!taintedVersions.contains(currentSnapshot.getVersionId())) {
				taintedSnapshots.add(currentSnapshot);
			}
		}
		if (currentLocationMarker == null) {// If I didn't modify the locationsMarker
			currentLocationMarker = locationsMarker;
		}
		if (currentLocationMarker.previousClearBit(numberOfBuckets - 1) == -1) { // If all locations are tainted
			return;
		}
		for (OramSnapshot previous : currentSnapshot.getPrevious()) {
			if (!visitedVersions.contains(previous.getVersionId()))
				taintVersions(previous, currentLocationMarker, visitedVersions, taintedSnapshots, taintedVersions);
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
		return currentNumberOfSnapshots;
	}
}
