package oram.server.structure;

import java.util.*;

public class OutstandingTree {
	private final BucketHolder[] tree;
	private final Map<Integer, EncryptedStash> stashes;
	private final Set<Integer> outstandingVersions;
	private final int nLevels;
	private final Set<Integer> dirtyLocations;
	private int nPointers;

	public OutstandingTree(int nBuckets, int nLevels, int versionId, EncryptedStash encryptedStash) {
		this.tree = new BucketHolder[nBuckets];
		this.nLevels = nLevels;
		this.dirtyLocations = new HashSet<>();
		for (int i = 0; i < nBuckets; i++) {
			tree[i] = new BucketHolder();
		}
		this.stashes = new HashMap<>();
		this.outstandingVersions = new HashSet<>();
		stashes.put(versionId, encryptedStash);
		outstandingVersions.add(versionId);
	}

	public OutstandingTree(OutstandingTree outstandingTree) {
		this.tree = new BucketHolder[outstandingTree.tree.length];
		for (int i = 0; i < outstandingTree.tree.length; i++) {
			this.tree[i] = new BucketHolder(outstandingTree.tree[i].getOutstandingBucketsVersions());
		}
		this.nLevels = outstandingTree.nLevels;
		this.dirtyLocations = new HashSet<>(outstandingTree.dirtyLocations);
		this.nPointers = 0;
		this.stashes = new HashMap<>(outstandingTree.stashes.size());
		this.outstandingVersions = new HashSet<>(outstandingTree.outstandingVersions.size());
	}

	public Map<Integer, EncryptedStash> getStashes() {
		return stashes;
	}

	public Set<Integer> getOutstandingVersions() {
		return outstandingVersions;
	}

	public void markDirtyLocation(Integer dirtyLocation) {
		dirtyLocations.add(dirtyLocation);
	}

	public Set<Integer> getDirtyLocations() {
		return dirtyLocations;
	}

	public int getNPointers() {
		return nPointers;
	}

	/**
	 * Increase the number of pointers
	 */
	public void increaseNPointers() {
		nPointers++;
	}

	/**
	 * Decrease the number of pointers
	 */
	public void decreaseNPointers() {
		nPointers--;
	}

	public ArrayList<BucketSnapshot> getLocation(int pathLocation) {
		return tree[pathLocation].getOutstandingBucketsVersions();
	}

	public void updateLocation(Integer location, ArrayList<BucketSnapshot> outstandingLocation) {
		tree[location].update(outstandingLocation);
	}

	public void updateLocation(Integer location, BucketSnapshot newBucketSnapshot,
							   Set<BucketSnapshot> outstandingBuckets) {
		tree[location].addSnapshot(newBucketSnapshot, outstandingBuckets);
	}

	public void clearDirtyLocations() {
		dirtyLocations.clear();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Tree:\n");
		//Write the size of each linked list in the tree
		for (int level = 0; level < nLevels; level++) {
			int nNodesPerLevel = (1 << level);
			sb.append(level);
			sb.append(": ");
			for (int node = 0; node < nNodesPerLevel; node++) {
				sb.append(getLocation(nNodesPerLevel - 1 + node).size());
				sb.append(" ");
			}
			sb.append('\n');
		}
		return sb.toString();
	}
}
