package structure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;


public class Oram implements Serializable{
	private static final long serialVersionUID = -4459879580826094264L;
	private static Integer TREE_SIZE;
	private static Integer TREE_LEVELS;
	private AtomicInteger version;
	private byte[] positionMap;
	private byte[] stash;
	private TreeMap<Integer,byte[]> tree= new TreeMap<>();

	public Oram(int size) {
		TREE_LEVELS = size+1;
		TREE_SIZE= 2 << size;
		for (int i = 0; i <TREE_SIZE; i++) {
			tree.put(i, null);
		}
		version=new AtomicInteger(0);
	}

	public byte[] getPositionMap() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectOutputStream oout = new ObjectOutputStream(out);
		oout.writeInt(version.get());
		oout.writeInt(TREE_SIZE);
		oout.writeObject(positionMap);
		return out.toByteArray();
	}
	public byte[] getPathAndStash(int v,Integer pathID) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectOutputStream oout = new ObjectOutputStream(out);
		Boolean corresponds = v==version.get();
		oout.writeBoolean(corresponds);
		if(corresponds) {
			oout.writeBoolean(stash==null?false:stash.length!=0);
			if(stash==null?false:stash.length!=0) 
				oout.writeObject(stash);
			ArrayList<byte[]> list = new ArrayList<byte[]>();
			int location = TREE_SIZE/2+pathID;
			for (int i = TREE_LEVELS-1; i >= 0; i--) {
				list.add(tree.get(location));
				location=location%2==0?location-2:location-1;
				location/=2;
			}
			oout.writeObject(list);

		}
		oout.flush();
		return out.toByteArray();

	}
	public List<byte[]> getTree() {
		return new ArrayList<byte[]>(tree.values());
	}
	public boolean doEviction(int newVersion,byte[] newPositionMap,byte[] newStash,Integer pathID,TreeMap<Integer,byte[]> newPath) {
		int oramVersion = newVersion==version.get()+1?version.addAndGet(1):-1;
		if(oramVersion!=-1) {
			positionMap=newPositionMap;
			stash=newStash;
			put(pathID, newPath);
		}
		return oramVersion!=-1; 
	}

	private void put(Integer pathID, TreeMap<Integer,byte[]> newPath) {
		int location = TREE_SIZE/2+pathID;
		for (int i = TREE_LEVELS-1; i >= 0; i--) {
			tree.put(location, newPath.get(i));
			location=location%2==0?location-2:location-1;
			location/=2;
		}
	}
}
