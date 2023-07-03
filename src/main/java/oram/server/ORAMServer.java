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
import oram.server.structure.*;
import oram.utils.ORAMContext;
import oram.utils.ServerOperationType;
import oram.utils.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.secretsharing.VerifiableShare;

import java.io.*;
import java.util.TreeMap;
import java.util.TreeSet;

public class ORAMServer implements ConfidentialSingleExecutable {
	private final Logger logger = LoggerFactory.getLogger("oram");
	private final TreeMap<Integer, ORAM> orams;
	private final TreeSet<Integer> senders;
	private int getPMCounter = 0;
	private int getPSCounter = 0;
	private int evictCounter = 0;
	private long lastPrint;


	public ORAMServer(int processId) {
		this.orams = new TreeMap<>();
		senders = new TreeSet<>();
		//Starting server
		new ConfidentialServerFacade(processId, this);

	}

	public static void main(String[] args) {
		new ORAMServer(Integer.parseInt(args[0]));
	}

	@Override
	public ConfidentialMessage appExecuteOrdered(byte[] plainData, VerifiableShare[] shares, MessageContext msgCtx) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(plainData);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			ServerOperationType op = ServerOperationType.getOperation(in.read());
			ORAMMessage request;
			senders.add(msgCtx.getSender());
			switch (op) {
				case CREATE_ORAM:
					request = new CreateORAMMessage();
					request.readExternal(in);
					return createORAM((CreateORAMMessage) request);
				case GET_ORAM:
					request = new ORAMMessage();
					request.readExternal(in);
					return getORAM(request);
				case GET_POSITION_MAP:
					request = new ORAMMessage();
					request.readExternal(in);
					return getPositionMap(request, msgCtx);
				case GET_STASH_AND_PATH:
					request = new StashPathORAMMessage();
					request.readExternal(in);
					return getStashesAndPaths((StashPathORAMMessage) request, msgCtx.getSender());
				case EVICTION:
					request = new EvictionORAMMessage();
					request.readExternal(in);
					return performEviction((EvictionORAMMessage) request, msgCtx);
			}
		} catch (IOException | ClassNotFoundException e) {
			logger.error("Failed to attend ordered request from {}", msgCtx.getSender(), e);
		} finally {
			printReport();
		}
		return null;
	}

	@Override
	public ConfidentialMessage appExecuteUnordered(byte[] plainData, VerifiableShare[] shares, MessageContext msgCtx) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(plainData);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			ServerOperationType op = ServerOperationType.getOperation(in.read());
			ORAMMessage request;
			senders.add(msgCtx.getSender());
			switch (op) {
				case GET_ORAM:
					request = new ORAMMessage();
					request.readExternal(in);
					return getORAM(request);
				case GET_STASH_AND_PATH:
					request = new StashPathORAMMessage();
					request.readExternal(in);
					return getStashesAndPaths((StashPathORAMMessage) request, msgCtx.getSender());
			}
		} catch (IOException | ClassNotFoundException e) {
			logger.error("Failed to attend unordered request from {}", msgCtx.getSender(), e);
		}
		return null;
	}

	private ConfidentialMessage performEviction(EvictionORAMMessage request, MessageContext msgCtx) {
		int oramId = request.getOramId();
		ORAM oram = orams.get(oramId);
		if (oram == null)
			return null;
		long start = System.nanoTime();
		boolean isEvicted = oram.performEviction(request.getEncryptedStash(), request.getEncryptedPositionMap(),
				request.getEncryptedPath(), msgCtx.getSender());
		long end = System.nanoTime();
		long delay = end - start;
		logger.info("eviction[ns]: {}", delay);
		evictCounter++;
		if (isEvicted)
			return new ConfidentialMessage(new byte[]{(byte) Status.SUCCESS.ordinal()});
		else
			return new ConfidentialMessage(new byte[]{(byte) Status.FAILED.ordinal()});
	}

	private ConfidentialMessage getStashesAndPaths(StashPathORAMMessage request, int clientId) {
		int oramId = request.getOramId();
		ORAM oram = orams.get(oramId);
		if (oram == null)
			return null;

		long start = System.nanoTime();
		EncryptedStashesAndPaths encryptedStashesAndPaths = oram.getStashesAndPaths(request.getPathId(), clientId);
		int size = 0;
		if (encryptedStashesAndPaths != null)
			size = encryptedStashesAndPaths.getSize(oram.getOramContext());
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream(size);
			 ObjectOutputStream out = new ObjectOutputStream(bos)) {
			if (encryptedStashesAndPaths != null)
				encryptedStashesAndPaths.writeExternal(out);
			out.flush();
			bos.flush();
			return new ConfidentialMessage(bos.toByteArray());
		} catch (IOException e) {
			logger.debug("Failed to serialize encrypted stashes and paths: {}", e.getMessage());
			return new ConfidentialMessage();
		} finally {
			long end = System.nanoTime();
			long delay = end - start;
			logger.info("getPathStash[ns]: {}", delay);
			getPSCounter++;
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
				logger.debug("Failed to serialize oram context: {}", e.getMessage());
				return new ConfidentialMessage();
			}
		}
	}

	private ConfidentialMessage getPositionMap(ORAMMessage request, MessageContext msgCtx) {
		int oramId = request.getOramId();
		ORAM oram = orams.get(oramId);
		if (oram == null)
			return null;
		long start = System.nanoTime();
		EncryptedPositionMaps positionMaps = oram.getPositionMaps(msgCtx.getSender());

		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 ObjectOutputStream out = new ObjectOutputStream(bos)) {
			positionMaps.writeExternal(out);
			out.flush();
			bos.flush();
			return new ConfidentialMessage(bos.toByteArray());
		} catch (IOException e) {
			logger.debug("Failed to serialize position maps: {}", e.getMessage());
			return new ConfidentialMessage();
		} finally {
			long end = System.nanoTime();
			long delay = end - start;
			logger.info("getPositionMap[ns]: {}", delay);
			getPMCounter++;
		}
	}

	private ConfidentialMessage createORAM(CreateORAMMessage request) {
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
			ORAM oram = new ORAM(oramId, treeHeight, nBlocksPerBucket, blockSize,
					encryptedPositionMap, encryptedStash);
			orams.put(oramId, oram);
			return new ConfidentialMessage(new byte[]{(byte) Status.SUCCESS.ordinal()});
		}
	}


	public void printReport() {
		long end = System.nanoTime();
		long delay = end - lastPrint;
		if (delay >= 2_000_000_000) {
			logger.info("M:(clients[#]|delta[ns]|requestsGetPM[#]|requestsGetPS[#]|requestsEvict[#]" +
							")>({}|{}|{}|{}|{})",
					senders.size(), delay, getPMCounter, getPSCounter, evictCounter);
			getPMCounter = 0;
			getPSCounter = 0;
			evictCounter = 0;
			lastPrint = end;
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
