package structure;

import org.apache.commons.lang3.tuple.Pair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;


public class OramSnapshot implements Serializable{
	private static final long serialVersionUID = -4459879580826094264L;
	private Pair<Integer,Integer> version;
	private byte[] positionMap;
	private byte[] stash;
	// LOCATION, VERSION, CONTENT
	private TreeMap<Integer,byte[]> tree= new TreeMap<>();

	public OramSnapshot(int size, int version, int clientName) {
		for (int i = 0; i < size; i++) {
			tree.put(i, null);
		}
		positionMap = new byte[]{};
		stash = new byte[]{};
		this.version = Pair.of(version,clientName);
	}

	public byte[] getPositionMap(){
		return positionMap;
	}


	/*private ArrayList<TreeMap<Integer,List<byte[]>>> flushOldVersions(int version,Integer pathID) {
		ArrayList<TreeMap<Integer,List<byte[]>>> list = new ArrayList<>();
		int location = TREE_SIZE/2+pathID;
		for (int i = TREE_LEVELS-1; i >= 0; i--) {
			TreeMap<Integer,List<byte[]>> treeMap = new TreeMap<Integer,List<byte[]>>();
			for (int v = 0; v < version; v++) {
				if (usersByVersion.get(v)>0) {
					treeMap.put(v,tree.get(v).get(location));
					location = location % 2 == 0 ? location - 2 : location - 1;
					location /= 2;
				}
			}
			list.add(treeMap);
		}

		return list;
	}*/

	/*public List<byte[]> getData(int version,Integer pathID) {
        if (version==this.version.get()){
            ArrayList<byte[]> list = new ArrayList<byte[]>();
            int location = TREE_SIZE/2+pathID;
            for (int i = TREE_LEVELS-1; i >= 0; i--) {
                list.add(tree.get(location));
                location=location%2==0?location-2:location-1;
                location/=2;
            }
            return list;
        }
        return null;
    }*/
	public List<byte[]> getTree() {
		ArrayList<byte[]> result = new ArrayList<byte[]>();
		for (int treeKey:tree.keySet()) {
			result.add(tree.get(treeKey));
		}
		return result;
	}



	private void put(Integer pathID, TreeMap<Integer,byte[]> newPath, int oramVersion) {

	}

	public byte[] getStash() {
		return stash;
	}

	public byte[] getFromLocation(int location) {
		return tree.get(location);
	}

	public void setPositionMap(byte[] newPositionMap) {
		this.positionMap = newPositionMap;
	}

	public void setStash(byte[] newStash) {
		this.stash = newStash;
	}

	public void putPath(Integer pathID, TreeMap<Integer,byte[]> newPath) {
		//TODO: make final tree_size and tree_levels
		int tree_size = tree.size();
		int tree_levels = (int)(Math.log(tree_size+1) / Math.log(2));
		int location = tree_size/2+pathID;
		for (int i = tree_levels-1; i >= 0; i--) {
			tree.put(location, newPath.get(i));
			location=location%2==0?location-2:location-1;
			location/=2;
		}
	}
}
