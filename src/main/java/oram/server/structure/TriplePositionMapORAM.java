package oram.server.structure;

import oram.messages.GetPositionMap;
import oram.utils.PositionMapType;

import java.util.ArrayList;
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
		Map<Integer, EncryptedPositionMap> resultedPositionMap = new HashMap<>(sequenceNumber - lastVersion);
		for (int i = lastVersion; i < sequenceNumber; i++) {
			EncryptedPositionMap encryptedPositionMap = positionMaps.get(i);
			if (encryptedPositionMap != null) {
				resultedPositionMap.put(i, encryptedPositionMap);
			}
		}

		int[] currentOutstandingVersions = new int[outstandingTrees.size()];
		EncryptedStash[] currentOutstandingStashes = new EncryptedStash[outstandingTrees.size()];
		int i = 0;
		for (int outstandingVersion : outstandingTrees) {
			currentOutstandingVersions[i] = outstandingVersion;
			currentOutstandingStashes[i] = stashes.get(outstandingVersion);
			i++;
		}
		OutstandingTreeContext outstandingTree = oramTree.getOutstandingBucketsVersions();
		int newVersionId = ++sequenceNumber;
		ORAMClientContext oramClientContext = new ORAMClientContext(currentOutstandingVersions,
				currentOutstandingStashes, newVersionId, outstandingTree);

		oramClientContexts.put(clientId, oramClientContext);
		return new EncryptedPositionMaps(newVersionId, resultedPositionMap);
	}


}
