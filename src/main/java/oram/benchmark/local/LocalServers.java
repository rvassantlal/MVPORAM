package oram.benchmark.local;

import oram.server.structure.*;
import oram.utils.ORAMContext;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LocalServers {
	private final Lock entryLock;
	private final ORAM[] orams;

	public LocalServers(int nServers, int oramId, ORAMContext oramContext,
						EncryptedPositionMap encryptedPositionMap, EncryptedStash encryptedStash) {
		this.entryLock = new ReentrantLock(true);
		this.orams = new ORAM[nServers];
		for (int i = 0; i < nServers; i++) {
			orams[i] = new ORAM(oramId, oramContext.getTreeHeight(), oramContext.getBucketSize(),
					oramContext.getBlockSize(), encryptedPositionMap, encryptedStash);
		}
	}

	public EncryptedPositionMaps[] getPositionMaps(int clientId) {
		try {
			entryLock.lock();
			System.out.println("[Client " + clientId + "] Getting position maps");
			EncryptedPositionMaps[] positionMaps = new EncryptedPositionMaps[orams.length];
			for (int i = 0; i < orams.length; i++) {
				positionMaps[i] = orams[i].getPositionMaps(clientId);
			}
			return positionMaps;
		} finally {
			entryLock.unlock();
		}
	}

	public EncryptedStashesAndPaths[] getStashesAndPaths(int clientId, int pathId) {
		try {
			entryLock.lock();
			System.out.println("[Client " + clientId + "] Getting stashes and paths");
			EncryptedStashesAndPaths[] stashesAndPaths = new EncryptedStashesAndPaths[orams.length];
			for (int i = 0; i < orams.length; i++) {
				stashesAndPaths[i] = orams[i].getStashesAndPaths(pathId, clientId);
			}
			return stashesAndPaths;
		} finally {
			entryLock.unlock();
		}
	}

	public boolean evict(int clientId, EncryptedStash encryptedStash, EncryptedPositionMap encryptedPositionMap,
						 Map<Integer, EncryptedBucket> encryptedPath) {
		try {
			entryLock.lock();
			System.out.println("[Client " + clientId + "] Evicting");
			boolean isSuccess = true;
			for (ORAM oram : orams) {
				isSuccess &= oram.performEviction(encryptedStash, encryptedPositionMap, encryptedPath, clientId);
			}
			return isSuccess;
		} finally {
			entryLock.unlock();
		}
	}

}
