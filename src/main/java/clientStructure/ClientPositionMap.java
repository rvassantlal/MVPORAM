package clientStructure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ClientPositionMap implements Externalizable {
	// This array maps a key to a pathId.
	// The key is the position in the array and the pathId is the byte stored in that position.
	// 256 paths, which means there are 511 nodes in total and the max storage is 511*Bucket.MAX_SIZE.
	// private byte[] positionMap;
	private TreeMap<Integer,Integer> positionMap;
	private Integer tree_size;

	public ClientPositionMap() {
		positionMap= new TreeMap<>();
	}
	public ClientPositionMap(int size) {
		positionMap= new TreeMap<>();
		tree_size=size;
	}

	public int getPosition(int key) {
		return positionMap.get(key)==null?0:positionMap.get(key);
	}

	public void putInPosition(int key, int value) {
		boolean validPosition = value<256 && key<511*Bucket.MAX_SIZE;
		if(validPosition)
			positionMap.put(key,value);
	}

	@Override
	public void writeExternal(ObjectOutput objectOutput) throws IOException {
		for (int i = 0; i < tree_size*Bucket.MAX_SIZE; i++) {
			Integer pathId = positionMap.get(i);
			if(pathId==null)
				objectOutput.writeByte(0);
			else{
				objectOutput.writeByte(pathId);
			}
		}
	}

	@Override
	public void readExternal(ObjectInput objectInput) throws IOException {
		for (int key = 0; key < tree_size; key++) {
			int value = objectInput.readByte();
			positionMap.put(key, value);
		}
	}

	public Set<Map.Entry<Integer, Integer>> entrySet() {
		return positionMap.entrySet();
	}

	public void putAll(ClientPositionMap map) {
		positionMap.putAll(map.positionMap);
	}


}
