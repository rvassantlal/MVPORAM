package oram.server;

import oram.messages.GetPositionMap;
import oram.server.structure.*;
import oram.utils.PositionMapType;
import vss.secretsharing.VerifiableShare;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TriplePositionMapORAM extends ORAM {

	public TriplePositionMapORAM(int oramId, VerifiableShare encryptionKeyShare, PositionMapType positionMapType,
								 int treeHeight, int bucketSize, int blockSize,
								 EncryptedPathMap encryptedPathMap, EncryptedStash encryptedStash) {
		super(oramId, encryptionKeyShare, positionMapType, treeHeight, bucketSize, blockSize,
				encryptedPathMap, encryptedStash);
	}

	public EncryptedPositionMaps getPositionMaps(int clientId, GetPositionMap request) {
		int lastVersion = request.getLastVersion();
		Set<Integer> missingTriples = request.getMissingTriples();

		OutstandingTree outstandingTree = oramTreeManager.getOutstandingTree();
		Set<Integer> outstandingVersions = outstandingTree.getOutstandingVersions();
		Map<Integer, EncryptedPathMap> resultedPositionMap = new HashMap<>(sequenceNumber - lastVersion);
		Map<Integer, int[]> resultedOutstandingVersions = new HashMap<>(sequenceNumber - lastVersion);

		for (int i : missingTriples) {
			EncryptedPathMap encryptedPathMap = pathMaps.get(i);
			int[] ov = this.outstandingVersions.get(i);
			if (encryptedPathMap != null) {
				resultedPositionMap.put(i, encryptedPathMap);
				resultedOutstandingVersions.put(i, ov);
			}
		}

		for (int i = lastVersion + 1; i <= sequenceNumber; i++) {
			EncryptedPathMap encryptedPathMap = pathMaps.get(i);
			int[] ov = this.outstandingVersions.get(i);
			if (encryptedPathMap != null) {
				resultedPositionMap.put(i, encryptedPathMap);
				resultedOutstandingVersions.put(i, ov);
			}
		}

		int[] currentOutstandingVersions = new int[outstandingVersions.size()];
		int i = 0;
		for (int outstandingVersion : outstandingVersions) {
			currentOutstandingVersions[i] = outstandingVersion;
			i++;
		}
		Arrays.sort(currentOutstandingVersions);
		int newVersionId = ++sequenceNumber;

		ORAMClientContext oramClientContext = new ORAMClientContext(currentOutstandingVersions, newVersionId,
				outstandingTree);
		oramClientContexts.put(clientId, oramClientContext);
		return new EncryptedPositionMaps(newVersionId, resultedPositionMap, currentOutstandingVersions,
				resultedOutstandingVersions);
	}

	@Override
	protected void cleanPositionMaps(int[] outstandingVersions) {

	}


}
