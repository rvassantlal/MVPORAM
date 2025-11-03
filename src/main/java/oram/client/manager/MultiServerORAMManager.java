package oram.client.manager;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.utils.ORAMUtils;
import oram.utils.Status;
import vss.facade.SecretSharingException;

import java.security.SecureRandom;

public class MultiServerORAMManager extends ORAMManager {
	private ConfidentialServiceProxy serviceProxy;
	private final SecureRandom rndGenerator;

	public MultiServerORAMManager(int clientId) {
		this.rndGenerator = new SecureRandom("oram".getBytes());
		//Connecting to the servers
		try {
			this.serviceProxy = new ConfidentialServiceProxy(clientId);
		} catch (SecretSharingException e) {
			logger.error("Failed to connect to servers", e);
		}
	}

	@Override
	protected Status createORAM(byte[] serializedRequest, String password) {
		try {
			Response response = serviceProxy.invokeOrdered(serializedRequest, password.getBytes());
			if (response == null || response.getPlainData() == null) {
				return Status.FAILED;
			}
			return Status.getStatus(response.getPlainData()[0]);
		} catch (SecretSharingException e) {
			logger.error("Failed to create ORAM", e);
			return Status.FAILED;
		}
	}

	@Override
	protected String generatePassword() {
		return ORAMUtils.generateRandomPassword(rndGenerator);
	}

	@Override
	public Response getORAMContext(byte[] serializedRequest) {
		try {
			return serviceProxy.invokeOrdered(serializedRequest);
		} catch (SecretSharingException e) {
			logger.error("Failed to get ORAM context", e);
			return null;
		}
	}

	@Override
	public void close() {
		serviceProxy.close();
	}

	@Override
	public int getProcessId() {
		return serviceProxy.getProcessId();
	}

	@Override
	public byte[] getDebugSnapshot(byte[] request) {
		try {
			Response response = serviceProxy.invokeOrderedHashed(request);
			if (response != null && response.getPlainData() != null) {
				return response.getPlainData();
			}
		} catch (SecretSharingException e) {
			logger.error("Failed to get debug snapshot", e);
		}
		return null;
	}

	@Override
	public byte[] getPathMaps(byte[] request) {
		try {
			Response response = serviceProxy.invokeOrderedHashed(request);
			if (response != null && response.getPlainData() != null) {
				return response.getPlainData();
			}
		} catch (SecretSharingException e) {
			logger.error("Failed to get path maps", e);
		}
		return null;
	}

	@Override
	public byte[] getStashesAndPaths(byte[] request) {
		try {
			Response response = serviceProxy.invokeOrderedHashed(request);
			if (response != null && response.getPlainData() != null) {
				return response.getPlainData();
			}
		} catch (SecretSharingException e) {
			logger.error("Failed to get stashes and paths", e);
		}
		return null;
	}

	@Override
	public byte[] sendEvictionPayload(byte[] request) {
		try {
			Response response = serviceProxy.invokeUnordered(request);
			if (response != null && response.getPlainData() != null) {
				return response.getPlainData();
			}
		} catch (SecretSharingException e) {
			logger.error("Failed to send eviction payload", e);
		}
		return null;
	}

	@Override
	public byte[] evict(byte[] request) {
		try {
			Response response = serviceProxy.invokeOrdered(request);
			if (response != null && response.getPlainData() != null) {
				return response.getPlainData();
			}
		} catch (SecretSharingException e) {
			logger.error("Failed to evict", e);
		}
		return null;
	}
}
