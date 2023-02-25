package pathoram;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.lang3.SerializationUtils;

import bftsmart.demo.pathoram.structure.FourTuple;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;

public class Server extends DefaultSingleRecoverable{
private static Integer TREE_SIZE;
private static Integer TREE_LEVELS;
private byte[] positionMap = SerializationUtils.serialize(new TreeMap<Short,Integer>());
private byte[] stash = SerializationUtils.serialize(new TreeMap<Short,Short>());
private TreeMap<Integer,byte[]> tree= new TreeMap<>();
private final ServiceReplica replica;

public static void main(String[] args) {
    new Server(Integer.parseInt(args[1]),Integer.parseInt(args[0]));
}
public Server(int size,int id) {
	TREE_SIZE=size;
	TREE_LEVELS = (int)(Math.log(TREE_SIZE+1) / Math.log(2));
	for (int i = 0; i <TREE_SIZE; i++) {
		tree.put(i, null);
	}
	replica = new ServiceReplica(id, this, this);
}
public byte[] getPositionMap() {
	return positionMap;
}

public byte[] getStash() {
	return stash;
}

public List<byte[]> getData(Integer pathID) {
	ArrayList<byte[]> list = new ArrayList<byte[]>();
	int location = TREE_SIZE/2+pathID;
	for (int i = TREE_LEVELS-1; i >= 0; i--) {
		list.add(tree.get(location));
		location=location%2==0?location-2:location-1;
		location/=2;
	}
	return list;
}
public List<byte[]> getTree() {
	return new ArrayList<byte[]>(tree.values());
}
public void doEviction(byte[] newPositionMap,byte[] newStash,Integer pathID,TreeMap<Integer,byte[]> newPath) {
	positionMap=newPositionMap;
	stash=newStash;
	put(pathID, newPath);
}

private void put(Integer pathID, TreeMap<Integer,byte[]> newPath) {
	int location = TREE_SIZE/2+pathID;
	for (int i = TREE_LEVELS-1; i >= 0; i--) {
		tree.put(location, newPath.get(i));
		location=location%2==0?location-2:location-1;
		location/=2;
	}
}
@Override
public void installSnapshot(byte[] state) {
	// TODO Auto-generated method stub
	
}
@Override
public byte[] getSnapshot() {
	// TODO Auto-generated method stub
	return null;
}
@Override
public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
	ByteArrayInputStream in = new ByteArrayInputStream(command);
    int cmd;
	try {
		cmd = new DataInputStream(in).readInt();
		ByteArrayOutputStream out;
		switch (cmd) {
			case ServerOperationType.GET_POSITION_MAP:
	    		return getPositionMap();
			case ServerOperationType.GET_STASH:
				return getStash();
			case ServerOperationType.GET_DATA:
				int pathId = new DataInputStream(in).readInt();
				out = new ByteArrayOutputStream();
                new ObjectOutputStream(out).writeObject(getData(pathId));
				return out.toByteArray();
			case ServerOperationType.EVICT:
				FourTuple<byte[],byte[],Integer,TreeMap<Integer,byte[]>> evict = SerializationUtils.deserialize(in.readAllBytes());
				doEviction(evict.getFirst(), evict.getSecond(), evict.getThird(), evict.getFourth());
				out = new ByteArrayOutputStream();
                new ObjectOutputStream(out).writeBoolean(true);
				return out.toByteArray();
			case ServerOperationType.GET_TREE:
				out = new ByteArrayOutputStream();
                new ObjectOutputStream(out).writeObject(getTree());
				return out.toByteArray();
	    	
	    }
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
    return null;
}
@Override
public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
	// TODO Auto-generated method stub
	return null;
}

}
