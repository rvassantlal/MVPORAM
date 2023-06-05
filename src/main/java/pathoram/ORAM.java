package pathoram;

import confidential.ConfidentialMessage;
import oram.ORAMUtils;
import oram.server.structure.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.snapshotIdentifiers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.stream.Collectors;

public class ORAM {
    private final Logger logger = LoggerFactory.getLogger("oram");
    private final int oramId;
    private final ORAMContext oramContext;
    private final TreeMap<Double,OramSnapshot> allTrees; // all versions
    private final List<OramSnapshot> outstandingTrees; //all versions that are not previous of any other version
    private final HashMap<Integer, ORAMClientContext> oramClientContexts;

    public ORAM(int oramId, int treeHeight, int clientId, int bucketSize, int blockSize,
                EncryptedPositionMap encryptedPositionMap, EncryptedStash encryptedStash){
        this.oramId = oramId;
        int treeSize = ORAMUtils.computeNumberOfNodes(treeHeight);
        this.oramContext = new ORAMContext(treeHeight, treeSize, bucketSize, blockSize);
        logger.debug("Total number of buckets: {}", treeSize);
        allTrees = new TreeMap<>();
        outstandingTrees = new LinkedList<>();
        double versionId = Double.parseDouble("1." + clientId);

        List<OramSnapshot> previous = new LinkedList<>();
        OramSnapshot snap = new OramSnapshot(versionId, treeSize, treeHeight, previous,
                encryptedPositionMap, encryptedStash);

        outstandingTrees.add(snap);
        allTrees.put(versionId, snap);
        oramClientContexts = new HashMap<>();
    }

    public ORAMContext getOramContext() {
        return oramContext;
    }

    public EncryptedPositionMap[] getPositionMaps(int clientId) {
        EncryptedPositionMap[] result = new EncryptedPositionMap[outstandingTrees.size()];
        double[] versionIds = new double[outstandingTrees.size()];
        int i = 0;
        for (OramSnapshot snapshot : outstandingTrees) {
            result[i] = snapshot.getPositionMap();
            snapshot.incrementReferenceCounter();
            versionIds[i] = snapshot.getVersionId();
            i++;
        }
        ORAMClientContext oramClientContext = new ORAMClientContext(versionIds);
        oramClientContexts.put(clientId, oramClientContext);
        return result;
    }

    public EncryptedStashesAndPaths getStashesAndPaths(byte pathId, int clientId) {
        ORAMClientContext oramClientContext = oramClientContexts.get(clientId);
        if (oramClientContext == null) {
            return null;
        }
        double[] versionIds = oramClientContext.getVersionIds();
        Queue<OramSnapshot> versions = new LinkedList<>();
        for (double versionId : versionIds) {
            versions.add(allTrees.get(versionId));
        }
        List<EncryptedStash> encryptedStashes = new LinkedList<>();
        int[] pathLocations = ORAMUtils.computePathLocations(pathId, oramContext.getTreeHeight());

        Map<Double, Map<Integer, EncryptedBucket>> paths = new TreeMap<>();
        Set<Double> visitedVersions = new HashSet<>();

        while (!versions.isEmpty()) {
            OramSnapshot version = versions.poll();
            visitedVersions.add(version.getVersionId());
            EncryptedStash encryptedStash = version.getStash();
            encryptedStashes.add(encryptedStash);

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

    public boolean doEviction(snapshotIdentifiers snapshots,byte[] newPositionMap,byte[] newStash,Integer pathID,List<byte[]> newPath, Integer senderId) {
        List<OramSnapshot> previousSnapshots;
        previousSnapshots = snapshots.getSnaps().stream().map(tree -> allTrees.get(tree)).collect(Collectors.toList());
        double snapId = Collections.max(snapshots.getSnaps()).intValue() + 1 + Double.parseDouble("0." + senderId.toString());
        OramSnapshot newTree = new OramSnapshot(TREE_SIZE, previousSnapshots, snapId);
        newTree.setPositionMap(newPositionMap);
        newTree.setStash(newStash);
        newTree.putPath(pathID, newPath);
        outstandingTrees.removeAll(previousSnapshots);
        outstandingTrees.add(newTree);
        allTrees.put(snapId,newTree);

        //removeDeadVersions(previousSnapshots, pathID);
        return true;
    }
    private void removeDeadVersions(List<OramSnapshot> previousTrees, Integer pathID) {
        for (OramSnapshot snapshot:previousTrees) {
            List<OramSnapshot> prev = snapshot.getPrevious();
            if(prev!=null && !prev.isEmpty())
                previousTrees.addAll(prev);
            snapshot.removePath(pathID);
            if(snapshot.isEmpty()){
                allTrees.remove(snapshot.getVersionId());
            }
        }
    }

    //gets one tree from this version

    public List<List<byte[]>> getTree() {
        return allTrees.values().stream().map(OramSnapshot::getDifTree).collect(Collectors.toList());
    }

    public int getTreeSize() {
        return TREE_SIZE;
    }
}
