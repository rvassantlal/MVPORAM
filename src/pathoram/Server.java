package pathoram;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.TreeMap;

import org.apache.commons.lang3.SerializationUtils;

import structure.*;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;

public class Server extends DefaultSingleRecoverable{

	@SuppressWarnings("unused")
	private ServiceReplica replica = null;
	private TreeMap<Integer, Oram> serverOrams;

	public static void main(String[] args) {
		new Server(Integer.parseInt(args[0]));
	}

	public Server(int id) {
		replica = new ServiceReplica(id, this, this);
		serverOrams=new TreeMap<Integer, Oram>();
	}

	@Override
	public void installSnapshot(byte[] state) {
		//TODO: ADD POSMAP AND STASH
		serverOrams = (TreeMap<Integer, Oram>) SerializationUtils.deserialize(state);
	}

	@Override
	public byte[] getSnapshot() {
		//TODO: ADD POSMAP AND STASH
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
					boolean bool = serverOram.doEviction(version,evict.getFirst(), evict.getSecond(), evict.getThird(), evict.getFourth(), msgCtx.getSender());
					out = new ByteArrayOutputStream();
					oout = new ObjectOutputStream(out);
					oout.writeBoolean(bool);
					oout.flush();
					return out.toByteArray();
			}
		} catch (IOException | ClassNotFoundException e) {
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
			if(serverOram ==null) {
				int size = ois.readInt();
				serverOrams.put(oramName, new Oram(size, msgCtx.getSender()));
				return SerializationUtils.serialize(true);
			}else {
				ObjectOutputStream oout;
				switch (cmd) {
					case ServerOperationType.GET_PATH_STASH -> {
						int v = ois.readInt();
						int pathID = ois.readInt();
						return serverOram.getPathAndStash(v, pathID);
					}
					case ServerOperationType.GET_POSITION_MAP -> {
						return serverOram.getPositionMap();
					}
					case ServerOperationType.GET_TREE -> {
						int v = ois.readInt();
						out = new ByteArrayOutputStream();
						oout = new ObjectOutputStream(out);
						oout.writeObject(serverOram.getTree(v));
						oout.flush();
						return out.toByteArray();
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}
