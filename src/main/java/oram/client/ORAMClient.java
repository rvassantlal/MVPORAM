package oram.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

public class ORAMClient {
	private static final Logger logger = LoggerFactory.getLogger("benchmark");

	public static void main(String[] args) throws SecretSharingException {
		ORAMManager oramManager = new ORAMManager(100);

		int oramId = 1;
		int treeHeight = 3;
		int nBlocksPerBucket = 4;
		ORAMObject oram = oramManager.createORAM(oramId, treeHeight, nBlocksPerBucket);

		if (oram != null) {
			logger.info("ORAM was created with id {}", oramId);
		} else {
			logger.info("Failed to create ORAM with id {}", oramId);
		}

		ORAMObject oram1 = oramManager.getORAM(oramId);
		if (oram1 != null) {
			logger.info("ORAM with id {} exists", oramId);
		} else {
			logger.info("ORAM with id {} does not exist", oramId);
		}
		oramManager.close();
	}
}
