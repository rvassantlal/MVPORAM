package oram.testers;

import oram.client.ORAMManager;
import oram.client.ORAMObject;
import oram.utils.ORAMUtils;
import oram.utils.Operation;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static oram.utils.ORAMUtils.getStatisticsFromList;

public class MultiCaseTester {
    private static final Logger logger = LoggerFactory.getLogger("benchmark");
    private static final String[] expressions10 = {"Lively", "Tranquil", "Rhythm", "Candid", "Glimpse", "Dazzle", "Effervescent", "Mirth", "Delicate", "Whimsy"};
    private static final String[] expressions55 = {"test1", "test2", "Parallel", "content", "oram", "benchmark", "Pineapple", "Cascade", "Whimsical", "Zephyr", "Serendipity", "Quixotic", "Bubble", "Enigma", "Luminous", "Jubilant", "Mellifluous", "Saffron", "Euphoria", "Quirk", "Galaxy", "Breeze", "Aurora", "Mystic", "Ethereal", "Bountiful", "Velvet", "Meadow", "Whiskers", "Harmonious", "Citrus", "Gossamer", "Tranquil", "Ponder", "Frolic", "Blissful", "Mystify", "Blossom", "Wanderlust", "Enchant", "Luminary", "Ripple", "Enthrall", "Symphony", "Labyrinth", "Sizzle", "Jubilee", "Driftwood", "Quench", "Twilight", "Spellbound", "Tranquility", "Mirage", "Elixir", "Glimmer", "Enchanted"};
    static int testSize = 100;
    static SecureRandom rnd;
    static int maxAddress;

    static int oramId = 0;

    // RUN WITH 3 ARGS : NUMBER_OF_CLIENTS, ORAM_ID AND TEST_SIZE
    public static void main(String[] args) throws SecretSharingException, InterruptedException {
        rnd = new SecureRandom("oram".getBytes());
        //// 10 Client Tests ////
        testSmallTreeAndBuckets(10, 512);
        testSmallTreeAndBigBuckets(10, 512);
        testBigTreeAndSmallBuckets(10, 512);
        testBigTreeAndBigBuckets(10, 512);
        // Big Blocks //
        testSmallTreeAndBuckets(10, 4096);
        testSmallTreeAndBigBuckets(10, 4096);
        testBigTreeAndSmallBuckets(10, 4096);
        testBigTreeAndBigBuckets(10, 4096);
        //// 50 Client Tests ////
        testSmallTreeAndBuckets(50, 512);
        testSmallTreeAndBigBuckets(50, 512);
        testBigTreeAndSmallBuckets(50, 512);
        testBigTreeAndBigBuckets(50, 512);
        // Big blocks //
        testSmallTreeAndBuckets(50, 4096);
        testSmallTreeAndBigBuckets(50, 4096);
        testBigTreeAndSmallBuckets(50, 4096);
        testBigTreeAndBigBuckets(50, 4096);
    }

    private static void testBigTreeAndSmallBuckets(int nClients, int blockSize) throws SecretSharingException,
            InterruptedException {
        List<ORAMManager> oramManagerList = new ArrayList<>();
        for (int i = 1; i < nClients + 1; i++) {
            oramManagerList.add(new ORAMManager(i));
        }
        generateTest(oramId++, oramManagerList, new ArrayList<>(), 7, 4, blockSize,
                "testBigTreeAndSmallBuckets" + nClients + "Clients" + blockSize + "ByteBlock");
    }

    private static void testBigTreeAndBigBuckets(int nClients, int blockSize) throws SecretSharingException,
            InterruptedException {
        List<ORAMManager> oramManagerList = new ArrayList<>();
        for (int i = 1; i < nClients + 1; i++) {
            oramManagerList.add(new ORAMManager(i));
        }
        generateTest(oramId++, oramManagerList, new ArrayList<>(), 7, 24, blockSize,
                "testBigTreeAndBigBuckets" + nClients + "Clients" + blockSize + "ByteBlock");
    }

    private static void testSmallTreeAndBigBuckets(int nClients, int blockSize) throws SecretSharingException,
            InterruptedException {
        List<ORAMManager> oramManagerList = new ArrayList<>();
        for (int i = 1; i < nClients + 1; i++) {
            oramManagerList.add(new ORAMManager(i));
        }
        generateTest(oramId++, oramManagerList, new ArrayList<>(), 3, 24, blockSize,
                "testSmallTreeAndBigBuckets" + nClients + "Clients" + blockSize + "ByteBlock");
    }

    private static void testSmallTreeAndBuckets(int nClients, int blockSize) throws SecretSharingException,
            InterruptedException {
        List<ORAMManager> oramManagerList = new ArrayList<>();
        for (int i = 1; i < nClients + 1; i++) {
            oramManagerList.add(new ORAMManager(i));
        }
        generateTest(oramId++, oramManagerList, new ArrayList<>(), 3, 4, blockSize,
                "testSmallTreeAndBuckets" + nClients + "Clients" + blockSize + "ByteBlock");
    }

    private static void generateTest(int oramId, List<ORAMManager> oramManagerList, List<Thread> threads,
                                     int treeHeight, int nBlocksPerBucket, int blockSize, String identifier)
            throws InterruptedException {

        maxAddress = ORAMUtils.computeNumberOfNodes(treeHeight);

        oramManagerList.get(0).createORAM(oramId, treeHeight, nBlocksPerBucket, blockSize);
        Queue<Integer> times = new ConcurrentLinkedQueue<>();
        for (ORAMManager oramManager : oramManagerList) {
            threads.add(new Thread(() -> {
                for (int i = 0; i < testSize; i++) {
                    long start = System.nanoTime();
                    randomAccess(oramManager.getORAM(oramId));
                    long end = System.nanoTime();
                    int delay = (int) ((end - start) / 1_000_000);
                    times.add(delay);
                }
            }));
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        for (ORAMManager oramManager : oramManagerList) {
            oramManager.close();
        }
        logger.info("-----EXECUTION TIMES (average, min, max) in ms-----");
        Triple<Integer, Integer, Double> stats = getStatisticsFromList(new ArrayList<>(times));
        logger.info("{}: ({}, {}, {})", identifier, stats.getRight(), stats.getLeft(), stats.getMiddle());
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
            logger.debug("write \"" + randomWord + "\" to address" + address + ". Response (oldValue): "
                    + responseString);
        } else {
            byte[] response = oram.readMemory(address);
            String responseString = response == null ? "null" : new String(response);
            logger.debug("read from address" + address + ". Response: " + responseString);
        }

    }


}
