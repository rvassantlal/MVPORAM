package oram.server.structure;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.HashMap;
import java.util.Map;

public class EncryptedPositionMaps implements RawCustomExternalizable {
	private int newVersionId;
	private Map<Integer, EncryptedPathMap> encryptedPathMaps;

	public EncryptedPositionMaps(){}

	public EncryptedPositionMaps(int newVersionId, Map<Integer, EncryptedPathMap> encryptedPathMaps) {
		this.newVersionId = newVersionId;
		this.encryptedPathMaps = encryptedPathMaps;
	}

	public Map<Integer, EncryptedPathMap> getEncryptedPathMaps() {
		return encryptedPathMaps;
	}

	public int getNewVersionId() {
		return newVersionId;
	}

	public int getSerializedSize() {
		int size = Integer.BYTES * 2;
		for (Map.Entry<Integer, EncryptedPathMap> entry : encryptedPathMaps.entrySet()) {
			size += Integer.BYTES + entry.getValue().getSerializedSize();
		}
		return size;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;

		//Serialize new version id
		ORAMUtils.serializeInteger(newVersionId, output, offset);
		offset += Integer.BYTES;

		//Serialize encrypted position maps. Entries are serialized in increasing order of their keys
		ORAMUtils.serializeInteger(encryptedPathMaps.size(), output, offset);
		offset += Integer.BYTES;

		int[] keys = ORAMUtils.convertSetIntoOrderedArray(encryptedPathMaps.keySet());
		for (int key : keys) {
			ORAMUtils.serializeInteger(key, output, offset);
			offset += Integer.BYTES;

			offset = encryptedPathMaps.get(key).writeExternal(output, offset);
		}

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = startOffset;

		//Deserialize new version id
		newVersionId = ORAMUtils.deserializeInteger(input, offset);
		offset += Integer.BYTES;

		//Deserialize encrypted position maps
		int encryptedPositionMapsSize = ORAMUtils.deserializeInteger(input, offset);
		offset += Integer.BYTES;

		encryptedPathMaps = new HashMap<>(encryptedPositionMapsSize);

		for (int i = 0; i < encryptedPositionMapsSize; i++) {
			int key = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;

			EncryptedPathMap encryptedPathMap = new EncryptedPathMap();
			offset = encryptedPathMap.readExternal(input, offset);
			encryptedPathMaps.put(key, encryptedPathMap);
		}

		return offset;
	}
}
