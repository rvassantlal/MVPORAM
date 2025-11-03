package oram.single.comunication.codecs;

import oram.single.comunication.Message;
import oram.single.comunication.util.ChannelSession;
import oram.single.comunication.util.NewSessionListener;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MessageDecoder extends ByteToMessageDecoder {
	private final Logger logger = LoggerFactory.getLogger("communication");
	private int bytesToSkip;
	private final NewSessionListener sessionListener;
	private int maxMessageSize;

	public MessageDecoder(NewSessionListener sessionListener, int maxMessageSize) {
		this.sessionListener = sessionListener;
		this.maxMessageSize = maxMessageSize;
	}

	public void setMaxMessageSize(int maxMessageSize) {
		this.maxMessageSize = maxMessageSize;
	}

	@Override
	protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf in, List<Object> list) {
		logger.debug("Decoding message of length {} bytes", in.readableBytes());
		//skip bytes if necessary - used when previous request is too big
		if (bytesToSkip > 0) {
			int readableBytes = in.readableBytes();
			if (readableBytes >= bytesToSkip) {
				in.skipBytes(bytesToSkip);
				bytesToSkip = 0;
			} else {
				in.skipBytes(readableBytes);
				bytesToSkip -= readableBytes;
				return;
			}
		}

		int sender, dataLength, messageType;
		do {
			//wait until sender, message type and data length are available
			if (in.readableBytes() < Integer.BYTES * 3) {
				return;
			}

			sender = in.getInt(in.readerIndex());
			messageType = in.getInt(in.readerIndex() + Integer.BYTES);
			dataLength = in.getInt(in.readerIndex() + Integer.BYTES * 2);

			logger.debug("Decoding message from {} of type {} with length {} ({} remaining bytes)", sender, messageType,
					dataLength, in.readableBytes());

			//skip if message is too big
			if (dataLength > maxMessageSize) {
				logger.warn("Discarding request from {} because it is too big: {} bytes", sender, dataLength);
				in.skipBytes(Integer.BYTES * 3);
				int remainingBytes = in.readableBytes();
				if (dataLength >= remainingBytes) {
					in.skipBytes(remainingBytes);
					bytesToSkip = dataLength - remainingBytes;
					return;
				} else {
					in.skipBytes(dataLength);
					logger.debug("Available bytes: {}", in.readableBytes());
				}
			} else {
				break;
			}
		} while (true);

		//check if all data has arrived
		if (in.readableBytes() < dataLength + Integer.BYTES * 3) {
			return;
		}
		in.skipBytes(Integer.BYTES * 3);

		byte[] serializedMessage = new byte[dataLength];
		in.readBytes(serializedMessage);

		Message message = new Message(sender, messageType, serializedMessage);

		if (!sessionListener.sessionExists(sender)) {
			ChannelSession newSession = new ChannelSession(sender, channelHandlerContext.channel());
			sessionListener.newSession(newSession);
		}

		list.add(message);
	}
}
