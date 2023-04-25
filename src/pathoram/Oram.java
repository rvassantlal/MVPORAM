package pathoram;

import structure.OramSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Oram {
    private static Integer TREE_SIZE;
    private static Integer TREE_LEVELS;
    private AtomicInteger mostRecentVersion;

    private TreeMap<Integer,Integer> usersByVersion;
    private TreeMap<Integer, List<OramSnapshot>> oramVersions;

    protected Oram(int size,int client_id) {
        TREE_SIZE = size;
        TREE_LEVELS = (int)(Math.log(TREE_SIZE+1) / Math.log(2));
        mostRecentVersion = new AtomicInteger(0);
        usersByVersion = new TreeMap<>();
        oramVersions = new TreeMap<>();
        ArrayList<OramSnapshot> firstVersion = new ArrayList<>();
        firstVersion.add(new OramSnapshot(TREE_SIZE,0,client_id));
        oramVersions.put(mostRecentVersion.get(),firstVersion);
    }
    protected byte[] getPositionMap() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(out);
        int newestVersion=mostRecentVersion.get();
        usersByVersion.put(newestVersion,usersByVersion.get(newestVersion)+1);
        oout.writeInt(newestVersion);
        oout.writeInt(TREE_SIZE);
        ArrayList<byte[]> positionMaps = new ArrayList<>();
        for ( OramSnapshot snapshot : oramVersions.get(newestVersion)) {
            positionMaps.add(snapshot.getPositionMap());
        }
        oout.writeObject(positionMaps);
        return out.toByteArray();
    }

    public byte[] getPathAndStash(int v,Integer pathID) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(out);
        List<OramSnapshot> snapshots = oramVersions.get(v);
        if(snapshots == null)
            return null;
        TreeMap<Integer,List<byte[]>> stashes = new TreeMap<>();
        List<byte[]> versionStashes = new ArrayList<>();
        for ( OramSnapshot snapshot : snapshots) {
            versionStashes.add(snapshot.getStash());
        }
        stashes.put(v,versionStashes);
        ArrayList<List<byte[]>> list = new ArrayList<>();
        int location = TREE_SIZE/2+pathID;
        for (int i = TREE_LEVELS-1; i >= 0; i--) {
            int version = v;
            boolean dataIsNull = true;
            /* TODO: VERY INEFFICIENT, ANOTHER SOLUTION TO BE FOUND
             * Basically, this version iterates over the multiple versions of the tree
             * It should know where to pick the info from or the eviction
             * should pull from the older versions to the newer ones
             */
            while (version>0 && dataIsNull){
                List<OramSnapshot> versionSnapshots = oramVersions.get(i);
                List<byte[]> tempVersionStashes = new ArrayList<>();
                List<byte[]> dataList = new ArrayList<>();
                for (OramSnapshot snapshot:versionSnapshots) {
                    byte[] tempData = snapshot.getFromLocation(location);
                    if(version != v)
                        tempVersionStashes.add(snapshot.getStash());
                    if (tempData != null){
                        dataList.add(tempData);
                        dataIsNull = false;
                    }
                }
                if(!dataIsNull && version!=v) {
                    stashes.put(version, tempVersionStashes);
                    list.add(dataList);
                }
                version--;
            }
            location=location%2==0?location-2:location-1;
            location/=2;
        }
        oout.writeBoolean(true);
        oout.writeObject(stashes);
        oout.writeObject(list);
        oout.flush();
        return out.toByteArray();
    }

    public boolean doEviction(int newVersion,byte[] newPositionMap,byte[] newStash,Integer pathID,TreeMap<Integer,byte[]> newPath, int clientId) {
        if(usersByVersion.get(newVersion-1)==1){
            List<OramSnapshot> currentVersion = oramVersions.get(newVersion-1);
            currentVersion.subList(1,currentVersion.size()).clear();
            currentVersion.get(0).setPositionMap(newPositionMap);
            currentVersion.get(0).setStash(newStash);
            currentVersion.get(0).putPath(pathID, newPath);
            usersByVersion.put(newVersion-1, 0);
        }
        else{
            OramSnapshot newSnapshot = new OramSnapshot(TREE_SIZE,newVersion,clientId);
            usersByVersion.put(newVersion, usersByVersion.get(newVersion+1));
            newSnapshot.putPath(pathID,newPath);
            oramVersions.get(newVersion).add(newSnapshot);
        }
        removeDeadVersions();
        return true;
    }

    private void removeDeadVersions() {
        for (int i = 0; i < oramVersions.size(); i++) {
            if(usersByVersion.get(i)==0){
                //TODO: make tree protected and change it from here
            }
        }
    }

    //gets one tree from this version
    public List<byte[]> getTree(int version) {
        return oramVersions.get(version).get(0).getTree();
    }
}
