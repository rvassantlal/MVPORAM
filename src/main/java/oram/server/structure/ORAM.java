package oram.server.structure;

import oram.messages.GetPositionMap;
import oram.security.EncryptionManager;
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
	private final Set<Integer> usedVersions;
	private final List<OramSnapshot> taintedSnapshots;
	private final Map<Integer, EncryptedStash> encryptedStashes;
	private final Set<Integer> visitedVersions;
	private final ORAMTree oramTree;
	private final Set<Integer> outstandingVersions;
	private final EncryptionManager encryptionManager; //For debugging


	public ORAM(int oramId, PositionMapType positionMapType, int garbageCollectionFrequency, int treeHeight,
				int bucketSize, int blockSize, EncryptedPositionMap encryptedPositionMap,
				EncryptedStash encryptedStash) {
		this.oramId = oramId;
		int treeSize = ORAMUtils.computeTreeSize(treeHeight, bucketSize);
		int nBuckets = ORAMUtils.computeNumberOfNodes(treeHeight);
		this.oramContext = new ORAMContext(positionMapType, garbageCollectionFrequency, treeHeight, treeSize,
				bucketSize, blockSize);
		logger.info("ORAM tree capacity: {} blocks ({} buckets)", treeSize, nBuckets);
		this.outstandingTrees = new LinkedList<>();
		this.positionMaps = new HashMap<>();
		this.numberOfBuckets = ORAMUtils.computeNumberOfNodes(treeHeight);
		this.visitedOutstandingVersions = new HashSet<>();
		this.taintedVersions = new HashSet<>();
		this.usedVersions = new HashSet<>();
		this.taintedSnapshots = new LinkedList<>();
		this.encryptedStashes = new HashMap<>();
		this.visitedVersions = new HashSet<>();
		this.outstandingVersions = new HashSet<>();
		int versionId = ++sequenceNumber;
		this.positionMaps.put(versionId, encryptedPositionMap);
		this.oramTree = new ORAMTree(oramContext);
		this.encryptionManager = new EncryptionManager();

		OramSnapshot[] previous = new OramSnapshot[0];
		OramSnapshot snap = new OramSnapshot(versionId, previous, encryptedStash, oramContext.getTreeLevels());
		currentNumberOfSnapshots++;
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

		oramClientContext.setPathId(pathId);
		encryptedStashes.clear();//This is global and reused - make sure that returning this object doesn't cause any issues
		OramSnapshot[] outstandingTrees = oramClientContext.getOutstandingVersions();

		int maxVersion = 0;
		outstandingVersions.clear();
		for (OramSnapshot outstandingTree : outstandingTrees) {
			encryptedStashes.put(outstandingTree.getVersionId(), outstandingTree.getStash());
			maxVersion = Math.max(maxVersion, outstandingTree.getVersionId());
			outstandingVersions.add(outstandingTree.getVersionId());
		}

		int[] pathLocations = ORAMUtils.computePathLocations(pathId, oramContext.getTreeHeight());// pre compute
		LinkedList<EncryptedBucket> paths = new LinkedList<>();
		//logger.info("Client {} has {} outstanding versions", clientId, outstandingVersions.size());
		for (int pathLocation : pathLocations) {
			LinkedList<EncryptedBucket> snapshots;
			if (pathLocation != 0) {
				snapshots = oramTree.getFromLocation(pathLocation, maxVersion);
			} else {
				snapshots = oramTree.getFromRoot(outstandingVersions);
			}
			//logger.info("[Client {}] Path location {} has {} buckets ({})", clientId, pathLocation, snapshots.size(), snapshots);
			paths.addAll(snapshots);
		}

		/*logger.info("Sending path {} to client {} with {} buckets", pathId, clientId, paths.size());
		for (EncryptedBucket path : paths) {
			Bucket bucket = encryptionManager.decryptBucket(oramContext, path);
			logger.info("{}", bucket);
		}*/

		Map<Integer, EncryptedBucket[]> compactedPaths = new HashMap<>(paths.size());
		EncryptedBucket[] buckets = new EncryptedBucket[paths.size()];
		int i = 0;
		for (EncryptedBucket bucket : paths) {
			buckets[i++] = bucket;
		}
		compactedPaths.put(maxVersion, buckets);

		return new EncryptedStashesAndPaths(encryptedStashes, compactedPaths);
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
				outstandingVersions, encryptedStash, oramContext.getTreeLevels());
		oramTree.storeSnapshot(oramClientContext.getPathId(), newVersion);
		for (OramSnapshot outstandingVersion : outstandingVersions) {
			outstandingVersion.addChild(newVersion);
		}

		for (Map.Entry<Integer, EncryptedBucket> entry : encryptedPath.entrySet()) {
			newVersion.setToLocation(entry.getKey(), entry.getValue());
		}

		cleanOutstandingTrees(outstandingVersions);
		currentNumberOfSnapshots++;
		outstandingTrees.add(newVersion);
		if(sequenceNumber % oramContext.getGarbageCollectionFrequency() == 0){
			garbageCollect();
		}
		logger.debug("{}", oramTree);
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
		//logger.info("Number of tainted versions: {}", taintedVersions.size());
		//logger.info("Number of used versions: {}", usedVersions.size());
		oramTree.garbageCollect(usedVersions);
		visitedVersions.clear();
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
		for (OramSnapshot outstandingVersion : outstandingVersions) {
			if (visitedOutstandingVersions.contains(outstandingVersion.getVersionId())) {
				continue;
			}
			visitedOutstandingVersions.add(outstandingVersion.getVersionId());
			BitSet locationsMarker = new BitSet(numberOfBuckets);
			visitedVersions.clear();
			taintVersions(outstandingVersion, locationsMarker, outstandingVersions);
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

	private void taintVersions(OramSnapshot currentSnapshot, BitSet locationsMarker, Set<OramSnapshot> outstandingVersions) {
		BitSet currentLocationMarker = null;
		Integer versionId = currentSnapshot.getVersionId();
		if (!visitedVersions.contains(versionId)) {
			if(!outstandingVersions.contains(currentSnapshot)) {
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
				taintVersions(previous, currentLocationMarker, outstandingVersions);
		}
	}

	@Override
	public String toString() {
		return String.valueOf(oramId);
	}

	public int getOramId() {
		return oramId;
	}

	public int getNumberOfOutstanding() {
		return outstandingTrees.size();
	}

	public int getNumberOfVersion() {
		return currentNumberOfSnapshots;
	}
}
