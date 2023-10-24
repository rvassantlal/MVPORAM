package oram.server.structure;

import oram.messages.GetPositionMap;
import oram.utils.PositionMapType;
import vss.secretsharing.VerifiableShare;

import java.util.HashMap;
import java.util.Map;

public class TriplePositionMapORAM extends ORAM {

	public TriplePositionMapORAM(int oramId, PositionMapType positionMapType, boolean isDistributedKey,
                                 int treeHeight, int bucketSize, int blockSize,
                                 EncryptedPositionMap encryptedPositionMap, EncryptedStash encryptedStash,
								 VerifiableShare encryptionKeyShare) {
		super(oramId, positionMapType, isDistributedKey, treeHeight, bucketSize, blockSize,
				encryptedPositionMap, encryptedStash, encryptionKeyShare);
	}

	public EncryptedPositionMaps getPositionMaps(int clientId, GetPositionMap request) {
		int lastVersion = request.getLastVersion();
		Map<Integer, EncryptedPositionMap> resultedPositionMap = new HashMap<>(sequenceNumber - lastVersion);
		if (oramContext.isDistributedKey()) {
			encryptionKeySharesBuffer.clear();
		}
		for (int i = lastVersion; i < sequenceNumber; i++) {
			EncryptedPositionMap encryptedPositionMap = positionMaps.get(i);
			if (encryptedPositionMap != null) {
				resultedPositionMap.put(i, encryptedPositionMap);
				if (oramContext.isDistributedKey()) {
					encryptionKeySharesBuffer.put(i, encryptionKeyShares.get(i));
				}
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
		return new EncryptedPositionMaps(newVersionId, resultedPositionMap, encryptionKeySharesBuffer);
	}


}
