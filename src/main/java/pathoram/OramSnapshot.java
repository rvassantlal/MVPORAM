package pathoram;


import oram.ORAMUtils;
import oram.server.structure.EncryptedBucket;
import oram.server.structure.EncryptedPath;
import oram.server.structure.EncryptedPositionMap;
import oram.server.structure.EncryptedStash;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;



public class OramSnapshot implements Serializable {
	private final double versionId;
	private final int TREE_SIZE;
	private final int TREE_HEIGHT;
	private  List<OramSnapshot> previous;
	private EncryptedPositionMap positionMap;
	private EncryptedStash stash;
	private int referenceCounter;
	private final TreeMap<Integer, EncryptedBucket> difTree = new TreeMap<>();

	public OramSnapshot(double versionId, int treeSize, int treeHeight, List<OramSnapshot> previousTrees,
						EncryptedPositionMap encryptedPositionMap, EncryptedStash encryptedStash) {
		this.versionId = versionId;
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

	public List<OramSnapshot> getPrevious(){
		return previous;
	}

	public Collection<EncryptedBucket> getDifTree() {
		return difTree.values();
	}
	public EncryptedStash getStash() {
		return stash;
	}

	public EncryptedBucket getFromLocation(int location) {
		return difTree.get(location);
	}

	public EncryptedPath getPath(byte pathId) {
		int[] pathLocations = ORAMUtils.computePathLocations(pathId, TREE_HEIGHT);

		return null;
	}

	public void putPath(Integer pathID, List<EncryptedBucket> newPath) {
		int location = TREE_SIZE/2+pathID;
		for (int i = TREE_HEIGHT -1; i >= 0; i--) {
			difTree.put(location, newPath.get(i));
			location=location%2==0?location-2:location-1;
			location/=2;
		}
		referenceCounter--;
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

	public boolean isEmpty() {
		return difTree.isEmpty();
	}
}
