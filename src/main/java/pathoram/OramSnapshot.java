package pathoram;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;



public class OramSnapshot implements Serializable{
	private static final long serialVersionUID = -4459879580826094264L;
	private Double id;
	private static Integer TREE_SIZE;
	private static Integer TREE_LEVELS;
	private  List<OramSnapshot> prev;
	private byte[] positionMap;
	private byte[] stash;
	private int ref_counter;
	// LOCATION, VERSION, CONTENT
	private TreeMap<Integer,byte[]> tree= new TreeMap<>();

	public OramSnapshot(int size, List<OramSnapshot> previousTrees, Double id) {
		this.id = id;
		for (int i = 0; i < size; i++) {
			tree.put(i, null);
		}
		positionMap = new byte[]{};
		stash = new byte[]{};
		TREE_SIZE = tree.size();
		TREE_LEVELS = (int)(Math.log(TREE_SIZE+1) / Math.log(2));
		this.prev=previousTrees;
		ref_counter=0;
	}

	public byte[] getPositionMap(){
		ref_counter++;
		return positionMap;
	}

	public int getRefCounter(){
		return ref_counter;
	}

	public List<OramSnapshot> getPrev(){
		return prev;
	}

	public List<byte[]> getTree() {
		ArrayList<byte[]> result = new ArrayList<>();
		for (int treeKey:tree.keySet()) {
			result.add(tree.get(treeKey));
		}
		return result;
	}
	public byte[] getStash() {
		return stash;
	}

	public byte[] getFromLocation(int location) {
		return tree.get(location);
	}

	public void setPositionMap(byte[] newPositionMap) {
		this.positionMap = newPositionMap;
	}

	public void setStash(byte[] newStash) {
		this.stash = newStash;
	}

	public void putPath(Integer pathID, List<byte[]> newPath) {
		int location = TREE_SIZE/2+pathID;
		for (int i = TREE_LEVELS-1; i >= 0; i--) {
			tree.put(location, newPath.get(i));
			location=location%2==0?location-2:location-1;
			location/=2;
		}
		ref_counter--;
	}

	public void removePath(Integer pathID) {
		int location = TREE_SIZE/2+pathID;
		for (int i = TREE_LEVELS-1; i >= 0; i--) {
			tree.remove(location);
			location=location%2==0?location-2:location-1;
			location/=2;
		}
	}

	public Double getId() {
		return id;
	}
	
	public boolean isEmpty() {
		return tree.isEmpty();
	}

}
