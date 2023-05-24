package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.ORAMUtils;
import oram.client.structure.PositionMap;
import oram.messages.CreateORAMMessage;
import oram.messages.ORAMMessage;
import oram.server.structure.EncryptedPositionMap;
import utils.Operation;
import utils.Status;
import utils.Utils;
import vss.facade.SecretSharingException;

import java.security.SecureRandom;
import java.util.Arrays;

public class ORAMManager {
	private final ConfidentialServiceProxy serviceProxy;
	private final EncryptionManager encryptionManager;
	private final SecureRandom rndGenerator;

	public ORAMManager(int clientId) throws SecretSharingException {
		//Connecting to servers
		this.serviceProxy = new ConfidentialServiceProxy(clientId);
		this.encryptionManager = new EncryptionManager();
		this.rndGenerator = new SecureRandom("oram".getBytes());
	}

	public ORAMObject createORAM(int oramId, int treeHeight, int nBlocksPerBucket) {
		try {
			int nBuckets = ORAMUtils.computeNumberOfNodes(treeHeight);
			int totalNumberBlocks = nBuckets * nBlocksPerBucket;
			EncryptedPositionMap encryptedPositionMap = initializeDummyPositionMap(totalNumberBlocks, treeHeight);

			CreateORAMMessage request = new CreateORAMMessage(oramId, treeHeight);
			byte[] serializedRequest = ORAMUtils.serializeRequest(Operation.CREATE_ORAM, request);
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
			return new ORAMObject(serviceProxy, oramId, treeHeight, encryptionManager);
		} catch (SecretSharingException e) {
			return null;
		}
	}

	private EncryptedPositionMap initializeDummyPositionMap(int totalNumberBlocks, int nPaths) {
		int[] positionMap = new int[totalNumberBlocks];
		Arrays.fill(positionMap, ORAMUtils.DUMMY_PATH);
		double versionId = 0;//TODO initialize
		PositionMap pm = new PositionMap(versionId, positionMap);
		return encryptionManager.encryptPositionMap(pm);
	}

	public ORAMObject getORAM(int oramId) {
		try {
			ORAMMessage request = new ORAMMessage(oramId);
			byte[] serializedRequest = ORAMUtils.serializeRequest(Operation.GET_ORAM, request);
			if (serializedRequest == null) {
				return null;
			}
			Response response = serviceProxy.invokeUnordered(serializedRequest);
			if (response == null || response.getPainData() == null) {
				return null;
			}
			int treeHeight = Utils.toNumber(response.getPainData());
			if (treeHeight == -1) {
				return null;
			}
			return new ORAMObject(serviceProxy, oramId, treeHeight, encryptionManager);
		} catch (SecretSharingException e) {
			return null;
		}
	}

	public void close() {
		serviceProxy.close();
	}
}
