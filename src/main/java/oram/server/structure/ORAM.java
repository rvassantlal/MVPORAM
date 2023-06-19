package oram.server.structure;

import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

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
                encryptedPositionMap, encryptedStash, -1);

        outstandingTrees.add(snap);
        oramClientContexts = new HashMap<>();
    }

    public ORAMContext getOramContext() {
        return oramContext;
    }

    public EncryptedPositionMap[] getPositionMaps(int clientId) {
        EncryptedPositionMap[] result = new EncryptedPositionMap[outstandingTrees.size()];
        OramSnapshot[] currentOutstandingVersions = new OramSnapshot[outstandingTrees.size()];
        logger.debug("{}", printORAM());
        int i = 0;
        for (OramSnapshot snapshot : outstandingTrees) {
            result[i] = snapshot.getPositionMap();
            snapshot.incrementReferenceCounter();
            currentOutstandingVersions[i] = snapshot;
            i++;
        }
        logger.debug("Creating oram client context for client {} in oram {}", clientId, oramId);
        ORAMClientContext oramClientContext = new ORAMClientContext(currentOutstandingVersions);

        oramClientContexts.put(clientId, oramClientContext);
        return result;
    }

    public EncryptedStashesAndPaths getStashesAndPaths(byte pathId, int clientId) {
        ORAMClientContext oramClientContext = oramClientContexts.get(clientId);
        if (oramClientContext == null) {
            logger.debug("There is no client context for {} in oram {}", clientId, oramId);
            return null;
        }
        OramSnapshot[] outstanding = oramClientContext.getOutstandingVersions();
        Map<Double,Queue<OramSnapshot>> pathsToOutstanding = new TreeMap<>();
        for (int i = 0; i < outstanding.length; i++) {
            pathsToOutstanding.put(outstanding[i].getVersionId(), new LinkedList<>());
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
                }
                else {
                    for (OramSnapshot previousVersion : version.getPrevious()) {
                        pathsToOutstanding.get(version.getVersionId()).add(previousVersion);
                    }
                }
            }
        }
        Map<Double,List<Double>> pathIdsToOutstanding = new HashMap<>(pathsToOutstanding.size());
        for (Double outstandingId : pathsToOutstanding.keySet()) {
            pathIdsToOutstanding.put(outstandingId,new ArrayList<>());
        }
        for (Map.Entry<Double, Queue<OramSnapshot>> entry : pathsToOutstanding.entrySet()) {
            Queue<OramSnapshot> previousSnaps = entry.getValue();
            while (!previousSnaps.isEmpty()){
                OramSnapshot prev = previousSnaps.poll();
                pathIdsToOutstanding.get(entry.getKey()).add(prev.getVersionId());
                if (!visitedVersions.contains(prev.getVersionId())){
                    visitedVersions.add(prev.getVersionId());
                    EncryptedStash encryptedStash = prev.getStash();
                    encryptedStashes.put(prev.getVersionId(), encryptedStash);

                    for (int pathLocation : pathLocations) {
                        EncryptedBucket bucket = prev.getFromLocation(pathLocation);
                        if (bucket != null) {
                            Map<Integer, EncryptedBucket> encryptedBuckets = paths.computeIfAbsent(prev.getVersionId(),
                                    k -> new HashMap<>(oramContext.getTreeLevels()));
                            encryptedBuckets.put(pathLocation, bucket);
                        }
                        else {
                            previousSnaps.addAll(Arrays.asList(prev.getPrevious()));
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

        return new EncryptedStashesAndPaths(encryptedStashes, compactedPaths,pathIdsToOutstanding);
    }

    public boolean performEviction(EncryptedStash encryptedStash, EncryptedPositionMap encryptedPositionMap,
                                   Map<Integer, EncryptedBucket> encryptedPath, int clientId, int pathId) {
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
                oramContext.getTreeHeight(), outstandingVersions, encryptedPositionMap, encryptedStash, pathId);
        for (Map.Entry<Integer, EncryptedBucket> entry : encryptedPath.entrySet()) {
            newVersion.setToLocation(entry.getKey(), entry.getValue());
        }
        for (OramSnapshot outstandingVersion : outstandingVersions) {
            outstandingTrees.remove(outstandingVersion);
        }
        outstandingTrees.add(newVersion);
        removeDeadVersions(Arrays.asList(outstandingVersions), pathId);
        return true;
    }


    private void removeDeadVersions(List<OramSnapshot> previousTrees, Integer pathID) {
        Set<OramSnapshot> taintedSnapshots = new TreeSet<>();
        for (ORAMClientContext value : oramClientContexts.values()) {
            for (OramSnapshot snapshot : value.getOutstandingVersions()) {
                taintedSnapshots.addAll(snapshot.getPaths());
            }
        }
        List<Integer> pathIds = new ArrayList<>();
        pathIds.add(pathID);
        for (OramSnapshot previousTree : previousTrees) {
            previousTree.removePath(pathIds,taintedSnapshots);
        }

    }

    @Override
    public String toString() {
        return String.valueOf(oramId);
    }

    public String printORAM(){
        Queue<OramSnapshot> snapshots = new LinkedList<>(outstandingTrees);
        double[] lastVersion = new double[oramContext.getTreeSize()];
        Arrays.fill(lastVersion,-1);
        int[] bucketSize = new int[oramContext.getTreeSize()];
        int[] numberOfNodesByLocation = new int[oramContext.getTreeSize()];
        while (!snapshots.isEmpty()) {
            OramSnapshot snapshot = snapshots.poll();
            for (int i = 0; i < oramContext.getTreeSize(); i++) {
                EncryptedBucket bucket = snapshot.getFromLocation(i);
                if (bucket != null) {
                    numberOfNodesByLocation[i]++;
                }
                if(lastVersion[i] < snapshot.getVersionId()) {
                    lastVersion[i] = snapshot.getVersionId();
                    if (bucket != null) {
                        bucketSize[i] = bucket.getBlocks().length;
                    }
                }
            }
            snapshots.addAll(Arrays.asList(snapshot.getPrevious()));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Printing ORAM ").append(oramId).append("\n");
        sb.append("(Location, Most Recent Version, Number of Buckets, Number of repetitions)\n");
        for (int i = 0; i < oramContext.getTreeSize(); i++) {
            sb.append("(").append(i).append(", ").append(bucketSize[i]).append(", ").append(numberOfNodesByLocation[i]).append(") ");
            if(i%7 == 0 && i != 0)
                sb.append("\n");
        }
        return sb.toString();
    }

    public Double[] getClientContext(int clientId) {
        return Arrays.stream(oramClientContexts.get(clientId).getOutstandingVersions()).map(OramSnapshot::getVersionId).toArray(Double[]::new);
    }
}
