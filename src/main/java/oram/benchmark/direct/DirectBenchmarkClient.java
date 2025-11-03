package oram.benchmark.direct;

import oram.client.structure.PathMap;
import oram.client.structure.Stash;
import oram.security.EncryptionManager;
import oram.server.ORAM;
import oram.server.structure.EncryptedPathMap;
import oram.server.structure.EncryptedStash;
import oram.utils.ORAMUtils;
import oram.utils.Operation;
import org.apache.commons.math3.distribution.ZipfDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

public class DirectBenchmarkClient {
	private final static Logger logger = LoggerFactory.getLogger("benchmarking");
	private static EncryptionManager encryptionManager;

	public static void main(String[] args) {
		if (args.length != 6) {
			System.out.println("Usage: ... oram.benchmark.direct.DirectBenchmarkClient <nClients> " +
					"<nRequests> <treeHeight> <bucketSize> <blockSize> <zipf parameter>");
			System.exit(-1);
		}

		int nClients = Integer.parseInt(args[0]);
		int nAccessesPerClient = Integer.parseInt(args[1]);
		int height = Integer.parseInt(args[2]);
		int bucketSize = Integer.parseInt(args[3]);
		int blockSize = Integer.parseInt(args[4]);
		double zipfParameter = Double.parseDouble(args[5]);

		int oramId = 0;
		int treeSize = ORAMUtils.computeNumberOfNodes(height);
		SecureRandom rndGenerator = new SecureRandom();
		ZipfDistribution zipfDistribution = new ZipfDistribution(treeSize, zipfParameter);

		encryptionManager = new EncryptionManager();
		String password = "DirectBenchmarkPassword";
		encryptionManager.createSecretKey(password);

		//Initialize ORAM
		EncryptedPathMap initialPathMap = initializeEmptyPathMap();;
		EncryptedStash initialStash = initializeEmptyStash(blockSize);;
		ORAM oram = new ORAM(oramId, height, bucketSize, blockSize, initialPathMap, initialStash);

		//Initialize clients
		DirectORAMObject[] clients = new DirectORAMObject[nClients];
		for (int i = 0; i < nClients; i++) {
			clients[i] = new DirectORAMObject(oram, oramId, i, encryptionManager);
		}

		boolean[] isWrite = new boolean[nClients];
		int[] addresses = new int[nClients];
		Set<Integer> writtenAddresses = new HashSet<>(treeSize);

		logger.info("Executing experiment");

		for (int a = 0; a < nAccessesPerClient; a++) {
			//Generate requests
			for (int i = 0; i < nClients; i++) {
				isWrite[i] = rndGenerator.nextBoolean();
				addresses[i] = zipfDistribution.sample() - 1;
			}

			//Execute step 1
			for (int i = 0; i < clients.length; i++) {
				int address = addresses[i];
				if (isWrite[i]) {
					byte[] data = new byte[blockSize];
					rndGenerator.nextBytes(data);
					clients[i].accessStepOne(Operation.WRITE, address, data);
				} else {
					clients[i].accessStepOne(Operation.READ, address, null);
				}
			}

			//Execute step 2
			for (DirectORAMObject client : clients) {
				client.accessStepTwo();
			}

			//Execute step 3 and verify
			for (int i = 0; i < nClients; i++) {
				byte[] oldData = clients[i].accessStepThree();
				int address = addresses[i];
				if (writtenAddresses.contains(address) && oldData == null) {
					throw new IllegalStateException("Received null data from accessStepThree");
				}
			}

			for (int i = 0; i < nClients; i++) {
				if (isWrite[i]) {
					writtenAddresses.add(addresses[i]);
				}
			}
		}
	}

	private static EncryptedStash initializeEmptyStash(int blockSize) {
		Stash stash = new Stash(blockSize);
		return encryptionManager.encryptStash(stash);
	}

	private static EncryptedPathMap initializeEmptyPathMap() {
		PathMap pathMap = new PathMap(1);
		return encryptionManager.encryptPathMap(pathMap);
	}
}
