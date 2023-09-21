package oram.server.structure;

import oram.messages.GetPositionMap;
import oram.utils.PositionMapType;

import java.util.HashMap;
import java.util.Map;

public class FullPositionMapORAM extends ORAM {

	public FullPositionMapORAM(int oramId, PositionMapType positionMapType, int treeHeight, int bucketSize,
							   int blockSize, EncryptedPositionMap encryptedPositionMap,
							   EncryptedStash encryptedStash) {
		super(oramId, positionMapType, treeHeight, bucketSize, blockSize, encryptedPositionMap, encryptedStash);
	}

	public EncryptedPositionMaps getPositionMaps(int clientId, GetPositionMap request) {
		Map<Integer, EncryptedPositionMap> resultedPositionMap = new HashMap<>(outstandingTrees.size());
		OramSnapshot[] currentOutstandingVersions = new OramSnapshot[outstandingTrees.size()];
		int i = 0;
		for (OramSnapshot snapshot : outstandingTrees) {
			int versionId = snapshot.getVersionId();
			currentOutstandingVersions[i] = snapshot;
			resultedPositionMap.put(versionId, positionMaps.get(versionId));
			i++;
		}
		int newVersionId = sequenceNumber++;
		ORAMClientContext oramClientContext = new ORAMClientContext(currentOutstandingVersions, newVersionId);

		oramClientContexts.put(clientId, oramClientContext);
		return new EncryptedPositionMaps(newVersionId, resultedPositionMap);
	}

	@Override
	protected void cleanOutstandingTrees(OramSnapshot[] outstandingVersions) {
		super.cleanOutstandingTrees(outstandingVersions);
		for (OramSnapshot outstandingVersion : outstandingVersions) {
			positionMaps.remove(outstandingVersion.getVersionId());
		}
	}
}
