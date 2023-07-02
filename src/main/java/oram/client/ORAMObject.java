package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.client.structure.*;
import oram.messages.EvictionORAMMessage;
import oram.messages.ORAMMessage;
import oram.messages.StashPathORAMMessage;
import oram.server.structure.EncryptedBucket;
import oram.server.structure.EncryptedPositionMap;
import oram.server.structure.EncryptedStash;
import oram.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.security.SecureRandom;
import java.util.*;

public class ORAMObject {
    private final Logger logger = LoggerFactory.getLogger("oram");
    private final ConfidentialServiceProxy serviceProxy;
    private final int oramId;
    private final ORAMContext oramContext;
    private final EncryptionManager encryptionManager;
    private final SecureRandom rndGenerator;
    byte[] oldContent = null;
    private boolean isRealAccess;

    public ORAMObject(ConfidentialServiceProxy serviceProxy, int oramId, ORAMContext oramContext,
                      EncryptionManager encryptionManager) throws SecretSharingException {
        this.serviceProxy = serviceProxy;
        this.oramId = oramId;
        this.oramContext = oramContext;
        this.encryptionManager = encryptionManager;
        this.rndGenerator = new SecureRandom("oram".getBytes());
    }

    /**
     * Read the memory address.
     *
     * @param address Memory address.
     * @return Content located at the memory address.
     */
    public byte[] readMemory(int address) {
        if (address < 0 || oramContext.getTreeSize() <= address)
            return null;
        byte[] result = access(Operation.READ, address, null);
        System.gc();
        return result;
    }

    /**
     * Write content to the memory address.
     *
     * @param address Memory address.
     * @param content Content to write.
     * @return Old content located at the memory address.
     */
    public byte[] writeMemory(int address, byte[] content) {
        if (address < 0 || oramContext.getTreeSize() <= address)
            return null;
        byte[] result = access(Operation.WRITE, address, content);
        System.gc();
        return result;
    }

    private byte[] access(Operation op, int address, byte[] newContent) {
        this.isRealAccess = true;
        oldContent = null;
        PositionMaps oldPositionMaps = getPositionMaps();
        if (oldPositionMaps == null) {
            logger.error("Position map of oram {} is null", oramId);
            return null;
        }

        PositionMap mergedPositionMap = mergePositionMaps(oldPositionMaps.getPositionMaps());
        byte pathId = getPathId(mergedPositionMap, address);
        Stash mergedStash = getPS(pathId, op, address, newContent, oldPositionMaps, mergedPositionMap);
        boolean isEvicted = evict(mergedPositionMap, mergedStash, pathId, op, address,
                oldPositionMaps.getNewVersionId());

        if (!isEvicted) {
            logger.error("Failed to do eviction on oram {}", oramId);
        }
        return oldContent;
    }

    public byte getPathId(PositionMap mergedPositionMap, int address) {
        byte pathId = mergedPositionMap.getPathAt(address);
        if (pathId == ORAMUtils.DUMMY_PATH) {
            pathId = generateRandomPathId();
            this.isRealAccess = false;
        }
        return pathId;
    }

    public Stash getPS(byte pathId, Operation op, int address, byte[] newContent,
                       PositionMaps positionMaps, PositionMap mergedPositionMap) {
        StashesAndPaths stashesAndPaths = getStashesAndPaths(pathId);
        if (stashesAndPaths == null) {
            logger.error("States and paths of oram {} are null", oramId);
            return null;
        }
        Stash mergedStash = mergeStashesAndPaths(stashesAndPaths.getStashes(), stashesAndPaths.getPaths(),
                stashesAndPaths.getVersionPaths(), positionMaps, mergedPositionMap);

        Block block = mergedStash.getBlock(address);

        if (this.isRealAccess && op == Operation.READ) {
            if (block == null) {
                logger.error("Reading address {} from pathId {}", address, pathId);
                logger.error(positionMaps.toString());
                logger.error(stashesAndPaths.toString());
                logger.error(mergedPositionMap.toString());
                logger.error(mergedStash.toString());
            }
            oldContent = block.getContent();
        } else if (op == Operation.WRITE) {
            if (block == null) {
                block = new Block(oramContext.getBlockSize(), address, newContent);
                mergedStash.putBlock(block);
            } else {
                oldContent = block.getContent();
                block.setContent(newContent);
            }
        }
        return mergedStash;
    }

    public boolean evict(PositionMap positionMap, Stash stash, byte oldPathId,
                         Operation op, int changedAddress, double newVersionId) {
        byte newPathId = generateRandomPathId();
        if (op == Operation.WRITE) {
            positionMap.setVersionIdAt(changedAddress, newVersionId);
        }
        if (op == Operation.WRITE || (op == Operation.READ && oldPathId != ORAMUtils.DUMMY_PATH)) {
            positionMap.setPathAt(changedAddress, newPathId);
        }
        Map<Integer, Bucket> path = new HashMap<>(oramContext.getTreeLevels());
        Stash remainingBlocks = populatePath(positionMap, stash, oldPathId, path);

        EncryptedStash encryptedStash = encryptionManager.encryptStash(remainingBlocks);
        EncryptedPositionMap encryptedPositionMap = encryptionManager.encryptPositionMap(positionMap);
        Map<Integer, EncryptedBucket> encryptedPath = encryptionManager.encryptPath(oramContext, path);
        return sendEvictionRequest(encryptedStash, encryptedPositionMap, encryptedPath);
    }

    private Stash populatePath(PositionMap positionMap, Stash stash, byte oldPathId,
                               Map<Integer, Bucket> pathToPopulate) {
        int[] oldPathLocations = ORAMUtils.computePathLocations(oldPathId, oramContext.getTreeHeight());
        Map<Byte, List<Integer>> commonPaths = new HashMap<>();
        for (int pathLocation : oldPathLocations) {
            pathToPopulate.put(pathLocation, new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize()));
        }
        Stash remainingBlocks = new Stash(oramContext.getBlockSize());
        for (Block block : stash.getBlocks()) {
            int address = block.getAddress();
            byte pathId = positionMap.getPathAt(address);
            List<Integer> commonPath = commonPaths.get(pathId);
            if (commonPath == null) {
                int[] pathLocations = ORAMUtils.computePathLocations(pathId, oramContext.getTreeHeight());
                commonPath = ORAMUtils.computePathIntersection(oramContext.getTreeLevels(), oldPathLocations,
                        pathLocations);
                commonPaths.put(pathId, commonPath);
            }
            boolean isPathEmpty = false;
            for (int pathLocation : commonPath) {
                Bucket bucket = pathToPopulate.get(pathLocation);
                if (bucket.putBlock(block)) {
                    isPathEmpty = true;
                    break;
                }
            }
            if (!isPathEmpty) {
                remainingBlocks.putBlock(block);
            }
        }
        return remainingBlocks;
    }

    private boolean sendEvictionRequest(EncryptedStash encryptedStash, EncryptedPositionMap encryptedPositionMap,
                                        Map<Integer, EncryptedBucket> encryptedPath) {
        try {
            ORAMMessage request = new EvictionORAMMessage(oramId, encryptedStash, encryptedPositionMap, encryptedPath);
            byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.EVICTION, request);
            if (serializedRequest == null) {
                return false;
            }

            Response response = serviceProxy.invokeOrdered(serializedRequest);
            if (response == null || response.getPainData() == null) {
                return false;
            }
            Status status = Status.getStatus(response.getPainData()[0]);
            return status != Status.FAILED;
        } catch (SecretSharingException e) {
            return false;
        }
    }

    private byte generateRandomPathId() {
        return (byte) rndGenerator.nextInt(1 << oramContext.getTreeHeight()); //2^height
    }

    private Stash mergeStashesAndPaths(Map<Double, Stash> stashes, Map<Double, Bucket[]> paths,
                                       Map<Double, Set<Double>> versionPaths, PositionMaps positionMaps,
                                       PositionMap mergedPositionMap) {
        Map<Integer, Block> recentBlocks = new HashMap<>();
        Map<Integer, Double> recentVersionIds = new HashMap<>();
        PositionMap[] positionMapsArray = positionMaps.getPositionMaps();
        double[] outstandingIds = positionMaps.getOutstandingVersionIds();
        Map<Double, PositionMap> positionMapPerVersion = new HashMap<>(outstandingIds.length);
        for (int i = 0; i < outstandingIds.length; i++) {
            positionMapPerVersion.put(outstandingIds[i], positionMapsArray[i]);
        }

        mergeStashes(recentBlocks, recentVersionIds, stashes, versionPaths, positionMapPerVersion, mergedPositionMap);
        mergePaths(recentBlocks, recentVersionIds, paths, versionPaths, positionMapPerVersion, mergedPositionMap);

        Stash mergedStash = new Stash(oramContext.getBlockSize());
        for (Block recentBlock : recentBlocks.values()) {
            mergedStash.putBlock(recentBlock);
        }
        return mergedStash;
    }

    private void mergePaths(Map<Integer, Block> recentBlocks, Map<Integer, Double> recentVersionIds,
                            Map<Double, Bucket[]> paths, Map<Double, Set<Double>> versionPaths,
                            Map<Double, PositionMap> positionMaps, PositionMap mergedPositionMap) {
        Map<Double, List<Block>> blocksToMerge = new HashMap<>();
        for (Map.Entry<Double, Bucket[]> entry : paths.entrySet()) {
            List<Block> blocks = new LinkedList<>();
            for (Bucket bucket : entry.getValue()) {
                for (Block block : bucket.readBucket()) {
                    if (block != null) {
                        blocks.add(block);
                    }
                }
            }
            blocksToMerge.put(entry.getKey(), blocks);
        }
        selectRecentBlocks(recentBlocks, recentVersionIds, versionPaths, positionMaps, mergedPositionMap, blocksToMerge);
    }

    private void mergeStashes(Map<Integer, Block> recentBlocks, Map<Integer, Double> recentVersionIds,
                              Map<Double, Stash> stashes, Map<Double, Set<Double>> versionPaths,
                              Map<Double, PositionMap> positionMaps, PositionMap mergedPositionMap) {
        Map<Double, List<Block>> blocksToMerge = new HashMap<>();
        for (Map.Entry<Double, Stash> entry : stashes.entrySet()) {
            blocksToMerge.put(entry.getKey(), entry.getValue().getBlocks());
        }
        selectRecentBlocks(recentBlocks, recentVersionIds, versionPaths, positionMaps, mergedPositionMap, blocksToMerge);
    }

    private void selectRecentBlocks(Map<Integer, Block> recentBlocks, Map<Integer, Double> recentVersionIds,
                                    Map<Double, Set<Double>> versionPaths, Map<Double, PositionMap> positionMaps,
                                    PositionMap mergedPositionMap, Map<Double, List<Block>> blocksToMerge) {
        for (Map.Entry<Double, List<Block>> entry : blocksToMerge.entrySet()) {
            Set<Double> outstandingTreeIds = versionPaths.get(entry.getKey());
            for (Block block : entry.getValue()) {
                for (double outstandingTreeId : outstandingTreeIds) {
                    PositionMap outstandingPositionMap = positionMaps.get(outstandingTreeId);
                    int blockAddress = block.getAddress();
                    double blockVersionIdInOutstandingTree = outstandingPositionMap.getVersionIdAt(blockAddress);
                    if (blockVersionIdInOutstandingTree == mergedPositionMap.getVersionIdAt(blockAddress)) {
                        recentBlocks.put(blockAddress, block);
                        recentVersionIds.put(blockAddress, blockVersionIdInOutstandingTree);
                    }
                }
            }
        }
    }
    private StashesAndPaths getStashesAndPaths(byte pathId) {
        try {
            ORAMMessage request = new StashPathORAMMessage(oramId, pathId);
            byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_STASH_AND_PATH, request);
            if (serializedRequest == null) {
                return null;
            }
            Response response = serviceProxy.invokeUnordered(serializedRequest);
            if (response == null || response.getPainData() == null)
                return null;

            return encryptionManager.decryptStashesAndPaths(oramContext, response.getPainData());
        } catch (SecretSharingException e) {
            return null;
        }
    }

    private PositionMap mergePositionMaps(PositionMap[] positionMaps) {
        int treeSize = oramContext.getTreeSize();
        byte[] pathIds = new byte[treeSize];
        double[] versionIds = new double[treeSize];

        for (int address = 0; address < treeSize; address++) {
            byte recentPathId = ORAMUtils.DUMMY_PATH;
            double recentVersionId = ORAMUtils.DUMMY_VERSION;
            for (PositionMap positionMap : positionMaps) {
                if (positionMap.getPathIds().length == 0)
                    continue;
                byte pathId = positionMap.getPathAt(address);
                double versionId = positionMap.getVersionIdAt(address);
                if (versionId > recentVersionId) {
                    recentVersionId = versionId;
                    recentPathId = pathId;
                }
            }
            pathIds[address] = recentPathId;
            versionIds[address] = recentVersionId;
        }
        return new PositionMap(versionIds, pathIds);
    }

    private PositionMaps getPositionMaps() {
        try {
            ORAMMessage request = new ORAMMessage(oramId);
            byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_POSITION_MAP, request);
            if (serializedRequest == null) {
                return null;
            }
            Response response = serviceProxy.invokeOrdered(serializedRequest);
            if (response == null || response.getPainData() == null)
                return null;
            return encryptionManager.decryptPositionMaps(response.getPainData());
        } catch (SecretSharingException e) {
            return null;
        }
    }
}
