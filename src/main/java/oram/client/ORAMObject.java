package oram.client;

import oram.ORAMUtils;
import oram.client.structure.PositionMap;
import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.messages.ORAMMessage;
import utils.Operation;
import vss.facade.SecretSharingException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

public class ORAMObject {
	private final ConfidentialServiceProxy serviceProxy;
	private final int oramId;
	private final int treeHeight;
	private final EncryptionManager encryptionManager;

	public ORAMObject(ConfidentialServiceProxy serviceProxy, int oramId, int treeHeight, EncryptionManager encryptionManager) throws SecretSharingException {
		this.serviceProxy = serviceProxy;
		this.oramId = oramId;
		this.treeHeight = treeHeight;
		this.encryptionManager = encryptionManager;
	}

	private PositionMap[] getPositionMaps(int oramId) {
		try {
			ORAMMessage request = new ORAMMessage(oramId);
			byte[] serializedRequest = ORAMUtils.serializeRequest(Operation.CREATE_ORAM, request);
			if (serializedRequest == null) {
				return null;
			}
			Response response = serviceProxy.invokeUnordered(serializedRequest);
			if (response == null || response.getPainData() == null)
				return null;
			return encryptionManager.decryptPositionMaps(response.getPainData());
		} catch (SecretSharingException e) {
			return null;
		}
	}
}
