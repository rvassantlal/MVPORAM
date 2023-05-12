package clientStructure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class ClientPositionMap implements Externalizable {
	// This array maps a key to a pathId.
	// The key is the position in the array and the pathId is the byte stored in that position.
	// 256 paths, which means there are 511 nodes in total and the max storage is 511*Bucket.MAX_SIZE.
	private byte[] positionMap;

	public ClientPositionMap(int size) {
		this.positionMap = new byte[size];
	}

	public int getPosition(int key) {
		return positionMap[key];
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		//TODO: this
		/*out.writeInt(positionMap.size());
		for (Map.Entry<Integer, Integer> entry : positionMap.entrySet()) {
			out.writeInt(entry.getKey());
			out.writeInt(entry.getValue());
		}*/
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		//TODO: this
		/*int nItems = positionMap.size();
		while (nItems-- > 0) {
			int key = in.readInt();
			int value = in.readInt();
			positionMap.put(key, value);
		}*/
	}
}
