package oram.client.positionmap.full;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.messages.GetPositionMap;
import oram.security.EncryptionManager;
import oram.client.ORAMObject;
import oram.client.structure.PositionMap;
import oram.client.structure.PositionMaps;
import oram.messages.ORAMMessage;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import oram.utils.Operation;
import oram.utils.ServerOperationType;
import vss.facade.SecretSharingException;

public class FullORAMObject extends ORAMObject {
	public FullORAMObject(ConfidentialServiceProxy serviceProxy, int oramId, ORAMContext oramContext,
						  EncryptionManager encryptionManager) throws SecretSharingException {
		super(serviceProxy, oramId, oramContext, encryptionManager);
	}

	@Override
	protected PositionMap updatePositionMap(Operation op, PositionMap mergedPositionMap, boolean isRealAccess,
											int accessedAddress, int accessedAddressNewLocation,
											int substitutedBlockAddress, int substitutedBlockNewLocation,
											int newVersionId) {
		if (op == Operation.WRITE || (op == Operation.READ && isRealAccess)) {
			mergedPositionMap.setLocationOf(accessedAddress, accessedAddressNewLocation);
			mergedPositionMap.setLocationOf(substitutedBlockAddress, substitutedBlockNewLocation);
			mergedPositionMap.setLocationVersionOf(accessedAddress, newVersionId);
			mergedPositionMap.setLocationVersionOf(substitutedBlockAddress, newVersionId);
		}
		return mergedPositionMap;
	}

	protected PositionMaps getPositionMaps() {
		try {
			ORAMMessage request = new GetPositionMap(oramId, -1, null, -1);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_POSITION_MAP, request);

			long start, end, delay;
			start = System.nanoTime();
			Response response = serviceProxy.invokeOrderedHashed(serializedRequest);
			end = System.nanoTime();
			delay = end - start;
			measurementLogger.info("M-getPM: {}", delay);

			if (response == null || response.getPlainData() == null)
				return null;
			return encryptionManager.decryptPositionMaps(response.getPlainData());
		} catch (SecretSharingException e) {
			logger.error("Error while decrypting position maps", e);
			return null;
		}
	}

	protected PositionMap mergePositionMaps(PositionMaps oldPositionMaps) {
		int treeSize = oramContext.getTreeSize();
		int[] pathIds = new int[treeSize];
		int[] versionIds = new int[treeSize];

		for (int address = 0; address < treeSize; address++) {
			int recentPathId = ORAMUtils.DUMMY_LOCATION;
			int recentVersionId = ORAMUtils.DUMMY_VERSION;
			for (PositionMap positionMap : oldPositionMaps.getPositionMaps().values()) {
				if (positionMap.getLocations().length == 0)
					continue;
				int pathId = positionMap.getLocationOf(address);
				int versionId = positionMap.getLocationVersionOf(address);
				if (versionId > recentVersionId) {
					recentVersionId = versionId;
					recentPathId = pathId;
				}
			}
			pathIds[address] = recentPathId;
			versionIds[address] = recentVersionId;
		}
		return new PositionMap(pathIds, versionIds);
	}
}
