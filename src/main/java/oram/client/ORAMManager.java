package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.client.structure.PathMap;
import oram.client.structure.Stash;
import oram.messages.CreateORAMMessage;
import oram.messages.ORAMMessage;
import oram.security.EncryptionManager;
import oram.server.structure.EncryptedPathMap;
import oram.server.structure.EncryptedStash;
import oram.utils.*;
import vss.facade.SecretSharingException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class ORAMManager {
	private final ConfidentialServiceProxy serviceProxy;
	private final EncryptionManager encryptionManager;

	public ORAMManager(int clientId) throws SecretSharingException {
		//Connecting to servers
		this.serviceProxy = new ConfidentialServiceProxy(clientId);
		this.encryptionManager = new EncryptionManager();
	}

	public ORAMObject createORAM(int oramId, PositionMapType positionMapType, int treeHeight, int bucketSize,
								 int blockSize) {
		try {
			String password = encryptionManager.generatePassword();
			encryptionManager.createSecretKey(password);

			EncryptedPathMap encryptedPathMap = initializeEmptyPathMap();
			EncryptedStash encryptedStash = initializeEmptyStash(blockSize);
			CreateORAMMessage request = new CreateORAMMessage(oramId, positionMapType,
					treeHeight, bucketSize, blockSize, encryptedPathMap, encryptedStash);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.CREATE_ORAM, request);
			Response response = serviceProxy.invokeOrdered(serializedRequest, password.getBytes());
			if (response == null || response.getPlainData() == null) {
				return null;
			}

			Status status = Status.getStatus(response.getPlainData()[0]);
			if (status == Status.FAILED) {
				return null;
			}
			int treeSize = ORAMUtils.computeTreeSize(treeHeight, bucketSize);
			ORAMContext oramContext = new ORAMContext(positionMapType, treeHeight,
					treeSize, bucketSize, blockSize);

			return new ORAMObject(serviceProxy, oramId, oramContext, encryptionManager);
		} catch (SecretSharingException e) {
			return null;
		}
	}

	public ORAMObject getORAM(int oramId) {
		try {
			ORAMMessage request = new ORAMMessage(oramId);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_ORAM, request);
			Response response = serviceProxy.invokeUnordered(serializedRequest);
			if (response == null || response.getPlainData() == null || response.getConfidentialData() == null) {
				return null;
			}

			try (ByteArrayInputStream bis = new ByteArrayInputStream(response.getPlainData());
				 DataInputStream in = new DataInputStream(bis)) {
				PositionMapType positionMapType = PositionMapType.getPositionMapType(in.readByte());
				int treeHeight = in.readInt();
				if (treeHeight == -1) {
					return null;
				}
				int bucketSize = in.readInt();
				int blockSize = in.readInt();
				int treeSize = ORAMUtils.computeTreeSize(treeHeight, bucketSize);
				ORAMContext oramContext = new ORAMContext(positionMapType, treeHeight, treeSize, bucketSize, blockSize);
				String password = new String(response.getConfidentialData()[0]);
				encryptionManager.createSecretKey(password);

				return new ORAMObject(serviceProxy, oramId, oramContext, encryptionManager);
			}
		} catch (SecretSharingException | IOException e) {
			return null;
		}
	}

	private EncryptedStash initializeEmptyStash(int blockSize) {
		Stash stash = new Stash(blockSize);
		return encryptionManager.encryptStash(stash);
	}

	private EncryptedPathMap initializeEmptyPathMap() {
		PathMap pathMap = new PathMap(1);
		return encryptionManager.encryptPathMap(pathMap);
	}

	public void close() {
		serviceProxy.close();
	}
}
