package oram.server.structure;

import oram.messages.GetPositionMap;
import oram.utils.PositionMapType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FullPositionMapORAM extends ORAM {

	public FullPositionMapORAM(int oramId, PositionMapType positionMapType, int garbageCollectionFrequency,
							   int treeHeight, int bucketSize, int blockSize, EncryptedPositionMap encryptedPositionMap,
							   EncryptedStash encryptedStash) {
		super(oramId, positionMapType, garbageCollectionFrequency, treeHeight, bucketSize, blockSize,
				encryptedPositionMap, encryptedStash);
	}

	public EncryptedPositionMaps getPositionMaps(int clientId, GetPositionMap request) {
		OutstandingTree outstandingTree = oramTreeManager.getOutstandingTree();
		Set<Integer> outstandingVersions = outstandingTree.getOutstandingVersions();
		Map<Integer, EncryptedPositionMap> resultedPositionMap = new HashMap<>(outstandingVersions.size());
		int[] currentOutstandingVersions = new int[outstandingVersions.size()];
		int i = 0;
		for (int outstandingVersion : outstandingVersions) {
			currentOutstandingVersions[i] = outstandingVersion;
			resultedPositionMap.put(outstandingVersion, positionMaps.get(outstandingVersion));
			i++;
		}

		int newVersionId = ++sequenceNumber;
		ORAMClientContext oramClientContext = new ORAMClientContext(currentOutstandingVersions, newVersionId,
				outstandingTree);

		oramClientContexts.put(clientId, oramClientContext);
		return new EncryptedPositionMaps(newVersionId, resultedPositionMap);
	}

	protected void cleanPositionMaps(int[] outstandingVersions) {
		for (int outstandingVersion : outstandingVersions) {
			positionMaps.remove(outstandingVersion);
		}
	}
}
