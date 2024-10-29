package oram.messages;

import oram.client.structure.EvictionMap;
import oram.server.structure.EncryptedBucket;
import oram.server.structure.EncryptedPositionMap;
import oram.server.structure.EncryptedStash;
import oram.utils.ORAMUtils;

import java.util.HashMap;
import java.util.Map;

public class EvictionORAMMessage extends ORAMMessage {
	private EncryptedStash encryptedStash;
	private EncryptedPositionMap encryptedPositionMap;
	private Map<Integer, EncryptedBucket> encryptedPath;
	private EvictionMap evictionMap;

	public EvictionORAMMessage() {}

	public EvictionORAMMessage(int oramId, EncryptedStash encryptedStash,
							   EncryptedPositionMap encryptedPositionMap,
							   Map<Integer, EncryptedBucket> encryptedPath,
							   EvictionMap evictionMap) {
		super(oramId);
		this.encryptedStash = encryptedStash;
		this.encryptedPositionMap = encryptedPositionMap;
		this.encryptedPath = encryptedPath;
		this.evictionMap = evictionMap;
	}

	public EncryptedStash getEncryptedStash() {
		return encryptedStash;
	}

	public EncryptedPositionMap getEncryptedPositionMap() {
		return encryptedPositionMap;
	}

	public Map<Integer, EncryptedBucket> getEncryptedPath() {
		return encryptedPath;
	}

	public EvictionMap getEvictionMap() {
		return evictionMap;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = super.writeExternal(output, startOffset);

		offset = encryptedStash.writeExternal(output, offset);
		offset = encryptedPositionMap.writeExternal(output, offset);
		offset = evictionMap.writeExternal(output, offset);

		int bucketSize = encryptedPath.values().iterator().next().getBlocks().length;
		byte[] bucketSizeBytes = ORAMUtils.toBytes(bucketSize);
		System.arraycopy(bucketSizeBytes, 0, output, offset, 4);
		offset += 4;

		byte[] pathSizeBytes = ORAMUtils.toBytes(encryptedPath.size());
		System.arraycopy(pathSizeBytes, 0, output, offset, 4);
		offset += 4;

		for (Map.Entry<Integer, EncryptedBucket> entry : encryptedPath.entrySet()) {
			byte[] locationBytes = ORAMUtils.toBytes(entry.getKey());
			System.arraycopy(locationBytes, 0, output, offset, 4);
			offset += 4;

			offset = entry.getValue().writeExternal(output, offset);
		}
		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = super.readExternal(input, startOffset);

		encryptedStash = new EncryptedStash();
		offset = encryptedStash.readExternal(input, offset);

		encryptedPositionMap = new EncryptedPositionMap();
		offset = encryptedPositionMap.readExternal(input, offset);

		evictionMap = new EvictionMap();
		offset = evictionMap.readExternal(input, offset);

		byte[] bucketSizeBytes = new byte[4];
		System.arraycopy(input, offset, bucketSizeBytes, 0, 4);
		offset += 4;
		int bucketSize = ORAMUtils.toNumber(bucketSizeBytes);

		byte[] pathSizeBytes = new byte[4];
		System.arraycopy(input, offset, pathSizeBytes, 0, 4);
		offset += 4;
		int pathSize = ORAMUtils.toNumber(pathSizeBytes);

		encryptedPath = new HashMap<>(pathSize);
		while (pathSize--> 0) {
			byte[] locationBytes = new byte[4];
			System.arraycopy(input, offset, locationBytes, 0, 4);
			offset += 4;
			int location = ORAMUtils.toNumber(locationBytes);

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
		size += encryptedPositionMap.getSerializedSize();
		size += evictionMap.getSerializedSize();
		size += 8;
		for (EncryptedBucket value : encryptedPath.values()) {
			size += 4 + value.getSerializedSize();
		}
		return size;
	}
}