package oram.messages;

import oram.utils.ORAMUtils;

public class StashPathORAMMessage extends ORAMMessage {

	private int pathId;

	public StashPathORAMMessage() {}

	public StashPathORAMMessage(int oramId, int pathId) {
		super(oramId);
		this.pathId = pathId;
	}

	public int getPathId() {
		return pathId;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = super.writeExternal(output, startOffset);

		byte[] pathIdBytes = ORAMUtils.toBytes(pathId);
		System.arraycopy(pathIdBytes, 0, output, offset, 4);
		offset += 4;

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = super.readExternal(input, startOffset);

		byte[] pathIdBytes = new byte[4];
		System.arraycopy(input, offset, pathIdBytes, 0, 4);
		pathId = ORAMUtils.toNumber(pathIdBytes);
		offset += 4;

		return offset;
	}

	@Override
	public int getSerializedSize() {
		return super.getSerializedSize() + 4;
	}
}
