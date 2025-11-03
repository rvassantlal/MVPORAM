package oram.single.comunication.util;

import oram.single.comunication.Message;
import oram.single.comunication.codecs.MessageDecoder;
import oram.single.comunication.codecs.MessageEncoder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;

public class BasicChannelInitializer extends ChannelInitializer<Channel> {
	private final SimpleChannelInboundHandler<Message> messageHandler;
	private final int maxMessageSize;
	private final NewSessionListener newSessionListener;

	public BasicChannelInitializer(SimpleChannelInboundHandler<Message> messageHandler, int maxMessageSize,
								   NewSessionListener newSessionListener) {
		this.messageHandler = messageHandler;
		this.maxMessageSize = maxMessageSize;
		this.newSessionListener = newSessionListener;
	}

	@Override
	protected void initChannel(Channel channel) {
		channel.pipeline()
				.addLast("decoder", new MessageDecoder(newSessionListener, maxMessageSize))
				.addLast("encoder", new MessageEncoder())
				.addLast("messageHandler", messageHandler);
	}
}
