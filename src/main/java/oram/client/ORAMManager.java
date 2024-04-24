package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.client.positionmap.full.FullORAMObject;
import oram.client.positionmap.triple.TripleORAMObject;
import oram.client.structure.PositionMap;
import oram.client.structure.Stash;
import oram.messages.CreateORAMMessage;
import oram.messages.ORAMMessage;
import oram.security.EncryptionManager;
import oram.server.structure.EncryptedPositionMap;
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

	public ORAMObject createORAM(int oramId, PositionMapType positionMapType, int garbageCollectionFrequency,
								 int treeHeight, int bucketSize, int blockSize) {
		try {
			String password = encryptionManager.generatePassword();
			encryptionManager.createSecretKey(password);

			EncryptedPositionMap encryptedPositionMap;
			if (positionMapType == PositionMapType.FULL_POSITION_MAP)
				encryptedPositionMap = initializeEmptyFullPositionMap();
			else
				encryptedPositionMap = initializeEmptyTriplePositionMap();
			EncryptedStash encryptedStash = initializeEmptyStash(blockSize);
			CreateORAMMessage request = new CreateORAMMessage(oramId, positionMapType, garbageCollectionFrequency,
					treeHeight, bucketSize, blockSize, encryptedPositionMap, encryptedStash);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.CREATE_ORAM, request);
			if (serializedRequest == null) {
				return null;
			}
			Response response = serviceProxy.invokeOrdered(serializedRequest, password.getBytes());
			if (response == null || response.getPlainData() == null) {
				return null;
			}

			Status status = Status.getStatus(response.getPlainData()[0]);
			if (status == Status.FAILED) {
				return null;
			}
			int treeSize = ORAMUtils.computeTreeSize(treeHeight, bucketSize);
			ORAMContext oramContext = new ORAMContext(positionMapType, garbageCollectionFrequency, treeHeight,
					treeSize, bucketSize, blockSize);
			if (positionMapType == PositionMapType.FULL_POSITION_MAP)
				return new FullORAMObject(serviceProxy, oramId, oramContext, encryptionManager);
			else
				return new TripleORAMObject(serviceProxy, oramId, oramContext, encryptionManager);
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
			if (response == null || response.getPlainData() == null || response.getConfidentialData() == null) {
				return null;
			}

			try (ByteArrayInputStream bis = new ByteArrayInputStream(response.getPlainData());
				 DataInputStream in = new DataInputStream(bis)) {
				PositionMapType positionMapType = PositionMapType.getPositionMapType(in.readByte());
				int garbageCollectionFrequency = in.readInt();
				int treeHeight = in.readInt();
				if (treeHeight == -1) {
					return null;
				}
				int bucketSize = in.readInt();
				int blockSize = in.readInt();
				int treeSize = ORAMUtils.computeTreeSize(treeHeight, bucketSize);
				ORAMContext oramContext = new ORAMContext(positionMapType, garbageCollectionFrequency, treeHeight,
						treeSize, bucketSize, blockSize);
				String password = new String(response.getConfidentialData()[0]);
				encryptionManager.createSecretKey(password);
				if (oramContext.getPositionMapType() == PositionMapType.FULL_POSITION_MAP)
					return new FullORAMObject(serviceProxy, oramId, oramContext, encryptionManager);
				else
					return new TripleORAMObject(serviceProxy, oramId, oramContext, encryptionManager);
			}
		} catch (SecretSharingException | IOException e) {
			return null;
		}
	}

	private EncryptedStash initializeEmptyStash(int blockSize) {
		Stash stash = new Stash(blockSize);
		return encryptionManager.encryptStash(stash);
	}

	private EncryptedPositionMap initializeEmptyFullPositionMap() {
		int[] positionMap = new int[0];
		int[] versionIds = new int[0];
		PositionMap pm = new PositionMap(versionIds, positionMap);
		return encryptionManager.encryptPositionMap(pm);
	}

	private EncryptedPositionMap initializeEmptyTriplePositionMap() {
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
