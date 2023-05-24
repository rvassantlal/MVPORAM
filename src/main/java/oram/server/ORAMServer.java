package oram.server;

import bftsmart.tom.MessageContext;
import confidential.ConfidentialMessage;
import confidential.facade.server.ConfidentialServerFacade;
import confidential.facade.server.ConfidentialSingleExecutable;
import confidential.statemanagement.ConfidentialSnapshot;
import oram.messages.CreateORAMMessage;
import oram.messages.ORAMMessage;
import oram.server.structure.EncryptedPositionMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pathoram.ORAM;
import utils.Operation;
import utils.Status;
import utils.Utils;
import vss.secretsharing.VerifiableShare;

import java.io.*;
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
			ORAMMessage request;
			switch (op) {
				case CREATE_ORAM:
					request = new CreateORAMMessage();
					request.readExternal(in);
					return createORAM((CreateORAMMessage)request, msgCtx.getSender());
				case GET_ORAM:
					request = new ORAMMessage();
					request.readExternal(in);
					return getORAM(request);
				case GET_POSITION_MAP:
					request = new ORAMMessage();
					request.readExternal(in);
					return getPositionMap(request);
			}
		} catch (IOException | ClassNotFoundException e) {
			logger.error("Failed to attend ordered request from {}", msgCtx.getSender(), e);
		}
		return null;
	}

	private ConfidentialMessage getORAM(ORAMMessage request) {
		ORAM oram = orams.get(request.getOramId());
		if (oram == null)
			return new ConfidentialMessage(new byte[]{-1});
		else
			return new ConfidentialMessage(Utils.toBytes(oram.getTreeLevels()));
	}

	private ConfidentialMessage getPositionMap(ORAMMessage request) {
		int oramId =  request.getOramId();
		ORAM oram = orams.get(oramId);
		if (oram == null)
			return null;
		EncryptedPositionMap[] positionMaps = oram.getPositionMaps();
		//TODO compute the size of serialized position maps
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 ObjectOutputStream out = new ObjectOutputStream(bos)) {
			out.writeInt(positionMaps.length);
			for (EncryptedPositionMap positionMap : positionMaps) {
				positionMap.writeExternal(out);
			}
			out.flush();
			bos.flush();
			return new ConfidentialMessage(bos.toByteArray());
		} catch (IOException e) {
			logger.debug("Failed to serialize position maps: {}",  e.getMessage());
			return new ConfidentialMessage();
		}
	}

	@Override
	public ConfidentialMessage appExecuteUnordered(byte[] plainData, VerifiableShare[] shares, MessageContext msgCtx) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(plainData);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			Operation op = Operation.getOperation(in.read());
			ORAMMessage request;
			switch (op) {
				case GET_ORAM:
					request = new ORAMMessage();
					request.readExternal(in);
					return getORAM(request);
				case GET_POSITION_MAP:
					request = new ORAMMessage();
					request.readExternal(in);
					return getPositionMap(request);
			}
		} catch (IOException | ClassNotFoundException e) {
			logger.error("Failed to attend ordered request from {}", msgCtx.getSender(), e);
		}
		return null;
	}

	private ConfidentialMessage createORAM(CreateORAMMessage request, int clientId) {
		int oramId = request.getOramId();
		int treeHeight = request.getTreeHeight();

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

	@Override
	public ConfidentialSnapshot getConfidentialSnapshot() {
		return new ConfidentialSnapshot(new byte[0]);
	}

	@Override
	public void installConfidentialSnapshot(ConfidentialSnapshot confidentialSnapshot) {

	}
}
