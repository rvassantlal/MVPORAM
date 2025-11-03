package oram.single.comunication.util;

public interface NewSessionListener {

	boolean sessionExists(int clientId);
	void newSession(ChannelSession session);
}
