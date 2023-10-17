package oram.server.structure;

import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;

import java.util.*;

public class ORAMTree {
	private final ArrayList<BucketHolder> tree;
	private final ORAMContext oramContext;
	private final Deque<HashMap<Integer, Set<BucketSnapshot>>> outstandingTreeContextHoldersPool;
	private final Set<Integer> dirtyLocations;
	private HashMap<Integer, Set<BucketSnapshot>> currentOutstandingTreeContext;
	private final Map<Integer, EncryptedBucket> getBucketsBuffer;

	public ORAMTree(ORAMContext oramContext) {
		this.oramContext = oramContext;
		int treeSize = ORAMUtils.computeNumberOfNodes(oramContext.getTreeHeight());
		this.tree = new ArrayList<>(treeSize);
		this.outstandingTreeContextHoldersPool = new ArrayDeque<>();
		this.dirtyLocations = new HashSet<>();
		this.currentOutstandingTreeContext = new HashMap<>(treeSize);
		this.getBucketsBuffer = new HashMap<>();
		for (int i = 0; i < treeSize; i++) {
			tree.add(new BucketHolder());
			currentOutstandingTreeContext.put(i, new HashSet<>());
		}
	}

	public void freeOutStandingTreeContextHolder(HashMap<Integer, Set<BucketSnapshot>> outstandingTreeContext) {
		outstandingTreeContextHoldersPool.addFirst(outstandingTreeContext);
	}

	public HashMap<Integer, Set<BucketSnapshot>> getOutstandingBucketsVersions() {
		HashMap<Integer, Set<BucketSnapshot>> result = outstandingTreeContextHoldersPool.pollFirst();
		if (result == null) {
			result = new HashMap<>(currentOutstandingTreeContext);
		}

		//Create copy of the current context
		result.putAll(currentOutstandingTreeContext);

		//Update dirty locations - location written during the last eviction
		for (Integer dirtyLocation : dirtyLocations) {
			BucketHolder bucketHolder = tree.get(dirtyLocation);
			ArrayList<BucketSnapshot> outstandingTreeBucket = bucketHolder.getOutstandingBucketsVersions();
			Set<BucketSnapshot> partialResult = new HashSet<>(outstandingTreeBucket);
			result.put(dirtyLocation, partialResult);
		}

		dirtyLocations.clear();

		currentOutstandingTreeContext = result;

		return currentOutstandingTreeContext;
	}

	public EncryptedBucket[][] getPath(int[] pathLocations, HashMap<Integer, Set<BucketSnapshot>> outstandingTree) {
		EncryptedBucket[][] buckets = new EncryptedBucket[pathLocations.length][];

		for (int i = 0; i < pathLocations.length; i++) {
			int pathLocation = pathLocations[i];
			//System.out.println(pathLocation);
			Set<BucketSnapshot> locationOutstandingVersions = outstandingTree.get(pathLocation);
			buckets[i] = getBuckets(locationOutstandingVersions);
		}
		return buckets;
	}

	private EncryptedBucket[] getBuckets(Set<BucketSnapshot> outstandingTree) {
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

	public void storeBucket(int location, BucketSnapshot snapshot, Set<BucketSnapshot> outstandingVersions) {
		tree.get(location).addSnapshot(snapshot, outstandingVersions);
		dirtyLocations.add(location);
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
