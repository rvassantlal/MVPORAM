package oram.testers;

import oram.client.ORAMManager;
import oram.client.ORAMObject;
import oram.utils.ORAMUtils;
import oram.utils.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RandomParallelTester {
    private static final Logger logger = LoggerFactory.getLogger("benchmark");
    private static final String[] expressions10 = {"Lively", "Tranquil", "Rhythm", "Candid", "Glimpse", "Dazzle", "Effervescent", "Mirth", "Delicate", "Whimsy"};
    private static final String[] expressions55 = {"test1", "test2", "Parallel", "content", "oram", "benchmark","Pineapple", "Cascade", "Whimsical", "Zephyr", "Serendipity", "Quixotic", "Bubble", "Enigma", "Luminous", "Jubilant", "Mellifluous", "Saffron", "Euphoria", "Quirk", "Galaxy", "Breeze", "Aurora", "Mystic", "Ethereal", "Bountiful", "Velvet", "Meadow", "Whiskers", "Harmonious", "Citrus", "Gossamer", "Tranquil", "Ponder", "Frolic", "Blissful", "Mystify", "Blossom", "Wanderlust", "Enchant", "Luminary", "Ripple", "Enthrall", "Symphony", "Labyrinth", "Sizzle", "Jubilee", "Driftwood", "Quench", "Twilight", "Spellbound", "Tranquility", "Mirage", "Elixir", "Glimmer", "Enchanted"};
    static int testSize = -1;
    static SecureRandom rnd;
    static int maxAddress;

    // RUN WITH 3 ARGS : NUMBER_OF_CLIENTS, ORAM_ID AND TEST_SIZE
    public static void main(String[] args) throws SecretSharingException, InterruptedException {
        rnd = new SecureRandom("oram".getBytes());
        int nClients = Integer.parseInt(args[0]);
        int oramId = Integer.parseInt(args[1]);
        testSize = Integer.parseInt(args[2]);
        List<ORAMManager> oramManagerList = new ArrayList<ORAMManager>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 1; i < nClients+1; i++) {
           oramManagerList.add(new ORAMManager(i));
        }
        int treeHeight = 3;
        int nBlocksPerBucket = 4;
        int blockSize = 512;
        maxAddress = ORAMUtils.computeNumberOfNodes(treeHeight);

        oramManagerList.get(0).createORAM(oramId, treeHeight, nBlocksPerBucket, blockSize);

        for (ORAMManager oramManager : oramManagerList) {

            threads.add(new Thread(new Runnable(){
                @Override
                public void run() {
                    for (int i = 0; i < testSize; i++) {
                        randomAccess(oramManager.getORAM(oramId));
                    }
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
    }

    private static void randomAccess(ORAMObject oram) {
        Operation op = rnd.nextBoolean() ? Operation.WRITE : Operation.READ;
        int address = rnd.nextInt(maxAddress);
        if (op==Operation.WRITE){
            String randomWord;
            if (testSize>55)
                randomWord=expressions55[rnd.nextInt(expressions55.length)];
            else
                randomWord=expressions10[rnd.nextInt(expressions10.length)];
            byte[] response = oram.writeMemory(address, randomWord.getBytes());
            String responseString = response==null ? "null" : new String(response);
            logger.debug("write \""+randomWord+"\" to address"+address+". Response (oldValue): "+responseString);
        }
        else{
            byte[] response = oram.readMemory(address);
            String responseString = response==null ? "null" : new String(response);
            logger.debug("read from address"+address+". Response: "+responseString);
        }

    }



}
