package oram.server.structure;


import java.io.Serializable;
import java.util.*;


public class OramSnapshot implements Serializable {
	private final Integer versionId;
	private final Set<OramSnapshot> previous;// older versions
	private final Set<OramSnapshot> childSnapshots; // newer versions
	private final EncryptedStash stash;
	private final TreeMap<Integer, EncryptedBucket> difTree;

	public OramSnapshot(int versionId, OramSnapshot[] previousTrees,
						EncryptedStash encryptedStash) {
		this.versionId = versionId;
		this.difTree = new TreeMap<>();
		this.stash = encryptedStash;
		this.previous = new HashSet<>();
		this.childSnapshots = new HashSet<>();
		Collections.addAll(previous, previousTrees);
	}

	public TreeMap<Integer, EncryptedBucket> getDifTree() {
		return difTree;
	}

	public void addChild(OramSnapshot child) {
		childSnapshots.add(child);
	}

	public Set<OramSnapshot> getPrevious() {
		return previous;
	}

	public Set<OramSnapshot> getChildSnapshots() {
		return childSnapshots;
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

	public void addPrevious(OramSnapshot previousVersion) {
		this.previous.add(previousVersion);
	}

	public void removePrevious(List<OramSnapshot> previousVersion) {
		previousVersion.forEach(previous::remove);
	}

	public void removePrevious(OramSnapshot previousVersion) {
		previous.remove(previousVersion);
	}
}