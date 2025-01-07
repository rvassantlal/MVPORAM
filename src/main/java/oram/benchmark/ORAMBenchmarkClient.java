package oram.benchmark;

import oram.client.ORAMManager;
import oram.client.ORAMObject;
import oram.utils.ORAMUtils;
import org.apache.commons.math3.distribution.ZipfDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class ORAMBenchmarkClient {
	private final static Logger logger = LoggerFactory.getLogger("benchmarking");
	public static void main(String[] args) throws SecretSharingException, InterruptedException {
		if (args.length != 8) {
			System.out.println("Usage: ... oram.benchmark.ORAMBenchmarkClient <initialClientId> <nClients> " +
					"<nRequests> <treeHeight> <bucketSize> <blockSize> <zipf parameter> <isMeasurementLeader>");
			System.exit(-1);
		}

		int oramId = 1;
		int initialClientId = Integer.parseInt(args[0]);
		int nClients = Integer.parseInt(args[1]);
		int nRequests = Integer.parseInt(args[2]);
		int treeHeight = Integer.parseInt(args[3]);
		int bucketSize = Integer.parseInt(args[4]);
		int blockSize = Integer.parseInt(args[5]);
		double zipfParameter = Double.parseDouble(args[6]);
		boolean measurementLeader = Boolean.parseBoolean(args[7]);

		CountDownLatch latch = new CountDownLatch(nClients);
		Client[] clients = new Client[nClients];
		for (int i = 0; i < nClients; i++) {
			clients[i] = new Client(oramId, initialClientId, initialClientId + i,
					treeHeight, bucketSize, blockSize, latch, nRequests, measurementLeader, zipfParameter);
			clients[i].start();
			Thread.sleep(10);
		}

		try {
			latch.await();
			logger.info("Executing experiment");
		} catch (InterruptedException e) {
			logger.error("Error while waiting for clients to start", e);
		}
	}

	private static class Client extends Thread {
		private final Logger measurementLogger = LoggerFactory.getLogger("measurement");
		private final int initialClientId;
		private final ORAMManager oramManager;
		private final int clientId;
		private final CountDownLatch latch;
		private final int nRequests;
		private ORAMObject oram;
		private final byte[] blockContent;
		private int address;
		private final boolean measurementLeader;
		private final SecureRandom rndGenerator;
		private final ZipfDistribution zipfDistribution;
		private final int treeSize;

		private Client(int oramId, int initialClientId, int clientId, int treeHeight, int bucketSize, int blockSize,
					   CountDownLatch latch, int nRequests, boolean measurementLeader, double zipfParameter) throws SecretSharingException {
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
			treeSize = ORAMUtils.computeNumberOfNodes(treeHeight);
			this.address = clientId % treeSize;
			this.rndGenerator = new SecureRandom();
			this.zipfDistribution = new ZipfDistribution(treeSize, zipfParameter);
		}

		@Override
		public void run() {
			try {
				latch.countDown();

				long t1, t2, delay;
				byte[] oldContent;
				boolean isWrite;
				for (int i = 0; i < nRequests; i++) {
					isWrite = rndGenerator.nextBoolean();
					address = zipfDistribution.sample() - 1;
					//address = rndGenerator.nextInt(treeSize);
					t1 = System.nanoTime();

					if (isWrite) {
						oldContent = oram.writeMemory(address, blockContent);
					} else {
						oldContent = oram.readMemory(address);
					}
					t2 = System.nanoTime();
					delay = t2 - t1;
					/*if (!Arrays.equals(blockContent, oldContent)) {
						measurementLogger.error("[Client {}] Content at address {} is different ({})", clientId, address, Arrays.toString(oldContent));
						//break;
					}*/
					if (initialClientId == clientId && measurementLeader) {
						measurementLogger.info("M-global: {}", delay);
						logger.info("Access latency: {} ms", delay / 1_000_000.0);
					}
				}
			} finally {
				oramManager.close();
			}
		}
	}
}
