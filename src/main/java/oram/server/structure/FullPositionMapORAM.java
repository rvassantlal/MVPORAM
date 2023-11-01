package oram.server.structure;

import oram.messages.GetPositionMap;
import oram.utils.PositionMapType;

import java.util.HashMap;
import java.util.Map;

public class FullPositionMapORAM extends ORAM {

	public FullPositionMapORAM(int oramId, PositionMapType positionMapType, int garbageCollectionFrequency,
							   int treeHeight, int bucketSize, int blockSize, EncryptedPositionMap encryptedPositionMap,
							   EncryptedStash encryptedStash) {
		super(oramId, positionMapType, garbageCollectionFrequency, treeHeight, bucketSize, blockSize,
				encryptedPositionMap, encryptedStash);
	}

	public EncryptedPositionMaps getPositionMaps(int clientId, GetPositionMap request) {
		Map<Integer, EncryptedPositionMap> resultedPositionMap = new HashMap<>(outstandingTrees.size());
		int[] currentOutstandingVersions = new int[outstandingTrees.size()];
		EncryptedStash[] currentOutstandingStashes = new EncryptedStash[outstandingTrees.size()];
		int i = 0;
		for (int outstandingVersion : outstandingTrees) {
			currentOutstandingVersions[i] = outstandingVersion;
			currentOutstandingStashes[i] = stashes.get(outstandingVersion);
			resultedPositionMap.put(outstandingVersion, positionMaps.get(outstandingVersion));
			i++;
		}
		OutstandingTree outstandingTree = oramTreeManager.getOutstandingTree();
		int newVersionId = ++sequenceNumber;
		ORAMClientContext oramClientContext = new ORAMClientContext(currentOutstandingVersions,
				currentOutstandingStashes, newVersionId, outstandingTree);

		oramClientContexts.put(clientId, oramClientContext);
		return new EncryptedPositionMaps(newVersionId, resultedPositionMap);
	}

	@Override
	protected void cleanOutstandingTrees(int[] outstandingVersions) {
		super.cleanOutstandingTrees(outstandingVersions);
		for (int outstandingVersion : outstandingVersions) {
			positionMaps.remove(outstandingVersion);
		}
	}
}
