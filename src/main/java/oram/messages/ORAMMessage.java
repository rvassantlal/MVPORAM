package oram.messages;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

public class ORAMMessage implements RawCustomExternalizable {
	private int oramId;

	public ORAMMessage() {}

	public ORAMMessage(int oramId) {
		this.oramId = oramId;
	}

	public int getOramId() {
		return oramId;
	}

	public int getSerializedSize() {
		return 4;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		byte[] oramIdBytes = ORAMUtils.toBytes(oramId);
		System.arraycopy(oramIdBytes, 0, output, startOffset, 4);
		return startOffset + 4;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		byte[] oramIdBytes = new byte[4];
		System.arraycopy(input, startOffset, oramIdBytes, 0, 4);
		oramId = ORAMUtils.toNumber(oramIdBytes);
		return startOffset + 4;
	}
}
