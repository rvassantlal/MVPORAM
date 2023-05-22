package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import utils.Operation;
import utils.Status;
import vss.facade.SecretSharingException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

public class ORAMFacade {
	private final ConfidentialServiceProxy serviceProxy;

	public ORAMFacade(int clientId) throws SecretSharingException {
		//Connecting to servers
		this.serviceProxy = new ConfidentialServiceProxy(clientId);
	}

	public boolean createORAM(int oramId, int treeHeight) {
		try {
			byte[] serializedRequest = serializeCreateORAMRequest(oramId, treeHeight);
			if (serializedRequest == null) {
				return false;
			}
			Response response = serviceProxy.invokeOrdered(serializedRequest);
			if (response == null || response.getPainData() == null) {
				return false;
			}
			return Status.getStatus(response.getPainData()[0]) == Status.SUCCESS;
		} catch (SecretSharingException e) {
			return false;
		}
	}

	private static byte[] serializeCreateORAMRequest(int oramId, int treeHeight) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 ObjectOutputStream out = new ObjectOutputStream(bos)) {
			out.write(Operation.CREATE_ORAM.ordinal());
			out.writeInt(oramId);
			out.writeInt(treeHeight);
			out.flush();
			bos.flush();
			return bos.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

	public void close() {
		serviceProxy.close();
	}
}
