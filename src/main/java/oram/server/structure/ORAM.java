package oram.server.structure;

import oram.utils.ORAMUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ORAM {
    private final Logger logger = LoggerFactory.getLogger("oram");
    private final int oramId;
    private final ORAMContext oramContext;
    private final List<OramSnapshot> outstandingTrees; //all versions that are not previous of any other version
    private final HashMap<Integer, ORAMClientContext> oramClientContexts;

    public ORAM(int oramId, int treeHeight, int clientId, int bucketSize, int blockSize,
                EncryptedPositionMap encryptedPositionMap, EncryptedStash encryptedStash){
        this.oramId = oramId;
        int treeSize = ORAMUtils.computeNumberOfNodes(treeHeight);
        this.oramContext = new ORAMContext(treeHeight, treeSize, bucketSize, blockSize);
        logger.debug("Total number of buckets: {}", treeSize);
        outstandingTrees = new LinkedList<>();
        double versionId = Double.parseDouble("1." + clientId);

        OramSnapshot[] previous = new OramSnapshot[0];
        OramSnapshot snap = new OramSnapshot(versionId, treeSize, treeHeight, previous,
                encryptedPositionMap, encryptedStash);

        outstandingTrees.add(snap);
        oramClientContexts = new HashMap<>();
    }

    public ORAMContext getOramContext() {
        return oramContext;
    }

    public EncryptedPositionMap[] getPositionMaps(int clientId) {
        EncryptedPositionMap[] result = new EncryptedPositionMap[outstandingTrees.size()];
        OramSnapshot[] currentOutstandingVersions = new OramSnapshot[outstandingTrees.size()];
        int i = 0;
        for (OramSnapshot snapshot : outstandingTrees) {
            result[i] = snapshot.getPositionMap();
            snapshot.incrementReferenceCounter();
            currentOutstandingVersions[i] = snapshot;
            i++;
        }
        ORAMClientContext oramClientContext = new ORAMClientContext(currentOutstandingVersions);
        oramClientContexts.put(clientId, oramClientContext);
        return result;
    }

    public EncryptedStashesAndPaths getStashesAndPaths(byte pathId, int clientId) {
        ORAMClientContext oramClientContext = oramClientContexts.get(clientId);
        if (oramClientContext == null) {
            return null;
        }

        Queue<OramSnapshot> versions = new LinkedList<>();
        Collections.addAll(versions, oramClientContext.getOutstandingVersions());

        Map<Double, EncryptedStash> encryptedStashes = new HashMap<>();
        int[] pathLocations = ORAMUtils.computePathLocations(pathId, oramContext.getTreeHeight());

        Map<Double, Map<Integer, EncryptedBucket>> paths = new TreeMap<>();
        Set<Double> visitedVersions = new HashSet<>();

        while (!versions.isEmpty()) {
            OramSnapshot version = versions.poll();
            visitedVersions.add(version.getVersionId());
            EncryptedStash encryptedStash = version.getStash();
            encryptedStashes.put(version.getVersionId(), encryptedStash);

            for (int pathLocation : pathLocations) {
                EncryptedBucket bucket = version.getFromLocation(pathLocation);
                if (bucket != null) {
                    Map<Integer, EncryptedBucket> encryptedBuckets = paths.computeIfAbsent(version.getVersionId(),
                            k -> new HashMap<>(oramContext.getTreeLevels()));
                    encryptedBuckets.put(pathLocation, bucket);
                } else {
                    for (OramSnapshot previousVersion : version.getPrevious()) {
                        if (!visitedVersions.contains(previousVersion.getVersionId())) {
                            versions.add(previousVersion);
                        }
                    }
                }
            }
        }

        Map<Double, EncryptedBucket[]> compactedPaths = new HashMap<>(paths.size());
        for (Map.Entry<Double, Map<Integer, EncryptedBucket>> entry : paths.entrySet()) {
            EncryptedBucket[] buckets = new EncryptedBucket[entry.getValue().size()];
            int i = 0;
            for (EncryptedBucket value : entry.getValue().values()) {
                buckets[i++] = value;
            }
            compactedPaths.put(entry.getKey(), buckets);
        }

        return new EncryptedStashesAndPaths(encryptedStashes, compactedPaths);
    }

    public boolean performEviction(EncryptedStash encryptedStash, EncryptedPositionMap encryptedPositionMap,
                                   Map<Integer, EncryptedBucket> encryptedPath, int clientId) {
        ORAMClientContext oramClientContext = oramClientContexts.remove(clientId);
        if (oramClientContext == null) {
            return false;
        }
        OramSnapshot[] outstandingVersions = oramClientContext.getOutstandingVersions();
        double currentMax = 0;
        for (OramSnapshot outstandingVersion : outstandingVersions) {
            currentMax = Math.max(currentMax, outstandingVersion.getVersionId());
            outstandingVersion.decrementReferenceCounter();
        }
        double newVersionId = (int) currentMax + Double.parseDouble("1." + clientId);

        OramSnapshot newVersion = new OramSnapshot(newVersionId, oramContext.getTreeSize(),
                oramContext.getTreeHeight(), outstandingVersions, encryptedPositionMap, encryptedStash);
        for (Map.Entry<Integer, EncryptedBucket> entry : encryptedPath.entrySet()) {
            newVersion.setToLocation(entry.getKey(), entry.getValue());
        }
        for (OramSnapshot outstandingVersion : outstandingVersions) {
            outstandingTrees.remove(outstandingVersion);
        }
        //removeDeadVersions(previousSnapshots, pathID);
        return true;
    }

    /**
     * TODO garbage collect old versions
     * @param previousTrees
     * @param pathID
     */
    private void removeDeadVersions(List<OramSnapshot> previousTrees, Integer pathID) {
        for (OramSnapshot snapshot:previousTrees) {
            OramSnapshot[] prev = snapshot.getPrevious();
            if(prev != null && prev.length > 0)
                Collections.addAll(previousTrees, prev);
            snapshot.removePath(pathID);

        }
    }

    @Override
    public String toString() {
        return String.valueOf(oramId);
    }
}
