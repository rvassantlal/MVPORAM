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

		for (OramSnapshot oramSnapshot : previous) {
			boolean[] newLocationsMarker = Arrays.copyOf(locationsMarker, locationsMarker.length);
			oramSnapshot.garbageCollect(newLocationsMarker);
			if (oramSnapshot.previous.length == 0 && oramSnapshot.difTree.isEmpty()) {
				//TODO work here
			}
		}
	}

	public void garbageCollect(Set<Integer> existingLocations, Set<Double> visitedVersions) {
		if (referenceCounter > 0) {
			return;
		}
		System.out.println("Garbage collecting version " + versionId + " | difSize: " + difTree.size());
		List<Integer> locations = new ArrayList<>(difTree.keySet());
		visitedVersions.add(versionId);
		for (int location : locations) {
			if (existingLocations.contains(location)) {
				difTree.remove(location);
			} else {
				existingLocations.add(location);
			}
		}
		for (OramSnapshot oramSnapshot : previous) {
			if (!visitedVersions.contains(oramSnapshot.getVersionId())) {
				oramSnapshot.garbageCollect(existingLocations, visitedVersions);
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