package oram.testers;

import oram.client.ORAMManager;
import oram.client.ORAMObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.util.Arrays;

public class GarbageCollectorTester {
	private static final Logger logger = LoggerFactory.getLogger("benchmark");

	public static void main(String[] args) throws SecretSharingException {
		ORAMManager oramManager = new ORAMManager(100);

		int oramId = 1;
		int treeHeight = 3;
		int nBlocksPerBucket = 4;
		int blockSize = 512;
		ORAMObject oram = oramManager.createORAM(oramId, treeHeight, nBlocksPerBucket, blockSize);

		if (oram != null) {
			logger.info("ORAM was created with id {}", oramId);
		} else {
			logger.info("Failed to create ORAM with id {}", oramId);
		}

		oram = oramManager.getORAM(oramId);
		if (oram != null) {
			logger.info("ORAM with id {} exists", oramId);
		} else {
			logger.info("ORAM with id {} does not exist", oramId);
			System.exit(-1);
		}

		logger.debug("Write \"test1\" to address 1");
		byte[] response = oram.writeMemory(1, "test1".getBytes());
		if (response != null) {
			logger.error("First write on a empty oram returned data :O");
		}

		logger.debug("Write \"test1\" to address 1");
		response = oram.writeMemory(1, "test1".getBytes());
		if (response != null) {
			logger.error("First write on a empty oram returned data :O");
		}

		logger.debug("Write \"test1\" to address 1");
		response = oram.writeMemory(1, "test1".getBytes());
		if (response != null) {
			logger.error("First write on a empty oram returned data :O");
		}

		logger.debug("Write \"test1\" to address 1");
		response = oram.writeMemory(1, "test1".getBytes());
		if (response != null) {
			logger.error("First write on a empty oram returned data :O");
		}

		logger.debug("Write \"test1\" to address 1");
		response = oram.writeMemory(1, "test1".getBytes());
		if (response != null) {
			logger.error("First write on a empty oram returned data :O");
		}

		logger.debug("Read from address 1");
		response = oram.readMemory(1);
		if (response == null || !new String(response).trim().equals("test1")) {
			logger.error("Read on address 1 returned different data: {}", Arrays.toString(response));
		}


		oramManager.close();
	}
}
