package oram.messages;

import oram.server.structure.EncryptedBucket;
import oram.server.structure.EncryptedPositionMap;
import oram.server.structure.EncryptedStash;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;

public class EvictionORAMMessage extends ORAMMessage {

	private byte pathId;
	private EncryptedStash encryptedStash;
	private EncryptedPositionMap encryptedPositionMap;
	private Map<Integer, EncryptedBucket> encryptedPath;

	public EvictionORAMMessage() {}

	public EvictionORAMMessage(int oramId, EncryptedStash encryptedStash,
							   EncryptedPositionMap encryptedPositionMap,
							   Map<Integer, EncryptedBucket> encryptedPath, byte pathId) {
		super(oramId);
		this.encryptedStash = encryptedStash;
		this.encryptedPositionMap = encryptedPositionMap;
		this.encryptedPath = encryptedPath;
		this.pathId = pathId;
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

	public byte getPathId() {
		return pathId;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		encryptedStash.writeExternal(out);
		encryptedPositionMap.writeExternal(out);
		out.writeByte(pathId);
		int bucketSize = encryptedPath.values().iterator().next().getBlocks().length;
		out.writeInt(bucketSize);
		out.writeInt(encryptedPath.size());
		for (Map.Entry<Integer, EncryptedBucket> entry : encryptedPath.entrySet()) {
			out.writeInt(entry.getKey());
			entry.getValue().writeExternal(out);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		encryptedStash = new EncryptedStash();
		encryptedStash.readExternal(in);
		encryptedPositionMap = new EncryptedPositionMap();
		encryptedPositionMap.readExternal(in);

		pathId = (byte) in.read();
		int bucketSize = in.readInt();
		int pathSize = in.readInt();
		encryptedPath = new HashMap<>(pathSize);
		while (pathSize--> 0) {
			int location = in.readInt();
			EncryptedBucket encryptedBucket = new EncryptedBucket(bucketSize);
			encryptedBucket.readExternal(in);
			encryptedPath.put(location, encryptedBucket);
		}

	}
}