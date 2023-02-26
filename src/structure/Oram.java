package structure;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.lang3.SerializationUtils;


public class Oram implements Serializable{
	private static final long serialVersionUID = -4459879580826094264L;
	private static Integer TREE_SIZE;
	private static Integer TREE_LEVELS;
	private static int OPERATION_THRESHOLD = 60*1000; // 1 minute
	private static int SESSION_THRESHOLD = 5*60*1000; //5 minutes
	private Boolean activeSession=false;
	private long sessionOpeningTimestamp;
	private Integer sessionOpenedBy; //userID
	private long lastOperationTimestamp;
	private byte[] positionMap = SerializationUtils.serialize(new TreeMap<Short,Integer>());
	private byte[] stash = SerializationUtils.serialize(new TreeMap<Short,Short>());
	private TreeMap<Integer,byte[]> tree= new TreeMap<>();

	public Oram(int size) {
		TREE_SIZE=size;
		TREE_LEVELS = (int)(Math.log(TREE_SIZE+1) / Math.log(2));
		for (int i = 0; i <TREE_SIZE; i++) {
			tree.put(i, null);
		}
	}
	public byte[] getPositionMap() {
		return positionMap;
	}

	public byte[] getStash() {
		return stash;
	}

	public List<byte[]> getData(Integer pathID) {
		ArrayList<byte[]> list = new ArrayList<byte[]>();
		int location = TREE_SIZE/2+pathID;
		for (int i = TREE_LEVELS-1; i >= 0; i--) {
			list.add(tree.get(location));
			location=location%2==0?location-2:location-1;
			location/=2;
		}
		return list;
	}
	public List<byte[]> getTree() {
		return new ArrayList<byte[]>(tree.values());
	}
	public void doEviction(byte[] newPositionMap,byte[] newStash,Integer pathID,TreeMap<Integer,byte[]> newPath) {
		positionMap=newPositionMap;
		stash=newStash;
		put(pathID, newPath);
	}

	private void put(Integer pathID, TreeMap<Integer,byte[]> newPath) {
		int location = TREE_SIZE/2+pathID;
		for (int i = TREE_LEVELS-1; i >= 0; i--) {
			tree.put(location, newPath.get(i));
			location=location%2==0?location-2:location-1;
			location/=2;
		}
	}

	public boolean openSession(Integer userId,long msgTimestamp) {
		if (activeSession==false || msgTimestamp-lastOperationTimestamp>OPERATION_THRESHOLD 
				|| msgTimestamp-sessionOpeningTimestamp>SESSION_THRESHOLD) { 
			activeSession=true;
			sessionOpeningTimestamp=msgTimestamp;
			sessionOpenedBy=userId;
			return true;
		}
		return false;
	}
	public boolean authorizeOperation(Integer userId,long msgTimestamp) {
		if (activeSession==true && sessionOpenedBy.equals(userId)) { 
			lastOperationTimestamp=msgTimestamp;
			return true;
		}
		return false;
	}
}
