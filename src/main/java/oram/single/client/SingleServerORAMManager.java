package oram.single.client;

import confidential.client.Response;
import oram.client.manager.ORAMManager;
import oram.single.comunication.Message;
import oram.utils.Status;

public class SingleServerORAMManager extends ORAMManager {
	private static final String DEFAULT_PASSWORD = "ORAM";
	private final SingleServiceProxy singleServiceProxy;
	public final static int MESSAGE_TYPE = 1;

	public SingleServerORAMManager(int clientId, String serverIP, int serverPort) {
		//Connecting to the server
		this.singleServiceProxy = new SingleServiceProxy(clientId, MESSAGE_TYPE, serverIP, serverPort);
	}

	@Override
	protected Status createORAM(byte[] serializedRequest, String password) {
		Message response = singleServiceProxy.sendMessage(serializedRequest);
		if (response == null || response.getSerializedMessage() == null) {
			return Status.FAILED;
		}
		return Status.getStatus(response.getSerializedMessage()[0]);
	}

	@Override
	protected String generatePassword() {
		return DEFAULT_PASSWORD;
	}

	@Override
	protected Response getORAMContext(byte[] serializedRequest) {
		Message response = singleServiceProxy.sendMessage(serializedRequest);
		if (response == null || response.getSerializedMessage() == null) {
			return null;
		}
		return new Response(response.getSerializedMessage(), new byte[][]{DEFAULT_PASSWORD.getBytes()});
	}

	@Override
	public void close() {
		singleServiceProxy.close();
	}

	@Override
	public int getProcessId() {
		return singleServiceProxy.getProcessId();
	}

	@Override
	public byte[] getDebugSnapshot(byte[] request) {
		return invoke(request);
	}

	@Override
	public byte[] getPathMaps(byte[] request) {
		return invoke(request);
	}

	@Override
	public byte[] getStashesAndPaths(byte[] request) {
		return invoke(request);
	}

	@Override
	public byte[] sendEvictionPayload(byte[] request) {
		return invoke(request);
	}

	@Override
	public byte[] evict(byte[] request) {
		return invoke(request);
	}

	private byte[] invoke(byte[] request) {
		Message response = singleServiceProxy.sendMessage(request);
		if (response == null || response.getSerializedMessage() == null) {
			return null;
		}
		return response.getSerializedMessage();
	}
}
