package oram.server;

import oram.messages.GetPathMaps;
import oram.server.structure.*;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.secretsharing.VerifiableShare;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ORAM {
	protected final Logger logger = LoggerFactory.getLogger("oram");
	private final int oramId;
	private VerifiableShare encryptionKeyShare;
	private final ORAMContext oramContext;
	protected final Map<Integer, EncryptedPathMap> pathMaps;
	protected final HashMap<Integer, ORAMClientContext> oramClientContexts;
	protected int sequenceNumber;
	protected final ORAMTreeManager oramTreeManager;
	private final Map<Integer, EncryptedPathMap> resultedPositionMap;

	public ORAM(int oramId, int treeHeight, int bucketSize, int blockSize,
				EncryptedPathMap encryptedPathMap, EncryptedStash encryptedStash) {
		this.oramId = oramId;
		this.oramContext = new ORAMContext(treeHeight, bucketSize, blockSize);
		logger.info("ORAM size: {} blocks", oramContext.getTreeSize());
		logger.info("Number of slots: {}", oramContext.getTreeSize() * oramContext.getBucketSize());
		this.pathMaps = new HashMap<>();
		this.resultedPositionMap = new HashMap<>();
		this.oramClientContexts = new HashMap<>();
		int versionId = ++sequenceNumber;
		this.oramTreeManager = new ORAMTreeManager(oramContext, versionId, encryptedStash);
		this.pathMaps.put(versionId, encryptedPathMap);
	}

	public void setEncryptionKeyShare(VerifiableShare share) {
		if (this.encryptionKeyShare == null) {
			this.encryptionKeyShare = share;
		} else {
			logger.debug("ORAM {} already has an encryption key share", oramId);
		}
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

	public EncryptedPathMaps getPositionMaps(int clientId, GetPathMaps request) {
		int lastVersion = request.getLastVersion();
		Set<Integer> missingTriples = request.getMissingTriples();

		OutstandingTree outstandingTree = oramTreeManager.getOutstandingTree();
		Set<Integer> outstandingVersions = outstandingTree.getOutstandingVersions();

		resultedPositionMap.clear();

		for (int i : missingTriples) {
			EncryptedPathMap encryptedPathMap = pathMaps.get(i);
			if (encryptedPathMap != null) {
				resultedPositionMap.put(i, encryptedPathMap);
			}
		}

		for (int i = lastVersion + 1; i <= sequenceNumber; i++) {
			EncryptedPathMap encryptedPathMap = pathMaps.get(i);
			if (encryptedPathMap != null) {
				resultedPositionMap.put(i, encryptedPathMap);
			}
		}

		int[] currentOutstandingVersions = new int[outstandingVersions.size()];
		int i = 0;
		for (int outstandingVersion : outstandingVersions) {
			currentOutstandingVersions[i] = outstandingVersion;
			i++;
		}
		int newVersionId = ++sequenceNumber;

		ORAMClientContext oramClientContext = new ORAMClientContext(currentOutstandingVersions, newVersionId,
				outstandingTree);
		oramClientContexts.put(clientId, oramClientContext);
		return new EncryptedPathMaps(newVersionId, resultedPositionMap);
	}

	public EncryptedStashesAndPaths getStashesAndPaths(int pathId, int clientId) {
		ORAMClientContext oramClientContext = oramClientContexts.get(clientId);
		if (oramClientContext == null) {
			return null;
		}
		oramClientContext.setPathId(pathId);

		int[] pathLocations = ORAMUtils.computePathLocations(pathId, oramContext.getTreeHeight());

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

	public boolean performEviction(EncryptedStash encryptedStash, EncryptedPathMap encryptedPathMap,
								   Map<Integer, EncryptedBucket> encryptedPath, int clientId) {
		ORAMClientContext oramClientContext = oramClientContexts.remove(clientId);
		if (oramClientContext == null) {
			logger.debug("Client {} doesn't have any client context", clientId);
			return false;
		}
		int newVersionId = oramClientContext.getOperationSequence();
		OutstandingPath outstandingPath = oramClientContext.getOutstandingPath();

		pathMaps.put(newVersionId, encryptedPathMap);

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

		oramTreeManager.storeBuckets(newVersionId, newBucketSnapshots, outstandingPath, outstandingVersions,
				encryptedStash);

		cleanPositionMaps(outstandingVersions);
		return true;
	}

	private void cleanPositionMaps(int[] outstandingVersions) {

	}

	@Override
	public String toString() {
		return String.valueOf(oramId);
	}

	public int getOramId() {
		return oramId;
	}
}
