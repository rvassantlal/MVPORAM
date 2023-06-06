package oram.server.structure;


import java.io.Serializable;
import java.util.Objects;
import java.util.TreeMap;



public class OramSnapshot implements Serializable {
	private final double versionId;
	private final int TREE_SIZE;
	private final int TREE_HEIGHT;
	private final OramSnapshot[] previous;
	private final EncryptedPositionMap positionMap;
	private final EncryptedStash stash;
	private int referenceCounter;
	private final TreeMap<Integer, EncryptedBucket> difTree;

	public OramSnapshot(double versionId, int treeSize, int treeHeight, OramSnapshot[] previousTrees,
						EncryptedPositionMap encryptedPositionMap, EncryptedStash encryptedStash) {
		this.versionId = versionId;
		this.difTree = new TreeMap<>();
		for (int i = 0; i < treeSize; i++) {
			difTree.put(i, null);
		}
		positionMap = encryptedPositionMap;
		stash = encryptedStash;
		TREE_SIZE = treeSize;
		TREE_HEIGHT = treeHeight;
		previous = previousTrees;
	}

	public int getReferenceCounter(){
		return referenceCounter;
	}

	public void incrementReferenceCounter() {
		referenceCounter++;
	}

	public void decrementReferenceCounter() {
		referenceCounter--;
	}

	public EncryptedPositionMap getPositionMap(){
		return positionMap;
	}

	public OramSnapshot[] getPrevious(){
		return previous;
	}

	public EncryptedStash getStash() {
		return stash;
	}

	public EncryptedBucket getFromLocation(int location) {
		return difTree.get(location);
	}

	public void setToLocation(int location, EncryptedBucket encryptedBucket) {
		difTree.put(location, encryptedBucket);
	}

	public void removePath(Integer pathID) {
		int location = TREE_SIZE/2+pathID;
		for (int i = TREE_HEIGHT -1; i >= 0; i--) {
			difTree.remove(location);
			location=location%2==0?location-2:location-1;
			location/=2;
		}
	}

	public double getVersionId() {
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
}
