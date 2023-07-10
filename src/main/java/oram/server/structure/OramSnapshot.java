package oram.server.structure;


import java.io.Serializable;
import java.util.*;


public class OramSnapshot implements Serializable, Comparable {
	private final Double versionId;
	private final List<OramSnapshot> previous;
	private final EncryptedPositionMap positionMap;
	private final EncryptedStash stash;
	private final TreeMap<Integer, EncryptedBucket> difTree;

	public OramSnapshot(double versionId, OramSnapshot[] previousTrees, EncryptedPositionMap encryptedPositionMap,
						EncryptedStash encryptedStash) {
		this.versionId = versionId;
		this.difTree = new TreeMap<>();
		positionMap = encryptedPositionMap;
		stash = encryptedStash;
		previous = new LinkedList<>();
		Collections.addAll(previous, previousTrees);
	}

	public void garbageCollect(BitSet locationsMarker, int tree_size, HashSet<Double> visitedVersions) {
		if (!visitedVersions.contains(versionId)) {
			visitedVersions.add(versionId);
			for (Map.Entry<Integer, EncryptedBucket> location : difTree.entrySet()) {
				if (!locationsMarker.get(location.getKey())) {
					locationsMarker.set(location.getKey(), true);
					location.getValue().taintBucket();
				} else {
					location.getValue().untaintBucket();
				}
			}
			if (locationsMarker.previousClearBit(tree_size - 1) != -1) {
				for (OramSnapshot oramSnapshot : previous) {
					oramSnapshot.garbageCollect((BitSet) locationsMarker.clone(), tree_size, visitedVersions);
				}
			}
		}
	}

	public EncryptedPositionMap getPositionMap() {
		return positionMap;
	}

	public List<OramSnapshot> getPrevious() {
		return previous;
	}

	public EncryptedStash getStash() {
		return stash;
	}

	public EncryptedBucket getFromLocation(Integer location) {
		if (difTree.containsKey(location))
			return difTree.get(location);
		return null;
	}

	public void setToLocation(int location, EncryptedBucket encryptedBucket) {
		difTree.put(location, encryptedBucket);
	}

	public Double getVersionId() {
		return versionId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		OramSnapshot that = (OramSnapshot) o;
		return Double.compare(that.versionId, versionId) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(versionId);
	}

	@Override
	public int compareTo(Object o) {
		if (o instanceof OramSnapshot) {
			return Double.compare(this.versionId, ((OramSnapshot) o).getVersionId());
		}
		throw new IllegalArgumentException();
	}

	public boolean removeNonTainted() {
		boolean allNonTainted = true;
		for (Map.Entry<Integer, EncryptedBucket> entry : difTree.entrySet()) {
			if (entry.getValue().isTainted()) {
				allNonTainted = false;
				break;
			}
		}
		return allNonTainted;
	}

	public void addPrevious(List<OramSnapshot> previousFromPrevious) {
		synchronized (previous) {
			for (OramSnapshot fromPrevious : previousFromPrevious) {
				if (!previous.contains(fromPrevious))
					previous.add(fromPrevious);
			}
		}
	}

	public void removePrevious(List<OramSnapshot> previousVersion) {
		synchronized (previous) {
			previous.removeAll(previousVersion);
		}
	}
}