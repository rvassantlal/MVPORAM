package oram.server;

import bftsmart.tom.MessageContext;

public interface ClientMessageSender {
	void sendMessageToClient(MessageContext clientMsgCtx, byte[] serializedMessage);
}
