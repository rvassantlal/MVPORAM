package oram.server.structure;


import java.io.Serializable;
import java.util.*;


public class OramSnapshot implements Serializable, Comparable {
	private final double versionId;
	private final OramSnapshot[] previous;
	private final EncryptedPositionMap positionMap;
	private final EncryptedStash stash;
	private int referenceCounter;
	private final TreeMap<Integer, EncryptedBucket> difTree;

	public OramSnapshot(double versionId, OramSnapshot[] previousTrees, EncryptedPositionMap encryptedPositionMap,
						EncryptedStash encryptedStash) {
		this.versionId = versionId;
		this.difTree = new TreeMap<>();

		positionMap = encryptedPositionMap;
		stash = encryptedStash;
		previous = previousTrees;
	}

	public void incrementReferenceCounter() {
		referenceCounter++;
		for (OramSnapshot oramSnapshot : previous) {
			oramSnapshot.incrementReferenceCounter();
		}
	}

	public void decrementReferenceCounter() {
		referenceCounter--;
		for (OramSnapshot oramSnapshot : previous) {
			oramSnapshot.decrementReferenceCounter();
		}
	}

	public int getReferenceCounter() {
		return referenceCounter;
	}

	public void garbageCollect(Set<Integer> existingLocations) {
		System.out.println("Garbage collecting version " + versionId);
		List<Integer> locations = new ArrayList<>(difTree.keySet());
		for (int location : locations) {
			if (existingLocations.contains(location)) {
				difTree.remove(location);
			} else {
				existingLocations.add(location);
			}
		}
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
		if (difTree.containsKey(location))
			return difTree.get(location);
		return null;
	}

	public void setToLocation(int location, EncryptedBucket encryptedBucket) {
		difTree.put(location, encryptedBucket);
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

	@Override
	public int compareTo(Object o) {
		if (o instanceof OramSnapshot){
			return Double.compare(this.versionId,((OramSnapshot) o).getVersionId());
		}
		throw new IllegalArgumentException();
	}
}