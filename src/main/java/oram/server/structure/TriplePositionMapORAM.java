package oram.server.structure;

import oram.messages.GetPositionMap;
import oram.utils.PositionMapType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TriplePositionMapORAM extends ORAM {

	public TriplePositionMapORAM(int oramId, PositionMapType positionMapType, int garbageCollectionFrequency,
								 int treeHeight, int bucketSize, int blockSize,
								 EncryptedPositionMap encryptedPositionMap, EncryptedStash encryptedStash) {
		super(oramId, positionMapType, garbageCollectionFrequency, treeHeight, bucketSize, blockSize,
				encryptedPositionMap, encryptedStash);
	}

	public EncryptedPositionMaps getPositionMaps(int clientId, GetPositionMap request) {
		int lastVersion = request.getLastVersion();
		OutstandingTree outstandingTree = oramTreeManager.getOutstandingTree();
		Set<Integer> outstandingVersions = outstandingTree.getOutstandingVersions();
		Map<Integer, EncryptedPositionMap> resultedPositionMap = new HashMap<>(sequenceNumber - lastVersion);
		for (int i = lastVersion; i < sequenceNumber; i++) {
			EncryptedPositionMap encryptedPositionMap = positionMaps.get(i);
			if (encryptedPositionMap != null) {
				resultedPositionMap.put(i, encryptedPositionMap);
			}
		}

		int[] currentOutstandingVersions = new int[outstandingVersions.size()];
		int i = 0;
		for (int outstandingVersion : outstandingVersions) {
			currentOutstandingVersions[i] = outstandingVersion;
			i++;
		}
		int newVersionId = ++sequenceNumber;
		ORAMClientContext oramClientContext = new ORAMClientContext(currentOutstandingVersions, newVersionId,
				outstandingTree);

		oramClientContexts.put(clientId, oramClientContext);
		return new EncryptedPositionMaps(newVersionId, resultedPositionMap);
	}

	@Override
	protected void cleanPositionMaps(int[] outstandingVersions) {

	}


}
