package oram.server.structure;


import java.io.Serializable;
import java.util.*;


public class OramSnapshot implements Serializable {
	private final double versionId;
	private final List<OramSnapshot> previous;
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
		previous = new ArrayList<>(previousTrees.length);
		previous.addAll(Arrays.asList(previousTrees));
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

	public void garbageCollect(boolean[] locationsMarker) {
		if (referenceCounter > 0) {
			return;
		}
		System.out.println("Garbage collecting version " + versionId + " | difSize: " + difTree.size());

		List<Integer> locations = new ArrayList<>(difTree.keySet());
		for (int location : locations) {
			if (locationsMarker[location]) {
				difTree.remove(location);
			} else {
				locationsMarker[location] = true;
			}
		}

		List<OramSnapshot> previousToRemove = new ArrayList<>(previous.size());
		for (OramSnapshot oramSnapshot : previous) {
			boolean[] newLocationsMarker = Arrays.copyOf(locationsMarker, locationsMarker.length);
			oramSnapshot.garbageCollect(newLocationsMarker);
			if (oramSnapshot.previous.isEmpty() && oramSnapshot.difTree.isEmpty()) {
				previousToRemove.add(oramSnapshot);
			}
		}

		previous.removeAll(previousToRemove);
	}

	public EncryptedPositionMap getPositionMap(){
		return positionMap;
	}

	public List<OramSnapshot> getPrevious(){
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
}