package oram.server.structure;


import java.io.Serializable;
import java.util.*;


public class OramSnapshot implements Serializable {
	private final Integer versionId;
	private final Set<OramSnapshot> previous;
	private final EncryptedStash stash;
	private final TreeMap<Integer, EncryptedBucket> difTree;

	public OramSnapshot(int versionId, OramSnapshot[] previousTrees,
						EncryptedStash encryptedStash) {
		this.versionId = versionId;
		this.difTree = new TreeMap<>();
		this.stash = encryptedStash;
		this.previous = new HashSet<>();
		Collections.addAll(previous, previousTrees);
	}

	public TreeMap<Integer, EncryptedBucket> getDifTree() {
		return difTree;
	}

	public Set<OramSnapshot> getPrevious() {
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
		return versionId;
	}

	public void addPrevious(List<OramSnapshot> previousFromPrevious) {
		previous.addAll(previousFromPrevious);
	}

	public void removePrevious(List<OramSnapshot> previousVersion) {
		previousVersion.forEach(previous::remove);
	}
}