package oram.testers;

import oram.utils.ORAMUtils;
import oram.client.ORAMManager;
import oram.client.ORAMObject;
import oram.utils.Operation;
import vss.facade.SecretSharingException;

import java.util.Arrays;
import java.util.Random;

public class ClientTester {
	// ARGS: clientId, oramName, testSize
	public static void main(String[] args) throws SecretSharingException {
		Random r = new Random();
		int clientId = Integer.parseInt(args[0]);
		int oramId = Integer.parseInt(args[1]);
		int testSize = Integer.parseInt(args[2]);
		int oramHeight = 6;
		int bucketSize = 7;
		int blockSize = 64;
		int maxAddress = ORAMUtils.computeNumberOfNodes(oramHeight);
		ORAMManager oramManager = new ORAMManager(clientId);

		ORAMObject oram = oramManager.getORAM(oramId);
		if (oram == null)
			oram = oramManager.createORAM(oramId, oramHeight, bucketSize, blockSize);
		for (short i = 0; i < testSize; i++) {
			Operation op = null;
			while (op == null) {
				op = r.nextBoolean() ? Operation.READ : Operation.WRITE;
			}
			int address = r.nextInt(maxAddress);
			byte[] value;
			byte[] answer;
			if (op.equals(Operation.WRITE)) {
				int v = r.nextInt();
				value = String.valueOf(v).getBytes();
				answer = oram.writeMemory(address, value);
			} else {
				answer = oram.readMemory(address);
			}
			System.out.println("Answer from server: " + Arrays.toString(answer));
		}

		oramManager.close();
	}
}
