package pathoram;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.TreeMap;

import org.apache.commons.lang3.SerializationUtils;

import structure.*;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;
/*
 * openSession (OramID) 
guardar timestamp
locks database
cada cliente tem a sua tree
 */
public class Server extends DefaultSingleRecoverable{

	@SuppressWarnings("unused")
	private ServiceReplica replica = null;
	private TreeMap<Integer,Oram> serverOrams;

	public static void main(String[] args) {
		new Server(Integer.parseInt(args[0]));
	}
	public Server(int id) {
		replica = new ServiceReplica(id, this, this);
	}
	@Override
	public void installSnapshot(byte[] state) {
		serverOrams = SerializationUtils.deserialize(state);
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
			cmd = new DataInputStream(in).readInt();
			ByteArrayOutputStream out;
			switch (cmd) {
			case ServerOperationType.EVICT:
				if(serverOram.authorizeOperation(msgCtx.getSender(), msgCtx.getTimestamp())) {
					FourTuple<byte[],byte[],Integer,TreeMap<Integer,byte[]>> evict = SerializationUtils.deserialize(in.readAllBytes());
					serverOram.doEviction(evict.getFirst(), evict.getSecond(), evict.getThird(), evict.getFourth());
					out = new ByteArrayOutputStream();
					new ObjectOutputStream(out).writeBoolean(true);
					return out.toByteArray();
				}
			case ServerOperationType.OPEN_SESSION:
				out = new ByteArrayOutputStream();
				new ObjectOutputStream(out).writeBoolean(serverOram.openSession(msgCtx.getSender(), msgCtx.getTimestamp()));
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
		ByteArrayInputStream in = new ByteArrayInputStream(command);
		int cmd;
		Oram serverOram = serverOrams.get(msgCtx.getSender());

		try {
			cmd = new DataInputStream(in).readInt();
			ByteArrayOutputStream out;
			if((serverOram==null && cmd==ServerOperationType.CREATE_ORAM) || serverOram.authorizeOperation(msgCtx.getSender(), msgCtx.getTimestamp()) ) {
				switch (cmd) {
				case ServerOperationType.CREATE_ORAM:
					int size = new DataInputStream(in).readInt();
					if(serverOram==null) {
						serverOrams.put(msgCtx.getSender(), new Oram(size));
					}
					out = new ByteArrayOutputStream();
					new ObjectOutputStream(out).writeBoolean(serverOram==null);
					return out.toByteArray();
				case ServerOperationType.GET_POSITION_MAP:
					return serverOram.getPositionMap();
				case ServerOperationType.GET_STASH:
					return serverOram.getStash();
				case ServerOperationType.GET_DATA:
					int pathId = new DataInputStream(in).readInt();
					out = new ByteArrayOutputStream();
					new ObjectOutputStream(out).writeObject(serverOram.getData(pathId));
					return out.toByteArray();
				case ServerOperationType.GET_TREE:
					out = new ByteArrayOutputStream();
					new ObjectOutputStream(out).writeObject(serverOram.getTree());
					return out.toByteArray();

				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}
