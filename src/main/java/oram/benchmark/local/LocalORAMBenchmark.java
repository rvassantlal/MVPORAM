package oram.benchmark.local;

import oram.benchmark.local.LocalClient;
import oram.benchmark.local.LocalServers;
import oram.client.EncryptionManager;
import oram.client.structure.PositionMap;
import oram.client.structure.Stash;
import oram.server.structure.EncryptedPositionMap;
import oram.server.structure.EncryptedStash;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;

public class LocalORAMBenchmark {
	private static EncryptionManager encryptionManager;

	public static void main(String[] args) throws InterruptedException {
		int oramId = 0;
		int initClientId = 1;
		int treeHeight = 4;
		int bucketSize = 4;
		int blockSize = 64;
		int treeSize = ORAMUtils.computeTreeSize(treeHeight, bucketSize);
		int nServers = 4;
		int nClients = 50;
		int nRequests = 1000000;


		ORAMContext oramContext = new ORAMContext(treeHeight, treeSize, bucketSize, blockSize);
		encryptionManager = new EncryptionManager();
		EncryptedPositionMap initialEncryptedPositionMap = initializeEmptyPositionMap();
		EncryptedStash emptyEncryptedStash = initializeEmptyStash(oramContext.getBlockSize());

		LocalServers localServers = new LocalServers(nServers, oramId, oramContext, initialEncryptedPositionMap,
				emptyEncryptedStash);
		LocalClient[] localClients = new LocalClient[nClients];
		for (int i = 0; i < nClients; i++) {
			localClients[i] = new LocalClient(initClientId + i, localServers, oramContext, nRequests);
			localClients[i].start();
		}

		for (LocalClient localClient : localClients) {
			localClient.join();
		}
	}

	private static EncryptedStash initializeEmptyStash(int blockSize) {
		Stash stash = new Stash(blockSize);
		return encryptionManager.encryptStash(stash);
	}

	private static EncryptedPositionMap initializeEmptyPositionMap() {
		int[] positionMap = new int[0];
		int[] versionIds = new int[0];
		PositionMap pm = new PositionMap(versionIds, positionMap);
		return encryptionManager.encryptPositionMap(pm);
	}
}
