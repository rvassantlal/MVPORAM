package oram.server.structure;

import oram.messages.GetPositionMap;
import oram.utils.PositionMapType;

import java.util.HashMap;
import java.util.Map;

public class TriplePositionMapORAM extends ORAM {

	public TriplePositionMapORAM(int oramId, PositionMapType positionMapType, int treeHeight, int bucketSize,
								 int blockSize, EncryptedPositionMap encryptedPositionMap,
								 EncryptedStash encryptedStash) {
		super(oramId, positionMapType, treeHeight, bucketSize, blockSize, encryptedPositionMap, encryptedStash);
	}

	public EncryptedPositionMaps getPositionMaps(int clientId, GetPositionMap request) {
		int lastVersion = request.getLastVersion();
		Map<Integer, EncryptedPositionMap> resultedPositionMap = new HashMap<>(sequenceNumber - lastVersion);
		for (int i = lastVersion; i < sequenceNumber; i++) {
			EncryptedPositionMap encryptedPositionMap = positionMaps.get(i);
			if (encryptedPositionMap != null) {
				resultedPositionMap.put(i, encryptedPositionMap);
			}
		}

		OramSnapshot[] currentOutstandingVersions = new OramSnapshot[outstandingTrees.size()];
		int i = 0;
		for (OramSnapshot snapshot : outstandingTrees) {
			currentOutstandingVersions[i] = snapshot;
			i++;
		}

		int newVersionId = sequenceNumber++;
		ORAMClientContext oramClientContext = new ORAMClientContext(currentOutstandingVersions, newVersionId);

		oramClientContexts.put(clientId, oramClientContext);
		return new EncryptedPositionMaps(newVersionId, resultedPositionMap);
	}


}
