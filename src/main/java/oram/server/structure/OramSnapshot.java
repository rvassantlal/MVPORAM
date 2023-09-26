package oram.server.structure;


import java.io.Serializable;
import java.util.*;


public class OramSnapshot implements Serializable {
	private final Integer versionId;
	private final Set<OramSnapshot> previous;// older versions
	private final Set<OramSnapshot> childSnapshots; // newer versions
	private EncryptedStash stash;
	private final HashMap<Integer, EncryptedBucket> difTree;

	public OramSnapshot(int versionId, OramSnapshot[] previousTrees,
						EncryptedStash encryptedStash) {
		this.versionId = versionId;
		this.difTree = new HashMap<>();
		this.stash = encryptedStash;
		this.previous = new LinkedHashSet<>();
		this.childSnapshots = new HashSet<>();
		Collections.addAll(previous, previousTrees);
	}

	public HashMap<Integer, EncryptedBucket> getDifTree() {
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
		return difTree.get(location);
	}

	public void setToLocation(int location, EncryptedBucket encryptedBucket) {
		difTree.put(location, encryptedBucket);
	}

	public Integer getVersionId() {
		return versionId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		OramSnapshot that = (OramSnapshot) o;
		return that.versionId.equals(versionId);
	}

	@Override
	public int hashCode() {
		return versionId;
	}

	public void addPrevious(OramSnapshot previousVersion) {
		this.previous.add(previousVersion);
	}
	public void addPrevious(Set<OramSnapshot> previous) {
		this.previous.addAll(previous);
	}

	public void removePrevious(List<OramSnapshot> previousVersion) {
		previousVersion.forEach(previous::remove);
	}

	public void removePrevious(OramSnapshot previousVersion) {
		previous.remove(previousVersion);
	}

	public void removeStash() {
		stash.clearStash();
	}
}