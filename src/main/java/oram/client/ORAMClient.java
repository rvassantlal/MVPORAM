package oram.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

public class ORAMClient {
	private static final Logger logger = LoggerFactory.getLogger("benchmark");

	public static void main(String[] args) throws SecretSharingException {
		ORAMFacade oramFacade = new ORAMFacade(100);
		int oramId = 1;
		int treeHeight = 3;
		boolean isORAMCreated = oramFacade.createORAM(oramId, treeHeight);

		if (isORAMCreated) {
			logger.info("ORAM was created with id {}", oramId);
		} else {
			logger.info("Failed to create ORAM with id {}", oramId);
		}

		oramFacade.close();
	}
}
