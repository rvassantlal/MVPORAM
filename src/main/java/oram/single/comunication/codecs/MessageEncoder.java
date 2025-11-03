package oram.single.comunication.codecs;

import oram.single.comunication.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageEncoder extends MessageToByteEncoder<Message> {
	private final Logger logger = LoggerFactory.getLogger("communication");

	@Override
	protected void encode(ChannelHandlerContext channelHandlerContext, Message message, ByteBuf out) {
		int sender = message.getSender();
		int messageType = message.getType();
		byte[] serializedMessage = message.getSerializedMessage();
		int dataLength = Integer.BYTES * 3 + serializedMessage.length;
		logger.debug("Encoding message of type {} from {} ({} bytes)", messageType, sender, dataLength);

		//sender id
		out.writeInt(sender);

		//message type
		out.writeInt(messageType);

		//serialized message
		out.writeInt(serializedMessage.length);
		out.writeBytes(serializedMessage);

		channelHandlerContext.flush();
	}
}
