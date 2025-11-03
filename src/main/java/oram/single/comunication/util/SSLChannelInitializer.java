package oram.single.comunication.util;

import oram.single.comunication.Message;
import oram.single.comunication.codecs.MessageDecoder;
import oram.single.comunication.codecs.MessageEncoder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;

public class SSLChannelInitializer extends ChannelInitializer<Channel> {
	private final SslContext sslContext;
	private final SimpleChannelInboundHandler<Message> messageHandler;
	private final int maxMessageSize;
	private final NewSessionListener newSessionListener;

	public SSLChannelInitializer(SslContext sslContext, SimpleChannelInboundHandler<Message> messageHandler, int maxMessageSize, NewSessionListener newSessionListener) {
		this.sslContext = sslContext;
		this.messageHandler = messageHandler;
		this.maxMessageSize = maxMessageSize;
		this.newSessionListener = newSessionListener;
	}

	@Override
	protected void initChannel(Channel channel) {
		SSLEngine engine = sslContext.newEngine(channel.alloc());
		System.out.println(sslContext);
		channel.pipeline()
				.addFirst("ssl", new SslHandler(engine, true))
				.addLast("decoder", new MessageDecoder(newSessionListener, maxMessageSize))
				.addLast("encoder", new MessageEncoder())
				.addLast("messageHandler", messageHandler);
	}
}
