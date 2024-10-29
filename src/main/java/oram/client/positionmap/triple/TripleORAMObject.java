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
	private final Set<Integer> missingTriples;

	public TripleORAMObject(ConfidentialServiceProxy serviceProxy, int oramId, ORAMContext oramContext,
							EncryptionManager encryptionManager) throws SecretSharingException {
		super(serviceProxy, oramId, oramContext, encryptionManager);
		this.mergedPositionMap = new PositionMap(oramContext.getTreeSize());
		this.latestSequenceNumber = 0; //server stores the initial position map and stash with version 1
		this.missingTriples = new HashSet<>();
	}

	@Override
	protected PositionMap updatePositionMap(Operation op, PositionMap mergedPositionMap, boolean isRealAccess,
											int accessedAddress, int accessedAddressNewLocation,
											int substitutedBlockAddress, int substitutedBlockNewLocation,
											int newVersionId) {
		int[] addresses = new int[] {accessedAddress, substitutedBlockAddress};
		int[] locations = new int[2];
		int[] contentVersions = new int[2];
		int[] locationVersions = new int[2];

		//updating accessed block information
		if (op == Operation.READ && isRealAccess) {
			locations[0] = accessedAddressNewLocation;
			locationVersions[0] = newVersionId;
			contentVersions[0] = mergedPositionMap.getContentVersionOf(accessedAddress);
			mergedPositionMap.setLocationOf(accessedAddress, accessedAddressNewLocation);
			mergedPositionMap.setLocationVersionOf(accessedAddress, newVersionId);
		} else if (op == Operation.WRITE) {
			locations[0] = accessedAddressNewLocation;
			locationVersions[0] = newVersionId;
			contentVersions[0] = newVersionId;
			mergedPositionMap.setLocationOf(accessedAddress, accessedAddressNewLocation);
			mergedPositionMap.setLocationVersionOf(accessedAddress, newVersionId);
			mergedPositionMap.setContentVersionOf(accessedAddress, newVersionId);
		} else {
			locations[0] = ORAMUtils.DUMMY_LOCATION;
			locationVersions[0] = ORAMUtils.DUMMY_VERSION;
			contentVersions[0] = ORAMUtils.DUMMY_VERSION;
		}

		//update substituted block information
		if (substitutedBlockAddress != ORAMUtils.DUMMY_ADDRESS) {
			locations[1] = substitutedBlockNewLocation;
			locationVersions[1] = newVersionId;
			contentVersions[1] = mergedPositionMap.getContentVersionOf(substitutedBlockAddress);
			mergedPositionMap.setLocationOf(substitutedBlockAddress, substitutedBlockNewLocation);
			mergedPositionMap.setLocationVersionOf(substitutedBlockAddress, newVersionId);
		} else {
			locations[1] = ORAMUtils.DUMMY_LOCATION;
			locationVersions[1] = ORAMUtils.DUMMY_VERSION;
			contentVersions[1] = ORAMUtils.DUMMY_VERSION;
		}

		return new PositionMap(addresses, locations, locationVersions, contentVersions);
	}


	protected PositionMaps getPositionMaps() {
		try {
			//sb.append("My last sequence number: ").append(latestSequenceNumber).append("\n");
			debugInfoBuilder.append("My last sequence number: ").append(latestSequenceNumber).append("\n");
			debugInfoBuilder.append("Missing triples: ").append(missingTriples).append("\n");
			ORAMMessage request = new GetPositionMap(oramId, latestSequenceNumber, missingTriples, latestEvictionSequenceNumber);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_POSITION_MAP, request);

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
		Map<Integer, PositionMap> positionMaps = oldPositionMaps.getPositionMaps();
		debugInfoBuilder.append("Number of position maps: ").append(positionMaps.size()).append("\n");

		int maxReceivedSequenceNumber = ORAMUtils.DUMMY_VERSION;
		for (Map.Entry<Integer, PositionMap> entry : positionMaps.entrySet()) {
			maxReceivedSequenceNumber = Math.max(maxReceivedSequenceNumber, entry.getKey());
			missingTriples.remove(entry.getKey());

			PositionMap currentPM = entry.getValue();

			//apply changes of modified block
			int accessedBlockAddress = currentPM.getAddress()[0];
			int accessedBlockLocation = currentPM.getLocationOf(accessedBlockAddress);
			int accessedBlockContentVersion = currentPM.getContentVersionOf(accessedBlockAddress);
			int accessedBlockLocationVersion = currentPM.getLocationVersionOf(accessedBlockAddress);

			if (accessedBlockAddress != ORAMUtils.DUMMY_ADDRESS
					&& accessedBlockLocation != ORAMUtils.DUMMY_LOCATION) {
				if (accessedBlockContentVersion > mergedPositionMap.getContentVersionOf(accessedBlockAddress)) { //if the block was recently written
					mergedPositionMap.setLocationOf(accessedBlockAddress, accessedBlockLocation);
					mergedPositionMap.setContentVersionOf(accessedBlockAddress, accessedBlockContentVersion);
					mergedPositionMap.setLocationVersionOf(accessedBlockAddress, accessedBlockLocationVersion);
				} else if (accessedBlockContentVersion == mergedPositionMap.getContentVersionOf(accessedBlockAddress)
						&& accessedBlockLocationVersion > mergedPositionMap.getLocationVersionOf(accessedBlockAddress)) { //if the block was recently read
					mergedPositionMap.setLocationOf(accessedBlockAddress, accessedBlockLocation);
					mergedPositionMap.setLocationVersionOf(accessedBlockAddress, accessedBlockLocationVersion);
				}
			}

			int substitutedBlockAddress = currentPM.getAddress()[1];
			int substitutedBlockLocation = currentPM.getLocationOf(substitutedBlockAddress);
			int substitutedBlockContentVersion = currentPM.getContentVersionOf(substitutedBlockAddress);
			int substitutedBlockLocationVersion = currentPM.getLocationVersionOf(substitutedBlockAddress);

			/*debugInfoBuilder.append("\t").append(" -> modified: ")
					.append(accessedBlockAddress).append(" ")
					.append(accessedBlockLocation).append(" ")
					.append(currentPM.getContentVersionOf(accessedBlockAddress)).append(" ")
					.append(currentPM.getLocationVersionOf(accessedBlockAddress))
					.append("\t\tsubstituted: ")
					.append(substitutedBlockAddress).append(" ")
					.append(substitutedBlockLocation).append(" ")
					.append(substitutedBlockContentVersion).append(" ")
					.append(substitutedBlockLocationVersion).append("\n");*/
		}

		for (PositionMap currentPM : positionMaps.values()) {
			//apply changes of substituted block
			int substitutedBlockAddress = currentPM.getAddress()[1];
			int substitutedBlockLocation = currentPM.getLocationOf(substitutedBlockAddress);
			int substitutedBlockContentVersion = currentPM.getContentVersionOf(substitutedBlockAddress);
			int substitutedBlockLocationVersion = currentPM.getLocationVersionOf(substitutedBlockAddress);

			if (substitutedBlockAddress != ORAMUtils.DUMMY_ADDRESS
					&& substitutedBlockLocation != ORAMUtils.DUMMY_LOCATION
					&& substitutedBlockContentVersion == mergedPositionMap.getContentVersionOf(substitutedBlockAddress)
					&& substitutedBlockLocationVersion >= mergedPositionMap.getLocationVersionOf(substitutedBlockAddress)) {
				mergedPositionMap.setLocationOf(substitutedBlockAddress, substitutedBlockLocation);
				mergedPositionMap.setLocationVersionOf(substitutedBlockAddress, substitutedBlockLocationVersion);
			}
		}

		for (int i = latestSequenceNumber + 1; i < maxReceivedSequenceNumber; i++) {
			if (!positionMaps.containsKey(i)) {
				missingTriples.add(i);
			}
		}
		latestSequenceNumber = maxReceivedSequenceNumber;
		debugInfoBuilder.append("Latest sequence number: ").append(latestSequenceNumber).append("\n");

		return mergedPositionMap;
	}
}
