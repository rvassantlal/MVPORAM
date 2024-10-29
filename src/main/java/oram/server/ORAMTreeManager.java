package oram.server;

import oram.server.structure.*;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;

import java.util.*;

public class ORAMTreeManager {
	private final ORAMContext oramContext;
	protected OutstandingTree currentOutstandingTree;
	private final Deque<OutstandingTree> unusedOutstandingTreesPool;
	private final ArrayList<OutstandingTree> allOutstandingTreesObjects;
	private final Map<Integer, EncryptedBucket> getBucketsBuffer;

	public ORAMTreeManager(ORAMContext oramContext, int versionId, EncryptedStash encryptedStash) {
		this.oramContext = oramContext;
		this.unusedOutstandingTreesPool = new ArrayDeque<>();
		this.getBucketsBuffer = new HashMap<>();
		int nBuckets = ORAMUtils.computeNumberOfNodes(oramContext.getTreeHeight());
		this.currentOutstandingTree = new OutstandingTree(nBuckets, oramContext.getTreeLevels(), versionId,
				encryptedStash);
		this.allOutstandingTreesObjects = new ArrayList<>();
		allOutstandingTreesObjects.add(currentOutstandingTree);
	}

	public int getNOutstandingTreeObjects() {
		return allOutstandingTreesObjects.size();
	}

	public OutstandingTree getOutstandingTree() {
		currentOutstandingTree.increaseNPointers();
		return currentOutstandingTree;
	}

	public OutstandingPath getPath(OutstandingTree outstandingTree, int[] pathLocations) {
		OutstandingPath outstandingPath = new OutstandingPath(pathLocations.length);
		for (int pathLocation : pathLocations) {
			ArrayList<BucketSnapshot> outstandingTreeBucket = outstandingTree.getLocation(pathLocation);
			Set<BucketSnapshot> outstandingBucketsSet = new HashSet<>(outstandingTreeBucket);
			outstandingPath.storeLocation(pathLocation, outstandingBucketsSet);
		}

		outstandingTree.decreaseNPointers();
		if (outstandingTree.getNPointers() == 0 && outstandingTree != currentOutstandingTree) {
			unusedOutstandingTreesPool.addFirst(outstandingTree);
			outstandingTree.getStashes().clear();
			outstandingTree.getOutstandingVersions().clear();
		}

		return outstandingPath;
	}

	public EncryptedBucket[] getBuckets(Set<BucketSnapshot> outstandingTree) {
		int[] versions = new int[outstandingTree.size()];
		int k = 0;
		for (BucketSnapshot bucketSnapshot : outstandingTree) {
			getBucketsBuffer.put(bucketSnapshot.getVersionId(), bucketSnapshot.getBucket());
			versions[k++] = bucketSnapshot.getVersionId();
		}
		Arrays.sort(versions);
		EncryptedBucket[] result = new EncryptedBucket[getBucketsBuffer.size()];
		for (int i = 0; i < versions.length; i++) {
			result[i] = getBucketsBuffer.get(versions[i]);
		}
		getBucketsBuffer.clear();

		return result;
	}

	public void storeBuckets(int newVersionId, Map<Integer, BucketSnapshot> newBucketSnapshots, OutstandingPath outstandingPath,
							 int[] outstandingVersions, EncryptedStash encryptedStash) {
		OutstandingTree workingOutstandingTree = currentOutstandingTree;
		if (workingOutstandingTree.getNPointers() > 0) {
			workingOutstandingTree = unusedOutstandingTreesPool.pollFirst();
			if (workingOutstandingTree == null) {
				workingOutstandingTree = new OutstandingTree(currentOutstandingTree);
				allOutstandingTreesObjects.add(workingOutstandingTree);
			}
		}

		//Update outstanding versions and stashes
		Map<Integer, EncryptedStash> workingOutstandingTreeStashes = workingOutstandingTree.getStashes();
		Set<Integer> workingOutstandingTreeOutstandingVersions = workingOutstandingTree.getOutstandingVersions();
		workingOutstandingTreeStashes.putAll(currentOutstandingTree.getStashes());
		workingOutstandingTreeOutstandingVersions.addAll(currentOutstandingTree.getOutstandingVersions());
		for (Integer outstandingVersion : outstandingVersions) {
			workingOutstandingTreeStashes.remove(outstandingVersion);
			workingOutstandingTreeOutstandingVersions.remove(outstandingVersion);
		}
		workingOutstandingTreeStashes.put(newVersionId, encryptedStash);
		workingOutstandingTreeOutstandingVersions.add(newVersionId);

		//Update dirty locations
		for (Integer dirtyLocation : workingOutstandingTree.getDirtyLocations()) {
			ArrayList<BucketSnapshot> currentOutstandingLocation = currentOutstandingTree.getLocation(dirtyLocation);
			workingOutstandingTree.updateLocation(dirtyLocation, currentOutstandingLocation);
		}

		workingOutstandingTree.clearDirtyLocations();

		//Update outstanding path
		for (Map.Entry<Integer, BucketSnapshot> entry : newBucketSnapshots.entrySet()) {
			Integer dirtyLocation = entry.getKey();
			Set<BucketSnapshot> outstandingLocation = outstandingPath.getLocation(dirtyLocation);
			workingOutstandingTree.updateLocation(dirtyLocation, entry.getValue(), outstandingLocation);

			//mark dirty locations in outstandingTreesPool
			for (OutstandingTree outstandingTree : allOutstandingTreesObjects) {
				if (outstandingTree == workingOutstandingTree) {
					continue;
				}
				outstandingTree.markDirtyLocation(dirtyLocation);
			}
		}

		currentOutstandingTree = workingOutstandingTree;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Tree:\n");
		int nLevels = oramContext.getTreeLevels();
		//Write the size of each linked list in the tree
		for (int level = 0; level < nLevels; level++) {
			int nNodesPerLevel = (1 << level);
			sb.append(level);
			sb.append(": ");
			for (int node = 0; node < nNodesPerLevel; node++) {
				sb.append(currentOutstandingTree.getLocation(nNodesPerLevel - 1 + node).size());
				sb.append(" ");
			}
			sb.append('\n');
		}
		return sb.toString();
	}
}
