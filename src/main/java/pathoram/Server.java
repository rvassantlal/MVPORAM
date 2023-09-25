package pathoram;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;
import org.apache.commons.lang3.SerializationUtils;
import structure.FourTuple;
import structure.Oram;

import java.io.*;
import java.util.TreeMap;

public class Server extends DefaultSingleRecoverable{

	@SuppressWarnings("unused")
	private ServiceReplica replica = null;
	private TreeMap<Integer,Oram> serverOrams;

	public static void main(String[] args) {
		new Server(Integer.parseInt(args[0]));
	}
	public Server(int id) {
		replica = new ServiceReplica(id, this, this);
		serverOrams=new TreeMap<Integer,Oram>();
	}
	@Override
	public void installSnapshot(byte[] state) {
		serverOrams = (TreeMap<Integer, Oram>) SerializationUtils.deserialize(state);
	}
	@Override
	public byte[] getSnapshot() {
		return SerializationUtils.serialize(serverOrams);
	}
	@Override
	public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
		ByteArrayInputStream in = new ByteArrayInputStream(command);
		int cmd;
		try {
			ObjectInputStream objin = new ObjectInputStream(in);
			int oramName = objin.readInt();
			Oram serverOram = serverOrams.get(oramName);
			cmd = objin.readInt();
			ByteArrayOutputStream out;
			ObjectOutputStream oout;
			switch (cmd) {
				case ServerOperationType.EVICT:
					int version = objin.readInt();
					FourTuple<byte[],byte[],Integer,TreeMap<Integer,byte[]>> evict = (FourTuple<byte[], byte[], Integer, TreeMap<Integer, byte[]>>) objin.readObject();
					boolean bool = serverOram.doEviction(version,evict.getFirst(), evict.getSecond(), evict.getThird(), evict.getFourth());
					out = new ByteArrayOutputStream();
					oout = new ObjectOutputStream(out);
					oout.writeBoolean(bool);
					oout.flush();
					return out.toByteArray();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return null;
	}
	@Override
	public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
		ByteArrayInputStream in = new ByteArrayInputStream(command);
		int cmd;
		try {
			ObjectInputStream ois = new ObjectInputStream(in);
			int oramName = ois.readInt();
			Oram serverOram = serverOrams.get(oramName);
			cmd = ois.readInt();
			ByteArrayOutputStream out;
			if(serverOram==null) {
				int size = ois.readInt();
				if(serverOram==null) {
					serverOrams.put(oramName, new Oram(size));
				}
				return SerializationUtils.serialize(serverOram==null);
			}else {
				ObjectOutputStream oout;
				switch (cmd) {
					case ServerOperationType.GET_PATH_STASH:
						int v = ois.readInt();
						int pathID = ois.readInt();
						return serverOram.getPathAndStash(v,pathID);
					case ServerOperationType.GET_POSITION_MAP:
						return serverOram.getPositionMap();
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}
