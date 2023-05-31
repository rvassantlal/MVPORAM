package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.ORAMUtils;
import oram.client.structure.PositionMap;
import oram.messages.ORAMMessage;
import utils.Operation;
import vss.facade.SecretSharingException;

import java.util.HashMap;

public class ORAMObject {
	private final ConfidentialServiceProxy serviceProxy;
	private final int oramId;
	private final int treeHeight;
	private final int nBlocks;
	private final EncryptionManager encryptionManager;

	public ORAMObject(ConfidentialServiceProxy serviceProxy, int oramId, int treeHeight, EncryptionManager encryptionManager) throws SecretSharingException {
		this.serviceProxy = serviceProxy;
		this.oramId = oramId;
		this.treeHeight = treeHeight;
		this.encryptionManager = encryptionManager;
		this.nBlocks = ORAMUtils.computeNumberOfNodes(treeHeight);
	}

	/**
	 * Read the memory position.
	 * @param position Memory position.
	 * @return Content located at the memory position.
	 */
	public byte[] readMemory(int position) {
		//TODO check if position is inside the limits
		throw new UnsupportedOperationException();
	}

	/**
	 * Write content to the memory position.
	 * @param position Memory position.
	 * @param content Content to write.
	 * @return Old content located at the memory position.
	 */
	public byte[] writeMemory(int position, byte[] content) {
		//TODO check if position is inside the limits
		throw new UnsupportedOperationException();
	}

	private byte[] access(Operation op, int position, byte[] newContent) {
		//TODO does it make sense calling PM everytime before calling eviction?
		PositionMap[] positionMaps = getPositionMaps();

		HashMap<Double, Integer> pathIds = locatePaths(positionMaps, position);

		return null;
	}

	private HashMap<Double, Integer> locatePaths(PositionMap[] positionMaps, int position) {
		return null;
	}

	private PositionMap[] getPositionMaps() {
		try {
			ORAMMessage request = new ORAMMessage(oramId);
			byte[] serializedRequest = ORAMUtils.serializeRequest(Operation.GET_POSITION_MAP, request);
			if (serializedRequest == null) {
				return null;
			}
			Response response = serviceProxy.invokeOrdered(serializedRequest);
			if (response == null || response.getPainData() == null)
				return null;
			return encryptionManager.decryptPositionMaps(response.getPainData());
		} catch (SecretSharingException e) {
			return null;
		}
	}
}
