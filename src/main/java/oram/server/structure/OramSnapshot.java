package oram.server.structure;


import oram.utils.ORAMUtils;

import java.io.Serializable;
import java.util.*;


public class OramSnapshot implements Serializable,Comparable {
	private final double versionId;
	private final int TREE_SIZE;
	private final int TREE_HEIGHT;
	private final OramSnapshot[] previous;
	private final EncryptedPositionMap positionMap;
	private final EncryptedStash stash;

	private int pathId;
	private int referenceCounter;
	private final TreeMap<Integer, EncryptedBucket> difTree;

	public OramSnapshot(double versionId, int treeSize, int treeHeight, OramSnapshot[] previousTrees,
						EncryptedPositionMap encryptedPositionMap, EncryptedStash encryptedStash, int pathId) {
		this.versionId = versionId;
		this.pathId = pathId;
		this.difTree = new TreeMap<>();

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
		if (difTree.containsKey(location))
			return difTree.get(location);
		return null;
	}

	public void setToLocation(int location, EncryptedBucket encryptedBucket) {
		difTree.put(location, encryptedBucket);
	}

	public void removePath(List<Integer> pathIDs, Set<OramSnapshot> taintedSnapshots) {
		if (!taintedSnapshots.contains(this)) {
			for (Integer pathID : pathIDs) {
				int[] locations = ORAMUtils.computePathLocations(pathID.byteValue(), TREE_HEIGHT);
				for (int location : locations) {
					difTree.remove(location);
				}
			}
			pathIDs.add(this.pathId);
			for (OramSnapshot oramSnapshot : previous) {
				oramSnapshot.removePath(pathIDs, taintedSnapshots);
			}
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

	public Set<OramSnapshot> getPaths() {
		Set<OramSnapshot> paths = new TreeSet<>();
		paths.add(this);
		for (OramSnapshot oramSnapshot : previous) {
			paths.addAll(oramSnapshot.getPaths());
		}
		return paths;
	}

	@Override
	public int compareTo(Object o) {
		if (o instanceof OramSnapshot){
			return Double.compare(this.versionId,((OramSnapshot) o).getVersionId());
		}
		throw new IllegalArgumentException();
	}
}
