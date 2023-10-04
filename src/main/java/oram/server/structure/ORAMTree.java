package oram.server.structure;

import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;

import java.util.*;

public class ORAMTree {
	private final ArrayList<LinkedList<OramSnapshot>> tree;
	private final ORAMContext oramContext;
	private final Map<Integer, int[]> preComputedPathLocations;
	private final Set<Integer> outstandingBucketsVersions;

	public ORAMTree(ORAMContext oramContext) {
		this.oramContext = oramContext;
		int treeSize = ORAMUtils.computeNumberOfNodes(oramContext.getTreeHeight());
		this.tree = new ArrayList<>(treeSize);
		for (int i = 0; i < treeSize; i++) {
			tree.add(new LinkedList<>());
		}
		int numberOfPaths = 1 << oramContext.getTreeHeight();
		this.preComputedPathLocations = new HashMap<>(numberOfPaths);
		for (int i = 0; i < numberOfPaths; i++) {
			preComputedPathLocations.put(i, ORAMUtils.computePathLocations(i, oramContext.getTreeHeight()));
		}
		this.outstandingBucketsVersions = new HashSet<>();
	}

	public void storeSnapshot(int pathId, OramSnapshot snapshot) {
		int[] pathLocations = preComputedPathLocations.get(pathId);
		for (int pathLocation : pathLocations) {
			tree.get(pathLocation).addFirst(snapshot);
		}
	}

	public LinkedList<EncryptedBucket> getFromRoot(Set<Integer> versionIds) {
		LinkedList<OramSnapshot> snapshots = tree.get(0);
		LinkedList<EncryptedBucket> result = new LinkedList<>();
		for (OramSnapshot snapshot : snapshots) {
			//System.out.println("Getting bucket from location " + pathLocation + " with version " + snapshot.getVersionId() + " and requested version " + versionId);
			if (versionIds.contains(snapshot.getVersionId())) {
				result.add(snapshot.getFromLocation(0));
			}
		}
		return result;
	}

	public LinkedList<EncryptedBucket> getFromLocation(int location, int versionId) {
		LinkedList<OramSnapshot> snapshots = tree.get(location);
		LinkedList<EncryptedBucket> result = new LinkedList<>();
		for (OramSnapshot snapshot : snapshots) {
			//System.out.println("Getting bucket from location " + pathLocation + " with version " + snapshot.getVersionId() + " and requested version " + versionId);
			if (snapshot.getVersionId() <= versionId) {
				result.add(snapshot.getFromLocation(location));
			}
		}
		return result;
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

	public void garbageCollect(int minOutstandingVersionId) {
		for (LinkedList<OramSnapshot> oramSnapshots : tree) {
			Iterator<OramSnapshot> iterator = oramSnapshots.iterator();
			while (iterator.hasNext()) {
				OramSnapshot oramSnapshot = iterator.next();
				if (oramSnapshot.getVersionId() < minOutstandingVersionId) {
					if (iterator.hasNext())
						iterator.next();
					break;
				}
			}
			while (iterator.hasNext()) {
				iterator.next();
				iterator.remove();
			}
		}
	}

	public void garbageCollect(Set<Integer> taintedVersions) {
		for (LinkedList<OramSnapshot> oramSnapshots : tree) {
			oramSnapshots.removeIf(oramSnapshot -> !taintedVersions.contains(oramSnapshot.getVersionId()));
		}
	}
}
