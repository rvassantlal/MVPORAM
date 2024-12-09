package oram.client.positionmap.triple;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.client.ORAMObject;
import oram.client.structure.PathMap;
import oram.client.structure.PositionMap;
import oram.client.structure.PositionMaps;
import oram.messages.GetPositionMap;
import oram.messages.ORAMMessage;
import oram.security.EncryptionManager;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
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

	protected PositionMaps getPositionMaps() {
		try {
			ORAMMessage request = new GetPositionMap(oramId, latestSequenceNumber, missingTriples);
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
	protected PositionMap consolidatePathMaps(PositionMaps oldPositionMaps) {
		Map<Integer, PathMap> pathMaps = oldPositionMaps.getPathMaps();
		localPathMapHistory.putAll(pathMaps);

		Map<Integer, int[]> allOutstandingVersions = oldPositionMaps.getAllOutstandingVersions();
		int[] outstandingVersions = oldPositionMaps.getOutstandingVersions();
		int newVersionId = oldPositionMaps.getNewVersionId();
		allOutstandingVersions.put(newVersionId, outstandingVersions);
		outstandingGraph.addOutstandingVersions(allOutstandingVersions);


		Set<Integer> updatedAddresses = new HashSet<>();
		int maxReceivedSequenceNumber = ORAMUtils.DUMMY_VERSION;
		for (Map.Entry<Integer, PathMap> entry : pathMaps.entrySet()) {
			maxReceivedSequenceNumber = Math.max(maxReceivedSequenceNumber, entry.getKey());
			missingTriples.remove(entry.getKey());

			PathMap currentPM = entry.getValue();
			updatedAddresses.addAll(currentPM.getStoredAddresses());
		}

		for (int i = latestSequenceNumber + 1; i < maxReceivedSequenceNumber; i++) {
			if (!pathMaps.containsKey(i)) {
				missingTriples.add(i);
			}
		}
		latestSequenceNumber = maxReceivedSequenceNumber;

		return updatePositionMap(updatedAddresses, pathMaps);
	}

	private PositionMap updatePositionMap(Set<Integer> updatedAddresses, Map<Integer, PathMap> pathMaps) {
		debugInformation.append("Updated addresses: ").append(updatedAddresses).append("\n");
		for (int updatedAddress : updatedAddresses) {
			int positionMapLocation = mergedPositionMap.getLocationOf(updatedAddress);
			int positionMapWriteVersion = mergedPositionMap.getWriteVersionOf(updatedAddress);
			int positionMapAccessVersion = mergedPositionMap.getAccessVersionOf(updatedAddress);
			int positionMapLocationUpdateVersion = mergedPositionMap.getLocationUpdateVersion(updatedAddress);
			debugInformation.append("\tUpdated address: ").append(updatedAddress)
					.append(" (L: ").append(positionMapLocation)
					.append(" WV: ").append(positionMapWriteVersion)
					.append(" AV: ").append(positionMapAccessVersion)
					.append(" LUV: ").append(positionMapLocationUpdateVersion).append(")\n");
			for (Map.Entry<Integer, PathMap> entry : pathMaps.entrySet()) {
				int pathMapLocationUpdateVersion = entry.getKey();
				PathMap pathMap = entry.getValue();
				int pathMapLocation = pathMap.getLocationOf(updatedAddress);
				int pathMapWriteVersion = pathMap.getWriteVersionOf(updatedAddress);
				int pathMapAccessVersion = pathMap.getAccessVersionOf(updatedAddress);
				if (pathMapAccessVersion == ORAMUtils.DUMMY_VERSION) {
					continue;
				}
				debugInformation.append("\t\tUpdated op sequence: ").append(pathMapLocationUpdateVersion)
						.append(" (L: ").append(pathMapLocation)
						.append(" WV: ").append(pathMapWriteVersion)
						.append(" AV: ").append(pathMapAccessVersion)
						.append(" LUV: ").append(pathMapLocationUpdateVersion).append(")\n");
				if (pathMapWriteVersion > positionMapWriteVersion ||
						(pathMapWriteVersion == positionMapWriteVersion && pathMapAccessVersion > positionMapAccessVersion)) {
					positionMapLocation = pathMapLocation;
					positionMapWriteVersion = pathMapWriteVersion;
					positionMapAccessVersion = pathMapAccessVersion;
					positionMapLocationUpdateVersion = pathMapLocationUpdateVersion;
				} else if (pathMapWriteVersion == positionMapWriteVersion && pathMapAccessVersion == positionMapAccessVersion)  {
					if (pathMapLocationUpdateVersion > positionMapLocationUpdateVersion) {
						positionMapLocation = pathMapLocation;
						positionMapLocationUpdateVersion = pathMapLocationUpdateVersion;
					}
					/*if (outstandingGraph.doesOverrides(pathMapLocationUpdateVersion, positionMapLocationUpdateVersion)) {
						positionMapLocationUpdateVersion = pathMapLocationUpdateVersion;
						positionMapLocation = pathMapLocation;
					} else if (!outstandingGraph.doesOverrides(positionMapLocationUpdateVersion, pathMapLocationUpdateVersion)) {//concurrent version
						//TODO have to compute and compare heights
						if (pathMapLocation > positionMapLocation || (pathMapLocation == positionMapLocation && pathMapLocationUpdateVersion > positionMapLocationUpdateVersion)) {
							positionMapLocationUpdateVersion = pathMapLocationUpdateVersion;
							positionMapLocation = pathMapLocation;
						}
					}*/
				}
			}

			mergedPositionMap.setLocationOf(updatedAddress, positionMapLocation);
			mergedPositionMap.setWriteVersionOf(updatedAddress, positionMapWriteVersion);
			mergedPositionMap.setAccessVersionOf(updatedAddress, positionMapAccessVersion);
			mergedPositionMap.setLocationUpdateVersions(updatedAddress, positionMapLocationUpdateVersion);
		}

		return mergedPositionMap;
	}
}
