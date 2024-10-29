package oram.server.structure;

import oram.client.structure.EvictionMap;
import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class EncryptedPositionMaps implements RawCustomExternalizable {

	private int newVersionId;
	private Map<Integer, EncryptedPositionMap> encryptedPositionMaps;
	private Map<Integer, EvictionMap> evictionMap;
	private int[] outstandingVersions;
	private Map<Integer, int[]> allOutstandingVersions;

	public EncryptedPositionMaps(){}

	public EncryptedPositionMaps(int newVersionId, Map<Integer, EncryptedPositionMap> encryptedPositionMaps,
								 Map<Integer, EvictionMap> evictionMap, int[] outstandingVersions,
								 Map<Integer, int[]> allOutstandingVersions) {
		this.newVersionId = newVersionId;
		this.encryptedPositionMaps = encryptedPositionMaps;
		this.evictionMap = evictionMap;
		this.outstandingVersions = outstandingVersions;
		this.allOutstandingVersions = allOutstandingVersions;
	}

	public Map<Integer, EncryptedPositionMap> getEncryptedPositionMaps() {
		return encryptedPositionMaps;
	}

	public Map<Integer, EvictionMap> getEvictionMap() {
		return evictionMap;
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
		int size = 8;
		for (Map.Entry<Integer, EncryptedPositionMap> entry : encryptedPositionMaps.entrySet()) {
			size += 4 + entry.getValue().getSerializedSize();
		}

		size += 4;
		for (Map.Entry<Integer, EvictionMap> entry : evictionMap.entrySet()) {
			size += 4 + entry.getValue().getSerializedSize();
		}

		size += 4;
		for (Map.Entry<Integer, int[]> entry : allOutstandingVersions.entrySet()) {
			size += 4 + 4 + entry.getValue().length * 4;
		}

		size += 4 + outstandingVersions.length * 4;
		return size;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;

		//Serialize new version id
		byte[] newVersionIdBytes = ORAMUtils.toBytes(newVersionId);
		System.arraycopy(newVersionIdBytes, 0, output, startOffset, 4);
		offset += 4;

		//Serialize encrypted position maps. Entries are serialized in increasing order of their keys
		byte[] encryptedPositionMapsSizeBytes = ORAMUtils.toBytes(encryptedPositionMaps.size());
		System.arraycopy(encryptedPositionMapsSizeBytes, 0, output, offset, 4);
		offset += 4;

		int[] keys = new int[encryptedPositionMaps.size()];
		int k = 0;
		for (Integer i : encryptedPositionMaps.keySet()) {
			keys[k++] = i;
		}
		Arrays.sort(keys);

		for (int key : keys) {
			byte[] keyBytes = ORAMUtils.toBytes(key);
			System.arraycopy(keyBytes, 0, output, offset, 4);
			offset += 4;
			offset = encryptedPositionMaps.get(key).writeExternal(output, offset);
		}

		//Serialize eviction map. Entries are serialized in increasing order of their keys
		byte[] evictionMapSizeBytes = ORAMUtils.toBytes(evictionMap.size());
		System.arraycopy(evictionMapSizeBytes, 0, output, offset, 4);
		offset += 4;

		keys = new int[evictionMap.size()];
		k = 0;
		for (Integer i : evictionMap.keySet()) {
			keys[k++] = i;
		}
		Arrays.sort(keys);

		for (int key : keys) {
			byte[] keyBytes = ORAMUtils.toBytes(key);
			System.arraycopy(keyBytes, 0, output, offset, 4);
			offset += 4;
			offset = evictionMap.get(key).writeExternal(output, offset);
		}

		//Serializing all outstanding versions
		byte[] allOutstandingVersionsSizeBytes = ORAMUtils.toBytes(allOutstandingVersions.size());
		System.arraycopy(allOutstandingVersionsSizeBytes, 0, output, offset, 4);
		offset += 4;

		keys = new int[allOutstandingVersions.size()];
		k = 0;
		for (Integer i : allOutstandingVersions.keySet()) {
			keys[k++] = i;
		}
		Arrays.sort(keys);
		for (int key : keys) {
			byte[] keyBytes = ORAMUtils.toBytes(key);
			System.arraycopy(keyBytes, 0, output, offset, 4);
			offset += 4;

			int[] value = allOutstandingVersions.get(key);
			byte[] valueSizeBytes = ORAMUtils.toBytes(value.length);
			System.arraycopy(valueSizeBytes, 0, output, offset, 4);
			offset += 4;

			for (int v : value) {
				byte[] vBytes = ORAMUtils.toBytes(v);
				System.arraycopy(vBytes, 0, output, offset, 4);
				offset += 4;
			}
		}

		//Serialize outstanding versions
		byte[] outstandingVersionsSizeBytes = ORAMUtils.toBytes(outstandingVersions.length);
		System.arraycopy(outstandingVersionsSizeBytes, 0, output, offset, 4);
		offset += 4;

		for (int outstandingVersion : outstandingVersions) {
			byte[] outstandingVersionBytes = ORAMUtils.toBytes(outstandingVersion);
			System.arraycopy(outstandingVersionBytes, 0, output, offset, 4);
			offset += 4;
		}
		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = startOffset;

		//Deserialize new version id
		byte[] newVersionIdBytes = new byte[4];
		System.arraycopy(input, offset, newVersionIdBytes, 0, 4);
		offset += 4;
		newVersionId = ORAMUtils.toNumber(newVersionIdBytes);

		//Deserialize encrypted position maps
		byte[] encryptedPositionMapsSizeBytes = new byte[4];
		System.arraycopy(input, offset, encryptedPositionMapsSizeBytes, 0, 4);
		offset += 4;
		int encryptedPositionMapsSize = ORAMUtils.toNumber(encryptedPositionMapsSizeBytes);
		encryptedPositionMaps = new HashMap<>(encryptedPositionMapsSize);

		for (int i = 0; i < encryptedPositionMapsSize; i++) {
			byte[] keyBytes = new byte[4];
			System.arraycopy(input, offset, keyBytes, 0, 4);
			offset += 4;
			int key = ORAMUtils.toNumber(keyBytes);

			EncryptedPositionMap encryptedPositionMap = new EncryptedPositionMap();
			offset = encryptedPositionMap.readExternal(input, offset);
			encryptedPositionMaps.put(key, encryptedPositionMap);
		}

		//Deserialize eviction map
		byte[] evictionMapSizeBytes = new byte[4];
		System.arraycopy(input, offset, evictionMapSizeBytes, 0, 4);
		offset += 4;
		int evictionMapSize = ORAMUtils.toNumber(evictionMapSizeBytes);
		evictionMap = new HashMap<>(evictionMapSize);

		for (int i = 0; i < evictionMapSize; i++) {
			byte[] keyBytes = new byte[4];
			System.arraycopy(input, offset, keyBytes, 0, 4);
			offset += 4;
			int key = ORAMUtils.toNumber(keyBytes);

			EvictionMap evictionMapEntry = new EvictionMap();
			offset = evictionMapEntry.readExternal(input, offset);
			evictionMap.put(key, evictionMapEntry);
		}

		//Deserialize all outstanding versions
		byte[] allOutstandingVersionsSizeBytes = new byte[4];
		System.arraycopy(input, offset, allOutstandingVersionsSizeBytes, 0, 4);
		offset += 4;
		int allOutstandingVersionsSize = ORAMUtils.toNumber(allOutstandingVersionsSizeBytes);
		allOutstandingVersions = new HashMap<>(allOutstandingVersionsSize);

		for (int i = 0; i < allOutstandingVersionsSize; i++) {
			byte[] keyBytes = new byte[4];
			System.arraycopy(input, offset, keyBytes, 0, 4);
			offset += 4;
			int key = ORAMUtils.toNumber(keyBytes);

			byte[] valueSizeBytes = new byte[4];
			System.arraycopy(input, offset, valueSizeBytes, 0, 4);
			offset += 4;
			int valueSize = ORAMUtils.toNumber(valueSizeBytes);
			int[] value = new int[valueSize];
			for (int ii = 0; ii < valueSize; ii++) {
				byte[] vBytes = new byte[4];
				System.arraycopy(input, offset, vBytes, 0, 4);
				offset += 4;
				value[ii] = ORAMUtils.toNumber(vBytes);
			}
			allOutstandingVersions.put(key, value);
		}

		//Deserialize outstanding versions
		byte[] outstandingVersionsSizeBytes = new byte[4];
		System.arraycopy(input, offset, outstandingVersionsSizeBytes, 0, 4);
		offset += 4;
		int outstandingVersionsSize = ORAMUtils.toNumber(outstandingVersionsSizeBytes);
		outstandingVersions = new int[outstandingVersionsSize];

		for (int i = 0; i < outstandingVersionsSize; i++) {
			byte[] outstandingVersionBytes = new byte[4];
			System.arraycopy(input, offset, outstandingVersionBytes, 0, 4);
			offset += 4;
			outstandingVersions[i] = ORAMUtils.toNumber(outstandingVersionBytes);
		}

		return offset;
	}
}
