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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
	private int MAX_N_CLIENTS;
	private final ConfidentialRecoverable confidentialRecoverable;

	public ORAMServer(int max_clients, int processId) {
		this.orams = new TreeMap<>();
		this.senders = new TreeSet<>();
		this.getPMLatencies = new ArrayList<>();
		this.getPSLatencies = new ArrayList<>();
		this.evictionLatencies = new ArrayList<>();
		this.evictionRequests = new HashMap<>();
		this.clientsRequests = new HashMap<>();
		this.clientsQueue = new ArrayDeque<>();
		this.MAX_N_CLIENTS = max_clients;

		//Starting server
		confidentialRecoverable = new ConfidentialRecoverable(processId, this);
		new ServiceReplica(processId, confidentialRecoverable, confidentialRecoverable, confidentialRecoverable, confidentialRecoverable);
	}

	public static void main(String[] args) {
		new ORAMServer(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
	}

	@Override
	public ConfidentialMessage appExecuteOrdered(byte[] plainData, VerifiableShare[] shares, MessageContext msgCtx) {
		try {
			ServerOperationType op = ServerOperationType.getOperation(plainData[0]);
			ORAMMessage request;
			senders.add(msgCtx.getSender());
			switch (op) {
				case UPDATE_CONCURRENT_CLIENTS:
					request = new UpdateConcurrentClientsMessage();
					request.readExternal(plainData, 1);
					MAX_N_CLIENTS = ((UpdateConcurrentClientsMessage)request).getMaximumNConcurrentClients();
					logger.info("MAX_N_CLIENTS: {}", MAX_N_CLIENTS);
					return new ConfidentialMessage(new byte[]{(byte) Status.SUCCESS.ordinal()});
				case DEBUG:
					request = new GetDebugMessage();
					request.readExternal(plainData, 1);
					return getDebugInformation((GetDebugMessage) request);
				case CREATE_ORAM:
					request = new CreateORAMMessage();
					request.readExternal(plainData, 1);
					VerifiableShare encryptionKeyShare = shares.length > 0 ? shares[0] : null;
					return createORAM((CreateORAMMessage) request, encryptionKeyShare);
				case GET_ORAM:
					request = new ORAMMessage();
					request.readExternal(plainData, 1);
					return getORAM(request);
				case GET_POSITION_MAP:
					logger.debug("Received getPM request from {}", msgCtx.getSender());
					request = new GetPathMaps();
					request.readExternal(plainData, 1);

					clientsQueue.addLast(msgCtx);
					clientsRequests.put(msgCtx.getSender(), request);
					processQueuedGetPMRequests();
					return null;
				case GET_STASH_AND_PATH:
					logger.debug("Received getPS request from {}", msgCtx.getSender());
					request = new StashPathORAMMessage();
					request.readExternal(plainData, 1);
					return getStashesAndPaths((StashPathORAMMessage) request, msgCtx.getSender());
				case EVICTION:
					logger.debug("Received eviction request from {}", msgCtx.getSender());
					evictionLock.lock();
					request = new ORAMMessage();
					request.readExternal(plainData, 1);
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
					processQueuedGetPMRequests();
					return evictionResponse;
			}
		} finally {
			printReport();
		}
		throw new RuntimeException("Unknown operation type");
	}

	private void processQueuedGetPMRequests() {
		while (activeClients < MAX_N_CLIENTS && !clientsQueue.isEmpty()) {
			activeClients++;
			MessageContext nextClientMsgCtx = clientsQueue.poll();
			if (nextClientMsgCtx == null) {
				logger.debug("No more requests in the queue");
				break;
			}
			logger.debug("Processing getPM request from queue from {} | active clients: {}", nextClientMsgCtx.getSender(),
					activeClients);
			ORAMMessage nextRequest = clientsRequests.remove(nextClientMsgCtx.getSender());
			ConfidentialMessage pmResponse = getPositionMap((GetPathMaps) nextRequest, nextClientMsgCtx);
			if (pmResponse == null) {
				logger.error("Failed to process getPM request from {}", nextClientMsgCtx.getSender());
				break;
			}
			confidentialRecoverable.sendMessageToClient(nextClientMsgCtx, pmResponse);
		}
	}

	@Override
	public ConfidentialMessage appExecuteUnordered(byte[] plainData, VerifiableShare[] shares, MessageContext msgCtx) {
		ServerOperationType op = ServerOperationType.getOperation(plainData[0]);
		ORAMMessage request;
		switch (op) {
			case GET_ORAM:
				request = new ORAMMessage();
				request.readExternal(plainData, 1);
				return getORAM(request);
			case EVICTION:
				request = new EvictionORAMMessage();
				request.readExternal(plainData, 1);
				evictionLock.lock();
				int hash = msgCtx.getSender() + ORAMUtils.computeHashCode(plainData) * 32;
				logger.debug("Received eviction data request from {} in {} ({})", msgCtx.getSender(), msgCtx.getSequence(), hash);
				evictionRequests.put(hash, (EvictionORAMMessage) request);
				evictionCondition.signal();
				evictionLock.unlock();
				return new ConfidentialMessage(new byte[]{(byte) Status.SUCCESS.ordinal()});
		}
		throw new RuntimeException("Unknown operation type");
	}

	private ConfidentialMessage performEviction(EvictionORAMMessage request, MessageContext msgCtx) {
		logger.debug("Processing eviction request from {}", msgCtx.getSender());
		int oramId = request.getOramId();
		ORAM oram = orams.get(oramId);
		if (oram == null)
			return null;
		long start = System.nanoTime();
		boolean isEvicted = oram.performEviction(request.getEncryptedStash(), request.getEncryptedPathMap(),
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
		logger.debug("Processing getPS request from {}", clientId);
		int oramId = request.getOramId();
		ORAM oram = orams.get(oramId);
		if (oram == null) {
			return null;
		}
		long start = System.nanoTime();
		EncryptedStashesAndPaths encryptedStashesAndPaths = oram.getStashesAndPaths(request.getPathId(), clientId);
		byte[] serializedPathAndStash = null;
		if (encryptedStashesAndPaths != null) {
			int dataSize = encryptedStashesAndPaths.getSerializedSize();
			serializedPathAndStash = new byte[dataSize];
			int offset = encryptedStashesAndPaths.writeExternal(serializedPathAndStash, 0);
			if (offset != dataSize) {
				logger.error("Failed to serialize path and stash");
				return new ConfidentialMessage();
			}
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

	private ConfidentialMessage getDebugInformation(GetDebugMessage request) {
		ORAM oram = orams.get(request.getOramId());
		if (oram == null) {
			return new ConfidentialMessage(new byte[]{-1});
		}
		EncryptedDebugSnapshot encryptedDebugSnapshot = oram.getDebugSnapshot(request.getClientId());
		int dataSize = encryptedDebugSnapshot.getSerializedSize();
		byte[] serializedDebugSnapshot = new byte[dataSize];
		int offset = encryptedDebugSnapshot.writeExternal(serializedDebugSnapshot, 0);
		if (offset != dataSize) {
			logger.error("Failed to serialize debug snapshot");
			return new ConfidentialMessage();
		}

		return new ConfidentialMessage(serializedDebugSnapshot);
	}

	private ConfidentialMessage getORAM(ORAMMessage request) {
		ORAM oram = orams.get(request.getOramId());
		if (oram == null) {
			return new ConfidentialMessage(new byte[]{-1});
		} else {
			ORAMContext oramContext = oram.getOramContext();
			VerifiableShare encryptionKeyShare = oram.getEncryptionKeyShare();
			PositionMapType positionMapType = oramContext.getPositionMapType();
			int treeHeight = oramContext.getTreeHeight();
			int nBlocksPerBucket = oramContext.getBucketSize();
			int blockSize = oramContext.getBlockSize();
			try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				 DataOutputStream out = new DataOutputStream(bos)) {
				out.writeByte(positionMapType.ordinal());
				out.writeInt(treeHeight);
				out.writeInt(nBlocksPerBucket);
				out.writeInt(blockSize);
				out.flush();
				bos.flush();
				return new ConfidentialMessage(bos.toByteArray(), encryptionKeyShare);
			} catch (IOException e) {
				logger.error("Failed to serialize oram context: {}", e.getMessage());
				return new ConfidentialMessage();
			}
		}
	}

	private ConfidentialMessage getPositionMap(GetPathMaps request, MessageContext msgCtx) {
		logger.debug("Processing getPM request from {}", msgCtx.getSender());
		int oramId = request.getOramId();
		ORAM oram = orams.get(oramId);
		if (oram == null)
			return null;
		long start = System.nanoTime();
		EncryptedPathMaps positionMaps = oram.getPositionMaps(msgCtx.getSender(), request);

		int dataSize = positionMaps.getSerializedSize();
		byte[] serializedPositionMaps = new byte[dataSize];

		int offset = positionMaps.writeExternal(serializedPositionMaps, 0);
		if (offset != dataSize) {
			logger.error("Failed to serialize position maps");
			return new ConfidentialMessage();
		}

		long end = System.nanoTime();
		long delay = end - start;
		getPMLatencies.add(delay);
		//measurementLogger.debug("getPositionMap[ns]: {}", delay);
		getPMCounter++;
		getPMBytesSent += serializedPositionMaps.length;
		return new ConfidentialMessage(serializedPositionMaps);
	}

	private ConfidentialMessage createORAM(CreateORAMMessage request, VerifiableShare encryptionKeyShare) {
		int oramId = request.getOramId();
		PositionMapType positionMapType = request.getPositionMapType();
		int treeHeight = request.getTreeHeight();
		int nBlocksPerBucket = request.getBucketSize();
		int blockSize = request.getBlockSize();
		EncryptedPathMap encryptedPathMap = request.getEncryptedPathMap();
		EncryptedStash encryptedStash = request.getEncryptedStash();
		if (orams.containsKey(oramId)) {
			logger.debug("ORAM with id {} exists", oramId);
			return new ConfidentialMessage(new byte[]{(byte) Status.FAILED.ordinal()});
		} else {
			logger.debug("Created an ORAM with id {} of {} levels", oramId, treeHeight + 1);
			ORAM oram = new ORAM(oramId, encryptionKeyShare, positionMapType, treeHeight,
					nBlocksPerBucket, blockSize, encryptedPathMap, encryptedStash);
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

			//compute throughput
			double getPMThroughput = getPMCounter / (delay / 1_000_000_000.0);
			double getPSThroughput = getPSCounter / (delay / 1_000_000_000.0);
			double evictionThroughput = evictCounter / (delay / 1_000_000_000.0);

			logger.info("Throughput: {} getPM/s, {} getPS/s, {} eviction/s | maxClients: {}", getPMThroughput,
					getPSThroughput, evictionThroughput, MAX_N_CLIENTS);

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
