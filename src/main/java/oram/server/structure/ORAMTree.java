package oram.server.structure;

import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;

import java.util.*;

public class ORAMTree {
	private final ArrayList<BucketHolder> tree;
	private final ORAMContext oramContext;
	private final Deque<HashMap<Integer, Set<BucketSnapshot>>> outstandingTreeContextHolders;

	public ORAMTree(ORAMContext oramContext) {
		this.oramContext = oramContext;
		int treeSize = ORAMUtils.computeNumberOfNodes(oramContext.getTreeHeight());
		this.tree = new ArrayList<>(treeSize);
		this.outstandingTreeContextHolders = new ArrayDeque<>();
		for (int i = 0; i < treeSize; i++) {
			tree.add(new BucketHolder());
		}
	}

	public void freeOutStandingTreeContextHolder(HashMap<Integer, Set<BucketSnapshot>> outstandingTreeContext) {
		for (int i = 0; i < tree.size(); i++) {
			outstandingTreeContext.get(i).clear();
		}
		outstandingTreeContextHolders.addFirst(outstandingTreeContext);
	}

	public HashMap<Integer, Set<BucketSnapshot>> getOutstandingBucketsVersions() {
		HashMap<Integer, Set<BucketSnapshot>> result = outstandingTreeContextHolders.pollFirst();
		if (result == null) {
			result = new HashMap<>(tree.size());
		}

		for (int i = 0; i < tree.size(); i++) {
			BucketHolder bucketHolder = tree.get(i);
			Set<BucketSnapshot> outstandingTreeBucket = bucketHolder.getOutstandingBucketsVersions();
			Set<BucketSnapshot> partialResult = result.get(i);
			if (partialResult == null) {
				partialResult = new HashSet<>(outstandingTreeBucket);
				result.put(i, partialResult);
			} else {
				partialResult.addAll(outstandingTreeBucket);
			}
		}

		return result;
	}

	public EncryptedBucket[][] getPath(int[] pathLocations, HashMap<Integer, Set<BucketSnapshot>> outstandingTree) {
		EncryptedBucket[][] buckets = new EncryptedBucket[pathLocations.length][];
		for (int i = 0; i < pathLocations.length; i++) {
			int pathLocation = pathLocations[i];
			Set<BucketSnapshot> locationOutstandingVersions = outstandingTree.get(pathLocation);
			BucketHolder bucketHolder = tree.get(pathLocation);
			buckets[i] = bucketHolder.getBuckets(locationOutstandingVersions);
		}
		return buckets;
	}

	public void storeBucket(int location, BucketSnapshot snapshot, Set<BucketSnapshot> outstandingVersions,
							Set<Integer> globalOutstandingVersions) {
		tree.get(location).addSnapshot(snapshot, outstandingVersions, globalOutstandingVersions);
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
				sb.append(tree.get(nNodesPerLevel - 1 + node).size());
				sb.append(" ");
			}
			sb.append('\n');
		}
		return sb.toString();
	}
}
