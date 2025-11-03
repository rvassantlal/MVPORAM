package oram.single.comunication;

import oram.single.comunication.util.ChannelSession;
import oram.single.comunication.util.NewSessionListener;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Sharable
public abstract class CommunicationSystem extends SimpleChannelInboundHandler<Message> implements NewSessionListener {
	protected final Logger logger = LoggerFactory.getLogger("communication");
	protected static final String SECRET = "MySeCreT_2hMOygBwY";
	protected static final int tcpSendBufferSize = 8 * 1024 * 1024;
	protected static final int connectionTimeoutMsec = 40000;
	protected final int myId;
	private final ConcurrentMap<Integer, ChannelSession> sessions;
	private final ConcurrentMap<Integer, MessageProcessor> messageListeners;

	public CommunicationSystem(int myId) {
		this.myId = myId;
		this.sessions = new ConcurrentHashMap<>();
		this.messageListeners = new ConcurrentHashMap<>();
	}

	public void registerMessageListener(int messageType, MessageProcessor messageListener) {
		messageListeners.put(messageType, messageListener);
	}

	protected void shutdownChannel(Channel channel) {
		channel.flush();
		channel.deregister();
		channel.close();
		channel.eventLoop().shutdownGracefully();
	}

	public void shutdown() {
		for (ChannelSession session : sessions.values()) {
			shutdownChannel(session.getChannel());
		}
	}

	@Override
	public boolean sessionExists(int clientId) {
		return sessions.containsKey(clientId);
	}

	@Override
	public void newSession(ChannelSession session) {
		logger.debug("New session with client {}", session.getClientId());
		sessions.put(session.getClientId(), session);
		logger.info("Active communication channels: {}", sessions.size());
	}

	public void sendMessage(int target, Message message) {
		ChannelSession channelSession = sessions.get(target);
		if (channelSession == null) {
			logger.warn("No connect found for target {}", target);
			return;
		}
		Channel channel = channelSession.getChannel();
		channel.writeAndFlush(message);
	}

	@Override
	protected void channelRead0(ChannelHandlerContext channelHandlerContext, Message message) {
		MessageProcessor messageProcessor = messageListeners.get(message.getType());
		if (messageProcessor == null) {
			logger.warn("There is no message processor for message type {}", message.getType());
			return;
		}
		messageProcessor.messageReceived(message);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		SocketAddress socketAddress = ctx.channel().remoteAddress();
		logger.debug("Client disconnected {}", socketAddress);
		//remove the session
		int sessionToRemove = -1;
		for (Map.Entry<Integer, ChannelSession> entry : sessions.entrySet()) {
			if (entry.getValue().getChannel().equals(ctx.channel())) {
				sessionToRemove = entry.getKey();
				break;
			}
		}
		if (sessionToRemove != -1) {
			sessions.remove(sessionToRemove);
			logger.info("Active communication channels: {}", sessions.size());
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		if (cause instanceof ClosedChannelException) {
			logger.info("Client connection closed");
		} else if (cause instanceof IOException) {
			SocketAddress socketAddress = ctx.channel().remoteAddress();
			logger.error("Connection reset by the client {}", socketAddress);
		} else {
			logger.error("Connection problem.", cause);
		}
	}
}
