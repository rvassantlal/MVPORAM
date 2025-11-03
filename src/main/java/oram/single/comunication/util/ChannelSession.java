package oram.single.comunication.util;

import io.netty.channel.Channel;

public class ChannelSession {
	private int clientId;
	private Channel channel;

	public ChannelSession(int clientId, Channel channel) {
		this.clientId = clientId;
		this.channel = channel;
	}

	public Channel getChannel() {
		return channel;
	}

	public int getClientId() {
		return clientId;
	}
}
