package oram.server.structure;

import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class EncryptedStashesAndPaths implements RawCustomExternalizable {
	private ORAMContext oramContext;
	private Map<Integer, EncryptedStash> encryptedStashes;
	private EncryptedBucket[] paths;

	public EncryptedStashesAndPaths(ORAMContext oramContext) {
		this.oramContext = oramContext;
	}

	public EncryptedStashesAndPaths(Map<Integer, EncryptedStash> encryptedStashes, EncryptedBucket[] paths) {
		this.encryptedStashes = encryptedStashes;
		this.paths = paths;
	}

	public Map<Integer, EncryptedStash> getEncryptedStashes() {
		return encryptedStashes;
	}

	public EncryptedBucket[] getPaths() {
		return paths;
	}

	public int getSerializedSize() {
		int size = 4;
		for (EncryptedStash entry : encryptedStashes.values()) {
			size += 4 + entry.getSerializedSize();
		}
		size += 4;
		for (EncryptedBucket encryptedBucket : paths) {
			size += 1;
			if (encryptedBucket != null) {
				size += encryptedBucket.getSerializedSize();
			}
		}
		return size;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;

		// Serialize encrypted stashes
		byte[] encryptedStashesSizeBytes = ORAMUtils.toBytes(encryptedStashes.size());
		System.arraycopy(encryptedStashesSizeBytes, 0, output, offset, 4);
		offset += 4;

		int[] keys = new int[encryptedStashes.size()];
		int k = 0;
		for (Integer key : encryptedStashes.keySet()) {
			keys[k++] = key;
		}
		Arrays.sort(keys);

		for (int key : keys) {
			byte[] keyBytes = ORAMUtils.toBytes(key);
			System.arraycopy(keyBytes, 0, output, offset, 4);
			offset += 4;

			offset = encryptedStashes.get(key).writeExternal(output, offset);
		}

		// Serialize encrypted paths
		byte[] pathsSizeBytes = ORAMUtils.toBytes(paths.length);
		System.arraycopy(pathsSizeBytes, 0, output, offset, 4);
		offset += 4;
		for (EncryptedBucket encryptedBucket : paths) {
			if (encryptedBucket == null) {
				output[offset++] = 0;
			} else {
				output[offset++] = 1;
				offset = encryptedBucket.writeExternal(output, offset);
			}
		}

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = startOffset;

		// Deserialize encrypted stashes
		byte[] nEncryptedStashesBytes = new byte[4];
		System.arraycopy(input, offset, nEncryptedStashesBytes, 0, 4);
		offset += 4;
		int encryptedStashesSize = ORAMUtils.toNumber(nEncryptedStashesBytes);
		encryptedStashes = new HashMap<>(encryptedStashesSize);
		for (int i = 0; i < encryptedStashesSize; i++) {
			byte[] keyBytes = new byte[4];
			System.arraycopy(input, offset, keyBytes, 0, 4);
			offset += 4;
			int key = ORAMUtils.toNumber(keyBytes);

			EncryptedStash entry = new EncryptedStash();
			offset = entry.readExternal(input, offset);
			encryptedStashes.put(key, entry);
		}

		// Deserialize encrypted paths
		byte[] nPathsBytes = new byte[4];
		System.arraycopy(input, offset, nPathsBytes, 0, 4);
		offset += 4;
		int pathsSize = ORAMUtils.toNumber(nPathsBytes);
		paths = new EncryptedBucket[pathsSize];
		for (int i = 0; i < pathsSize; i++) {
			byte isNull = input[offset++];
			if (isNull == 0) {
				paths[i] = null;
			} else {
				EncryptedBucket encryptedBucket = new EncryptedBucket(oramContext.getBucketSize());
				offset = encryptedBucket.readExternal(input, offset);
				paths[i] = encryptedBucket;
			}
		}

		return offset;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("EncryptedStashesAndPaths{");
		sb.append("\n\tencryptedStashes: ").append(encryptedStashes);
		sb.append("\n\tpaths:");
		for (EncryptedBucket entry : paths) {
			sb.append("\n\t\t").append(entry.toString());
		}
		sb.append("\n}");

		return sb.toString();
	}
}
