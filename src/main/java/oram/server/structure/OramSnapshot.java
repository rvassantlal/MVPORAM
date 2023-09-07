package oram.server.structure;


import org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;
import java.util.*;


public class OramSnapshot implements Serializable, Comparable {
	private final Integer versionId;
	private final List<OramSnapshot> previous;
	private EncryptedStash stash;
	private final TreeMap<Integer, EncryptedBucket> difTree;
	private boolean currentlyOutstanding;

	public OramSnapshot(int versionId, OramSnapshot[] previousTrees, EncryptedStash encryptedStash) {
		this.versionId = versionId;
		this.difTree = new TreeMap<>();
		stash = encryptedStash;
		previous = new LinkedList<>();
		Collections.addAll(previous, previousTrees);
	}

	public void garbageCollect(BitSet locationsMarker, int tree_size, HashSet<Integer> visitedVersions,
							   boolean isOutstanding) {
		if(isOutstanding){
			currentlyOutstanding = true;
		}
		if (!visitedVersions.contains(versionId)) {
			visitedVersions.add(versionId);
			for (Map.Entry<Integer, EncryptedBucket> location : difTree.entrySet()) {
				if (!locationsMarker.get(location.getKey())) {
					locationsMarker.set(location.getKey(), true);
					location.getValue().taintBucket();
				}
			}
			if (locationsMarker.previousClearBit(tree_size - 1) != -1) {
				for (OramSnapshot oramSnapshot : previous) {
					oramSnapshot.garbageCollect(SerializationUtils.clone(locationsMarker), tree_size, visitedVersions, false);
				}
			}
		}
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

	public int getVersionId() {
		return versionId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		OramSnapshot that = (OramSnapshot) o;
		return (int) that.versionId == versionId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(versionId);
	}

	@Override
	public int compareTo(Object o) {
		if (o instanceof OramSnapshot) {
			return Integer.compare(this.versionId, ((OramSnapshot) o).getVersionId());
		}
		throw new IllegalArgumentException();
	}

	public boolean removeNonTainted() {
		boolean allNonTainted = true;
		for (EncryptedBucket value : difTree.values()) {
			if (value.isTainted()) {
				allNonTainted = false;
				value.untaintBucket();
			}
		}
		if (!currentlyOutstanding)
			stash = new EncryptedStash();
		currentlyOutstanding = false;
		return allNonTainted;
	}

	public void addPrevious(List<OramSnapshot> previousFromPrevious) {
		for (OramSnapshot fromPrevious : previousFromPrevious) {
			if (!previous.contains(fromPrevious))
				previous.add(fromPrevious);
		}
	}

	public void removePrevious(List<OramSnapshot> previousVersion) {
		previous.removeAll(previousVersion);
	}
}