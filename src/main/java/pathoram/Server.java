package pathoram;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import confidential.ConfidentialMessage;
import confidential.facade.server.ConfidentialServerFacade;
import confidential.facade.server.ConfidentialSingleExecutable;
import confidential.statemanagement.ConfidentialSnapshot;
import org.apache.commons.lang3.SerializationUtils;

import utils.*;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;
import vss.secretsharing.VerifiableShare;

public class Server implements ConfidentialSingleExecutable {
	private TreeMap<Integer, ORAM> serverOrams;

	public static void main(String[] args) {
		new Server(Integer.parseInt(args[0]));
	}

	public Server(int id) {
		new ConfidentialServerFacade(id, this);
		serverOrams= new TreeMap<>();
	}
	@Override
	public ConfidentialMessage appExecuteOrdered(byte[] command, VerifiableShare[] verifiableShares, MessageContext msgCtx) {
		ByteArrayInputStream in = new ByteArrayInputStream(command);
		int cmd;
		try {
			ObjectInputStream objin = new ObjectInputStream(in);
			int oramName = objin.readInt();
			ORAM serverORAM = serverOrams.get(oramName);
			cmd = objin.readInt();
			ByteArrayOutputStream out;
			ObjectOutputStream oout;
			if(serverORAM ==null) {
				int size = objin.readInt();
				serverOrams.put(oramName, new ORAM(size, msgCtx.getSender()));
				return new ConfidentialMessage(SerializationUtils.serialize(true));
			}else {
				switch (cmd){
					case ServerOperationType.GET_POSITION_MAP:
						return serverORAM.getPositionMap();
					case ServerOperationType.EVICT:
						int numberOfSnaps = objin.readInt();
						snapshotIdentifiers snaps = new snapshotIdentifiers(numberOfSnaps);
						snaps.readExternal(objin);
						int oldPosition = objin.readInt();
						byte[] posMap = new byte[objin.readInt()];
						objin.read(posMap);
						byte[] stash = new byte[objin.readInt()];
						objin.read(stash);
						List<byte[]> path = (List<byte[]>) objin.readObject();
					/*for (int i = 0; i < serverOram.getTreeLevels(); i++) {
						byte[] bucket = new byte[objin.readInt()];
						objin.read(bucket);
						path.add(bucket);
					}*/
						boolean bool = serverORAM.doEviction(snaps, posMap, stash, oldPosition, path, msgCtx.getSender());
						out = new ByteArrayOutputStream();
						oout = new ObjectOutputStream(out);
						oout.writeBoolean(bool);
						oout.flush();
						return new ConfidentialMessage(out.toByteArray());
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	@Override
	public ConfidentialMessage appExecuteUnordered(byte[] command, VerifiableShare[] verifiableShares, MessageContext msgCtx) {
		ByteArrayInputStream in = new ByteArrayInputStream(command);
		int cmd;
		try {
			ObjectInputStream ois = new ObjectInputStream(in);
			int oramName = ois.readInt();
			ORAM serverORAM = serverOrams.get(oramName);
			cmd = ois.readInt();
			ByteArrayOutputStream out;
			ObjectOutputStream oout;
			switch (cmd) {
				case ServerOperationType.GET_PATH_STASH:
					int numberOfSnaps = ois.readInt();
					snapshotIdentifiers snaps = new snapshotIdentifiers(numberOfSnaps);
					snaps.readExternal(ois);
					int numberOfPaths = ois.readInt();
					List<Integer> pathIDs = new ArrayList<>();
					for (int i = 0; i < numberOfPaths; i++) {
						pathIDs.add(ois.readInt());
					}
					return serverORAM.getPathAndStash(snaps, pathIDs);
				case ServerOperationType.GET_TREE:
					out = new ByteArrayOutputStream();
					oout = new ObjectOutputStream(out);
					oout.writeObject(serverORAM.getTree());
					oout.flush();
					return new ConfidentialMessage(out.toByteArray());
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public ConfidentialSnapshot getConfidentialSnapshot() {
		return new ConfidentialSnapshot(SerializationUtils.serialize(serverOrams));
	}

	@Override
	public void installConfidentialSnapshot(ConfidentialSnapshot confidentialSnapshot) {
		serverOrams = SerializationUtils.deserialize(confidentialSnapshot.getPlainData());
	}
}
