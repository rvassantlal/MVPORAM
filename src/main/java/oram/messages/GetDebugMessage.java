package oram.messages;

import oram.utils.ORAMUtils;

public class GetDebugMessage extends ORAMMessage {
	private int clientId;

	public GetDebugMessage() {}

	public GetDebugMessage(int oramId, int clientId) {
		super(oramId);
		this.clientId = clientId;
	}

	public int getClientId() {
		return clientId;
	}

	@Override
	public int getSerializedSize() {
		return super.getSerializedSize() + 4;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = super.writeExternal(output, startOffset);
		ORAMUtils.serializeInteger(clientId, output, offset);
		return offset + Integer.BYTES;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = super.readExternal(input, startOffset);
		clientId = ORAMUtils.deserializeInteger(input, offset);
		return offset + Integer.BYTES;
	}
}
