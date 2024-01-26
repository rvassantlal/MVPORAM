package oram.client.positionmap.triple;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.client.ORAMObject;
import oram.client.structure.PositionMap;
import oram.client.structure.PositionMaps;
import oram.messages.GetPositionMap;
import oram.messages.ORAMMessage;
import oram.security.EncryptionManager;
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
		this.latestSequenceNumber = 1; //server stores the initial position map and stash with version 1
	}

	@Override
	protected PositionMap updatePositionMap(Operation op, PositionMap mergedPositionMap, boolean isRealAccess,
											int address, int newPathId, int newVersionId) {
		int versionId;
		int pathId;
		if (isRealAccess || op == Operation.WRITE) {
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

			long start, end, delay;
			start = System.nanoTime();
			Response response = serviceProxy.invokeOrderedHashed(serializedRequest);
			end = System.nanoTime();
			if (response == null || response.getPlainData() == null) {
				return null;
			}

			delay = end - start;
			globalDelayRemoteInvocation += delay;
			measurementLogger.info("M-getPM: {}", delay);

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
		boolean notNull = true;
		for (int i = latestSequenceNumber; i < highestSequenceNumber; i++) {
			if(i == ORAMUtils.DUMMY_VERSION)
				continue;
			PositionMap currentPM = positionMaps.get(i);
			if (currentPM == null) {
				notNull = false;
			} else {
				int address = currentPM.getAddress();
				if(address != ORAMUtils.DUMMY_ADDRESS && currentPM.getVersionIdAt(address) >=
						mergedPositionMap.getVersionIdAt(address)) {
					mergedPositionMap.setPathAt(address, currentPM.getPathAt(address));
					mergedPositionMap.setVersionIdAt(address, currentPM.getVersionIdAt(address));
				}
			}
			if (notNull) {
				latestSequenceNumber = i;
			}
		}
		return mergedPositionMap;
	}
}
