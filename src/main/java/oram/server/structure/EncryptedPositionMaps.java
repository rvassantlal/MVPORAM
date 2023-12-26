package oram.server.structure;

import oram.utils.CustomExternalizable;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class EncryptedPositionMaps implements CustomExternalizable {

	private int newVersionId;
	private Map<Integer, EncryptedPositionMap> encryptedPositionMaps;

	public EncryptedPositionMaps(){}

	public EncryptedPositionMaps(int newVersionId, Map<Integer, EncryptedPositionMap> encryptedPositionMaps) {
		this.newVersionId = newVersionId;
		this.encryptedPositionMaps = encryptedPositionMaps;
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		out.writeInt(newVersionId);
		int[] keys = new int[encryptedPositionMaps.size()];
		int k = 0;
		for (Integer i : encryptedPositionMaps.keySet()) {
			keys[k++] = i;
		}
		Arrays.sort(keys);

		out.writeInt(encryptedPositionMaps.size());
		for (int key : keys) {
			out.writeInt(key);
			encryptedPositionMaps.get(key).writeExternal(out);
		}
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		newVersionId = in.readInt();
		int size = in.readInt();
		encryptedPositionMaps = new HashMap<>(size);
		while (size-- > 0) {
			int key = in.readInt();
			EncryptedPositionMap pm = new EncryptedPositionMap();
			pm.readExternal(in);
			encryptedPositionMaps.put(key, pm);
		}
	}

	public Map<Integer, EncryptedPositionMap> getEncryptedPositionMaps() {
		return encryptedPositionMaps;
	}

	public int getNewVersionId() {
		return newVersionId;
	}

	public int getSerializedSize() {
		int size = 8;
		for (Map.Entry<Integer, EncryptedPositionMap> entry : encryptedPositionMaps.entrySet()) {
			size += 4 + entry.getValue().getSerializedSize();
		}
		return size;
	}
}
