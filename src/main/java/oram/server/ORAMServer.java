package oram.server;

import bftsmart.tom.MessageContext;
import confidential.ConfidentialMessage;
import confidential.facade.server.ConfidentialServerFacade;
import confidential.facade.server.ConfidentialSingleExecutable;
import confidential.statemanagement.ConfidentialSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pathoram.ORAM;
import utils.Operation;
import utils.Status;
import vss.secretsharing.VerifiableShare;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.TreeMap;

public class ORAMServer implements ConfidentialSingleExecutable {
	private final Logger logger = LoggerFactory.getLogger("oram");
	private TreeMap<Integer, ORAM> orams;

	public static void main(String[] args) {
		new ORAMServer(Integer.parseInt(args[0]));
	}

	public ORAMServer(int processId) {
		this.orams = new TreeMap<>();
		//Starting server
		new ConfidentialServerFacade(processId, this);
	}

	@Override
	public ConfidentialMessage appExecuteOrdered(byte[] plainData, VerifiableShare[] shares, MessageContext msgCtx) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(plainData);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			Operation op = Operation.getOperation(in.read());
			switch (op) {
				case CREATE_ORAM:
					int oramId = in.readInt();
					int treeHeight = in.readInt();
					int clientId = msgCtx.getSender();
					if (orams.containsKey(oramId)) {
						logger.debug("ORAM with id {} exists", oramId);
						return new ConfidentialMessage(new byte[]{(byte) Status.FAILED.ordinal()});
					} else {
						logger.debug("Created an ORAM with id {} of height {}", oramId, treeHeight);
						ORAM oram = new ORAM(oramId, treeHeight, clientId);
						orams.put(oramId, oram);
						return new ConfidentialMessage(new byte[]{(byte) Status.SUCCESS.ordinal()});
					}
			}
		} catch (IOException e) {
			logger.error("Failed to attend ordered request from {}", msgCtx.getSender(), e);
		}
		return null;
	}

	@Override
	public ConfidentialMessage appExecuteUnordered(byte[] plainData, VerifiableShare[] shares, MessageContext msgCtx) {
		return null;
	}

	@Override
	public ConfidentialSnapshot getConfidentialSnapshot() {
		return new ConfidentialSnapshot(new byte[0]);
	}

	@Override
	public void installConfidentialSnapshot(ConfidentialSnapshot confidentialSnapshot) {

	}
}
