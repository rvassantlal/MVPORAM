package pathoram;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Oram {
    private static Integer TREE_SIZE;
    private static Integer TREE_LEVELS;
    private TreeMap<Double,OramSnapshot> allTrees;
    private List<OramSnapshot> outstandingTrees;

    protected Oram(int size,Integer client_id) {
        TREE_SIZE = size;
        TREE_LEVELS = (int)(Math.log(TREE_SIZE+1) / Math.log(2));
        allTrees=new TreeMap<>();
        outstandingTrees = new ArrayList<>();
        Double snapId = 1+Double.parseDouble("0."+client_id.toString());
        OramSnapshot snap = new OramSnapshot(TREE_SIZE, null, snapId);
        int numberOfPaths = TREE_SIZE/TREE_LEVELS;
        TreeMap<Integer, byte[]> newPath = new TreeMap<>();
        for (int i = 0; i < TREE_LEVELS; i++) {
			newPath.put(i, null);
		}
        for (int i = 0; i < numberOfPaths; i++) {
        	snap.putPath(i, newPath);
		}
        outstandingTrees.add(snap);
        allTrees.put(snapId,snap);
    }
    protected byte[] getPositionMap() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(out);
        oout.writeInt(TREE_SIZE);
        ArrayList<Double> snapIds = new ArrayList<>();
        ArrayList<byte[]> positionMaps = new ArrayList<>();
        for ( OramSnapshot snapshot : outstandingTrees) {
            positionMaps.add(snapshot.getPositionMap());
            snapIds.add(snapshot.getId());
        }
        oout.writeObject(positionMaps);
        oout.writeObject(snapIds);
        return out.toByteArray();
    }

    public byte[] getPathAndStash(List<Double> snapIds,List<Integer> pathIDs) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(out);
        List<OramSnapshot> snapshots = new ArrayList();
       	snapshots = snapIds.stream().map(tree -> allTrees.get(tree)).collect(Collectors.toList());
        if(snapshots.size() == 0)
            return null;
        TreeMap<Double,byte[]> stashes = new TreeMap<>();
        TreeMap<Double,byte[]> list = new TreeMap<>();
        oout.writeBoolean(true);

        for (Integer pathID: pathIDs) {
            for ( OramSnapshot snapshot : snapshots) {
                stashes.put(snapshot.getId(), snapshot.getStash());
            }
            int location = TREE_SIZE/2+pathID;
            for (int i = TREE_LEVELS-1; i >= 0; i--) {
                boolean dataIsNull = true;
                /* Basically, this version iterates over the multiple versions of the tree
                 * It should know where to pick the info from or the eviction
                 * should pull from the older versions to the newer ones
                 */
                List<OramSnapshot> versionSnapshots = new ArrayList<>();
                snapIds.stream().map(id -> versionSnapshots.add(allTrees.get(id)));
                while (dataIsNull){
                    TreeMap<Double,byte[]> tempVersionStashes = new TreeMap<>();
                    TreeMap<Double,byte[]> dataList = new TreeMap<>();
                    for (OramSnapshot snapshot:versionSnapshots) {
                        byte[] tempData = snapshot.getFromLocation(location);

                        if (tempData != null){
                            if(!stashes.containsKey(snapshot.getId()))
                                tempVersionStashes.put(snapshot.getId(),snapshot.getStash());
                            dataList.put(snapshot.getId(),tempData);
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
            oout.writeObject(stashes);
            oout.writeObject(list);
            stashes.clear();
        }
        oout.flush();
        return out.toByteArray();
    }

    public boolean doEviction(List<Double> snapshots,byte[] newPositionMap,byte[] newStash,Integer pathID,TreeMap<Integer,byte[]> newPath, Integer senderId) {
        List<OramSnapshot> previousSnapshots = new ArrayList();
       	previousSnapshots = snapshots.stream().map(tree -> allTrees.get(tree)).collect(Collectors.toList());
        double snapId = Collections.max(snapshots).intValue() + 1 + Double.parseDouble("0." + senderId.toString());
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
        return allTrees.values().stream().map(e -> e.getTree()).collect(Collectors.toList());
    }
}
