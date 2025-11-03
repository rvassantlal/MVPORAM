package oram.single.server;

import bftsmart.tom.MessageContext;
import bftsmart.tom.core.messages.TOMMessageType;
import oram.server.ClientMessageSender;
import oram.server.ORAMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ORAMSingleServer extends ServerExecutable implements ClientMessageSender {
	private final Logger logger = LoggerFactory.getLogger("oram");
	private final ORAMService oramService;

	public ORAMSingleServer(int maxClients, int processId, String ip, int port) throws InterruptedException {
		super(processId, ip, port);
		oramService = new ORAMService(maxClients, this);
		logger.info("Ready to process operations");
	}

	public static void main(String[] args) throws InterruptedException {
		int maxClients = Integer.parseInt(args[0]);
		String ip = args[1];
		int port = Integer.parseInt(args[2]);
		int id = Integer.parseInt(args[3]);
		new ORAMSingleServer(maxClients, id, ip, port);
	}

	@Override
	public byte[] execute(int sender, byte[] requestData) {
		MessageContext messageContext = new MessageContext(sender, -1, TOMMessageType.ORDERED_REQUEST, -1,
				-1, -1, -1, null, -1, -1, -1, -1,
				-1, -1, null, null, false, false, (byte) -1);
		return oramService.executeOrdered(requestData, messageContext);
	}

	@Override
	public void sendMessageToClient(MessageContext clientMsgCtx, byte[] serializedMessage) {
		sendResponse(clientMsgCtx.getSender(), serializedMessage);
	}
}
