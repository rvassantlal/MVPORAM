package oram.server;

import bftsmart.tom.MessageContext;
import confidential.ConfidentialMessage;
import confidential.facade.server.ConfidentialServerFacade;
import confidential.facade.server.ConfidentialSingleExecutable;
import confidential.statemanagement.ConfidentialSnapshot;
import oram.messages.CreateORAMMessage;
import oram.messages.EvictionORAMMessage;
import oram.messages.ORAMMessage;
import oram.messages.StashPathORAMMessage;
import oram.server.structure.EncryptedPositionMap;
import oram.server.structure.EncryptedStash;
import oram.server.structure.EncryptedStashesAndPaths;
import oram.server.structure.ORAMContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oram.server.structure.ORAM;
import oram.utils.ServerOperationType;
import oram.utils.Status;
import vss.secretsharing.VerifiableShare;

import java.io.*;
import java.util.TreeMap;

public class ORAMServer implements ConfidentialSingleExecutable {
	private final Logger logger = LoggerFactory.getLogger("oram");
	private final TreeMap<Integer, ORAM> orams;

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
			ServerOperationType op = ServerOperationType.getOperation(in.read());
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
					return getPositionMap(request, msgCtx.getSender());
				case GET_STASH_AND_PATH:
					request = new StashPathORAMMessage();
					request.readExternal(in);
					return getStashesAndPaths((StashPathORAMMessage)request, msgCtx.getSender());
				case EVICTION:
					request = new EvictionORAMMessage();
					request.readExternal(in);
					return performEviction((EvictionORAMMessage)request, msgCtx.getSender());
			}
		} catch (IOException | ClassNotFoundException e) {
			logger.error("Failed to attend ordered request from {}", msgCtx.getSender(), e);
		}
		return null;
	}

	@Override
	public ConfidentialMessage appExecuteUnordered(byte[] plainData, VerifiableShare[] shares, MessageContext msgCtx) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(plainData);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			ServerOperationType op = ServerOperationType.getOperation(in.read());
			ORAMMessage request;
			switch (op) {
				case GET_ORAM:
					request = new ORAMMessage();
					request.readExternal(in);
					return getORAM(request);
				case GET_STASH_AND_PATH:
					request = new StashPathORAMMessage();
					request.readExternal(in);
					return getStashesAndPaths((StashPathORAMMessage)request, msgCtx.getSender());
			}
		} catch (IOException | ClassNotFoundException e) {
			logger.error("Failed to attend ordered request from {}", msgCtx.getSender(), e);
		}
		return null;
	}

	private ConfidentialMessage performEviction(EvictionORAMMessage request, int clientId) {
		int oramId =  request.getOramId();
		ORAM oram = orams.get(oramId);
		if (oram == null)
			return null;
		boolean isEvicted = oram.performEviction(request.getEncryptedStash(), request.getEncryptedPositionMap(),
				request.getEncryptedPath(), clientId);
		if (isEvicted)
			return new ConfidentialMessage(new byte[]{(byte) Status.SUCCESS.ordinal()});
		else
			return new ConfidentialMessage(new byte[]{(byte) Status.FAILED.ordinal()});
	}

	private ConfidentialMessage getStashesAndPaths(StashPathORAMMessage request, int clientId) {
		int oramId =  request.getOramId();
		ORAM oram = orams.get(oramId);
		if (oram == null)
			return null;
		EncryptedStashesAndPaths encryptedStashesAndPaths = oram.getStashesAndPaths(request.getPathId(), clientId);
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 ObjectOutputStream out = new ObjectOutputStream(bos)) {
			encryptedStashesAndPaths.writeExternal(out);
			out.flush();
			bos.flush();
			return new ConfidentialMessage(bos.toByteArray());
		} catch (IOException e) {
			logger.debug("Failed to serialize encrypted stashes and paths: {}",  e.getMessage());
			return new ConfidentialMessage();
		}
	}

	private ConfidentialMessage getORAM(ORAMMessage request) {
		ORAM oram = orams.get(request.getOramId());
		if (oram == null) {
			return new ConfidentialMessage(new byte[]{-1});
		} else {
			ORAMContext oramContext = oram.getOramContext();
			int treeHeight = oramContext.getTreeHeight();
			int nBlocksPerBucket = oramContext.getBucketSize();
			int blockSize = oramContext.getBlockSize();
			try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				 ObjectOutputStream out = new ObjectOutputStream(bos)) {
				out.writeInt(treeHeight);
				out.writeInt(nBlocksPerBucket);
				out.writeInt(blockSize);
				out.flush();
				bos.flush();
				return new ConfidentialMessage(bos.toByteArray());
			} catch (IOException e) {
				logger.debug("Failed to serialize oram context: {}",  e.getMessage());
				return new ConfidentialMessage();
			}
		}
	}

	private ConfidentialMessage getPositionMap(ORAMMessage request, int clientId) {
		int oramId =  request.getOramId();
		ORAM oram = orams.get(oramId);
		if (oram == null)
			return null;
		EncryptedPositionMap[] positionMaps = oram.getPositionMaps(clientId);

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

	private ConfidentialMessage createORAM(CreateORAMMessage request, int clientId) {
		int oramId = request.getOramId();
		int treeHeight = request.getTreeHeight();
		int nBlocksPerBucket = request.getNBlocksPerBucket();
		int blockSize = request.getBlockSize();
		EncryptedPositionMap encryptedPositionMap = request.getEncryptedPositionMap();
		EncryptedStash encryptedStash = request.getEncryptedStash();
		if (orams.containsKey(oramId)) {
			logger.debug("ORAM with id {} exists", oramId);
			return new ConfidentialMessage(new byte[]{(byte) Status.FAILED.ordinal()});
		} else {
			logger.debug("Created an ORAM with id {} of {} levels", oramId, treeHeight + 1);
			ORAM oram = new ORAM(oramId,treeHeight, clientId, nBlocksPerBucket, blockSize, encryptedPositionMap,
					encryptedStash);
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
