package oram.server.structure;

import oram.messages.GetPositionMap;
import oram.utils.PositionMapType;
import vss.secretsharing.VerifiableShare;

import java.util.HashMap;
import java.util.Map;

public class FullPositionMapORAM extends ORAM {

	public FullPositionMapORAM(int oramId, PositionMapType positionMapType, boolean isDistributedKey,
                               int treeHeight, int bucketSize, int blockSize, EncryptedPositionMap encryptedPositionMap,
                               EncryptedStash encryptedStash, VerifiableShare encryptionKeyShare) {
		super(oramId, positionMapType, isDistributedKey, treeHeight, bucketSize, blockSize,
				encryptedPositionMap, encryptedStash, encryptionKeyShare);
	}

	public EncryptedPositionMaps getPositionMaps(int clientId, GetPositionMap request) {
		Map<Integer, EncryptedPositionMap> resultedPositionMap = new HashMap<>(outstandingTrees.size());
		int[] currentOutstandingVersions = new int[outstandingTrees.size()];
		EncryptedStash[] currentOutstandingStashes = new EncryptedStash[outstandingTrees.size()];
		if (oramContext.isDistributedKey()) {
			encryptionKeySharesBuffer.clear();
		}
		int i = 0;
		for (int outstandingVersion : outstandingTrees) {
			currentOutstandingVersions[i] = outstandingVersion;
			currentOutstandingStashes[i] = stashes.get(outstandingVersion);
			resultedPositionMap.put(outstandingVersion, positionMaps.get(outstandingVersion));
			if (oramContext.isDistributedKey()) {
				encryptionKeySharesBuffer.put(outstandingVersion, encryptionKeyShares.get(outstandingVersion));
			}
			i++;
		}
		OutstandingTreeContext outstandingTree = oramTree.getOutstandingBucketsVersions();
		int newVersionId = ++sequenceNumber;
		ORAMClientContext oramClientContext = new ORAMClientContext(currentOutstandingVersions,
				currentOutstandingStashes, newVersionId, outstandingTree);

		oramClientContexts.put(clientId, oramClientContext);
		return new EncryptedPositionMaps(newVersionId, resultedPositionMap, encryptionKeySharesBuffer);
	}

	@Override
	protected void cleanOutstandingTrees(int[] outstandingVersions) {
		super.cleanOutstandingTrees(outstandingVersions);
		for (int outstandingVersion : outstandingVersions) {
			positionMaps.remove(outstandingVersion);
		}
	}
}
