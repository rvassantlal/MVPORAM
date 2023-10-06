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
	protected final List<Integer> outstandingTrees; //all versions that are not previous of any other version
	protected final Map<Integer, EncryptedPositionMap> positionMaps;
	protected final Map<Integer, EncryptedStash> stashes;
	protected final HashMap<Integer, ORAMClientContext> oramClientContexts;
	protected int sequenceNumber = 0;
	private int currentNumberOfSnapshots;
	private final Map<Integer, EncryptedStash> getPSEncryptedStashesBuffer;
	private final Map<Integer, int[]> preComputedPathLocations;
	protected final ORAMTree oramTree;
	private final Set<Integer> locationOutstandingVersions;// Stores outstanding versions of all clients

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
		this.stashes = new HashMap<>();
		this.getPSEncryptedStashesBuffer = new HashMap<>();
		this.locationOutstandingVersions = new HashSet<>();
		this.oramTree = new ORAMTree(oramContext);
		this.oramClientContexts = new HashMap<>();
		int numberOfPaths = 1 << oramContext.getTreeHeight();
		this.preComputedPathLocations = new HashMap<>(numberOfPaths);
		for (int i = 0; i < numberOfPaths; i++) {
			preComputedPathLocations.put(i, ORAMUtils.computePathLocations(i, oramContext.getTreeHeight()));
		}
		int versionId = ++sequenceNumber;
		this.positionMaps.put(versionId, encryptedPositionMap);
		this.stashes.put(versionId, encryptedStash);
		currentNumberOfSnapshots++;
		outstandingTrees.add(versionId);
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
		getPSEncryptedStashesBuffer.clear();//This is global and reused - make sure that returning this object doesn't cause any issues
		int[] outstandingVersions = oramClientContext.getOutstandingVersions();
		int[] pathLocations = preComputedPathLocations.get(pathId);
		EncryptedStash[] outstandingStashes = oramClientContext.getOutstandingStashes();
		HashMap<Integer, Set<Integer>> outstandingTree = oramClientContext.getOutstandingTree();
		HashMap<Integer, Set<Integer>> usedOutstandingTreeVersions = new HashMap<>(oramContext.getTreeLevels());
		for (int i = 0; i < outstandingVersions.length; i++) {
			getPSEncryptedStashesBuffer.put(outstandingVersions[i], outstandingStashes[i]);
		}

		logger.info("Client {} is reading path {} with {} outstanding versions", clientId, pathId,
				outstandingVersions.length);
		for (int pathLocation : pathLocations) {
			usedOutstandingTreeVersions.put(pathLocation, outstandingTree.get(pathLocation));
			logger.info("Location {} has {} outstanding versions", pathLocation,
					outstandingTree.get(pathLocation));
		}
		EncryptedBucket[][] path = oramTree.getPath(pathLocations, usedOutstandingTreeVersions);
		oramClientContext.setOutstandingTree(usedOutstandingTreeVersions);

		Map<Integer, EncryptedBucket[]> compactedPaths = new HashMap<>(path.length);
		for (int i = 0; i < path.length; i++) {
			logger.info("Path location index {} has {} buckets", i, path[i].length);
			compactedPaths.put(i, path[i]);
		}

		return new EncryptedStashesAndPaths(getPSEncryptedStashesBuffer, compactedPaths);
	}

	public boolean performEviction(EncryptedStash encryptedStash, EncryptedPositionMap encryptedPositionMap,
								   Map<Integer, EncryptedBucket> encryptedPath, int clientId) {
		ORAMClientContext oramClientContext = oramClientContexts.remove(clientId);
		if (oramClientContext == null) {
			logger.debug("Client {} doesn't have any client context", clientId);
			return false;
		}

		int newVersionId = oramClientContext.getNewVersionId();

		positionMaps.put(newVersionId, encryptedPositionMap);
		stashes.put(newVersionId, encryptedStash);

		logger.info("Client {} is performing eviction in path {}", clientId, oramClientContext.getPathId());
		for (Map.Entry<Integer, EncryptedBucket> entry : encryptedPath.entrySet()) {
			int location = entry.getKey();
			locationOutstandingVersions.clear();
			for (ORAMClientContext value : oramClientContexts.values()) {
				Set<Integer> clientLocationOV = value.getOutstandingTree().get(location);
				if (clientLocationOV == null) {// This client doesn't have any outstanding version for this location
					continue;
				}
				locationOutstandingVersions.addAll(clientLocationOV);
			}
			BucketSnapshot bucketSnapshot = new BucketSnapshot(newVersionId, entry.getValue());
			oramTree.storeBucket(location, bucketSnapshot, oramClientContext.getOutstandingTree().get(location),
					locationOutstandingVersions);
			logger.debug("Storing bucket {} at location {}", bucketSnapshot, location);
		}

		cleanOutstandingTrees(oramClientContext.getOutstandingVersions());
		currentNumberOfSnapshots++;
		outstandingTrees.add(newVersionId);

		logger.info("{}", oramTree);
		return true;
	}

	protected void cleanOutstandingTrees(int[] outstandingVersions) {
		for (Integer outstandingVersion : outstandingVersions) {
			outstandingTrees.remove(outstandingVersion);
			stashes.remove(outstandingVersion);
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
