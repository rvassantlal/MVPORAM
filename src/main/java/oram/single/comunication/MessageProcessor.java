package oram.single.comunication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class MessageProcessor extends Thread {
	private final Logger logger = LoggerFactory.getLogger("communication");
	private final BlockingQueue<Message> messages;
	private final int messageType;

	public MessageProcessor(int messageType) {
		super("Message Processor for " + messageType);
		this.messageType = messageType;
		this.messages = new LinkedBlockingQueue<>();
	}

	public int getMessageType() {
		return messageType;
	}

	public void messageReceived(Message message) {
		messages.add(message);
	}

	public abstract void deliverMessage(Message message);

	@Override
	public void run() {
		while (true) {
			try {
				Message m = messages.take();
				logger.debug("I have message with tag {} to deliver", m.getType());
				deliverMessage(m);
			} catch (InterruptedException e) {
				break;
			}
		}
		logger.debug("Exiting message processor thread for {}", messageType);
	}
}
