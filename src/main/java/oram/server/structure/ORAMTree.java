package oram.server.structure;

import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;

import java.util.*;

public class ORAMTree {
	private final ArrayList<BucketHolder> tree;
	private final ORAMContext oramContext;
	private final HashMap<Integer, Set<Integer>> outstandingBucketsVersions;

	public ORAMTree(ORAMContext oramContext) {
		this.oramContext = oramContext;
		int treeSize = ORAMUtils.computeNumberOfNodes(oramContext.getTreeHeight());
		this.tree = new ArrayList<>(treeSize);
		this.outstandingBucketsVersions = new HashMap<>(treeSize);
		for (int i = 0; i < treeSize; i++) {
			tree.add(new BucketHolder());
			outstandingBucketsVersions.put(i, new HashSet<>());
		}
	}

	public HashMap<Integer, Set<Integer>> getOutstandingBucketsVersions() {
		HashMap<Integer, Set<Integer>> result = new HashMap<>(tree.size());

		for (int i = 0; i < tree.size(); i++) {
			BucketHolder bucketHolder = tree.get(i);
			Set<Integer> outstandingBuckets = outstandingBucketsVersions.get(i);
			outstandingBuckets.clear();
			outstandingBuckets.addAll(bucketHolder.getOutstandingBucketsVersions());
			Set<Integer> partialResult = new HashSet<>(outstandingBuckets);
			result.put(i, partialResult);
		}

		return result;
	}

	public EncryptedBucket[][] getPath(int[] pathLocations, HashMap<Integer, Set<Integer>> outstandingTree) {
		EncryptedBucket[][] buckets = new EncryptedBucket[pathLocations.length][];
		for (int i = 0; i < pathLocations.length; i++) {
			int pathLocation = pathLocations[i];
			Set<Integer> locationOutstandingVersions = outstandingTree.get(pathLocation);
			BucketHolder bucketHolder = tree.get(pathLocation);
			buckets[i] = bucketHolder.getBuckets(locationOutstandingVersions);
		}
		return buckets;
	}

	public void storeBucket(int location, BucketSnapshot snapshot, Set<Integer> outstandingVersions,
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
