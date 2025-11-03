package oram.single.client;

import oram.single.comunication.Message;
import oram.single.comunication.MessageProcessor;
import oram.single.comunication.client.ClientCommunicationSystem;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SingleServiceProxy extends MessageProcessor {
	private final int id;
	private final int messageType;
	private final ClientCommunicationSystem serviceProxy;
	private final Lock responseLock;
	private final Condition responseCondition;
	private Message response;

	public SingleServiceProxy(int id, int messageType, String serverIP, int serverPort) {
		super(messageType);
		this.id = id;
		this.messageType = messageType;
		this.responseLock = new ReentrantLock();
		this.responseCondition = responseLock.newCondition();
		try {
			this.serviceProxy = new ClientCommunicationSystem(
					id,
					1,
					100_000_000
			);
			start();
			serviceProxy.registerMessageListener(messageType, this);
			this.serviceProxy.connectTo(0, serverIP, serverPort);
		} catch (InterruptedException | IOException | NoSuchAlgorithmException | KeyStoreException |
				 CertificateException | UnrecoverableKeyException e) {
			throw new RuntimeException("Failed to connect to server", e);
		}
	}

	public Message sendMessage(byte[] serializedMessage) {
		try {
			responseLock.lock();
			Message m = new Message(id, messageType, serializedMessage);
			serviceProxy.sendMessage(0, m);
			responseCondition.await();
			return response;
		} catch (InterruptedException e) {
			return null;
		} finally {
			responseLock.unlock();
		}
	}

	@Override
	public void deliverMessage(Message message) {
		try {
			responseLock.lock();
			response = message;
			responseCondition.signal();
		} finally {
			responseLock.unlock();
		}
	}

	public void close() {
		serviceProxy.shutdown();
		interrupt();
	}

	public int getProcessId() {
		return id;
	}
}
