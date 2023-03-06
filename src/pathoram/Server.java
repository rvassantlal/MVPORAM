package pathoram;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
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
	private TreeMap<Integer,Oram> serverOrams;

	public static void main(String[] args) {
		new Server(Integer.parseInt(args[0]));
	}
	public Server(int id) {
		replica = new ServiceReplica(id, this, this);
		serverOrams=new TreeMap<>();
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
		Oram serverOram = serverOrams.get(msgCtx.getSender());
		try {
			ObjectInputStream objin = new ObjectInputStream(in);
			cmd = objin.readInt();
			ByteArrayOutputStream out;
			switch (cmd) {
				case ServerOperationType.EVICT:
					if(serverOram.authorizeOperation(msgCtx.getSender(), msgCtx.getTimestamp())) {
						FourTuple<byte[],byte[],Integer,TreeMap<Integer,byte[]>> evict = (FourTuple<byte[], byte[], Integer, TreeMap<Integer, byte[]>>) objin.readObject();
						serverOram.doEviction(evict.getFirst(), evict.getSecond(), evict.getThird(), evict.getFourth());
						out = new ByteArrayOutputStream();
						new ObjectOutputStream(out).writeBoolean(true);
						return out.toByteArray();
					}else {
						return unauthorizedMessage();
					}
				case ServerOperationType.OPEN_SESSION:
					out = new ByteArrayOutputStream();
					new ObjectOutputStream(out).writeBoolean(serverOram.openSession(msgCtx.getSender(), msgCtx.getTimestamp()));
					return out.toByteArray();
				case ServerOperationType.CLOSE_SESSION:
					out = new ByteArrayOutputStream();
					new ObjectOutputStream(out).writeBoolean(serverOram.closeSession(msgCtx.getSender()));
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
	private byte[] unauthorizedMessage() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new ObjectOutputStream(out).write(null);
		return out.toByteArray();
	}
	@Override
	public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
		ByteArrayInputStream in = new ByteArrayInputStream(command);
		int cmd;
		Oram serverOram = serverOrams.get(msgCtx.getSender());
		try {
			ObjectInputStream ois = new ObjectInputStream(in);
			cmd = ois.readInt();
			ByteArrayOutputStream out;
			if(serverOram==null) {
				int size = ois.readInt();
				if(serverOram==null) {
					serverOrams.put(msgCtx.getSender(), new Oram(size));
				}
				return SerializationUtils.serialize(serverOram==null);
			}else if(serverOram.authorizeOperation(msgCtx.getSender(), msgCtx.getTimestamp()) ) {
				System.out.println("HERE");
				switch (cmd) {
					case ServerOperationType.GET_POSITION_MAP:
						return serverOram.getPositionMap();
					case ServerOperationType.GET_STASH:
						return serverOram.getStash();
					case ServerOperationType.GET_DATA:
						int pathId = ois.readInt();
						out = new ByteArrayOutputStream();
						new ObjectOutputStream(out).writeObject(serverOram.getData(pathId));
						return out.toByteArray();
					case ServerOperationType.GET_TREE:
						out = new ByteArrayOutputStream();
						new ObjectOutputStream(out).writeObject(serverOram.getTree());
						return out.toByteArray();

				}
			}else {
				return unauthorizedMessage();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}
