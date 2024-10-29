package oram.server;

import oram.client.structure.EvictionMap;
import oram.messages.GetPositionMap;
import oram.server.structure.*;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import oram.utils.PositionMapType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.secretsharing.VerifiableShare;

import java.util.*;

public abstract class ORAM {
	protected final Logger logger = LoggerFactory.getLogger("oram");
	private final int oramId;
	private final VerifiableShare encryptionKeyShare;
	private final ORAMContext oramContext;
	protected final Map<Integer, EncryptedPositionMap> positionMaps;
	protected final HashMap<Integer, ORAMClientContext> oramClientContexts;
	protected int sequenceNumber;
	protected final ORAMTreeManager oramTreeManager;
	private final Map<Integer, int[]> preComputedPathLocations;
	protected final Map<Integer, EvictionMap> evictionMaps;
	protected final Map<Integer, int[]> outstandingVersions;

	public ORAM(int oramId, VerifiableShare encryptionKeyShare, PositionMapType positionMapType, int treeHeight,
				int bucketSize, int blockSize, EncryptedPositionMap encryptedPositionMap,
				EncryptedStash encryptedStash) {
		this.oramId = oramId;
		this.encryptionKeyShare = encryptionKeyShare;
		int treeSize = ORAMUtils.computeTreeSize(treeHeight, bucketSize);
		int nBuckets = ORAMUtils.computeNumberOfNodes(treeHeight);
		this.oramContext = new ORAMContext(positionMapType, treeHeight, treeSize, bucketSize, blockSize);
		logger.info("ORAM tree capacity: {} blocks ({} buckets)", treeSize, nBuckets);
		this.positionMaps = new HashMap<>();
		this.evictionMaps = new HashMap<>();
		this.outstandingVersions = new HashMap<>();
		int numberOfPaths = 1 << oramContext.getTreeHeight();
		this.preComputedPathLocations = new HashMap<>(numberOfPaths);
		for (int i = 0; i < numberOfPaths; i++) {
			preComputedPathLocations.put(i, ORAMUtils.computePathLocations(i, oramContext.getTreeHeight()));
		}
		this.oramClientContexts = new HashMap<>();
		int versionId = ++sequenceNumber;
		this.oramTreeManager = new ORAMTreeManager(oramContext, versionId, encryptedStash);
		this.positionMaps.put(versionId, encryptedPositionMap);
		this.evictionMaps.put(versionId, new EvictionMap());
		this.outstandingVersions.put(versionId, new int[]{ORAMUtils.DUMMY_VERSION});
	}

	public EncryptedDebugSnapshot getDebugSnapshot(int clientId) {
		ORAMClientContext oramClientContext = oramClientContexts.get(clientId);
		if (oramClientContext == null) {
			return null;
		}
		OutstandingTree outstandingTree = oramClientContext.getOutstandingTree();

		return new EncryptedDebugSnapshot(outstandingTree.getTree(), outstandingTree.getStashes());
	}

	public VerifiableShare getEncryptionKeyShare() {
		return encryptionKeyShare;
	}

	public ORAMContext getOramContext() {
		return oramContext;
	}

	public int getNOutstandingTreeObjects() {
		return oramTreeManager.getNOutstandingTreeObjects();
	}

	public abstract EncryptedPositionMaps getPositionMaps(int clientId, GetPositionMap request);

	public EncryptedStashesAndPaths getStashesAndPaths(int pathId, int clientId) {
		ORAMClientContext oramClientContext = oramClientContexts.get(clientId);
		if (oramClientContext == null) {
			return null;
		}
		oramClientContext.setPathId(pathId);

		int[] pathLocations = preComputedPathLocations.get(pathId);

		OutstandingTree outstandingTree = oramClientContext.getOutstandingTree();
		Map<Integer, EncryptedStash> outstandingStashes = new HashMap<>(outstandingTree.getStashes());
		//EncryptedStash[] outstandingStashes = getOrderedStashesArray(outstandingTree.getStashes());

		logger.debug("Client {} is reading path {} ({}) with {} outstanding stashes", clientId, pathId, pathLocations,
				outstandingStashes.size());

		OutstandingPath outstandingPath = oramTreeManager.getPath(outstandingTree, pathLocations);

		oramClientContext.storeOutstandingPath(outstandingPath);
		int nTotalBuckets = outstandingPath.getTotalNumberOfBuckets();
		EncryptedBucket[] encryptedBuckets = new EncryptedBucket[nTotalBuckets];
		int k = 0;
		for (int pathLocation : pathLocations) {
			Set<BucketSnapshot> bucketsSet = outstandingPath.getLocation(pathLocation);
			EncryptedBucket[] orderedBuckets = oramTreeManager.getBuckets(bucketsSet);
			logger.debug("Path location {} has {} buckets", pathLocation, orderedBuckets.length);
			for (EncryptedBucket orderedBucket : orderedBuckets) {
				encryptedBuckets[k++] = orderedBucket;
			}
		}
		return new EncryptedStashesAndPaths(outstandingStashes, encryptedBuckets);
	}

	public boolean performEviction(EncryptedStash encryptedStash, EncryptedPositionMap encryptedPositionMap,
								   Map<Integer, EncryptedBucket> encryptedPath, int clientId, EvictionMap evictionMap) {
		ORAMClientContext oramClientContext = oramClientContexts.remove(clientId);
		if (oramClientContext == null) {
			logger.debug("Client {} doesn't have any client context", clientId);
			return false;
		}
		int newVersionId = oramClientContext.getNewVersionId();
		OutstandingPath outstandingPath = oramClientContext.getOutstandingPath();

		positionMaps.put(newVersionId, encryptedPositionMap);
		evictionMaps.put(newVersionId, evictionMap);

		logger.debug("Client {} is performing eviction in path {} with version {}", clientId,
				oramClientContext.getPathId(), newVersionId);
		Map<Integer, BucketSnapshot> newBucketSnapshots = new HashMap<>(encryptedPath.size());

		for (Map.Entry<Integer, EncryptedBucket> entry : encryptedPath.entrySet()) {
			Integer location = entry.getKey();
			BucketSnapshot bucketSnapshot = new BucketSnapshot(newVersionId, entry.getValue());
			logger.debug("Storing bucket {} at location {}", bucketSnapshot, location);
			newBucketSnapshots.put(location, bucketSnapshot);
		}
		int[] outstandingVersions = oramClientContext.getOutstandingVersions();
		this.outstandingVersions.put(newVersionId, outstandingVersions);

		oramTreeManager.storeBuckets(newVersionId, newBucketSnapshots, outstandingPath, outstandingVersions,
				encryptedStash);

		cleanPositionMaps(outstandingVersions);

		logger.debug("{}\n", oramTreeManager);
		return true;
	}

	abstract protected void cleanPositionMaps(int[] outstandingVersions);

	@Override
	public String toString() {
		return String.valueOf(oramId);
	}

	public int getOramId() {
		return oramId;
	}
}
