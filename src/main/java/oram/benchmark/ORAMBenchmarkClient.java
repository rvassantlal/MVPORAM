package oram.benchmark;

import oram.client.ORAMManager;
import oram.client.ORAMObject;
import oram.utils.ORAMUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class ORAMBenchmarkClient {
	private final static Logger logger = LoggerFactory.getLogger("benchmark.oram");
	public static void main(String[] args) throws SecretSharingException, InterruptedException {
		if (args.length != 7) {
			System.out.println("Usage: ... oram.benchmark.ORAMBenchmarkClient <initialClientId> <nClients> " +
					"<nRequests> <treeHeight> <bucketSize> <blockSize> <isMeasurementLeader>");
			System.exit(-1);
		}

		int oramId = 1;
		int initialClientId = Integer.parseInt(args[0]);
		int nClients = Integer.parseInt(args[1]);
		int nRequests = Integer.parseInt(args[2]);
		int treeHeight = Integer.parseInt(args[3]);
		int bucketSize = Integer.parseInt(args[4]);
		int blockSize = Integer.parseInt(args[5]);
		boolean measurementLeader = Boolean.parseBoolean(args[6]);

		CountDownLatch latch = new CountDownLatch(nClients);
		Client[] clients = new Client[nClients];
		for (int i = 0; i < nClients; i++) {
			clients[i] = new Client(oramId, initialClientId, initialClientId + i, treeHeight, bucketSize, blockSize, latch,
					nRequests, measurementLeader);
			clients[i].start();
			Thread.sleep(10);
		}

		try {
			latch.await();
			logger.info("Executing experiment");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static class Client extends Thread {
		private final Logger logger = LoggerFactory.getLogger("benchmark.oram");
		private final int initialClientId;
		private final ORAMManager oramManager;
		private final int clientId;
		private final CountDownLatch latch;
		private final int nRequests;
		private ORAMObject oram;
		private final byte[] blockContent;
		private final int address;
		private final boolean measurementLeader;

		private Client(int oramId, int initialClientId, int clientId, int treeHeight, int bucketSize, int blockSize,
					   CountDownLatch latch, int nRequests, boolean measurementLeader) throws SecretSharingException {
			this.initialClientId = initialClientId;
			this.oramManager = new ORAMManager(clientId);
			this.clientId = clientId;
			this.latch = latch;
			this.nRequests = nRequests;
			this.measurementLeader = measurementLeader;
			this.oram = oramManager.createORAM(oramId, treeHeight, bucketSize, blockSize);
			if (oram == null) {
				oram = oramManager.getORAM(oramId);
			}
			this.blockContent = new byte[blockSize];
			Arrays.fill(blockContent, (byte) 'a');
			int treeSize = ORAMUtils.computeNumberOfNodes(treeHeight);
			this.address = clientId % treeSize;
		}

		@Override
		public void run() {
			try {
				latch.countDown();
				oram.writeMemory(address, blockContent);
				long t1, t2, delay;
				byte[] oldContent;
				for (int i = 0; i < nRequests; i++) {
					t1 = System.nanoTime();
					oldContent = oram.writeMemory(address, blockContent);
					t2 = System.nanoTime();
					delay = t2 - t1;
					if (!Arrays.equals(blockContent, oldContent)) {
						logger.error("[Client {}] Content at address {} is different ({})", clientId, address, Arrays.toString(oldContent));
					}
					if (initialClientId == clientId && measurementLeader) {
						logger.info("M: " + delay);
					}
				}
			} finally {
				oramManager.close();
			}
		}
	}
}
