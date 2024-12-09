package oram.server.structure;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.HashMap;
import java.util.Map;

public class EncryptedPositionMaps implements RawCustomExternalizable {
	private int newVersionId;
	private Map<Integer, EncryptedPathMap> encryptedPathMaps;
	private int[] outstandingVersions;
	private Map<Integer, int[]> allOutstandingVersions;

	public EncryptedPositionMaps(){}

	public EncryptedPositionMaps(int newVersionId, Map<Integer, EncryptedPathMap> encryptedPathMaps,
								 int[] outstandingVersions, Map<Integer, int[]> allOutstandingVersions) {
		this.newVersionId = newVersionId;
		this.encryptedPathMaps = encryptedPathMaps;
		this.outstandingVersions = outstandingVersions;
		this.allOutstandingVersions = allOutstandingVersions;
	}

	public Map<Integer, EncryptedPathMap> getEncryptedPathMaps() {
		return encryptedPathMaps;
	}

	public Map<Integer, int[]> getAllOutstandingVersions() {
		return allOutstandingVersions;
	}

	public int getNewVersionId() {
		return newVersionId;
	}

	public int[] getOutstandingVersions() {
		return outstandingVersions;
	}

	public int getSerializedSize() {
		int size = Integer.BYTES * 2;
		for (Map.Entry<Integer, EncryptedPathMap> entry : encryptedPathMaps.entrySet()) {
			size += Integer.BYTES + entry.getValue().getSerializedSize();
		}

		size += Integer.BYTES;
		for (Map.Entry<Integer, int[]> entry : allOutstandingVersions.entrySet()) {
			size += Integer.BYTES * (2 + entry.getValue().length);
		}

		size += Integer.BYTES + outstandingVersions.length * Integer.BYTES;
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

		//Serializing all outstanding versions
		ORAMUtils.serializeInteger(allOutstandingVersions.size(), output, offset);
		offset += Integer.BYTES;

		keys = ORAMUtils.convertSetIntoOrderedArray(allOutstandingVersions.keySet());
		for (int key : keys) {
			ORAMUtils.serializeInteger(key, output, offset);
			offset += Integer.BYTES;

			int[] value = allOutstandingVersions.get(key);
			ORAMUtils.serializeInteger(value.length, output, offset);
			offset += Integer.BYTES;

			for (int v : value) {
				ORAMUtils.serializeInteger(v, output, offset);
				offset += Integer.BYTES;
			}
		}

		//Serialize outstanding versions
		ORAMUtils.serializeInteger(outstandingVersions.length, output, offset);
		offset += Integer.BYTES;

		for (int outstandingVersion : outstandingVersions) {
			ORAMUtils.serializeInteger(outstandingVersion, output, offset);
			offset += Integer.BYTES;
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

		//Deserialize all outstanding versions
		int allOutstandingVersionsSize = ORAMUtils.deserializeInteger(input, offset);
		offset += Integer.BYTES;
		allOutstandingVersions = new HashMap<>(allOutstandingVersionsSize);

		for (int i = 0; i < allOutstandingVersionsSize; i++) {
			int key = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;

			int valueSize = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;
			int[] value = new int[valueSize];
			for (int ii = 0; ii < valueSize; ii++) {
				value[ii] = ORAMUtils.deserializeInteger(input, offset);
				offset += Integer.BYTES;
			}
			allOutstandingVersions.put(key, value);
		}

		//Deserialize outstanding versions
		int outstandingVersionsSize = ORAMUtils.deserializeInteger(input, offset);
		offset += Integer.BYTES;

		outstandingVersions = new int[outstandingVersionsSize];

		for (int i = 0; i < outstandingVersionsSize; i++) {
			outstandingVersions[i] = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;
		}

		return offset;
	}
}
