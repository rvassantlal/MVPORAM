package oram.server.structure;

import oram.messages.GetPositionMap;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import oram.utils.PositionMapType;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
	private final Set<Integer> usedVersions;
	private final List<OramSnapshot> taintedSnapshots;
	private final Map<Integer, EncryptedStash> encryptedStashes;
	private final Map<Integer, Map<Integer, EncryptedBucket>> pathContents;
	private final Set<Integer> visitedVersions;

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
		this.usedVersions = new HashSet<>();
		this.taintedSnapshots = new LinkedList<>();
		this.encryptedStashes = new HashMap<>();
		this.pathContents = new HashMap<>();
		this.visitedVersions = new HashSet<>();
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
		encryptedStashes.clear();//This is global and reused - make sure that returning this object doesn't cause any issues
		pathContents.clear();
		visitedVersions.clear();
		OramSnapshot[] outstandingTrees = oramClientContext.getOutstandingVersions();

		List<Integer> pathLocations = ORAMUtils.computePathLocationsList(pathId, oramContext.getTreeHeight());
		for (OramSnapshot outstandingTree : outstandingTrees) {
			encryptedStashes.put(outstandingTree.getVersionId(),outstandingTree.getStash());
			traverseVersions(outstandingTree, pathLocations);
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

	private void traverseVersions(OramSnapshot outstanding, List<Integer> pathLocations) {
		Queue<OramSnapshot> queue = new ArrayDeque<>();
		queue.add(outstanding);
		Set<Integer> visitedVersionsPerSnapshot = new HashSet<>();
		visitedVersionsPerSnapshot.add(outstanding.getVersionId());
		Integer leaf = pathLocations.get(0);
		while (!queue.isEmpty()) {
			OramSnapshot version = queue.poll();
			Integer versionId = version.getVersionId();
			if (!visitedVersions.contains(versionId)) {
				for (Integer pathLocation : pathLocations) {
					EncryptedBucket bucket = version.getFromLocation(pathLocation);
					if (bucket != null) {
						Map<Integer, EncryptedBucket> encryptedBuckets = pathContents.computeIfAbsent(
								versionId, k -> new HashMap<>(oramContext.getTreeLevels()));
						encryptedBuckets.put(pathLocation, bucket);
					}
				}
				Map<Integer, EncryptedBucket> map = pathContents.get(versionId);
				if (map != null && map.get(leaf) == null) {
					Set<OramSnapshot> previous = version.getPrevious();
					for (OramSnapshot oramSnapshot : previous) {
						if (!visitedVersionsPerSnapshot.contains(oramSnapshot.getVersionId())) {
							visitedVersionsPerSnapshot.add(oramSnapshot.getVersionId());
							queue.add(oramSnapshot);
						}
					}
				}
			}
		}
	}
	private void traverseVersions2(OramSnapshot outstanding, List<Integer> pathLocations,
										  Map<Integer, EncryptedStash> encryptedStashes,
										  Map<Integer, Map<Integer, EncryptedBucket>> pathContents,
										  Set<Integer> visitedVersions) {
		Queue<Pair<Integer, OramSnapshot>> queue = new ArrayDeque<>();
		queue.add(Pair.of(0, outstanding));
		Set<Integer> visitedVersionsPerSnapshot = new HashSet<>(numberOfBuckets);
		visitedVersionsPerSnapshot.add(outstanding.getVersionId());
		Map<Integer, Set<Integer>> myEncryptedPath = new HashMap<>(oramContext.getTreeLevels());
		Integer leaf = pathLocations.get(0);
		while (!queue.isEmpty()) {
			Pair<Integer, OramSnapshot> pair = queue.poll();
			Integer level = pair.getLeft();
			OramSnapshot version = pair.getRight();
			Integer versionId = version.getVersionId();
			if (!visitedVersions.contains(versionId)) {
				encryptedStashes.put(versionId, version.getStash());
				Set<Integer> previousStep = new HashSet<>();
				myEncryptedPath.entrySet().stream().filter(a -> a.getKey() < level).forEach(lvl -> previousStep.addAll(lvl.getValue()));
				for (Integer pathLocation : pathLocations) {
					EncryptedBucket bucket = version.getFromLocation(pathLocation);
					if (bucket != null && (level == 0 || !previousStep.contains(pathLocation))) {
						Map<Integer, EncryptedBucket> encryptedBuckets = pathContents.computeIfAbsent(
								versionId, k -> new HashMap<>(oramContext.getTreeLevels()));
						encryptedBuckets.put(pathLocation, bucket);
						Set<Integer> locations = myEncryptedPath.computeIfAbsent(
								level, k -> new HashSet<>());
						locations.add(pathLocation);
					}
				}
				if (myEncryptedPath.size() < oramContext.getTreeLevels()) {
					Set<OramSnapshot> previous = version.getPrevious();
					for (OramSnapshot oramSnapshot : previous) {
						if (!visitedVersionsPerSnapshot.contains(oramSnapshot.getVersionId())) {
							visitedVersionsPerSnapshot.add(oramSnapshot.getVersionId());
							queue.add(Pair.of(pair.getLeft() + 1, oramSnapshot));
						}
					}
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
		for (OramSnapshot outstandingVersion : outstandingVersions) {
			outstandingVersion.addChild(newVersion);
		}

		for (Map.Entry<Integer, EncryptedBucket> entry : encryptedPath.entrySet()) {
			newVersion.setToLocation(entry.getKey(), entry.getValue());
		}

		cleanOutstandingTrees(outstandingVersions);

		outstandingTrees.add(newVersion);
		if(sequenceNumber % 500 == 0){
			garbageCollect();
		}
		return true;
	}

	protected void cleanOutstandingTrees(OramSnapshot[] outstandingVersions) {
		for (OramSnapshot outstandingVersion : outstandingVersions) {
			outstandingTrees.remove(outstandingVersion);
		}
	}

	private void garbageCollect() {
		//long start, end;
		//start = System.nanoTime();
		garbageCollectSnapshot();
		//long delta = end - start;
		//logger.info("garbageCollection[ns]: {}", delta);
	}

	private void garbageCollectSnapshot() {
		visitedOutstandingVersions.clear();
		taintedVersions.clear();
		usedVersions.clear();
		taintedSnapshots.clear();

		//New and old outstanding snapshots
		Set<OramSnapshot> outstandingVersions = new HashSet<>(outstandingTrees);
		for (ORAMClientContext clientContext : oramClientContexts.values()) {
			Collections.addAll(outstandingVersions, clientContext.getOutstandingVersions());
		}
		//Taint versions
		Set<Integer> visitedVersions = new LinkedHashSet<>();
		for (OramSnapshot outstandingVersion : outstandingVersions) {
			if (visitedOutstandingVersions.contains(outstandingVersion.getVersionId())) {
				continue;
			}
			visitedOutstandingVersions.add(outstandingVersion.getVersionId());
			BitSet locationsMarker = new BitSet(numberOfBuckets);
			visitedVersions.clear();
			taintVersions(outstandingVersion, locationsMarker, visitedVersions);
			taintedVersions.addAll(visitedVersions);
		}

		//Remove not tainted versions

		List<OramSnapshot> previousRemove = new ArrayList<>();
		for (OramSnapshot taintedSnapshot : taintedSnapshots) {
			previousRemove.clear();
			for (OramSnapshot previous : taintedSnapshot.getPrevious()) {
				if (!taintedVersions.contains(previous.getVersionId())) {
					previousRemove.add(previous);
				}
			}
			taintedSnapshot.removePrevious(previousRemove);
		}
		currentNumberOfSnapshots = usedVersions.size();

		//Remove not used versions
		for (OramSnapshot taintedSnapshot : taintedSnapshots) {
			if (!usedVersions.contains(taintedSnapshot.getVersionId())) {
				removeUnusedVersion(taintedSnapshot);
			}
		}
	}

	private void removeUnusedVersion(OramSnapshot unusedSnapshot) {
		Set<OramSnapshot> previous = unusedSnapshot.getPrevious();
		for (OramSnapshot childSnapshot : unusedSnapshot.getChildSnapshots()) {
			childSnapshot.removePrevious(unusedSnapshot);
			for (OramSnapshot previousSnapshot : previous) {
				if(usedVersions.contains(previousSnapshot.getVersionId()))
					childSnapshot.addPrevious(previousSnapshot);
			}
		}
	}

	private void taintVersions(OramSnapshot currentSnapshot, BitSet locationsMarker, Set<Integer> visitedVersions) {
		BitSet currentLocationMarker = null;
		Integer versionId = currentSnapshot.getVersionId();
		if (!visitedVersions.contains(versionId)) {
			if(!locationsMarker.isEmpty()){
				currentSnapshot.removeStash();
			}
			//Check is this snapshot has needed path
			for (Integer key : currentSnapshot.getDifTree().keySet()) {
				if (!locationsMarker.get(key)) {
					if (currentLocationMarker == null) {
						currentLocationMarker = new BitSet(numberOfBuckets);
						currentLocationMarker.or(locationsMarker);
					}
					currentLocationMarker.set(key, true); // Mark the location as tainted
				}
			}

			if (currentLocationMarker != null) {
				usedVersions.add(versionId);
			}
			visitedVersions.add(versionId);
			if (!taintedVersions.contains(versionId)) {
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
				taintVersions(previous, currentLocationMarker, visitedVersions);
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
