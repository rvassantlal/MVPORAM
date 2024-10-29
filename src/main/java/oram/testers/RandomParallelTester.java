package oram.testers;

import oram.client.ORAMManager;
import oram.client.ORAMObject;
import oram.utils.ORAMUtils;
import oram.utils.Operation;
import oram.utils.PositionMapType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class RandomParallelTester {
	private static final Logger logger = LoggerFactory.getLogger("benchmark");
	private static final String[] expressions10 = {"Lively", "Tranquil", "Rhythm", "Candid", "Glimpse", "Dazzle",
			"Effervescent", "Mirth", "Delicate", "Whimsy"};
	private static final String[] expressions55 = {"test1", "test2", "Parallel", "content", "oram", "benchmark",
			"Pineapple", "Cascade", "Whimsical", "Zephyr", "Serendipity", "Quixotic", "Bubble", "Enigma",
			"Luminous", "Jubilant", "Mellifluous", "Saffron", "Euphoria", "Quirk", "Galaxy", "Breeze", "Aurora",
			"Mystic", "Ethereal", "Bountiful", "Velvet", "Meadow", "Whiskers", "Harmonious", "Citrus", "Gossamer",
			"Tranquil", "Ponder", "Frolic", "Blissful", "Mystify", "Blossom", "Wanderlust", "Enchant", "Luminary",
			"Ripple", "Enthrall", "Symphony", "Labyrinth", "Sizzle", "Jubilee", "Driftwood", "Quench", "Twilight",
			"Spellbound", "Tranquility", "Mirage", "Elixir", "Glimmer", "Enchanted"};
	static int testSize = -1;
	static SecureRandom rnd;
	static int maxAddress;

	public static void main(String[] args) throws SecretSharingException, InterruptedException {
		if (args.length != 6) {
			System.out.println("Usage: oram.testers.RandomParallelTester <initial client id> <nClients> <testSize> <treeHeight> <nBlocksPerBucket> <full | triple>");
			System.exit(-1);
		}
		rnd = new SecureRandom("oram".getBytes());
		int initialClientId = Integer.parseInt(args[0]);
		int nClients = Integer.parseInt(args[1]);
		testSize = Integer.parseInt(args[2]);
		int treeHeight = Integer.parseInt(args[3]);
		int nBlocksPerBucket = Integer.parseInt(args[4]);
		int blockSize = 20;
		int oramId = 1;
		PositionMapType oramType;
		switch (args[5]) {
			case "full":
				oramType = PositionMapType.FULL_POSITION_MAP;
				break;
			case "triple":
				oramType = PositionMapType.TRIPLE_POSITION_MAP;
				break;
			default:
				throw new IllegalArgumentException("Invalid ORAM type");
		}
		List<ORAMManager> oramManagerList = new ArrayList<>();
		List<Thread> threads = new ArrayList<>();
		for (int i = 0; i < nClients; i++) {
			oramManagerList.add(new ORAMManager(initialClientId + i));
		}

		maxAddress = ORAMUtils.computeTreeSize(treeHeight, nBlocksPerBucket);

		oramManagerList.get(0).createORAM(oramId, oramType, treeHeight, nBlocksPerBucket, blockSize);

		for (ORAMManager oramManager : oramManagerList) {
			ORAMObject oram = oramManager.getORAM(oramId);
			threads.add(new Thread(() -> {
				for (int i = 0; i < testSize; i++) {
					//System.out.println(i);
					try {
						randomAccess(oram);
					} catch (Exception e) {
						//oram.serializeDebugData();
						throw e;
					}
				}
				//oram.serializeDebugData();
			}));
		}
		////// For profiling uncomment these lines
        /*
        Scanner sc = new Scanner(System.in);
        sc.nextLine();
        */
		//////
		for (Thread thread : threads) {
			thread.start();
		}
		for (Thread thread : threads) {
			thread.join();
		}
		for (ORAMManager oramManager : oramManagerList) {
			oramManager.close();
		}

		ORAMManager oramManager = new ORAMManager(100000 + initialClientId);
		ORAMObject oram = oramManager.getORAM(oramId);
		oram.writeMemory(0, "test".getBytes());
		//oram.getORAMSnapshot();
		oramManager.close();
	}

	private static void randomAccess(ORAMObject oram) {
		Operation op = rnd.nextBoolean() ? Operation.WRITE : Operation.READ;
		int address = rnd.nextInt(maxAddress);
		if (op == Operation.WRITE) {
			String randomWord;
			if (testSize > 55)
				randomWord = expressions55[rnd.nextInt(expressions55.length)];
			else
				randomWord = expressions10[rnd.nextInt(expressions10.length)];
			byte[] response = oram.writeMemory(address, randomWord.getBytes());
			String responseString = response == null ? "null" : new String(response);
			//logger.info("write \"" + randomWord + "\" to address" + address + ". Response (oldValue): " + responseString);
		} else {
			byte[] response = oram.readMemory(address);
			String responseString = response == null ? "null" : new String(response);
			//logger.info("read from address" + address + ". Response: " + responseString);
		}

	}


}
