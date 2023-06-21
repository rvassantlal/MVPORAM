package oram.benchmark;

import oram.client.ORAMManager;
import oram.client.ORAMObject;
import vss.facade.SecretSharingException;

import java.util.Arrays;

public class ORAMBenchmarkClient {
	public static void main(String[] args) throws SecretSharingException {
		int nClients = 2;
		int nTests = 10;
		int clientId = 1;
		int oramId = 1;
		int treeHeight = 3;
		int bucketSize = 4;
		int blockSize = 10;
		int address = 0;
		byte[] blockContent = new byte[blockSize];
		Arrays.fill(blockContent, (byte) 'a');

		ORAMManager oramManager = new ORAMManager(clientId);
		ORAMObject oram = oramManager.getORAM(oramId);
		if (oram == null) {
			oram = oramManager.createORAM(oramId, treeHeight, bucketSize, blockSize);
		}

		oram.writeMemory(address, blockContent);
		for (int i = 0; i < nTests; i++) {
			byte[] oldContent = oram.writeMemory(address, blockContent);
			if (!Arrays.equals(blockContent, oldContent)) {
				System.out.println("Content at address " + address + " is different (" + Arrays.toString(oldContent) + ")");
				break;
			}
		}

		oramManager.close();

	}
}
