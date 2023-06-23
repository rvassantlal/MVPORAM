package oram.server.structure;

import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ORAM {
    private final Logger logger = LoggerFactory.getLogger("oram");
    private final int oramId;
    private final ORAMContext oramContext;
    private int treeVersions;
    private final List<OramSnapshot> outstandingTrees; //all versions that are not previous of any other version
    private final HashMap<Integer, ORAMClientContext> oramClientContexts;

    public ORAM(int oramId, int treeHeight, int clientId, int bucketSize, int blockSize,
                EncryptedPositionMap encryptedPositionMap, EncryptedStash encryptedStash){
        this.oramId = oramId;
        int treeSize = ORAMUtils.computeNumberOfNodes(treeHeight);
        this.oramContext = new ORAMContext(treeHeight, treeSize, bucketSize, blockSize);
        logger.debug("Total number of buckets: {}", treeSize);
        this.outstandingTrees = new LinkedList<>();
        this.treeVersions = 1;
        double versionId = Double.parseDouble("1." + clientId);

        OramSnapshot[] previous = new OramSnapshot[0];
        OramSnapshot snap = new OramSnapshot(versionId, previous,
                encryptedPositionMap, encryptedStash);

        outstandingTrees.add(snap);
        oramClientContexts = new HashMap<>();
    }

    public ORAMContext getOramContext() {
        return oramContext;
    }

    public EncryptedPositionMaps getPositionMaps(int clientId) {
        EncryptedPositionMap[] encryptedPositionMaps = new EncryptedPositionMap[outstandingTrees.size()];
        double[] outstandingVersionIds = new double[outstandingTrees.size()];
        OramSnapshot[] currentOutstandingVersions = new OramSnapshot[outstandingTrees.size()];

        int i = 0;
        double currentMax = 0;
        for (OramSnapshot snapshot : outstandingTrees) {
            encryptedPositionMaps[i] = snapshot.getPositionMap();
            snapshot.incrementReferenceCounter();
            currentOutstandingVersions[i] = snapshot;
            outstandingVersionIds[i] = snapshot.getVersionId();
            currentMax = Math.max(currentMax, snapshot.getVersionId());
            i++;
        }
        double newVersionId = (int) currentMax + Double.parseDouble("1." + clientId);
        logger.debug("Creating oram client context for client {} in oram {}", clientId, oramId);
        ORAMClientContext oramClientContext = new ORAMClientContext(currentOutstandingVersions, newVersionId);

        oramClientContexts.put(clientId, oramClientContext);
        return new EncryptedPositionMaps(newVersionId, outstandingVersionIds, encryptedPositionMaps);
    }

    public EncryptedStashesAndPaths getStashesAndPaths(byte pathId, int clientId) {
        ORAMClientContext oramClientContext = oramClientContexts.get(clientId);
        if (oramClientContext == null) {
            logger.error("There is no client context for {} in oram {} ({})", clientId, oramId, oramClientContexts.keySet());
            return null;
        }
        OramSnapshot[] outstandingTrees = oramClientContext.getOutstandingVersions();
        Map<Double, Set<Double>> versionPaths = new HashMap<>();// Map<Version id, Set<OutStanding id>>

        int[] pathLocations = ORAMUtils.computePathLocations(pathId, oramContext.getTreeHeight());
        Map<Double, EncryptedStash> encryptedStashes = new HashMap<>();
        Map<Double, Map<Integer, EncryptedBucket>> pathContents = new TreeMap<>();

        for (OramSnapshot outstandingTree : outstandingTrees) {
            Set<Double> traversedVersions = traverseVersions(outstandingTree, pathLocations, encryptedStashes,
                    pathContents);
            for (double traversedVersion : traversedVersions) {
                Set<Double> outstandingTreeIds = versionPaths.computeIfAbsent(traversedVersion,
                        k -> new HashSet<>(outstandingTrees.length));
                outstandingTreeIds.add(outstandingTree.getVersionId());
            }
        }

        Map<Double, EncryptedBucket[]> compactedPaths = new HashMap<>(pathContents.size());
        for (Map.Entry<Double, Map<Integer, EncryptedBucket>> entry : pathContents.entrySet()) {
            EncryptedBucket[] buckets = new EncryptedBucket[entry.getValue().size()];
            int i = 0;
            for (EncryptedBucket value : entry.getValue().values()) {
                buckets[i++] = value;
            }
            compactedPaths.put(entry.getKey(), buckets);
        }

        return new EncryptedStashesAndPaths(encryptedStashes, compactedPaths, versionPaths);
    }

    private Set<Double> traverseVersions(OramSnapshot outstanding, int[] pathLocations,
                                         Map<Double, EncryptedStash> encryptedStashes,
                                         Map<Double, Map<Integer, EncryptedBucket>> pathContents) {
        Queue<OramSnapshot> queue = new LinkedList<>();
        queue.add(outstanding);
        Set<Double> visitedVersions = new HashSet<>(treeVersions);

        while (!queue.isEmpty()) {
            OramSnapshot version = queue.poll();
            visitedVersions.add(version.getVersionId());
            EncryptedStash encryptedStash = version.getStash();
            encryptedStashes.put(version.getVersionId(), encryptedStash);
            Set<OramSnapshot> toVisit = null;
            for (int pathLocation : pathLocations) {
                EncryptedBucket bucket = version.getFromLocation(pathLocation);
                if (bucket != null) {
                    Map<Integer, EncryptedBucket> encryptedBuckets = pathContents.computeIfAbsent(version.getVersionId(),
                            k -> new HashMap<>(oramContext.getTreeLevels()));
                    encryptedBuckets.put(pathLocation, bucket);
                } else {
                    for (OramSnapshot previousVersion : version.getPrevious()) {
                        if (!visitedVersions.contains(previousVersion.getVersionId())) {
                            if (toVisit == null) {
                                toVisit = new HashSet<>(version.getPrevious().size());
                            }
                            toVisit.add(previousVersion);
                        }
                    }
                }
            }
            if (toVisit != null) {
                queue.addAll(toVisit);
            }
        }
        return visitedVersions;
    }

    public boolean performEviction(EncryptedStash encryptedStash, EncryptedPositionMap encryptedPositionMap,
                                   Map<Integer, EncryptedBucket> encryptedPath, int clientId) {
        ORAMClientContext oramClientContext = oramClientContexts.remove(clientId);
        if (oramClientContext == null) {
            return false;
        }
        OramSnapshot[] outstandingVersions = oramClientContext.getOutstandingVersions();
        for (OramSnapshot outstandingVersion : outstandingVersions) {
            outstandingVersion.decrementReferenceCounter();
        }
        double newVersionId = oramClientContext.getNewVersionId();

        OramSnapshot newVersion = new OramSnapshot(newVersionId,
                outstandingVersions, encryptedPositionMap, encryptedStash);
        for (Map.Entry<Integer, EncryptedBucket> entry : encryptedPath.entrySet()) {
            newVersion.setToLocation(entry.getKey(), entry.getValue());
        }
        for (OramSnapshot outstandingVersion : outstandingVersions) {
            outstandingTrees.remove(outstandingVersion);
        }
        outstandingTrees.add(newVersion);
        treeVersions++;
        logger.debug("Number of outstanding versions: {} out of {}", outstandingTrees.size(), treeVersions);
        logger.debug("====== Start garbage collection for {} ======", clientId);
        garbageCollect(newVersion);
        logger.debug("====== End garbage collection for {} ======", clientId);
        return true;
    }


    private void garbageCollect(OramSnapshot newVersion) {
        boolean[] locationsMarker = new boolean[oramContext.getTreeSize()];
        newVersion.garbageCollect(locationsMarker);
    }

    @Override
    public String toString() {
        return String.valueOf(oramId);
    }

    public String printORAM(){
        Queue<OramSnapshot> snapshots = new LinkedList<>(outstandingTrees);
        int[] bucketSize = new int[oramContext.getTreeSize()];
        int[] numberOfNodesByLocation = new int[oramContext.getTreeSize()];
        while (!snapshots.isEmpty()) {
            OramSnapshot snapshot = snapshots.poll();
            for (int i = 0; i < oramContext.getTreeSize(); i++) {
                EncryptedBucket bucket = snapshot.getFromLocation(i);
                if (bucket != null) {
                    numberOfNodesByLocation[i]++;
                }
                if (bucket != null) {
                        bucketSize[i] = bucket.getBlocks().length;
                }
            }
            snapshots.addAll(snapshot.getPrevious());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Printing ORAM ").append(oramId).append("\n");
        sb.append("(Location, Is Empty?, Number of versions)\n");
        for (int i = 0; i < oramContext.getTreeSize(); i++) {
            sb.append("(").append(i).append(", ").append(bucketSize[i]==0).append(", ").append(numberOfNodesByLocation[i]).append(") ");
            if(i%7 == 0 && i != 0)
                sb.append("\n");
        }
        return sb.toString();
    }
}
