package oram.client.positionmap.triple;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.security.EncryptionManager;
import oram.client.ORAMObject;
import oram.client.structure.PositionMap;
import oram.client.structure.PositionMaps;
import oram.messages.GetPositionMap;
import oram.messages.ORAMMessage;
import oram.server.structure.EncryptedPositionMap;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import oram.utils.Operation;
import oram.utils.ServerOperationType;
import vss.facade.SecretSharingException;

import java.util.Map;

public class TripleORAMObject extends ORAMObject {
	private final PositionMap mergedPositionMap;
	private int latestSequenceNumber;

	public TripleORAMObject(ConfidentialServiceProxy serviceProxy, int oramId, ORAMContext oramContext,
							EncryptionManager encryptionManager) throws SecretSharingException {
		super(serviceProxy, oramId, oramContext, encryptionManager);
		this.mergedPositionMap = new PositionMap(oramContext.getTreeSize());
		this.latestSequenceNumber = ORAMUtils.DUMMY_VERSION;
	}

	@Override
	protected PositionMap updatePositionMap(Operation op, PositionMap mergedPositionMap, boolean isRealAccess,
											int address, int newPathId, int newVersionId) {
		int versionId;
		int pathId;
		if (isRealAccess) {
			pathId = newPathId;
			mergedPositionMap.setPathAt(address, newPathId);
		} else {
			pathId = ORAMUtils.DUMMY_PATH;
		}

		if (op == Operation.WRITE) {
			versionId = newVersionId;
			mergedPositionMap.setVersionIdAt(address, newVersionId);
		} else {
			versionId = mergedPositionMap.getVersionIdAt(address);
		}

		return new PositionMap(versionId, pathId, address);
	}


	protected PositionMaps getPositionMaps() {
		try {
			ORAMMessage request = new GetPositionMap(oramId, latestSequenceNumber);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_POSITION_MAP, request);
			if (serializedRequest == null) {
				return null;
			}
			Response response = serviceProxy.invokeOrdered(serializedRequest);
			if (response == null || response.getPlainData() == null)
				return null;
			return encryptionManager.decryptPositionMaps(response.getPlainData());
		} catch (SecretSharingException e) {
			logger.error("Error while decrypting position maps", e);
			return null;
		}
	}

	@Override
	protected PositionMap mergePositionMaps(PositionMaps oldPositionMaps) {
		int highestSequenceNumber = oldPositionMaps.getNewVersionId();
		Map<Integer, PositionMap> positionMaps = oldPositionMaps.getPositionMaps();

		int firstSequenceNumber = latestSequenceNumber;
		int oldSequenceNumber = latestSequenceNumber;
		int j = 0;
		for (int i = firstSequenceNumber; i < highestSequenceNumber; i++) {
			PositionMap currentPM = positionMaps.get(i);
			if (currentPM == null && oldSequenceNumber == firstSequenceNumber){
				oldSequenceNumber += j;
				latestSequenceNumber = oldSequenceNumber;
			} else if (currentPM != null) {
				int address = currentPM.getAddress();
				if(address != ORAMUtils.DUMMY_ADDRESS && currentPM.getVersionIdAt(address) >=
						mergedPositionMap.getVersionIdAt(address)) {
					mergedPositionMap.setPathAt(address, currentPM.getPathAt(address));
					mergedPositionMap.setVersionIdAt(address, currentPM.getVersionIdAt(address));
				}
			}
			j++;
		}
		if(firstSequenceNumber == latestSequenceNumber){
			latestSequenceNumber = highestSequenceNumber;
		}
		return mergedPositionMap;
	}
}
