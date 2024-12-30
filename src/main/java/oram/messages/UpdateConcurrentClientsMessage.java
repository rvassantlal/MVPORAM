package oram.messages;

import oram.utils.ORAMUtils;

public class UpdateConcurrentClientsMessage extends ORAMMessage {

	private int maximumNConcurrentClients;

	public UpdateConcurrentClientsMessage() {}

	public UpdateConcurrentClientsMessage(int oramId, int maximumNConcurrentClients) {
		super(oramId);
		this.maximumNConcurrentClients = maximumNConcurrentClients;
	}

	public int getMaximumNConcurrentClients() {
		return maximumNConcurrentClients;
	}

	@Override
	public int getSerializedSize() {
		return super.getSerializedSize() + Integer.BYTES;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = super.writeExternal(output, startOffset);
		ORAMUtils.serializeInteger(maximumNConcurrentClients, output, offset);
		return offset + Integer.BYTES;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = super.readExternal(input, startOffset);
		maximumNConcurrentClients = ORAMUtils.deserializeInteger(input, offset);
		return offset + Integer.BYTES;
	}
}
