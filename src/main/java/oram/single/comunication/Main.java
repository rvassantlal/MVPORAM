package oram.single.comunication;

import oram.single.comunication.client.ClientCommunicationSystem;
import oram.single.comunication.server.ServerCommunicationSystem;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;

public class Main {
	public static void main(String[] args) throws InterruptedException, IOException, CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
		int myId = 0;
		String myIpAddress = "localhost";
		int listeningPort = 12000;
		int workerNThreads = 10;
		int maxMessageSize = 1024;
		int messageType = 0;
		ServerCommunicationSystem serverCommunicationSystem = new ServerCommunicationSystem(
				myId,
				myIpAddress,
				listeningPort,
				workerNThreads,
				maxMessageSize
		);

		MessageProcessor messageProcessor = new MessageProcessor(messageType) {
			@Override
			public void deliverMessage(Message message) {
				System.out.println("Delivering message from " + message.getSender() + ": " + Arrays.toString(message.getSerializedMessage()));
			}
		};
		serverCommunicationSystem.registerMessageListener(messageType, messageProcessor);
		messageProcessor.start();

		Thread.sleep(5000);
		ClientCommunicationSystem clientCommunicationSystem = new ClientCommunicationSystem(
				myId,
				workerNThreads,
				maxMessageSize
		);
		clientCommunicationSystem.connectTo(0, "localhost", listeningPort);

		Thread.sleep(5000);
		byte[] normalMessage = new byte[]{1, 2, 3};
		byte[] bigMessage = new byte[maxMessageSize + 1];
		clientCommunicationSystem.sendMessage(0, new Message(10, messageType, bigMessage));
		clientCommunicationSystem.sendMessage(0, new Message(10, messageType, normalMessage));
		clientCommunicationSystem.sendMessage(0, new Message(10, messageType, bigMessage));
		clientCommunicationSystem.sendMessage(0, new Message(10, messageType, normalMessage));

		Thread.sleep(10000);
		serverCommunicationSystem.shutdown();
		clientCommunicationSystem.shutdown();
		messageProcessor.interrupt();
	}
}
