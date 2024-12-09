package oram.messages;

import oram.server.structure.EncryptedBucket;
import oram.server.structure.EncryptedPathMap;
import oram.server.structure.EncryptedStash;
import oram.utils.ORAMUtils;

import java.util.HashMap;
import java.util.Map;

public class EvictionORAMMessage extends ORAMMessage {
	private EncryptedStash encryptedStash;
	private EncryptedPathMap encryptedPathMap;
	private Map<Integer, EncryptedBucket> encryptedPath;

	public EvictionORAMMessage() {}

	public EvictionORAMMessage(int oramId, EncryptedStash encryptedStash,
							   EncryptedPathMap encryptedPathMap,
							   Map<Integer, EncryptedBucket> encryptedPath) {
		super(oramId);
		this.encryptedStash = encryptedStash;
		this.encryptedPathMap = encryptedPathMap;
		this.encryptedPath = encryptedPath;
	}

	public EncryptedStash getEncryptedStash() {
		return encryptedStash;
	}

	public EncryptedPathMap getEncryptedPathMap() {
		return encryptedPathMap;
	}

	public Map<Integer, EncryptedBucket> getEncryptedPath() {
		return encryptedPath;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = super.writeExternal(output, startOffset);

		offset = encryptedStash.writeExternal(output, offset);
		offset = encryptedPathMap.writeExternal(output, offset);

		int bucketSize = encryptedPath.values().iterator().next().getBlocks().length;
		ORAMUtils.serializeInteger(bucketSize, output, offset);
		offset += Integer.BYTES;

		ORAMUtils.serializeInteger(encryptedPath.size(), output, offset);
		offset += Integer.BYTES;

		for (Map.Entry<Integer, EncryptedBucket> entry : encryptedPath.entrySet()) {
			ORAMUtils.serializeInteger(entry.getKey(), output, offset);
			offset += Integer.BYTES;

			offset = entry.getValue().writeExternal(output, offset);
		}
		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = super.readExternal(input, startOffset);

		encryptedStash = new EncryptedStash();
		offset = encryptedStash.readExternal(input, offset);

		encryptedPathMap = new EncryptedPathMap();
		offset = encryptedPathMap.readExternal(input, offset);

		int bucketSize = ORAMUtils.deserializeInteger(input, offset);
		offset += Integer.BYTES;

		int pathSize = ORAMUtils.deserializeInteger(input, offset);
		offset += Integer.BYTES;

		encryptedPath = new HashMap<>(pathSize);
		while (pathSize--> 0) {
			int location = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;

			EncryptedBucket encryptedBucket = new EncryptedBucket(bucketSize);
			offset = encryptedBucket.readExternal(input, offset);
			encryptedPath.put(location, encryptedBucket);
		}
		return offset;
	}

	@Override
	public int getSerializedSize() {
		int size = super.getSerializedSize();
		size += encryptedStash.getSerializedSize();
		size += encryptedPathMap.getSerializedSize();
		size += Integer.BYTES * 2;
		for (EncryptedBucket value : encryptedPath.values()) {
			size += Integer.BYTES + value.getSerializedSize();
		}
		return size;
	}
}