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
		return Integer.BYTES;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		ORAMUtils.serializeInteger(oramId, output, startOffset);
		return startOffset + Integer.BYTES;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		oramId = ORAMUtils.deserializeInteger(input, startOffset);
		return startOffset + Integer.BYTES;
	}
}
