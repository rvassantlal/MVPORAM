package oram.benchmark;

import controller.IBenchmarkStrategy;
import controller.IWorkerStatusListener;
import controller.WorkerHandler;
import oram.benchmark.measurements.DefaultMeasurements;
import oram.benchmark.measurements.ResourcesMeasurements;
import oram.utils.ORAMUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.Storage;
import worker.IProcessingResult;
import worker.ProcessInformation;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MeasurementBenchmarkStrategy implements IBenchmarkStrategy, IWorkerStatusListener {
	private final Logger logger = LoggerFactory.getLogger("benchmark.oram");
	private final Lock lock;
	private final Condition sleepCondition;
	private final Set<Integer> serverWorkersIds;
	private final Set<Integer> clientWorkersIds;
	private final String serverCommand;
	private final String clientCommand;
	private final String sarCommand;
	private int round;
	private int nRounds;
	private int treeHeight;
	private int bucketSize;
	private int blockSize;
	private WorkerHandler[] clientWorkers;
	private WorkerHandler[] serverWorkers;
	private final Map<Integer,WorkerHandler> measurementWorkers;
	private CountDownLatch serversReadyCounter;
	private CountDownLatch clientsReadyCounter;
	private CountDownLatch measurementDeliveredCounter;
	private int[] numMaxRealClients;
	private double[] avgLatency;
	private double[] latencyDev;
	private double[] avgThroughput;
	private double[] throughputDev;
	private double[] maxLatency;
	private double[] maxThroughput;
	private boolean measureResources;
	private int queueSize;
	private String storageFileNamePrefix;
	private String positionMapType;


	public MeasurementBenchmarkStrategy() {
		this.lock = new ReentrantLock(true);
		this.sleepCondition = lock.newCondition();
		this.serverWorkersIds = new HashSet<>();
		this.clientWorkersIds = new HashSet<>();
		this.measurementWorkers = new HashMap<>();
		String initialCommand = "java -Xmx60g -Djava.security.properties=./config/java" +
				".security -Dlogback.configurationFile=./config/logback.xml -cp lib/* ";
		this.serverCommand = initialCommand + "oram.server.ORAMServer ";
		this.clientCommand = initialCommand + "oram.benchmark.ORAMBenchmarkClient ";
		this.sarCommand = "sar -u -r -n DEV 1";
	}

	@Override
	public void executeBenchmark(WorkerHandler[] workers, Properties benchmarkParameters) {
		int f = Integer.parseInt(benchmarkParameters.getProperty("experiment.f"));
		String hostFile = benchmarkParameters.getProperty("experiment.hosts.file");
		String[] tokens = benchmarkParameters.getProperty("experiment.clients_per_round").split(" ");
		measureResources = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.measure_resources"));

		int nServerWorkers = 3 * f + 1;
		int nClientWorkers = workers.length - nServerWorkers;
		int maxClientsPerProcess = 3;
		int nRequests = 10_000_000;
		int sleepBetweenRounds = 30;
		int[] clientsPerRound = new int[tokens.length];
		for (int i = 0; i < tokens.length; i++) {
			clientsPerRound[i] = Integer.parseInt(tokens[i]);
		}
		positionMapType = benchmarkParameters.getProperty("experiment.position_map_type");
		String[] treeHeightTokens = benchmarkParameters.getProperty("experiment.tree_heights").split(" ");
		String[] bucketSizeTokens = benchmarkParameters.getProperty("experiment.bucket_sizes").split(" ");
		String[] blockSizeTokens = benchmarkParameters.getProperty("experiment.block_sizes").split(" ");
		String[] queueSizeTokens = benchmarkParameters.getProperty("experiment.concurrent_clients").split(" ");

		//Parse parameters
		int[] treeHeights = new int[treeHeightTokens.length];
		int[] bucketSizes = new int[bucketSizeTokens.length];
		int[] blockSizes = new int[blockSizeTokens.length];
		int[] queueSizes = new int[queueSizeTokens.length];
		for (int i = 0; i < treeHeightTokens.length; i++) {
			treeHeights[i] = Integer.parseInt(treeHeightTokens[i]);
			bucketSizes[i] = Integer.parseInt(bucketSizeTokens[i]);
			blockSizes[i] = Integer.parseInt(blockSizeTokens[i]);
			queueSizes[i] = Integer.parseInt(queueSizeTokens[i]);
		}

		//Separate workers
		serverWorkers = new WorkerHandler[nServerWorkers];
		clientWorkers = new WorkerHandler[nClientWorkers];
		System.arraycopy(workers, 0, serverWorkers, 0, nServerWorkers);
		System.arraycopy(workers, nServerWorkers, clientWorkers, 0, nClientWorkers);
		//Sort client workers to use the same worker as measurement client
		Arrays.sort(clientWorkers, (o1, o2) -> -Integer.compare(o1.getWorkerId(), o2.getWorkerId()));
		Arrays.stream(serverWorkers).forEach(w -> serverWorkersIds.add(w.getWorkerId()));
		Arrays.stream(clientWorkers).forEach(w -> clientWorkersIds.add(w.getWorkerId()));

		printWorkersInfo();

		//Setup workers
		if (hostFile != null) {
			logger.info("Setting up workers...");
			String hosts = loadHosts(hostFile);
			if (hosts == null)
				return;
			String setupInformation = String.format("%b\t%d\t%s", true, f, hosts);
			Arrays.stream(workers).forEach(w -> w.setupWorker(setupInformation));
		}

		for (int i = 0; i < treeHeights.length; i++) {
			long startTime = System.currentTimeMillis();
			logger.info("============ Strategy Parameters ============");
			treeHeight = treeHeights[i];
			bucketSize = bucketSizes[i];
			blockSize = blockSizes[i];
			queueSize = queueSizes[i];

			logger.info("Tree height: {}", treeHeight);
			logger.info("Bucket size: {}", bucketSize);
			logger.info("Block size: {}", blockSize);
			logger.info("Position map type: {}", positionMapType);
			logger.info("Number of buckets: {}", ORAMUtils.computeNumberOfNodes(treeHeight));
			logger.info("ORAM size: {} blocks", ORAMUtils.computeTreeSize(treeHeight, bucketSize));
			logger.info("Database size: {} bytes", ORAMUtils.computeDatabaseSize(treeHeight, bucketSize, blockSize));
			logger.info("Path length: {} blocks", ORAMUtils.computePathLength(treeHeight, bucketSize));
			logger.info("Path size: {} bytes", ORAMUtils.computePathSize(treeHeight, bucketSize, blockSize));
			logger.info("Queue size: {} clients", queueSize);

			nRounds = clientsPerRound.length;
			numMaxRealClients = new int[nRounds];
			avgLatency = new double[nRounds];
			latencyDev = new double[nRounds];
			avgThroughput = new double[nRounds];
			throughputDev = new double[nRounds];
			maxLatency = new double[nRounds];
			maxThroughput = new double[nRounds];

			round = 1;
			while (true) {
				try {
					lock.lock();
					logger.info("============ Round: {} ============", round);
					int nClients = clientsPerRound[round - 1];
					measurementWorkers.clear();
					storageFileNamePrefix = String.format("f_%d_pm_%s_height_%d_bucket_%d_block_%d_round_%d_", f,
							positionMapType, treeHeight, bucketSize, blockSize, nClients);
					//Distribute clients per workers
					int[] clientsPerWorker = distributeClientsPerWorkers(nClientWorkers, nClients);
					String vector = Arrays.toString(clientsPerWorker);
					int total = Arrays.stream(clientsPerWorker).sum();
					logger.info("Clients per worker: {} -> Total: {}", vector, total);

					//Start servers
					startServers(nServerWorkers, serverWorkers);

					//Start Clients
					startClients(nServerWorkers, maxClientsPerProcess, nRequests,
							clientWorkers, clientsPerWorker);

					//Wait for system to stabilize
					logger.info("Waiting 30s...");
					sleepSeconds(30);

					//Get measurements
					getMeasurements();

					//Stop processes
					Arrays.stream(workers).forEach(WorkerHandler::stopWorker);

					round++;
					if (round > nRounds) {
						storeResumedMeasurements(numMaxRealClients, avgLatency, latencyDev, avgThroughput,
								throughputDev, maxLatency, maxThroughput);
						break;
					}

					//Wait between round
					logger.info("Waiting {}s before new round", sleepBetweenRounds);
					sleepSeconds(sleepBetweenRounds);
				} catch (InterruptedException e) {
					break;
				} finally {
					lock.unlock();
				}
			}

			long endTime = System.currentTimeMillis();
			logger.info("Execution duration: {}s", (endTime - startTime) / 1000);

		}
	}

	private void printWorkersInfo() {
		StringBuilder sb = new StringBuilder();
		for (WorkerHandler serverWorker : serverWorkers) {
			sb.append(serverWorker.getWorkerId());
			sb.append(" ");
		}
		logger.info("Server workers: {}", sb);

		sb = new StringBuilder();
		for (WorkerHandler clientWorker : clientWorkers) {
			sb.append(clientWorker.getWorkerId());
			sb.append(" ");
		}
		logger.info("Client workers: {}", sb);
	}

	private void startServers(int nServerWorkers, WorkerHandler[] serverWorkers) throws InterruptedException {
		logger.info("Starting servers...");
		serversReadyCounter = new CountDownLatch(nServerWorkers);
		measurementWorkers.put(serverWorkers[0].getWorkerId(), serverWorkers[0]);
		if (measureResources && serverWorkers.length > 1)
			measurementWorkers.put(serverWorkers[1].getWorkerId(), serverWorkers[1]);
		for (int i = 0; i < serverWorkers.length; i++) {
			String command = serverCommand + queueSize + " " + i;
			int nCommands = measureResources && i < 2 ? 2 : 1;
			ProcessInformation[] commands = new ProcessInformation[nCommands];
			commands[0] = new ProcessInformation(command, ".");
			if (measureResources && i < 2) {// Measure resources of leader and a follower server
				commands[1] = new ProcessInformation(sarCommand, ".");
			}
			serverWorkers[i].startWorker(0, commands, this);
			sleepSeconds(2);
		}
		serversReadyCounter.await();
	}

	private void startClients(int nServerWorkers, int maxClientsPerProcess, int nRequests,
							  WorkerHandler[] clientWorkers,
							  int[] clientsPerWorker) throws InterruptedException {
		logger.info("Starting clients...");
		clientsReadyCounter = new CountDownLatch(clientsPerWorker.length);
		int clientInitialId = nServerWorkers + 1000;
		measurementWorkers.put(clientWorkers[0].getWorkerId(), clientWorkers[0]);
		if (measureResources && clientsPerWorker.length > 1)
			measurementWorkers.put(clientWorkers[1].getWorkerId(), clientWorkers[1]);

		for (int i = 0; i < clientsPerWorker.length && i < clientWorkers.length; i++) {
			int totalClientsPerWorker = clientsPerWorker[i];
			int nProcesses = totalClientsPerWorker / maxClientsPerProcess
					+ (totalClientsPerWorker % maxClientsPerProcess == 0 ? 0 : 1);
			int nCommands = nProcesses + (measureResources && i < 2 ? 1 : 0);
			ProcessInformation[] commands = new ProcessInformation[nCommands];
			boolean isMeasurementWorker = i == 0; // First client is measurement client

			for (int j = 0; j < nProcesses; j++) {
				int clientsPerProcess = Math.min(totalClientsPerWorker, maxClientsPerProcess);
				String command = clientCommand + clientInitialId + " " + clientsPerProcess
						+ " " + nRequests + " " + positionMapType + " " + treeHeight + " "
						+ bucketSize + " " + blockSize + " " + isMeasurementWorker;
				commands[j] = new ProcessInformation(command, ".");
				totalClientsPerWorker -= clientsPerProcess;
				clientInitialId += clientsPerProcess;
			}
			if (measureResources && i < 2) {// Measure resources of measurement and a load client
				commands[nProcesses] = new ProcessInformation(sarCommand, ".");
			}
			clientWorkers[i].startWorker(50, commands, this);
		}
		clientsReadyCounter.await();
	}

	private void storeResumedMeasurements(int[] numMaxRealClients, double[] avgLatency, double[] latencyDev,
										  double[] avgThroughput, double[] throughputDev, double[] maxLatency,
										  double[] maxThroughput) {
		String fileName = "measurements_height_" + treeHeight + "_bucket_" + bucketSize + "_block_" + blockSize + ".csv";
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(
				Files.newOutputStream(Paths.get(fileName))))) {
			resultFile.write("clients(#),avgLatency(ns),latencyDev(ns),avgThroughput(ops/s),throughputDev(ops/s)," +
					"maxLatency(ns),maxThroughput(ops/s)\n");
			for (int i = 0; i < nRounds; i++) {
				int clients = numMaxRealClients[i];
				double aLat = avgLatency[i];
				double dLat = latencyDev[i];
				double aThr = avgThroughput[i];
				double dThr = throughputDev[i];
				double mLat = maxLatency[i];
				double mThr = maxThroughput[i];
				resultFile.write(String.format("%d,%f,%f,%f,%f,%f,%f\n", clients, aLat, dLat, aThr, dThr, mLat, mThr));
			}
			resultFile.flush();
		} catch (IOException e) {
			logger.error("Error while storing measurements", e);
		}
	}

	private void getMeasurements() throws InterruptedException {
		//Start measurements
		logger.debug("Starting measurements...");
		measurementWorkers.values().forEach(WorkerHandler::startProcessing);

		//Wait for measurements
		logger.info("Measuring during {}s", 60 * 2);
		sleepSeconds(60 * 2);

		//Stop measurements
		measurementWorkers.values().forEach(WorkerHandler::stopProcessing);

		//Get measurement results
		int nMeasurements;
		if (measureResources) {
			//servers: 2 + 1 + 1 ...
			//clients: 2 + 1 + 1 ...
			nMeasurements = measurementWorkers.size() + 2;
		} else {
			nMeasurements = 2;
		}
		logger.debug("Getting {} measurements from {} workers...", nMeasurements, measurementWorkers.size());
		measurementDeliveredCounter = new CountDownLatch(nMeasurements);

		measurementWorkers.values().forEach(WorkerHandler::requestProcessingResult);

		measurementDeliveredCounter.await();
	}

	private static int[] distributeClientsPerWorkers(int nWorkers, int nClients) {
		if (nClients == 1) {
			return new int[]{1};
		}
		if (nWorkers < 2) {
			return new int[]{nClients};
		}
		nClients--;//remove measurement client

		int nClientsPerWorkers = nClients / (nWorkers - 1);// -1 for measurement worker

		int nWorkersToUse = Math.min(nWorkers, nClients + 1);

		int[] distribution = new int[nWorkersToUse];//one for measurement worker
		Arrays.fill(distribution, nClientsPerWorkers);
		distribution[0] = 1;
		nClients -= nClientsPerWorkers * (nWorkers - 1);
		int i = 1;
		while (nClients > 0) {
			nClients--;
			distribution[i]++;
			i = (i + 1) % distribution.length;
			if (i == 0) {
				i++;
			}
		}

		return distribution;
	}

	private void sleepSeconds(long duration) throws InterruptedException {
		lock.lock();
		ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
		scheduledExecutorService.schedule(() -> {
			lock.lock();
			sleepCondition.signal();
			lock.unlock();
		}, duration, TimeUnit.SECONDS);
		sleepCondition.await();
		scheduledExecutorService.shutdown();
		lock.unlock();
	}

	@Override
	public void onReady(int workerId) {
		if (serverWorkersIds.contains(workerId)) {
			serversReadyCounter.countDown();
		} else if (clientWorkersIds.contains(workerId)) {
			clientsReadyCounter.countDown();
		}
	}

	@Override
	public void onEnded(int i) {

	}

	@Override
	public void onError(int workerId, String errorMessage) {
		if (serverWorkersIds.contains(workerId)) {
			logger.error("Error in server worker {}: {}", workerId, errorMessage);
		} else if (clientWorkersIds.contains(workerId)) {
			logger.error("Error in client worker {}: {}", workerId, errorMessage);
		} else {
			logger.error("Error in unused worker {}: {}", workerId, errorMessage);
		}
	}

	@Override
	public synchronized void onResult(int workerId, IProcessingResult processingResult) {
		if (!measurementWorkers.containsKey(workerId)) {
			logger.warn("Received measurements results from unused worker {}", workerId);
			return;
		}

		if (processingResult instanceof DefaultMeasurements && workerId == serverWorkers[0].getWorkerId()) {
			logger.debug("Received leader server performance results");
			DefaultMeasurements serverMeasurements = (DefaultMeasurements) processingResult;
			processServerMeasurementResults(serverMeasurements);
			measurementDeliveredCounter.countDown();
		} else if (processingResult instanceof DefaultMeasurements && workerId == clientWorkers[0].getWorkerId()) {
			logger.debug("Received measurement client performance results");
			DefaultMeasurements clientMeasurements = (DefaultMeasurements) processingResult;
			processClientMeasurementResults(clientMeasurements);
			measurementDeliveredCounter.countDown();
		} else if (processingResult instanceof ResourcesMeasurements) {
			String tag = null;
			if (workerId == serverWorkers[0].getWorkerId()) {
				logger.debug("Received leader server resources usage results");
				tag = "leader_server";
			} else if (serverWorkers.length > 1 && workerId == serverWorkers[1].getWorkerId()) {
				logger.debug("Received follower server resources usage results");
				tag = "follower_server";
			} else if (workerId == clientWorkers[0].getWorkerId()) {
				logger.debug("Received measurement client resources usage results");
				tag = "measurement_client";
			} else if (clientWorkers.length > 1 && workerId == clientWorkers[1].getWorkerId()) {
				logger.debug("Received load client resources usage results");
				tag = "load_client";
			}

			if (tag != null) {
				ResourcesMeasurements resourcesMeasurements = (ResourcesMeasurements) processingResult;
				processResourcesMeasurements(resourcesMeasurements, tag);
				measurementDeliveredCounter.countDown();
			} else {
				logger.warn("Received resources usage results from unused worker {}", workerId);
			}
		}
	}

	private void processResourcesMeasurements(ResourcesMeasurements resourcesMeasurements, String tag) {
		long[] cpu = resourcesMeasurements.getCpu();
		long[] mem = resourcesMeasurements.getMemory();
		long[][] netReceived = resourcesMeasurements.getNetReceived();
		long[][] netTransmitted = resourcesMeasurements.getNetTransmitted();

		String fileName = storageFileNamePrefix + "cpu_" + tag + ".csv";
		saveResourcesMeasurements(fileName, cpu);

		fileName = storageFileNamePrefix + "mem_" + tag + ".csv";
		saveResourcesMeasurements(fileName, mem);

		fileName = storageFileNamePrefix + "net_received_" + tag + ".csv";
		saveResourcesMeasurements(fileName, netReceived);

		fileName = storageFileNamePrefix + "net_transmitted_" + tag + ".csv";
		saveResourcesMeasurements(fileName, netTransmitted);
	}

	private void saveResourcesMeasurements(String fileName, long[]... data) {
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(
				Files.newOutputStream(Paths.get(fileName))))) {
			int size = data[0].length;
			int i = 0;
			while (i < size) {
				StringBuilder sb = new StringBuilder();
				for (long[] datum : data) {
					sb.append(String.format("%.2f", datum[i] / 100.0));
					sb.append(",");
				}
				sb.deleteCharAt(sb.length() - 1);
				resultFile.write(sb + "\n");
				i++;
			}
			resultFile.flush();
		} catch (IOException e) {
			logger.error("Error while storing resources measurements results", e);
		}
	}


	private void processClientMeasurementResults(DefaultMeasurements clientMeasurements) {
		saveClientMeasurements(clientMeasurements.getMeasurements());

		long[] globalLatencies = clientMeasurements.getMeasurements("global");
		long[] pmLatencies = clientMeasurements.getMeasurements("map");
		long[] psLatencies = clientMeasurements.getMeasurements("ps");
		long[] evictionLatencies = clientMeasurements.getMeasurements("eviction");

		Storage st = new Storage(globalLatencies);
		Storage getPMLatenciesStorage = new Storage(pmLatencies);
		Storage getPSLatenciesStorage = new Storage(psLatencies);
		Storage evictionLatenciesStorage = new Storage(evictionLatencies);
		String sb = String.format("Client-side measurements [%d samples]:\n", globalLatencies.length) +
				String.format("\tAccess latency[ms]: avg:%.3f dev:%.3f max: %d\n",
						st.getAverage(true) / 1_000_000.0, st.getDP(true) / 1_000_000.0,
						st.getMax(true) / 1_000_000) +
				String.format("\tGet PM latency[ms]: avg:%.3f\n",
						getPMLatenciesStorage.getAverage(true) / 1_000_000.0) +
				String.format("\tGet PS latency[ms]: avg:%.3f\n",
						getPSLatenciesStorage.getAverage(true) / 1_000_000.0) +
				String.format("\tEviction latency[ms]: avg:%.3f",
						evictionLatenciesStorage.getAverage(true) / 1_000_000.0);
		logger.info(sb);

		avgLatency[round - 1] = st.getAverage(true);
		latencyDev[round - 1] = st.getDP(true);
		maxLatency[round - 1] = st.getMax(true);
	}

	private void processServerMeasurementResults(DefaultMeasurements serverMeasurements) {
		saveServerMeasurements(serverMeasurements.getMeasurements());

		long[] clients = serverMeasurements.getMeasurements("clients");
		long[] delta = serverMeasurements.getMeasurements("delta");
		long[] nGetPMRequests = serverMeasurements.getMeasurements("getPMRequests");
		long[] nGetPSRequests = serverMeasurements.getMeasurements("getPSRequests");
		long[] nEvictionRequests = serverMeasurements.getMeasurements("evictionRequests");
		long[] getPMLatencies = serverMeasurements.getMeasurements("getPMAvgLatency");
		long[] getPSLatencies = serverMeasurements.getMeasurements("getPSAvgLatency");
		long[] evictionLatencies = serverMeasurements.getMeasurements("evictionAvgLatency");
		long[] outstanding = serverMeasurements.getMeasurements("outstanding");

		long[] getPMThroughput = new long[clients.length];
		long[] getPSThroughput = new long[clients.length];
		long[] evictionThroughput = new long[clients.length];
		long minClients = Long.MAX_VALUE;
		long maxClients = Long.MIN_VALUE;
		long minOutstanding = Long.MAX_VALUE;
		long maxOutstanding = Long.MIN_VALUE;
		int size = clients.length;
		for (int i = 0; i < size; i++) {
			minClients = Long.min(minClients, clients[i]);
			maxClients = Long.max(maxClients, clients[i]);
			minOutstanding = Long.min(minOutstanding, outstanding[i]);
			maxOutstanding = Long.max(maxOutstanding, outstanding[i]);
			getPMThroughput[i] = (long) (nGetPMRequests[i] / (delta[i] / 1_000_000_000.0));
			getPSThroughput[i] = (long) (nGetPSRequests[i] / (delta[i] / 1_000_000_000.0));
			evictionThroughput[i] = (long) (nEvictionRequests[i] / (delta[i] / 1_000_000_000.0));
		}
		Storage getPMThroughputStorage = new Storage(getPMThroughput);
		Storage getPSThroughputStorage = new Storage(getPSThroughput);
		Storage evictionThroughputStorage = new Storage(evictionThroughput);
		Storage getPMLatenciesStorage = new Storage(getPMLatencies);
		Storage getPSLatenciesStorage = new Storage(getPSLatencies);
		Storage evictionLatenciesStorage = new Storage(evictionLatencies);
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Server-side measurements [%d samples]:\n", evictionThroughput.length));
		sb.append(String.format("\tClients[#]: min:%d max:%d\n", minClients, maxClients));
		sb.append(String.format("\tOutstanding trees[#]: min:%d max:%d\n", minOutstanding, maxOutstanding));
		sb.append(String.format("\tGet PM[ops/s]: avg:%.3f dev:%.3f max: %d\n",
				getPMThroughputStorage.getAverage(true), getPMThroughputStorage.getDP(true),
				getPMThroughputStorage.getMax(true)));
		sb.append(String.format("\tGet PS[ops/s]: avg:%.3f dev:%.3f max: %d\n",
				getPSThroughputStorage.getAverage(true), getPSThroughputStorage.getDP(true),
				getPSThroughputStorage.getMax(true)));
		sb.append(String.format("\tEviction[ops/s]: avg:%.3f dev:%.3f max: %d\n",
				evictionThroughputStorage.getAverage(true), evictionThroughputStorage.getDP(true),
				evictionThroughputStorage.getMax(true)));
		sb.append(String.format("\tGet PM latency[ms]: avg:%.3f\n",
				getPMLatenciesStorage.getAverage(true) / 1_000_000.0));
		sb.append(String.format("\tGet PS latency[ms]: avg:%.3f\n",
				getPSLatenciesStorage.getAverage(true) / 1_000_000.0));
		sb.append(String.format("\tEviction latency[ms]: avg:%.3f",
				evictionLatenciesStorage.getAverage(true) / 1_000_000.0));

		logger.info(sb.toString());

		numMaxRealClients[round - 1] = (int) maxClients;
		avgThroughput[round - 1] = evictionThroughputStorage.getAverage(true);
		throughputDev[round - 1] = evictionThroughputStorage.getDP(true);
		maxThroughput[round - 1] = evictionThroughputStorage.getMax(true);
	}

	private void saveClientMeasurements(Map<String, long[]> measurements) {
		String fileName = storageFileNamePrefix + "client_global.csv";
		String header = "";
		PrimitiveIterator.OfLong[] iterators = new PrimitiveIterator.OfLong[measurements.size()];

		header += "global[ns]";
		iterators[0] = Arrays.stream(measurements.get("global")).iterator();

		header += ",map[ns]";
		iterators[1] = Arrays.stream(measurements.get("map")).iterator();

		header += ",getPM[ns]";
		iterators[2] = Arrays.stream(measurements.get("getPM")).iterator();

		header += ",receivedPM[#pms]";
		iterators[3] = Arrays.stream(measurements.get("receivedPM")).iterator();

		header += ",ps[ns]";
		iterators[4] = Arrays.stream(measurements.get("ps")).iterator();

		header += ",getPS[ns]";
		iterators[5] = Arrays.stream(measurements.get("getPS")).iterator();

		header += ",receivedStashes[#stashes]";
		iterators[6] = Arrays.stream(measurements.get("receivedStashes")).iterator();

		header += ",receivedStashBlocks[#blocks]";
		iterators[7] = Arrays.stream(measurements.get("receivedStashBlocks")).iterator();

		header += ",receivedPathSize[#buckets]";
		iterators[8] = Arrays.stream(measurements.get("receivedPathSize")).iterator();

		header += ",eviction[ns]";
		iterators[9] = Arrays.stream(measurements.get("eviction")).iterator();

		header += ",sentStashBlocks[#blocks]";
		iterators[10] = Arrays.stream(measurements.get("sentStashBlocks")).iterator();

		header += ",sentPathSize[#buckets]";
		iterators[11] = Arrays.stream(measurements.get("sentPathSize")).iterator();

		header += ",evict[ns]";
		iterators[12] = Arrays.stream(measurements.get("evict")).iterator();

		header += ",serviceCall[ns]";
		iterators[13] = Arrays.stream(measurements.get("serviceCall")).iterator();

		saveGlobalMeasurements(fileName, header, iterators);
	}

	private void saveServerMeasurements(Map<String, long[]> measurements) {
		String fileName = storageFileNamePrefix + "server_global.csv";
		String header = "";
		PrimitiveIterator.OfLong[] iterators = new PrimitiveIterator.OfLong[measurements.size()];

		header += "clients[#]";
		iterators[0] = Arrays.stream(measurements.get("clients")).iterator();

		header += ",delta[ns]";
		iterators[1] = Arrays.stream(measurements.get("delta")).iterator();

		header += ",getPMRequests[#]";
		iterators[2] = Arrays.stream(measurements.get("getPMRequests")).iterator();

		header += ",getPMAvgLatency[ns]";
		iterators[3] = Arrays.stream(measurements.get("getPMAvgLatency")).iterator();

		header += ",getPMBandwidth[Bytes/s]";
		iterators[4] = Arrays.stream(measurements.get("getPMBandwidth")).iterator();

		header += ",getPSRequests[#]";
		iterators[5] = Arrays.stream(measurements.get("getPSRequests")).iterator();

		header += ",getPSAvgLatency[ns]";
		iterators[6] = Arrays.stream(measurements.get("getPSAvgLatency")).iterator();

		header += ",getPSBandwidth[Bytes/s]";
		iterators[7] = Arrays.stream(measurements.get("getPSBandwidth")).iterator();

		header += ",evictionRequests[#]";
		iterators[8] = Arrays.stream(measurements.get("evictionRequests")).iterator();

		header += ",evictionAvgLatency[ns]";
		iterators[9] = Arrays.stream(measurements.get("evictionAvgLatency")).iterator();

		header += ",evictionBandwidth[Bytes/s]";
		iterators[10] = Arrays.stream(measurements.get("evictionBandwidth")).iterator();

		header += ",outstanding[#]";
		iterators[11] = Arrays.stream(measurements.get("outstanding")).iterator();

		saveGlobalMeasurements(fileName, header, iterators);
	}

	private void saveGlobalMeasurements(String fileName, String header, PrimitiveIterator.OfLong[] dataIterators) {
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(
				Files.newOutputStream(Paths.get(fileName))))) {
			resultFile.write(header + "\n");
			int nEmptyIterators = 0;
			while(nEmptyIterators != dataIterators.length) {
				nEmptyIterators = 0;
				StringBuilder sb = new StringBuilder();
				for (PrimitiveIterator.OfLong iterator : dataIterators) {
					if (iterator.hasNext()) {
						sb.append(iterator.next());
						sb.append(",");
					} else {
						sb.append(",");
						nEmptyIterators++;
					}
				}
				if (nEmptyIterators != dataIterators.length) {
					sb.deleteCharAt(sb.length() - 1);
					resultFile.write(sb + "\n");
				}
			}

			resultFile.flush();
		} catch (IOException e) {
			logger.error("Failed to save client measurements", e);
		}
	}

	private String loadHosts(String hostFile) {
		try (BufferedReader in = new BufferedReader(new FileReader(hostFile))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = in.readLine()) != null) {
				sb.append(line);
				sb.append("\n");
			}
			sb.deleteCharAt(sb.length() - 1);
			return sb.toString();
		} catch (IOException e) {
			logger.error("Failed to load hosts file", e);
			return null;
		}
	}
}