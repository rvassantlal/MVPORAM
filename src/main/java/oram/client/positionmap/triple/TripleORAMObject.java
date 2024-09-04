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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TripleORAMObject extends ORAMObject {
	private final PositionMap mergedPositionMap;
	private int latestSequenceNumber;

	public TripleORAMObject(ConfidentialServiceProxy serviceProxy, int oramId, ORAMContext oramContext,
							EncryptionManager encryptionManager) throws SecretSharingException {
		super(serviceProxy, oramId, oramContext, encryptionManager);
		this.mergedPositionMap = new PositionMap(oramContext.getTreeSize());
		this.latestSequenceNumber = 0; //server stores the initial position map and stash with version 1
	}

	@Override
	protected PositionMap updatePositionMap(Operation op, PositionMap mergedPositionMap, boolean isRealAccess,
											int accessedAddress, int accessedAddressNewLocation,
											int substitutedBlockAddress, int substitutedBlockNewLocation,
											int newVersionId) {
		int[] addresses = new int[] {accessedAddress, substitutedBlockAddress};
		int[] blockModificationVersions;
		int[] versionId;
		int[] pathId;
		if (isRealAccess || op == Operation.WRITE) {
			pathId = new int[] {accessedAddressNewLocation, substitutedBlockNewLocation};
			mergedPositionMap.setPathAt(accessedAddress, accessedAddressNewLocation);
			mergedPositionMap.setPathAt(substitutedBlockAddress, substitutedBlockNewLocation);
			versionId = new int[] {newVersionId, newVersionId};
			mergedPositionMap.setVersionIdAt(accessedAddress, newVersionId);
			mergedPositionMap.setVersionIdAt(substitutedBlockAddress, newVersionId);
		} else {
			pathId = new int[] {ORAMUtils.DUMMY_LOCATION, ORAMUtils.DUMMY_LOCATION};
			versionId = new int[] {ORAMUtils.DUMMY_VERSION, ORAMUtils.DUMMY_VERSION};
		}

		if (op == Operation.WRITE) {
			blockModificationVersions = new int[] {newVersionId,
					mergedPositionMap.getBlockModificationVersionAt(substitutedBlockAddress)};
			mergedPositionMap.setBlockModificationVersionAt(accessedAddress, newVersionId);
		} else {
			blockModificationVersions = new int[] {
					mergedPositionMap.getBlockModificationVersionAt(accessedAddress),
					mergedPositionMap.getBlockModificationVersionAt(substitutedBlockAddress)
			};
		}

		return new PositionMap(versionId, pathId, addresses, blockModificationVersions);
	}


	protected PositionMaps getPositionMaps() {
		try {
			//sb.append("My last sequence number: ").append(latestSequenceNumber).append("\n");
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

		//sb.append("Number of position maps: ").append(positionMaps.size()).append("\n");
		//sb.append("Modified addresses: ").append(modifiedAddresses).append("\n");

		for (int i = latestSequenceNumber + 1; i < highestSequenceNumber; i++) {
			if(i == ORAMUtils.DUMMY_VERSION)
				continue;
			PositionMap currentPM = positionMaps.get(i);
			if (currentPM == null) {
				notNull = false;
			} else {
				//apply changes of modified block
				int modifiedBlockAddress = currentPM.getAddress()[0];
				int modifiedBlockLocation = currentPM.getPathAt(modifiedBlockAddress);
				if (modifiedBlockAddress != ORAMUtils.DUMMY_ADDRESS
						&& modifiedBlockLocation != ORAMUtils.DUMMY_LOCATION) {
					if (currentPM.getBlockModificationVersionAt(modifiedBlockAddress)
							> mergedPositionMap.getBlockModificationVersionAt(modifiedBlockAddress)) { //if the block was recently written
						mergedPositionMap.setPathAt(modifiedBlockAddress, modifiedBlockLocation);
						mergedPositionMap.setBlockModificationVersionAt(modifiedBlockAddress,
								currentPM.getBlockModificationVersionAt(modifiedBlockAddress));
						mergedPositionMap.setVersionIdAt(modifiedBlockAddress,
								currentPM.getVersionIdAt(modifiedBlockAddress));
					} else if (currentPM.getBlockModificationVersionAt(modifiedBlockAddress)
							== mergedPositionMap.getBlockModificationVersionAt(modifiedBlockAddress)
							&& currentPM.getVersionIdAt(modifiedBlockAddress)
							> mergedPositionMap.getVersionIdAt(modifiedBlockAddress)) { //if the block was recently read
						mergedPositionMap.setPathAt(modifiedBlockAddress, modifiedBlockLocation);
						mergedPositionMap.setVersionIdAt(modifiedBlockAddress, currentPM.getVersionIdAt(modifiedBlockAddress));
					}
				}

				/*int substitutedBlockAddress = currentPM.getAddress()[1];
				int substitutedBlockLocation = currentPM.getPathAt(substitutedBlockAddress);
				sb.append("\t").append(i).append(" -> modified: ")
						.append(modifiedBlockAddress).append(" ")
						.append(modifiedBlockLocation).append(" ")
						.append(currentPM.getBlockModificationVersionAt(modifiedBlockAddress)).append(" ")
						.append(currentPM.getVersionIdAt(modifiedBlockAddress))
						.append("\t\tsubstituted: ")
						.append(substitutedBlockAddress).append(" ")
						.append(substitutedBlockLocation).append(" ")
						.append(currentPM.getBlockModificationVersionAt(substitutedBlockAddress)).append(" ")
						.append(currentPM.getVersionIdAt(substitutedBlockAddress)).append("\n");*/
			}
			if (notNull) {
				latestSequenceNumber = i;
			}
		}

		for (PositionMap currentPM : positionMaps.values()) {
			//apply changes of substituted block
			int substitutedBlockAddress = currentPM.getAddress()[1];
			int substitutedBlockLocation = currentPM.getPathAt(substitutedBlockAddress);
			if (substitutedBlockAddress != ORAMUtils.DUMMY_ADDRESS
					&& substitutedBlockLocation != ORAMUtils.DUMMY_LOCATION) {
				if (currentPM.getBlockModificationVersionAt(substitutedBlockAddress)
						== mergedPositionMap.getBlockModificationVersionAt(substitutedBlockAddress)
						&& currentPM.getVersionIdAt(substitutedBlockAddress)
						>= mergedPositionMap.getVersionIdAt(substitutedBlockAddress)) {
					mergedPositionMap.setPathAt(substitutedBlockAddress, substitutedBlockLocation);
					mergedPositionMap.setVersionIdAt(substitutedBlockAddress, currentPM.getVersionIdAt(substitutedBlockAddress));
				}
			}
		}

		return mergedPositionMap;
	}
}
