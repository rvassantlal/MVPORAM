package oram.testers;

import oram.client.ORAMManager;
import oram.client.ORAMObject;
import oram.utils.ORAMUtils;
import oram.utils.PositionMapType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.util.Arrays;

public class LoadORAM {
	private final static Logger logger = LoggerFactory.getLogger("benchmarking");

	public static void main(String[] args) throws SecretSharingException {
		if (args.length != 4) {
			System.out.println("Usage: ... oram.testers.LoadORAM <position map type: full | triple> <treeHeight> <bucketSize> <blockSize>");
			System.exit(-1);
		}

		int oramId = 1;
		int initialClientId = 50;
		String positionMapTypeString = args[0];
		PositionMapType positionMapType;
		if (positionMapTypeString.equals("full")) {
			positionMapType = PositionMapType.FULL_POSITION_MAP;
		} else if (positionMapTypeString.equals("triple")) {
			positionMapType = PositionMapType.TRIPLE_POSITION_MAP;
		} else {
			throw new IllegalArgumentException("Invalid Position Map type");
		}

		int treeHeight = Integer.parseInt(args[1]);
		int bucketSize = Integer.parseInt(args[2]);
		int blockSize = Integer.parseInt(args[3]);

		ORAMManager oramManager = new ORAMManager(initialClientId);
		ORAMObject oram = oramManager.createORAM(oramId, positionMapType, treeHeight, bucketSize, blockSize);
		if (oram == null) {
			logger.warn("ORAM with id {} already exists", oramId);
			oram = oramManager.getORAM(oramId);
		}

		int treeSize = ORAMUtils.computeTreeSize(treeHeight, bucketSize);
		byte[] data = new byte[blockSize];
		Arrays.fill(data, (byte) 'a');

		long start = System.currentTimeMillis();

		for (int i = 0; i < treeSize; i++) {
			logger.info("Writing to address {} out of {} ({} %)", i, treeSize, (int)((i * 100.0) / treeSize));
			oram.writeMemory(i, data);
		}

		long end = System.currentTimeMillis();

		oramManager.close();

		long delay = end - start;
		long delayInSeconds = delay / 1000;
		long delayInMinutes = delayInSeconds / 60;
		logger.info("Took {} seconds ({} minutes) to write to {} addresses", delayInSeconds, delayInMinutes, treeSize);
	}
}
