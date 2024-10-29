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
		byte[] clientIdBytes = ORAMUtils.toBytes(clientId);
		System.arraycopy(clientIdBytes, 0, output, offset, clientIdBytes.length);
		offset += clientIdBytes.length;
		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = super.readExternal(input, startOffset);
		byte[] clientIdBytes = new byte[4];
		System.arraycopy(input, offset, clientIdBytes, 0, 4);
		offset += 4;
		clientId = ORAMUtils.toNumber(clientIdBytes);
		return offset;
	}
}
