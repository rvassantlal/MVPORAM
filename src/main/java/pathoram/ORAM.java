package pathoram;

import oram.ORAMUtils;
import oram.server.structure.EncryptedPositionMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.snapshotIdentifiers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ORAM {
    private final Logger logger = LoggerFactory.getLogger("oram");
    private final int TREE_SIZE;
    private final int TREE_LEVELS;
    private final int oramId;
    private TreeMap<Double,OramSnapshot> allTrees;
    private List<OramSnapshot> outstandingTrees;

    public ORAM(int oramId, int treeHeight, int clientId) {
        this.oramId = oramId;
        int nBuckets = ORAMUtils.computeNumberOfNodes(treeHeight);
        TREE_SIZE = nBuckets;
        TREE_LEVELS = treeHeight;
        logger.debug("Total number of buckets: {}", nBuckets);
        allTrees = new TreeMap<>();
        outstandingTrees = new ArrayList<>();
        double snapId = 1 + Double.parseDouble("0." + clientId);
        OramSnapshot snap = new OramSnapshot(TREE_SIZE, null, snapId);
        List<byte[]> newPath = new ArrayList<>();
        for (int i = 0; i < TREE_LEVELS; i++) {
            newPath.add(null);
        }
        for (int i = 0; i < TREE_SIZE/2; i++) {
            snap.putPath(i, newPath);
        }
        outstandingTrees.add(snap);
        allTrees.put(snapId,snap);
    }

    protected ORAM(int oramId, int size, Integer client_id) {
        this.oramId = oramId;
        TREE_SIZE = size;
        TREE_LEVELS = (int)(Math.log(TREE_SIZE+1) / Math.log(2));
        allTrees=new TreeMap<>();
        outstandingTrees = new ArrayList<>();
        Double snapId = 1+Double.parseDouble("0."+client_id.toString());
        OramSnapshot snap = new OramSnapshot(TREE_SIZE, null, snapId);
        List<byte[]> newPath = new ArrayList<>();
        for (int i = 0; i < TREE_LEVELS; i++) {
            newPath.add(null);
        }
        for (int i = 0; i < TREE_SIZE/2; i++) {
            snap.putPath(i, newPath);
        }
        outstandingTrees.add(snap);
        allTrees.put(snapId,snap);
    }
    protected byte[] getPositionMap() throws IOException {
        try(ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream oout = new ObjectOutputStream(out)){
            oout.writeInt(outstandingTrees.size());
            snapshotIdentifiers snapIds = new snapshotIdentifiers(outstandingTrees.size());
            List<byte[]> positionMaps = new ArrayList<>();
            int encryptedSize = 0;
            for ( OramSnapshot snapshot : outstandingTrees) {
                byte[] pm = snapshot.getPositionMap();
                encryptedSize += pm.length;
                positionMaps.add(pm);
                snapIds.add(snapshot.getId());
            }
            snapIds.writeExternal(oout);
            oout.writeInt(encryptedSize);
            for (byte[] pm: positionMaps) {
                oout.write(pm);
            }
            oout.flush();
            out.flush();
            return out.toByteArray();
        }
    }

    public EncryptedPositionMap[] getPositionMaps() {
        //Warning outstandingTree might change due to eviction executed using invokeOrdered
        EncryptedPositionMap[] result = new EncryptedPositionMap[outstandingTrees.size()];
        int i = 0;
        for (OramSnapshot snapshot : outstandingTrees) {
            byte[] pm = snapshot.getPositionMap();
            result[i++] = new EncryptedPositionMap(snapshot.getId(), pm);
        }
        return result;
    }

    public byte[] getPathAndStash(snapshotIdentifiers snapIds,List<Integer> pathIDs) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(out);
        List<OramSnapshot> snapshots;
        snapshots = snapIds.getSnaps().stream().map(tree -> allTrees.get(tree)).collect(Collectors.toList());
        if(snapshots.size() == 0)
            return null;
        List<TreeMap<Double,byte[]>> allPathStashes = new ArrayList<>();
        List<TreeMap<Double,List<byte[]>>> allPathPaths = new ArrayList<>();
        List<List<Double>> snapsByPath = new ArrayList<>();
        for (Integer pathID: pathIDs) {
            TreeMap<Double,byte[]> stashes = new TreeMap<>();
            TreeMap<Double,List<byte[]>> list = new TreeMap<>();
            List<Double> snapsInPath = new ArrayList<>();
            int location = TREE_SIZE/2+pathID;
            for (int i = TREE_LEVELS-1; i >= 0; i--) {
                boolean dataIsNull = true;
                /* Basically, this version iterates over the multiple versions of the tree
                 * It should know where to pick the info from or the eviction
                 * should pull from the older versions to the newer ones
                 */
                List<OramSnapshot> versionSnapshots = new ArrayList<>();
                snapIds.getSnaps().stream().map(id -> versionSnapshots.add(allTrees.get(id)));
                while (dataIsNull){
                    TreeMap<Double,byte[]> tempVersionStashes = new TreeMap<>();
                    TreeMap<Double,List<byte[]>> dataList = new TreeMap<>();
                    for (OramSnapshot snapshot:versionSnapshots) {
                        byte[] tempData = snapshot.getFromLocation(location);
                        if (tempData != null){
                            if(!stashes.containsKey(snapshot.getId()))
                                tempVersionStashes.put(snapshot.getId(),snapshot.getStash());
                            if(dataList.get(snapshot.getId())==null)
                                dataList.put(snapshot.getId(), new ArrayList<>());
                            dataList.get(snapshot.getId()).add(tempData);
                            if (!snapsInPath.contains(snapshot.getId()))
                                snapsInPath.add(snapshot.getId());
                            dataIsNull = false;
                        }
                    }
                    if(!dataIsNull) {
                        stashes.putAll(tempVersionStashes);
                        list.putAll(dataList);
                    }else {
                        List<OramSnapshot> newSnapshots=new ArrayList<>();
                        versionSnapshots.stream().map(snap -> newSnapshots.addAll(snap.getPrev()));
                        if(newSnapshots.size()==0)
                            dataIsNull=false;
                    }
                }
                location=location%2==0?location-2:location-1;
                location/=2;
            }
            oout.writeInt(snapsInPath.size());
            for (int i = 0; i < snapsInPath.size(); i++) {
                Double snapID = snapsInPath.get(i);
                oout.writeDouble(snapID);
                oout.writeInt(stashes.get(snapID).length);
                oout.writeInt(list.get(snapID).size());
            }
            allPathStashes.add(stashes);
            allPathPaths.add(list);
            snapsByPath.add(snapsInPath);
        }
        for (int i = 0; i < pathIDs.size(); i++) {
            TreeMap<Double, byte[]> stashes = allPathStashes.get(i);
            TreeMap<Double, List<byte[]>> paths = allPathPaths.get(i);
            for(Double snapId : snapsByPath.get(i)){
                oout.write(stashes.get(snapId));
                for(byte[] bucket : paths.get(snapId)){
                    oout.write(bucket);
                }
            }
        }
        oout.flush();
        return out.toByteArray();
    }

    public boolean doEviction(snapshotIdentifiers snapshots,byte[] newPositionMap,byte[] newStash,Integer pathID,List<byte[]> newPath, Integer senderId) {
        List<OramSnapshot> previousSnapshots;
        previousSnapshots = snapshots.getSnaps().stream().map(tree -> allTrees.get(tree)).collect(Collectors.toList());
        double snapId = Collections.max(snapshots.getSnaps()).intValue() + 1 + Double.parseDouble("0." + senderId.toString());
        OramSnapshot newTree = new OramSnapshot(TREE_SIZE, previousSnapshots, snapId);
        newTree.setPositionMap(newPositionMap);
        newTree.setStash(newStash);
        newTree.putPath(pathID, newPath);
        outstandingTrees.clear();
        outstandingTrees.add(newTree);
        allTrees.put(snapId,newTree);

        //removeDeadVersions(previousSnapshots, pathID);
        return true;
    }

    private void removeDeadVersions(List<OramSnapshot> previousTrees, Integer pathID) {
        for (OramSnapshot snapshot:previousTrees) {
            List<OramSnapshot> prev = snapshot.getPrev();
            if(prev!=null && !prev.isEmpty())
                previousTrees.addAll(prev);
            snapshot.removePath(pathID);
            if(snapshot.isEmpty()){
                allTrees.remove(snapshot.getId());
            }
        }
    }

    //gets one tree from this version
    public List<List<byte[]>> getTree() {
        return allTrees.values().stream().map(OramSnapshot::getTree).collect(Collectors.toList());
    }

    public int getTreeSize() {
        return TREE_SIZE;
    }

    public int getTreeLevels() {
        return TREE_LEVELS;
    }
}
