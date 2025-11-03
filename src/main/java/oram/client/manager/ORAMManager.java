package oram.client.manager;

import confidential.client.Response;
import oram.client.ORAMObject;
import oram.client.ORAMServiceProxy;
import oram.client.structure.PathMap;
import oram.client.structure.Stash;
import oram.messages.CreateORAMMessage;
import oram.messages.ORAMMessage;
import oram.security.EncryptionManager;
import oram.server.structure.EncryptedPathMap;
import oram.server.structure.EncryptedStash;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import oram.utils.ServerOperationType;
import oram.utils.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public abstract class ORAMManager implements ORAMServiceProxy {
	protected final Logger logger = LoggerFactory.getLogger("oram");
	protected final EncryptionManager encryptionManager;

	public ORAMManager() {
		this.encryptionManager = new EncryptionManager();
	}

	public ORAMObject createORAM(int oramId, int treeHeight, int bucketSize, int blockSize) {
		String password = generatePassword();
		encryptionManager.createSecretKey(password);

		EncryptedPathMap encryptedPathMap = initializeEmptyPathMap();
		EncryptedStash encryptedStash = initializeEmptyStash(blockSize);
		CreateORAMMessage request = new CreateORAMMessage(oramId, treeHeight, bucketSize, blockSize,
				encryptedPathMap, encryptedStash);
		byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.CREATE_ORAM, request);

		Status status = createORAM(serializedRequest, password);
		if (status == Status.FAILED) {
			return null;
		}

		ORAMContext oramContext = new ORAMContext(treeHeight, bucketSize, blockSize);

		return new ORAMObject(this, oramId, oramContext, encryptionManager);
	}

	public ORAMObject getORAM(int oramId) {
		ORAMMessage request = new ORAMMessage(oramId);
		byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_ORAM, request);

		Response contextResponse = getORAMContext(serializedRequest);
		if (contextResponse == null || contextResponse.getPlainData() == null || contextResponse.getConfidentialData() == null) {
			return null;
		}

		ORAMContext oramContext = deserializeORAMContext(contextResponse.getPlainData());
		if (oramContext == null) {
			return null;
		}

		String password = new String(contextResponse.getConfidentialData()[0]);
		encryptionManager.createSecretKey(password);

		return new ORAMObject(this, oramId, oramContext, encryptionManager);
	}

	protected abstract Status createORAM(byte[] serializedRequest, String password);

	protected abstract String generatePassword();

	protected abstract Response getORAMContext(byte[] serializedRequest);

	public abstract void close();

	private EncryptedStash initializeEmptyStash(int blockSize) {
		Stash stash = new Stash(blockSize);
		return encryptionManager.encryptStash(stash);
	}

	private EncryptedPathMap initializeEmptyPathMap() {
		PathMap pathMap = new PathMap(1);
		return encryptionManager.encryptPathMap(pathMap);
	}

	private ORAMContext deserializeORAMContext(byte[] serializedORAMContext) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedORAMContext);
			 DataInputStream in = new DataInputStream(bis)) {
			int treeHeight = in.readInt();
			if (treeHeight == -1) {
				return null;
			}
			int bucketSize = in.readInt();
			int blockSize = in.readInt();
			return new ORAMContext(treeHeight, bucketSize, blockSize);
		} catch (IOException e) {
			logger.error("Error deserializing ORAMContext", e);
			return null;
		}
	}
}
