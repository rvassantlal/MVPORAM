package oram.single.comunication;

public class Message {
	private final int sender;
	private final int type;
	private final byte[] serializedMessage;

	public Message(int sender, int type, byte[] serializedMessage) {
		this.sender = sender;
		this.type = type;
		this.serializedMessage = serializedMessage;
	}

	public int getSender() {
		return sender;
	}

	public int getType() {
		return type;
	}

	public byte[] getSerializedMessage() {
		return serializedMessage;
	}
}
