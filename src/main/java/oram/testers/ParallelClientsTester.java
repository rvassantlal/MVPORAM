package oram.testers;

import oram.client.ORAMManager;
import oram.client.ORAMObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.util.Arrays;

public class ParallelClientsTester {
    private static final Logger logger = LoggerFactory.getLogger("benchmark");

    public static void main(String[] args) throws SecretSharingException, InterruptedException {
        ORAMManager oramManager = new ORAMManager(1);
        ORAMManager oramManager2 = new ORAMManager(2);

        int oramId = 1;
        int treeHeight = 3;
        int nBlocksPerBucket = 4;
        int blockSize = 512;
        ORAMObject oramInit = oramManager.createORAM(oramId, treeHeight, nBlocksPerBucket, blockSize);
        ORAMObject oram2 = oramManager2.getORAM(oramId);

        if (oramInit != null) {
            logger.info("ORAM was created with id {}", oramId);
        } else {
            logger.info("Failed to create ORAM with id {}", oramId);
        }

        ORAMObject oram = oramManager.getORAM(oramId);
        if (oram != null) {
            logger.info("ORAM with id {} exists", oramId);
        } else {
            logger.info("ORAM with id {} does not exist", oramId);
            System.exit(-1);
        }

        if (oram2 != null) {
            logger.info("ORAM was found with id {}", oramId);
        } else {
            logger.info("Failed to find ORAM with id {}", oramId);
            System.exit(-1);
        }

        Thread t = new Thread(new Runnable(){
            @Override
            public void run() {
               write1(oram2);
               write2(oram2);
            }
        });
        Thread t2 = new Thread(new Runnable(){
            @Override
            public void run() {
                write2(oram);
                write1(oram);
            }
        });
        t.start();
        t2.start();
        t2.join();
        t.join();

        byte[] response = oram.readMemory(1);
        String answer = response==null ? "null" : new String(response);
        logger.debug("Read from address 1: " + answer);


        response = oram.readMemory(2);
        answer = response==null ? "null" : new String(response);
        logger.debug("Read from address 2: " + answer);


        response = oram2.readMemory(1);
        answer = response==null ? "null" : new String(response);
        logger.debug("Read from address 1: " + answer);


        response = oram2.readMemory(2);
        answer = response==null ? "null" : new String(response);
        logger.debug("Read from address 2: " + answer);

        oramManager.close();
    }

    private static void write1(ORAMObject oram) {
        logger.debug("Write \"test1\" to address 1");
        byte[] response = oram.writeMemory(1, "test1".getBytes());
    }

    private static void write2(ORAMObject oram) {
        logger.debug("Write \"test\" to address 2");
        byte[] response = oram.writeMemory(2, "test".getBytes());
    }


}
