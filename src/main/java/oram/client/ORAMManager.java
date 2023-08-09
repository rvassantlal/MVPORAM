package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.utils.ORAMUtils;
import oram.client.structure.PositionMap;
import oram.client.structure.Stash;
import oram.messages.CreateORAMMessage;
import oram.messages.ORAMMessage;
import oram.server.structure.EncryptedPositionMap;
import oram.server.structure.EncryptedStash;
import oram.utils.ORAMContext;
import oram.utils.ServerOperationType;
import oram.utils.Status;
import vss.facade.SecretSharingException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

public class ORAMManager {
	private final ConfidentialServiceProxy serviceProxy;
	private final EncryptionManager encryptionManager;

	public ORAMManager(int clientId) throws SecretSharingException {
		//Connecting to servers
		this.serviceProxy = new ConfidentialServiceProxy(clientId);
		this.encryptionManager = new EncryptionManager();
	}

	public ORAMObject createORAM(int oramId, int treeHeight, int bucketSize, int blockSize) {
		try {
			EncryptedPositionMap encryptedPositionMap = initializeEmptyPositionMap();
			EncryptedStash encryptedStash = initializeEmptyStash(blockSize);
			CreateORAMMessage request = new CreateORAMMessage(oramId, treeHeight, bucketSize, blockSize,
					encryptedPositionMap, encryptedStash);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.CREATE_ORAM, request);
			if (serializedRequest == null) {
				return null;
			}
			Response response = serviceProxy.invokeOrdered(serializedRequest);
			if (response == null || response.getPainData() == null) {
				return null;
			}
			Status status = Status.getStatus(response.getPainData()[0]);
			if (status == Status.FAILED) {
				return null;
			}
			int treeSize = ORAMUtils.computeNumberOfNodes(treeHeight) * bucketSize;
			ORAMContext oramContext = new ORAMContext(treeHeight, treeSize, bucketSize, blockSize);
			return new ORAMObject(serviceProxy, oramId, oramContext, encryptionManager);
		} catch (SecretSharingException e) {
			return null;
		}
	}

	public ORAMObject getORAM(int oramId) {
		try {
			ORAMMessage request = new ORAMMessage(oramId);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_ORAM, request);
			if (serializedRequest == null) {
				return null;
			}
			Response response = serviceProxy.invokeUnordered(serializedRequest);
			if (response == null || response.getPainData() == null) {
				return null;
			}

			try (ByteArrayInputStream bis = new ByteArrayInputStream(response.getPainData());
				 ObjectInputStream in = new ObjectInputStream(bis)) {
				int treeHeight = in.readInt();
				if (treeHeight == -1) {
					return null;
				}
				int bucketSize = in.readInt();
				int blockSize = in.readInt();
				int treeSize = ORAMUtils.computeNumberOfNodes(treeHeight) * bucketSize;
				ORAMContext oramContext = new ORAMContext(treeHeight, treeSize, bucketSize, blockSize);
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

	private EncryptedPositionMap initializeEmptyPositionMap() {
		int pathId = ORAMUtils.DUMMY_PATH;
		int versionId = ORAMUtils.DUMMY_VERSION;
		int address = ORAMUtils.DUMMY_ADDRESS;
		PositionMap pm = new PositionMap(versionId, pathId, address);
		return encryptionManager.encryptPositionMap(pm);
	}

	public void close() {
		serviceProxy.close();
	}
}
