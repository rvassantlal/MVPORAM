package pathoram;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.lang3.SerializationUtils;

import structure.*;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;

public class Server extends DefaultSingleRecoverable{
	
	private ServiceReplica replica = null;
	private Oram serverOram;

	public static void main(String[] args) {
		new Server(Integer.parseInt(args[1]),Integer.parseInt(args[0]));
	}
	public Server(int size,int id) {
		serverOram=new Oram(size);
		replica = new ServiceReplica(id, this, this);
	}
	@Override
	public void installSnapshot(byte[] state) {
		serverOram = SerializationUtils.deserialize(state);
	}
	@Override
	public byte[] getSnapshot() {
		return SerializationUtils.serialize(serverOram);
	}
	@Override
	public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
		ByteArrayInputStream in = new ByteArrayInputStream(command);
		int cmd;
		try {
			cmd = new DataInputStream(in).readInt();
			ByteArrayOutputStream out;
			if  (ServerOperationType.EVICT==cmd) {
				FourTuple<byte[],byte[],Integer,TreeMap<Integer,byte[]>> evict = SerializationUtils.deserialize(in.readAllBytes());
				serverOram.doEviction(evict.getFirst(), evict.getSecond(), evict.getThird(), evict.getFourth());
				out = new ByteArrayOutputStream();
				new ObjectOutputStream(out).writeBoolean(true);
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
		try {
			cmd = new DataInputStream(in).readInt();
			ByteArrayOutputStream out;
			switch (cmd) {
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
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}
