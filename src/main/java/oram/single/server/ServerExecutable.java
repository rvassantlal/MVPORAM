package oram.single.server;

import oram.single.comunication.Message;
import oram.single.comunication.MessageProcessor;
import oram.single.comunication.server.ServerCommunicationSystem;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

public abstract class ServerExecutable extends MessageProcessor {
	protected final ServerCommunicationSystem serverCommunicationSystem;
	private final static int MESSAGE_TYPE = 1;
	private final int id;

	public ServerExecutable(int id, String listeningIp, int listeningPort) throws InterruptedException {
		super(id);
		this.id = id;
		int workerNThreads = 16;
		int maxMessageSize = 100_000_000;
		try {
			serverCommunicationSystem = new ServerCommunicationSystem(
					id,
					listeningIp,
					listeningPort,
					workerNThreads,
					maxMessageSize
			);
			serverCommunicationSystem.registerMessageListener(MESSAGE_TYPE, this);
			start();
		} catch (CertificateException | IOException | UnrecoverableKeyException | NoSuchAlgorithmException |
				 KeyStoreException e) {
			throw new RuntimeException("Failed to create server communication system", e);
		}
	}

	public abstract byte[] execute(int sender, byte[] data);

	@Override
	public void deliverMessage(Message message) {
		byte[] response = execute(message.getSender(), message.getSerializedMessage());
		if (response != null) {
			sendResponse(message.getSender(), response);
		}
	}

	public void sendResponse(int target, byte[] response) {
		Message responseMessage = new Message(id, MESSAGE_TYPE, response);
		serverCommunicationSystem.sendMessage(target, responseMessage);
	}
}
