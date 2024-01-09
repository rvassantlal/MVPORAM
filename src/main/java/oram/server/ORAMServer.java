package oram.server;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import confidential.ConfidentialMessage;
import confidential.facade.server.ConfidentialSingleExecutable;
import confidential.server.ConfidentialRecoverable;
import confidential.statemanagement.ConfidentialSnapshot;
import oram.messages.*;
import oram.server.structure.*;
import oram.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.secretsharing.VerifiableShare;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ORAMServer implements ConfidentialSingleExecutable {
	private final Logger logger = LoggerFactory.getLogger("oram");
	private final Logger measurementLogger = LoggerFactory.getLogger("measurement");
	private final TreeMap<Integer, ORAM> orams;
	private final TreeSet<Integer> senders;
	private int getPMCounter;
	private final ArrayList<Long> getPMLatencies;
	private int getPSCounter;
	private final ArrayList<Long> getPSLatencies;
	private long getPMBytesSent;
	private long getPSBytesSent;
	private long evictionBytesReceived;
	private int evictCounter;
	private final ArrayList<Long> evictionLatencies;
	private long lastPrint;
	private int nOutstandingTreeObjects;
	private final HashMap<Integer, EvictionORAMMessage> evictionRequests;
	private final Lock evictionLock = new ReentrantLock();
	private final Condition evictionCondition = evictionLock.newCondition();

	private final ArrayDeque<MessageContext> clientsQueue;
	private final Map<Integer, ORAMMessage> clientsRequests;
	private int activeClients;
	private static final int MAX_N_CLIENTS = 2;
	private final ConfidentialRecoverable confidentialRecoverable;

	public ORAMServer(int processId) {
		this.orams = new TreeMap<>();
		this.senders = new TreeSet<>();
		this.getPMLatencies = new ArrayList<>();
		this.getPSLatencies = new ArrayList<>();
		this.evictionLatencies = new ArrayList<>();
		this.evictionRequests = new HashMap<>();
		this.clientsRequests = new HashMap<>();
		this.clientsQueue = new ArrayDeque<>();

		//Starting server
		confidentialRecoverable = new ConfidentialRecoverable(processId, this);
		new ServiceReplica(processId, "", confidentialRecoverable, confidentialRecoverable, null, null, null, confidentialRecoverable, confidentialRecoverable);
	}

	public static void main(String[] args) {
		new ORAMServer(Integer.parseInt(args[0]));
	}

	@Override
	public ConfidentialMessage appExecuteOrdered(byte[] plainData, VerifiableShare[] shares, MessageContext msgCtx) {
		ServerOperationType op;
		try (ByteArrayInputStream bis = new ByteArrayInputStream(plainData);
			 DataInputStream in = new DataInputStream(bis)) {
			op = ServerOperationType.getOperation(in.readByte());
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
					logger.debug("Received getPM request from {}", msgCtx.getSender());
					request = new GetPositionMap();
					request.readExternal(in);

					clientsQueue.addLast(msgCtx);
					clientsRequests.put(msgCtx.getSender(), request);
					if (activeClients < MAX_N_CLIENTS) {
						activeClients++;
						MessageContext nextClientMsgCtx = clientsQueue.pollFirst();
						if (nextClientMsgCtx == null) {
							logger.error("Something went wrong. There should be a getPM request in the queue");
							return null;
						}
						logger.debug("Processing getPM request from {} | active clients: {}", nextClientMsgCtx.getSender(),
								activeClients);
						ORAMMessage nextRequest = clientsRequests.remove(nextClientMsgCtx.getSender());
						ConfidentialMessage response = getPositionMap((GetPositionMap) nextRequest, nextClientMsgCtx);
						confidentialRecoverable.sendMessageToClient(nextClientMsgCtx, response);
						return null;
					}
					logger.debug("Added getPM request from {} to the queue (queue size: {}) | active clients: {}",
							msgCtx.getSender(), clientsQueue.size(), activeClients);
					return null;
				case GET_STASH_AND_PATH:
					logger.debug("Received getPS request from {}", msgCtx.getSender());
					request = new StashPathORAMMessage();
					request.readExternal(in);
					return getStashesAndPaths((StashPathORAMMessage) request, msgCtx.getSender());
				case EVICTION:
					logger.debug("Received eviction request from {}", msgCtx.getSender());
					evictionLock.lock();
					request = new ORAMMessage();
					request.readExternal(in);
					int hash = request.getOramId();

					do {
						logger.debug("Received eviction request from {} in {} ({})", msgCtx.getSender(), msgCtx.getSequence(), hash);
						request = evictionRequests.get(hash);
						if (request == null) {
							logger.debug("No eviction request from {} in {}", msgCtx.getSender(), msgCtx.getSequence());
							try {
								evictionCondition.await();
							} catch (InterruptedException e) {
								logger.error("Interrupted while waiting for eviction request from {}", msgCtx.getSender());
							}
						}
					} while (request == null);
					evictionLock.unlock();
					evictionBytesReceived += plainData.length;
					ConfidentialMessage evictionResponse = performEviction((EvictionORAMMessage) request, msgCtx);
					activeClients--;
					//process requests from the queue
					//processQueuedGetPMRequests();
					return evictionResponse;
			}
		} catch (IOException e) {
			logger.error("Failed to attend ordered request from {}", msgCtx.getSender(), e);
		} finally {
			printReport();
		}
		return null;
	}

	private void processQueuedGetPMRequests() {
		while (activeClients < MAX_N_CLIENTS && !clientsQueue.isEmpty()) {
			activeClients++;
			MessageContext nextClientMsgCtx = clientsQueue.poll();
			if (nextClientMsgCtx == null) {
				logger.debug("No more requests in the queue");
				break;
			}
			logger.info("Processing getPM request from queue from {} | active clients: {}", nextClientMsgCtx.getSender(),
					activeClients);
			ORAMMessage nextRequest = clientsRequests.remove(nextClientMsgCtx.getSender());
			ConfidentialMessage pmResponse = getPositionMap((GetPositionMap) nextRequest, nextClientMsgCtx);
			if (pmResponse == null) {
				logger.error("Failed to process getPM request from {}", nextClientMsgCtx.getSender());
				break;
			}
			confidentialRecoverable.sendMessageToClient(nextClientMsgCtx, pmResponse);
		}
	}

	@Override
	public ConfidentialMessage appExecuteUnordered(byte[] plainData, VerifiableShare[] shares, MessageContext msgCtx) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(plainData);
			 DataInputStream in = new DataInputStream(bis)) {
			ServerOperationType op = ServerOperationType.getOperation(in.readByte());
			ORAMMessage request;
			switch (op) {
				case GET_ORAM:
					request = new ORAMMessage();
					request.readExternal(in);
					return getORAM(request);
				case EVICTION:
					request = new EvictionORAMMessage();
					request.readExternal(in);
					evictionLock.lock();
					int hash = msgCtx.getSender() + Arrays.hashCode(plainData) * 32;
					logger.debug("Received eviction data request from {} in {} ({})", msgCtx.getSender(), msgCtx.getSequence(), hash);
					evictionRequests.put(hash, (EvictionORAMMessage) request);
					evictionCondition.signal();
					evictionLock.unlock();
					return new ConfidentialMessage(new byte[]{(byte) Status.SUCCESS.ordinal()});
			}
		} catch (IOException e) {
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
		evictionLatencies.add(delay);
		//measurementLogger.debug("eviction[ns]: {}", delay);
		evictCounter++;

		nOutstandingTreeObjects = oram.getNOutstandingTreeObjects();
		if (isEvicted)
			return new ConfidentialMessage(new byte[]{(byte) Status.SUCCESS.ordinal()});
		else
			return new ConfidentialMessage(new byte[]{(byte) Status.FAILED.ordinal()});
	}

	private ConfidentialMessage getStashesAndPaths(StashPathORAMMessage request, int clientId) {
		int oramId = request.getOramId();
		ORAM oram = orams.get(oramId);
		if (oram == null) {
			return null;
		}
		long start = System.nanoTime();
		EncryptedStashesAndPaths encryptedStashesAndPaths = oram.getStashesAndPaths(request.getPathId(), clientId);
		byte[] serializedPathAndStash = null;
		if (encryptedStashesAndPaths != null) {
			serializedPathAndStash = ORAMUtils.serializeEncryptedPathAndStash(encryptedStashesAndPaths);
		}
		long end = System.nanoTime();
		long delay = end - start;
		getPSLatencies.add(delay);
		//measurementLogger.debug("getPathStash[ns]: {}", delay);
		getPSCounter++;

		if (serializedPathAndStash == null)
			return new ConfidentialMessage();
		getPSBytesSent += serializedPathAndStash.length;
		return new ConfidentialMessage(serializedPathAndStash);
	}

	private ConfidentialMessage getORAM(ORAMMessage request) {
		ORAM oram = orams.get(request.getOramId());
		if (oram == null) {
			return new ConfidentialMessage(new byte[]{-1});
		} else {
			ORAMContext oramContext = oram.getOramContext();
			PositionMapType positionMapType = oramContext.getPositionMapType();
			int garbageCollectionFrequency = oramContext.getGarbageCollectionFrequency();
			int treeHeight = oramContext.getTreeHeight();
			int nBlocksPerBucket = oramContext.getBucketSize();
			int blockSize = oramContext.getBlockSize();
			try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				 DataOutputStream out = new DataOutputStream(bos)) {
				out.writeByte(positionMapType.ordinal());
				out.writeInt(garbageCollectionFrequency);
				out.writeInt(treeHeight);
				out.writeInt(nBlocksPerBucket);
				out.writeInt(blockSize);
				out.flush();
				bos.flush();
				return new ConfidentialMessage(bos.toByteArray());
			} catch (IOException e) {
				logger.error("Failed to serialize oram context: {}", e.getMessage());
				return new ConfidentialMessage();
			}
		}
	}

	private ConfidentialMessage getPositionMap(GetPositionMap request, MessageContext msgCtx) {
		int oramId = request.getOramId();
		ORAM oram = orams.get(oramId);
		if (oram == null)
			return null;
		long start = System.nanoTime();
		EncryptedPositionMaps positionMaps = oram.getPositionMaps(msgCtx.getSender(), request);

		byte[] serializedPositionMaps = ORAMUtils.serializeEncryptedPositionMaps(positionMaps);

		long end = System.nanoTime();
		long delay = end - start;
		getPMLatencies.add(delay);
		//measurementLogger.debug("getPositionMap[ns]: {}", delay);
		getPMCounter++;
		getPMBytesSent += serializedPositionMaps.length;
		return new ConfidentialMessage(serializedPositionMaps);
	}

	private ConfidentialMessage createORAM(CreateORAMMessage request) {
		int oramId = request.getOramId();
		PositionMapType positionMapType = request.getPositionMapType();
		int garbageCollectionFrequency = request.getGarbageCollectionFrequency();
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
			ORAM oram;
			if (positionMapType == PositionMapType.FULL_POSITION_MAP) {
				logger.debug("Using full position map");
				oram = new FullPositionMapORAM(oramId, positionMapType, garbageCollectionFrequency, treeHeight,
						nBlocksPerBucket, blockSize, encryptedPositionMap, encryptedStash);
			} else if (positionMapType == PositionMapType.TRIPLE_POSITION_MAP) {
				logger.debug("Using triple position map");
				oram = new TriplePositionMapORAM(oramId, positionMapType, garbageCollectionFrequency, treeHeight,
						nBlocksPerBucket, blockSize, encryptedPositionMap, encryptedStash);
			} else {
				logger.error("Unknown position map type");
				return new ConfidentialMessage(new byte[]{(byte) Status.FAILED.ordinal()});
			}
			orams.put(oramId, oram);
			return new ConfidentialMessage(new byte[]{(byte) Status.SUCCESS.ordinal()});
		}
	}


	public void printReport() {
		long end = System.nanoTime();
		long delay = end - lastPrint;
		if (delay >= 2_000_000_000) {
			long getPMAvgLatency = computeAverage(getPMLatencies);
			long getPSAvgLatency = computeAverage(getPSLatencies);
			long evictionAvgLatency = computeAverage(evictionLatencies);
			long getPMBandwidth = (long)(getPMBytesSent / (delay / 1_000_000_000.0));
			long getPSBandwidth = (long)(getPSBytesSent / (delay / 1_000_000_000.0));
			long evictionBandwidth = (long)(evictionBytesReceived / (delay / 1_000_000_000.0));

			measurementLogger.info("M-clients: {}", senders.size());
			measurementLogger.info("M-delta: {}", delay);
			measurementLogger.info("M-getPMRequests: {}", getPMCounter);
			measurementLogger.info("M-getPMAvgLatency: {}", getPMAvgLatency);
			measurementLogger.info("M-getPMBandwidth: {}", getPMBandwidth);
			measurementLogger.info("M-getPSRequests: {}", getPSCounter);
			measurementLogger.info("M-getPSAvgLatency: {}", getPSAvgLatency);
			measurementLogger.info("M-getPSBandwidth: {}", getPSBandwidth);
			measurementLogger.info("M-evictionRequests: {}", evictCounter);
			measurementLogger.info("M-evictionAvgLatency: {}", evictionAvgLatency);
			measurementLogger.info("M-evictionBandwidth: {}", evictionBandwidth);
			measurementLogger.info("M-outstanding: {}", nOutstandingTreeObjects);

			getPMCounter = 0;
			getPSCounter = 0;
			evictCounter = 0;
			getPMLatencies.clear();
			getPSLatencies.clear();
			evictionLatencies.clear();
			getPMBytesSent = 0;
			getPSBytesSent = 0;
			evictionBytesReceived = 0;
			lastPrint = end;
		}
	}

	private long computeAverage(ArrayList<Long> values) {
		if (values.isEmpty()) {
			return -1;
		}
		long sum = values.get(0);
		for (int i = 1; i < values.size(); i++) {
			sum += values.get(i);
		}
		return sum / values.size();
	}

	@Override
	public ConfidentialSnapshot getConfidentialSnapshot() {
		return new ConfidentialSnapshot(new byte[0]);
	}

	@Override
	public void installConfidentialSnapshot(ConfidentialSnapshot confidentialSnapshot) {

	}
}
