package oram.testers;

import oram.utils.ORAMUtils;
import oram.client.ORAMManager;
import oram.client.ORAMObject;
import oram.utils.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.util.Arrays;
import java.util.Random;

public class ClientTester {
	private static final Logger logger = LoggerFactory.getLogger("benchmark");

	// ARGS: clientId, oramName, testSize
	public static void main(String[] args) throws SecretSharingException {
		Random r = new Random();
		int clientId = Integer.parseInt(args[0]);
		int oramId = Integer.parseInt(args[1]);
		int testSize = Integer.parseInt(args[2]);
		int oramHeight = 3;
		int bucketSize = 4;
		int blockSize = 32;
		int maxAddress = ORAMUtils.computeNumberOfNodes(oramHeight) * bucketSize;
		ORAMManager oramManager = new ORAMManager(clientId);

		ORAMObject oram = oramManager.getORAM(oramId);
		if (oram == null)
			oram = oramManager.createORAM(oramId, oramHeight, bucketSize, blockSize);
		for (short i = 0; i < testSize; i++) {
			Operation op = null;
			while (op == null) {
				op = r.nextBoolean() ? Operation.READ : Operation.WRITE;
			}
			int address = 1;
			byte[] value;
			byte[] answer;
			if (op == Operation.WRITE) {
				//int v = r.nextInt();
				value = String.valueOf(2).getBytes();
				answer = oram.writeMemory(address, value);
			} else {
				answer = oram.readMemory(address);
			}
			logger.info("op {} | answer: {}", op, Arrays.toString(answer));
		}

		oramManager.close();
	}
}
