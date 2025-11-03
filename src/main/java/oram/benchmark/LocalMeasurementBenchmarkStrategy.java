package oram.benchmark;

import controller.IBenchmarkStrategy;
import controller.IWorkerStatusListener;
import controller.WorkerHandler;
import generic.DefaultMeasurements;
import generic.ResourcesMeasurements;
import oram.utils.ORAMUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.Storage;
import worker.IProcessingResult;
import worker.ProcessInformation;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LocalMeasurementBenchmarkStrategy implements IBenchmarkStrategy, IWorkerStatusListener {
	private final Logger logger = LoggerFactory.getLogger("benchmarking");
	private final Lock lock;
	private final Condition sleepCondition;
	private final Set<Integer> serverWorkersIds;
	private final Set<Integer> clientWorkersIds;
	private final String initialCommand;
	private String serverCommand;
	private String clientCommand;
	private final String loadClientCommand;
	private final String sarCommand;
	private int treeHeight;
	private int bucketSize;
	private final int dataBinSize;
	private int blockSize;
	private double zipfParameter;
	private WorkerHandler[] clientWorkers;
	private WorkerHandler[] serverWorkers;
	private final Map<Integer,WorkerHandler> measurementWorkers;
	private CountDownLatch workersReadyCounter;
	private CountDownLatch measurementDeliveredCounter;
	private String storageFileNamePrefix;
	private String stashFileNamePrefix;
	private final Semaphore loadORAMSemaphore;
	private int round;
	private String resultsPath;
	private int measurementDuration;
	private int faultThreshold;
	private String singleServerIp;
	private int singleServerPort;

	//Storing data for plotting
	private double[] latencyValues;
	private double[] latencyStdValues;
	private double[] throughputValues;
	private double[] throughputStdValues;

	public LocalMeasurementBenchmarkStrategy() {
		this.lock = new ReentrantLock(true);
		this.loadORAMSemaphore = new Semaphore(0);
		this.sleepCondition = lock.newCondition();
		this.serverWorkersIds = new HashSet<>();
		this.clientWorkersIds = new HashSet<>();
		this.measurementWorkers = new HashMap<>();
		initialCommand = "java -Xmx60g -Djava.security.properties=./config/java" +
				".security -Dlogback.configurationFile=./config/logback.xml -cp lib/* ";

		this.loadClientCommand = initialCommand + "oram.testers.LoadORAM ";
		this.sarCommand = "sar -u -r -n DEV 1";
		this.dataBinSize = 5;
	}

	@Override
	public void executeBenchmark(WorkerHandler[] workers, Properties benchmarkParameters) {
		faultThreshold = Integer.parseInt(benchmarkParameters.getProperty("experiment.f"));
		String[] tokens = benchmarkParameters.getProperty("experiment.clients_per_round").split(" ");
		boolean measureResources = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.measure_resources"));
		measurementDuration = Integer.parseInt(benchmarkParameters.getProperty("experiment.measurement_duration"));
		boolean isLoadORAM = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.load_oram"));
		String zipfParameterStr = benchmarkParameters.getProperty("experiment.zipf_parameter");
		zipfParameter = Double.parseDouble(zipfParameterStr);
		int nLoadClients = Integer.parseInt(benchmarkParameters.getProperty("experiment.load_clients"));
		resultsPath = benchmarkParameters.getProperty("experiment.results.path");

		if (faultThreshold == 0) {
			serverCommand = initialCommand + "oram.single.server.ORAMSingleServer ";
			clientCommand = initialCommand + "oram.benchmark.SingleServerBenchmarkClient ";
			singleServerIp = benchmarkParameters.getProperty("experiment.server.ip");
			singleServerPort = Integer.parseInt(benchmarkParameters.getProperty("experiment.server.port"));
		} else {
			serverCommand = initialCommand + "oram.server.ORAMServer ";
			clientCommand = initialCommand + "oram.benchmark.MultiServerBenchmarkClient ";
		}

		File resultsDir = new File(resultsPath, "data");
		if (!resultsDir.exists()) {
			boolean isCreated = resultsDir.mkdirs();
			if (!isCreated) {
				logger.error("Could not create results directory: {}", resultsDir);
				return;
			}
		}

		int nServerWorkers = 3 * faultThreshold + 1;
		int nClientWorkers = workers.length - nServerWorkers;//-1 (for update client)
		int maxClientsPerProcess = 3;
		int nRequests = 2_000_000_000;
		int sleepBetweenRounds = 30;
		int[] clientsPerRound = new int[tokens.length];
		for (int i = 0; i < tokens.length; i++) {
			clientsPerRound[i] = Integer.parseInt(tokens[i]);
		}

		treeHeight = Integer.parseInt(benchmarkParameters.getProperty("experiment.tree_height"));
		bucketSize = Integer.parseInt(benchmarkParameters.getProperty("experiment.bucket_size"));
		blockSize = Integer.parseInt(benchmarkParameters.getProperty("experiment.block_size"));
		int nConcurrentClients = Integer.parseInt(benchmarkParameters.getProperty("experiment.concurrent_clients"));

		//Separate workers
		serverWorkers = new WorkerHandler[nServerWorkers];
		clientWorkers = new WorkerHandler[nClientWorkers];
		System.arraycopy(workers, 0, serverWorkers, 0, nServerWorkers);
		System.arraycopy(workers, nServerWorkers, clientWorkers, 0, nClientWorkers);
		//Sort client workers to use the same worker as measurement client
		Arrays.sort(clientWorkers, (o1, o2) -> -Integer.compare(o1.getWorkerId(), o2.getWorkerId()));
		Arrays.stream(serverWorkers).forEach(w -> serverWorkersIds.add(w.getWorkerId()));
		Arrays.stream(clientWorkers).forEach(w -> clientWorkersIds.add(w.getWorkerId()));

		//WorkerHandler updateClientWorker = workers[workers.length - 1];

		printWorkersInfo();

		//Setup workers
		String hosts = generateLocalHosts(nServerWorkers);
		logger.info("Setting up workers...");
		String setupInformation = String.format("%b\t%d\t%s", true, faultThreshold, hosts);
		Arrays.stream(workers).forEach(w -> w.setupWorker(setupInformation));

		long startTime = System.currentTimeMillis();
		logger.info("============ Strategy Parameters ============");
		logger.info("Tree height: {}", treeHeight);
		logger.info("Bucket size: {}", bucketSize);
		logger.info("Block size: {}", blockSize);
		logger.info("Slots: {}", ORAMUtils.computeNumberOfSlots(treeHeight, bucketSize));
		logger.info("ORAM size: {} blocks", ORAMUtils.computeNumberOfNodes(treeHeight));
		logger.info("Database size: {} bytes", ORAMUtils.computeDatabaseSize(treeHeight, blockSize));
		logger.info("Path length: {} slots", ORAMUtils.computePathLength(treeHeight, bucketSize));
		logger.info("Path size: {} bytes", ORAMUtils.computePathSize(treeHeight, bucketSize, blockSize));
		logger.info("Concurrent clients: {} clients", nConcurrentClients);
		logger.info("Zipf parameter: {}", zipfParameter);

		int nRounds = clientsPerRound.length;
		latencyValues = new double[nRounds];
		latencyStdValues = new double[nRounds];
		throughputValues = new double[nRounds];
		throughputStdValues = new double[nRounds];

		round = 1;
		while (true) {
			try {
				lock.lock();
				logger.info("============ Round: {} ============", round);
				int nClients = clientsPerRound[round - 1];
				measurementWorkers.clear();
				storageFileNamePrefix = String.format("f_%d_height_%d_bucket_%d_block_%d_zipf_%s_clients_%d_", faultThreshold,
						treeHeight, bucketSize, blockSize, zipfParameterStr, nClients);
				stashFileNamePrefix = String.format("height_%d_bucket_%d_zipf_%s_clients_%d_", treeHeight, bucketSize,
						zipfParameterStr, nClients);
				//Distribute clients per workers
				int[] clientsPerWorker = distributeClientsPerWorkers(nClientWorkers, nClients);
				String vector = Arrays.toString(clientsPerWorker);
				int total = Arrays.stream(clientsPerWorker).sum();
				logger.info("Clients per worker: {} -> Total: {}", vector, total);

				//Start servers
				startServers(serverWorkers, nConcurrentClients);

				//Load oram
				if (isLoadORAM) {
					long s = System.currentTimeMillis();
					loadORAM(nLoadClients, clientWorkers[0]);
					long e = System.currentTimeMillis();
					logger.info("Load duration: {}s", (e - s) / 1000);
					logger.info("Waiting 5s...");
					sleepSeconds(5);
				}

				//Start Clients
				startClients(maxClientsPerProcess, nRequests, clientWorkers, clientsPerWorker);

				//Start resource measurement
				if (measureResources) {
					int nServerResourceMeasurementWorkers = serverWorkers.length > 1 ? 2 : 1;
					int nClientResourceMeasurementWorkers = clientWorkers.length > 1 ? 2 : 1;
					nClientResourceMeasurementWorkers = Math.min(nClientResourceMeasurementWorkers,
							clientsPerWorker.length); // this is to account for 1 client
					startResourceMeasurements(nServerResourceMeasurementWorkers, nClientResourceMeasurementWorkers);
				}

				//Wait for system to stabilize
				logger.info("Waiting 15s...");
				sleepSeconds(15);

				//Get measurements
				getMeasurements(measureResources, measurementDuration);

				//Stop processes
				Arrays.stream(workers).forEach(WorkerHandler::stopWorker);

				if (round == nRounds) {
					break;
				}

				//Wait between round
				logger.info("Waiting {}s before new round", sleepBetweenRounds);
				sleepSeconds(sleepBetweenRounds);
				round++;
			} catch (InterruptedException e) {
				break;
			} finally {
				lock.unlock();
			}
		}
		storeProcessedResults(clientsPerRound, faultThreshold);

		long endTime = System.currentTimeMillis();
		logger.info("Execution duration: {}s", (endTime - startTime) / 1000);
	}

	private void storeProcessedResults(int[] clientsPerRound, int f) {
		String fileName = "f_" + f + "_throughput_latency_results.dat";
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(
				Files.newOutputStream(Paths.get(resultsPath, fileName))))) {
			resultFile.write("#clients throughput[ops/s] throughput_dev[ops/s] latency[ms] latency_dev[ms]\n");
			for (int i = 0; i < clientsPerRound.length; i++) {
				resultFile.write(String.format("%d %.3f %.3f %.3f %.3f\n", clientsPerRound[i],
						throughputValues[i], throughputStdValues[i],
						latencyValues[i], latencyStdValues[i]));
			}
		} catch (IOException e) {
			logger.error("Error while storing processed results", e);
		}
	}

	private void loadORAM(int nLoadClients, WorkerHandler worker) throws InterruptedException {
		logger.info("Loading ORAM...");
		String command = loadClientCommand + nLoadClients + " " + treeHeight + " " + bucketSize + " " + blockSize;
		ProcessInformation[] commandInfo = {
				new ProcessInformation(
						command,
						"."
				)
		};
		worker.startWorker(1, commandInfo, this);
		loadORAMSemaphore.acquire();
		worker.stopWorker();
	}

	private void startResourceMeasurements(int nServerResourceMeasurementWorkers,
										   int nClientResourceMeasurementWorkers) throws InterruptedException {
		WorkerHandler[] resourceMeasurementWorkers =
				new WorkerHandler[nServerResourceMeasurementWorkers + nClientResourceMeasurementWorkers];
		System.arraycopy(serverWorkers, 0, resourceMeasurementWorkers, 0, nServerResourceMeasurementWorkers);
		System.arraycopy(clientWorkers, 0, resourceMeasurementWorkers, nServerResourceMeasurementWorkers,
				nClientResourceMeasurementWorkers);

		logger.info("Starting resource measurements...");
		workersReadyCounter = new CountDownLatch(resourceMeasurementWorkers.length);
		for (WorkerHandler worker : resourceMeasurementWorkers) {
			measurementWorkers.put(worker.getWorkerId(), worker);
			ProcessInformation[] commands = {
					new ProcessInformation(sarCommand, ".")
			};
			worker.startWorker(0, commands, this);
		}
		workersReadyCounter.await();
	}

	private void printWorkersInfo() {
		StringBuilder sb = new StringBuilder();
		for (WorkerHandler serverWorker : serverWorkers) {
			sb.append(serverWorker.getWorkerId());
			sb.append(" ");
		}
		logger.info("Server workers[{}]: {}", serverWorkers.length, sb);

		sb = new StringBuilder();
		for (WorkerHandler clientWorker : clientWorkers) {
			sb.append(clientWorker.getWorkerId());
			sb.append(" ");
		}
		logger.info("Client workers[{}]: {}", clientWorkers.length, sb);
	}

	private void startServers(WorkerHandler[] serverWorkers, int nConcurrentClients) throws InterruptedException {
		logger.info("Starting servers...");
		workersReadyCounter = new CountDownLatch(serverWorkers.length);
		measurementWorkers.put(serverWorkers[0].getWorkerId(), serverWorkers[0]);

		for (int i = 0; i < serverWorkers.length; i++) {
			WorkerHandler serverWorker = serverWorkers[i];
			logger.debug("Using server worker {}", serverWorker.getWorkerId());
			String command;
			if (faultThreshold == 0) {
				command = serverCommand + nConcurrentClients + " " + singleServerIp + " " + singleServerPort + " " + i;
			} else {
				command = serverCommand + nConcurrentClients + " " + i;
			}
			ProcessInformation[] commandInfo = {
					new ProcessInformation(command, ".")
			};
			serverWorker.startWorker(0, commandInfo, this);
			sleepSeconds(2);
		}

		workersReadyCounter.await();
	}

	private void startClients(int maxClientsPerProcess, int nRequests, WorkerHandler[] clientWorkers,
							  int[] clientsPerWorker) throws InterruptedException {
		logger.info("Starting clients...");
		workersReadyCounter = new CountDownLatch(clientsPerWorker.length);
		int clientInitialId = 100000;
		measurementWorkers.put(clientWorkers[0].getWorkerId(), clientWorkers[0]);

		for (int i = 0; i < clientsPerWorker.length && i < clientWorkers.length; i++) {
			WorkerHandler clientWorker = clientWorkers[i];
			int totalClientsPerWorker = clientsPerWorker[i];
			int nProcesses = totalClientsPerWorker / maxClientsPerProcess
					+ (totalClientsPerWorker % maxClientsPerProcess == 0 ? 0 : 1);

			ProcessInformation[] commandInfo = new ProcessInformation[nProcesses];
			boolean isMeasurementWorker = i == 0; // First client is measurement client

			for (int j = 0; j < nProcesses; j++) {
				int clientsPerProcess = Math.min(totalClientsPerWorker, maxClientsPerProcess);
				String command;
				if (faultThreshold == 0) {
					command = clientCommand + clientInitialId + " " + clientsPerProcess
							+ " " + nRequests + " " + treeHeight + " " + bucketSize + " " + blockSize + " "
							+ zipfParameter + " " + singleServerIp + " " + singleServerPort + " " + isMeasurementWorker;
				} else {
					command = clientCommand + clientInitialId + " " + clientsPerProcess
							+ " " + nRequests + " " + treeHeight + " "
							+ bucketSize + " " + blockSize + " " + zipfParameter + " " + isMeasurementWorker;
				}
				commandInfo[j] = new ProcessInformation(command, ".");
				totalClientsPerWorker -= clientsPerProcess;
				clientInitialId += clientsPerProcess;
			}

			clientWorker.startWorker(50, commandInfo, this);
		}

		workersReadyCounter.await();
	}

	private void getMeasurements(boolean measureResources, int measurementDuration) throws InterruptedException {
		//Start measurements
		logger.debug("Starting measurements...");
		measurementWorkers.values().forEach(WorkerHandler::startProcessing);

		//Wait for measurements
		logger.info("Measuring during {}s", measurementDuration);
		sleepSeconds(measurementDuration);

		//Stop measurements
		measurementWorkers.values().forEach(WorkerHandler::stopProcessing);

		//Get measurement results
		int nMeasurements;
		if (measureResources) {
			//servers: 2 + 1
			//clients: 2 + 1
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
		if (nClients == 1 || nWorkers == 1) {
			return new int[]{nClients};
		}

		nClients--;//remove measurement client
		nWorkers--; //Subtract the measurement client

		if (nClients <= nWorkers) {
			int[] distribution = new int[1 + nClients];
			Arrays.fill(distribution, 1);
			return distribution;
		}

		int[] distribution = new int[1 + nWorkers];
		int nClientsPerWorker = nClients / nWorkers;

		Arrays.fill(distribution, nClientsPerWorker);
		distribution[0] = 1;//Measurement client

		int remainingClients = nClients % nWorkers;
		for (int i = 1; i <= remainingClients; i++) {
			distribution[i]++;
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
		logger.debug("Worker {} is ready", workerId);
		workersReadyCounter.countDown();
	}

	@Override
	public void onEnded(int workerId) {
		loadORAMSemaphore.release();
	}

	@Override
	public void onError(int workerId, String errorMessage) {
		if (serverWorkersIds.contains(workerId)) {
			if (!errorMessage.contains("Impossible to connect to client")) {
				logger.error("Error in server worker {}: {}", workerId, errorMessage);
			}
		} else if (clientWorkersIds.contains(workerId)) {
			if (!errorMessage.contains("Replica disconnected. Connection reset by peer.")) {
				logger.error("Error in client worker {}: {}", workerId, errorMessage);
			}
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
			logger.debug("Received leader server performance results from worker {}", workerId);
			DefaultMeasurements serverMeasurements = (DefaultMeasurements) processingResult;
			processServerMeasurement(serverMeasurements);
			measurementDeliveredCounter.countDown();
		} else if (processingResult instanceof DefaultMeasurements && workerId == clientWorkers[0].getWorkerId()) {
			logger.debug("Received measurement client performance results from worker {}", workerId);
			DefaultMeasurements clientMeasurements = (DefaultMeasurements) processingResult;
			processClientMeasurement(clientMeasurements);
			measurementDeliveredCounter.countDown();
		} else if (processingResult instanceof ResourcesMeasurements) {
			String tag = null;
			if (workerId == serverWorkers[0].getWorkerId()) {
				logger.debug("Received leader server resources usage results from worker {}", workerId);
				tag = "leader_server";
			} else if (serverWorkers.length > 1 && workerId == serverWorkers[1].getWorkerId()) {
				logger.debug("Received follower server resources usage results from worker {}", workerId);
				tag = "follower_server";
			} else if (workerId == clientWorkers[0].getWorkerId()) {
				logger.debug("Received measurement client resources usage results from worker {}", workerId);
				tag = "measurement_client";
			} else if (clientWorkers.length > 1 && workerId == clientWorkers[1].getWorkerId()) {
				logger.debug("Received load client resources usage results from worker {}", workerId);
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
				Files.newOutputStream(Paths.get(resultsPath, fileName))))) {
			OptionalInt min = Arrays.stream(data).mapToInt(v -> v.length).min();
			if (!min.isPresent() || min.getAsInt() == 0) {
				throw new IllegalStateException("Resources measurements are empty");
			}
			int size = min.getAsInt();
			int i = 0;
			while (i < size) {
				StringBuilder sb = new StringBuilder();
				for (long[] datum : data) {
					if (i < datum.length) {
						sb.append(String.format("%.2f", datum[i] / 100.0));
					}
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


	private void processClientMeasurement(DefaultMeasurements clientMeasurements) {
		saveClientMeasurements(clientMeasurements.getMeasurements());

		long[] globalLatencies = clientMeasurements.getMeasurements("global");
		long[] pmLatencies = clientMeasurements.getMeasurements("map");
		long[] psLatencies = clientMeasurements.getMeasurements("ps");
		long[] evictionLatencies = clientMeasurements.getMeasurements("eviction");
		long[] receivedPM = clientMeasurements.getMeasurements("receivedPM");
		long[] receivedPMLocations = clientMeasurements.getMeasurements("receivedPMLocations");
		long[] receivedStashes = clientMeasurements.getMeasurements("receivedStashes");
		long[] receivedStashBlocks = clientMeasurements.getMeasurements("receivedStashBlocks");
		long[] receivedPathSize = clientMeasurements.getMeasurements("receivedPathSize");
		long[] sentPMLocations = clientMeasurements.getMeasurements("sentPMLocations");
		long[] sentStashBlocks = clientMeasurements.getMeasurements("sentStashBlocks");
		long[] sentPathSize = clientMeasurements.getMeasurements("sentPathSize");

		binAndStore(sentStashBlocks, measurementDuration);

		Storage st = new Storage(globalLatencies);
		Storage getPMLatenciesStorage = new Storage(pmLatencies);
		Storage getPSLatenciesStorage = new Storage(psLatencies);
		Storage evictionLatenciesStorage = new Storage(evictionLatencies);
		Storage receivedPMStorage = new Storage(receivedPM);
		Storage receivedPMLocationsStorage = new Storage(receivedPMLocations);
		Storage receivedStashesStorage = new Storage(receivedStashes);
		Storage receivedStashBlocksStorage = new Storage(receivedStashBlocks);
		Storage receivedPathSizeStorage = new Storage(receivedPathSize);
		Storage sentPMLocationsStorage = new Storage(sentPMLocations);
		Storage sentStashBlocksStorage = new Storage(sentStashBlocks);
		Storage sentPathSizeStorage = new Storage(sentPathSize);

		latencyValues[round - 1] = st.getAverage(true) / 1_000_000.0;
		latencyStdValues[round - 1] = st.getDP(true) / 1_000_000.0;

		String sb = String.format("Client-side measurements [%d samples]:\n", globalLatencies.length) +
				String.format("\tAccess latency[ms]: avg:%.3f dev:%.3f max: %d\n",
						st.getAverage(true) / 1_000_000.0, st.getDP(true) / 1_000_000.0,
						st.getMax(true) / 1_000_000) +
				String.format("\tGet PM latency[ms]: avg:%.3f\n",
						getPMLatenciesStorage.getAverage(true) / 1_000_000.0) +
				String.format("\tGet PS latency[ms]: avg:%.3f\n",
						getPSLatenciesStorage.getAverage(true) / 1_000_000.0) +
				String.format("\tEviction latency[ms]: avg:%.3f\n",
						evictionLatenciesStorage.getAverage(true) / 1_000_000.0) +
				String.format("\tReceived PMs: avg:%.3f std:%.3f max:%d\n",
						receivedPMStorage.getAverage(true), receivedPMStorage.getDP(true),
						receivedPMStorage.getMax(true)) +
				String.format("\tReceived PM locations: avg:%.3f std:%.3f max:%d\n",
						receivedPMLocationsStorage.getAverage(true), receivedPMLocationsStorage.getDP(true),
						receivedPMLocationsStorage.getMax(true)) +
				String.format("\tReceived stashes: avg:%.3f std:%.3f max:%d\n",
						receivedStashesStorage.getAverage(true), receivedStashesStorage.getDP(true),
						receivedStashesStorage.getMax(true)) +
				String.format("\tReceived stash blocks: avg:%.3f std:%.3f max:%d\n",
						receivedStashBlocksStorage.getAverage(true), receivedStashBlocksStorage.getDP(true),
						receivedStashBlocksStorage.getMax(true)) +
				String.format("\tReceived path size: avg:%.3f std:%.3f max:%d\n",
						receivedPathSizeStorage.getAverage(true), receivedPathSizeStorage.getDP(true),
						receivedPathSizeStorage.getMax(true)) +
				String.format("\tSent PM locations: avg:%.3f std:%.3f max:%d\n",
						sentPMLocationsStorage.getAverage(true), sentPMLocationsStorage.getDP(true),
						sentPMLocationsStorage.getMax(true)) +
				String.format("\tSent stash blocks: avg:%.3f std:%.3f max:%d\n",
						sentStashBlocksStorage.getAverage(true), sentStashBlocksStorage.getDP(true),
						sentStashBlocksStorage.getMax(true)) +
				String.format("\tSent path size: avg:%.3f std:%.3f max:%d",
						sentPathSizeStorage.getAverage(true), sentPathSizeStorage.getDP(true),
						sentPathSizeStorage.getMax(true));
		logger.info(sb);
	}

	private void processServerMeasurement(DefaultMeasurements serverMeasurements) {
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

		int size = Math.min(clients.length, Math.min(delta.length, Math.min(nGetPMRequests.length,
				Math.min(nGetPSRequests.length, Math.min(nEvictionRequests.length, Math.min(getPMLatencies.length,
						Math.min(getPSLatencies.length, Math.min(evictionLatencies.length, outstanding.length))))))));
		long[] getPMThroughput = new long[size];
		long[] getPSThroughput = new long[size];
		long[] evictionThroughput = new long[size];
		long minClients = Long.MAX_VALUE;
		long maxClients = Long.MIN_VALUE;
		long minOutstanding = Long.MAX_VALUE;
		long maxOutstanding = Long.MIN_VALUE;
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

		throughputValues[round - 1] = evictionThroughputStorage.getAverage(true);
		throughputStdValues[round - 1] = evictionThroughputStorage.getDP(true);

		String sb = String.format("Server-side measurements [%d samples]:\n", evictionThroughput.length);
		sb += String.format("\tClients[#]: min:%d max:%d\n", minClients, maxClients);
		sb += String.format("\tOutstanding trees[#]: min:%d max:%d\n", minOutstanding, maxOutstanding);
		sb += String.format("\tGet PM[ops/s]: avg:%.3f dev:%.3f max: %d\n",
				getPMThroughputStorage.getAverage(true), getPMThroughputStorage.getDP(true),
				getPMThroughputStorage.getMax(true));
		sb += String.format("\tGet PS[ops/s]: avg:%.3f dev:%.3f max: %d\n",
				getPSThroughputStorage.getAverage(true), getPSThroughputStorage.getDP(true),
				getPSThroughputStorage.getMax(true));
		sb += String.format("\tEviction[ops/s]: avg:%.3f dev:%.3f max: %d\n",
				evictionThroughputStorage.getAverage(true), evictionThroughputStorage.getDP(true),
				evictionThroughputStorage.getMax(true));
		sb += String.format("\tGet PM latency[ms]: avg:%.3f\n",
				getPMLatenciesStorage.getAverage(true) / 1_000_000.0);
		sb += String.format("\tGet PS latency[ms]: avg:%.3f\n",
				getPSLatenciesStorage.getAverage(true) / 1_000_000.0);
		sb += String.format("\tEviction latency[ms]: avg:%.3f",
				evictionLatenciesStorage.getAverage(true) / 1_000_000.0);

		logger.info(sb);
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

		header += ",receivedPMLocations[#locations]";
		iterators[6] = Arrays.stream(measurements.get("receivedPMLocations")).iterator();

		header += ",receivedStashes[#stashes]";
		iterators[7] = Arrays.stream(measurements.get("receivedStashes")).iterator();

		header += ",receivedStashBlocks[#blocks]";
		iterators[8] = Arrays.stream(measurements.get("receivedStashBlocks")).iterator();

		header += ",receivedPathSize[#buckets]";
		iterators[9] = Arrays.stream(measurements.get("receivedPathSize")).iterator();

		header += ",eviction[ns]";
		iterators[10] = Arrays.stream(measurements.get("eviction")).iterator();

		header += ",sentPMLocations[#locations]";
		iterators[11] = Arrays.stream(measurements.get("sentPMLocations")).iterator();

		header += ",sentStashBlocks[#blocks]";
		iterators[12] = Arrays.stream(measurements.get("sentStashBlocks")).iterator();

		header += ",sentPathSize[#buckets]";
		iterators[13] = Arrays.stream(measurements.get("sentPathSize")).iterator();

		header += ",evict[ns]";
		iterators[14] = Arrays.stream(measurements.get("evict")).iterator();

		header += ",serviceCall[ns]";
		iterators[15] = Arrays.stream(measurements.get("serviceCall")).iterator();

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
				Files.newOutputStream(Paths.get(resultsPath, fileName))))) {
			resultFile.write(header + "\n");
			boolean hasData = true;
			while(true) {
				StringBuilder sb = new StringBuilder();
				for (PrimitiveIterator.OfLong iterator : dataIterators) {
					if (iterator.hasNext()) {
						sb.append(iterator.next());
						sb.append(",");
					} else {
						hasData = false;
						break;
					}
				}
				if (!hasData) {
					break;
				}
				sb.deleteCharAt(sb.length() - 1);
				resultFile.write(sb + "\n");
			}

			resultFile.flush();
		} catch (IOException e) {
			logger.error("Failed to save client measurements", e);
		}
	}

	private String generateLocalHosts(int nServers) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < nServers; i++) {
			sb.append(i).append(" ").append("127.0.0.1").append(" ").append(11000 + i * 10).append(" ")
					.append(11001 + i * 10).append("\n");
		}
		sb.append("7001 127.0.0.1 11100");
		return sb.toString();
	}

	private void binAndStore(long[] data, int measurementTime) {
		int samplesPerBin = dataBinSize * data.length / measurementTime;
		if (samplesPerBin == 0) {
			samplesPerBin = 1;
		}
		int nBins = data.length / samplesPerBin;
		double[] averages = new double[nBins];
		double[] stds = new double[nBins];
		int startIndex = 0;
		int endIndex = samplesPerBin;

		for (int index = 0; index < nBins; index++) {
			if (endIndex > data.length) {
				endIndex = data.length;
			}
			Storage st = new Storage(Arrays.copyOfRange(data, startIndex, endIndex));
			averages[index] = st.getAverage(false);
			stds[index] = st.getDP(false);
			startIndex = endIndex;
			endIndex = startIndex + samplesPerBin;
		}

		String fileName = stashFileNamePrefix + "stashes.dat";
		storeTimedData(fileName, averages, stds);
	}

	private void storeTimedData(String fileName, double[] averages, double[] stds) {
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(
				Files.newOutputStream(Paths.get(resultsPath, fileName))))) {
			resultFile.write("time average std\n");
			for (int i = 0; i < averages.length; i++) {
				resultFile.write(String.format("%d %.3f %.3f\n", i, averages[i], stds[i]));
			}
			resultFile.flush();
		} catch (IOException e) {
			logger.error("Error while storing timed data results", e);
		}
	}
}