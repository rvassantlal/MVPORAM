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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MeasurementBenchmarkStrategy implements IBenchmarkStrategy, IWorkerStatusListener {
	private final Logger logger = LoggerFactory.getLogger("benchmarking");
	private final Lock lock;
	private final Condition sleepCondition;
	private final Set<Integer> serverWorkersIds;
	private final Set<Integer> clientWorkersIds;
	private final String serverCommand;
	private final String clientCommand;
	private final String loadClientCommand;
	private final String updateClientCommand;
	private final String sarCommand;
	private int treeHeight;
	private int bucketSize;
	private int blockSize;
	private double zipfParameter;
	private WorkerHandler[] clientWorkers;
	private WorkerHandler[] serverWorkers;
	private final Map<Integer,WorkerHandler> measurementWorkers;
	private CountDownLatch workersReadyCounter;
	private CountDownLatch measurementDeliveredCounter;
	private String storageFileNamePrefix;
	private final Semaphore loadORAMSemaphore;


	public MeasurementBenchmarkStrategy() {
		this.lock = new ReentrantLock(true);
		this.loadORAMSemaphore = new Semaphore(0);
		this.sleepCondition = lock.newCondition();
		this.serverWorkersIds = new HashSet<>();
		this.clientWorkersIds = new HashSet<>();
		this.measurementWorkers = new HashMap<>();
		String initialCommand = "java -Xmx60g -Djava.security.properties=./config/java" +
				".security -Dlogback.configurationFile=./config/logback.xml -cp lib/* ";
		this.serverCommand = initialCommand + "oram.server.ORAMServer ";
		this.clientCommand = initialCommand + "oram.benchmark.ORAMBenchmarkClient ";
		this.loadClientCommand = initialCommand + "oram.testers.LoadORAM ";
		this.updateClientCommand = initialCommand + "oram.client.ManagerClient ";
		this.sarCommand = "sar -u -r -n DEV 1";
	}

	@Override
	public void executeBenchmark(WorkerHandler[] workers, Properties benchmarkParameters) {
		int f = Integer.parseInt(benchmarkParameters.getProperty("experiment.f"));
		String hostFile = benchmarkParameters.getProperty("experiment.hosts.file");
		String[] tokens = benchmarkParameters.getProperty("experiment.clients_per_round").split(" ");
		boolean measureResources = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.measure_resources"));
		int measurementDuration = Integer.parseInt(benchmarkParameters.getProperty("experiment.measurement_duration"));
		boolean isLoadORAM = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.load_oram"));
		//boolean isUpdateConcurrentClients = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.update_concurrent_clients"));
		//int updateInitialDelay = Integer.parseInt(benchmarkParameters.getProperty("experiment.initial_delay"));
		//int updatePeriod = Integer.parseInt(benchmarkParameters.getProperty("experiment.update_period"));
		//int numberOfUpdates = Integer.parseInt(benchmarkParameters.getProperty("experiment.number_of_updates"));
		zipfParameter = Double.parseDouble(benchmarkParameters.getProperty("experiment.zipf_parameter"));
		int nLoadClients = Integer.parseInt(benchmarkParameters.getProperty("experiment.load_clients"));

		int nServerWorkers = 3 * f + 1;
		int nClientWorkers = workers.length - nServerWorkers;//-1 (for update client)
		int maxClientsPerProcess = 3;
		int nRequests = 2_000_000_000;
		int sleepBetweenRounds = 30;
		int[] clientsPerRound = new int[tokens.length];
		for (int i = 0; i < tokens.length; i++) {
			clientsPerRound[i] = Integer.parseInt(tokens[i]);
		}

		String[] treeHeightTokens = benchmarkParameters.getProperty("experiment.tree_heights").split(" ");
		String[] bucketSizeTokens = benchmarkParameters.getProperty("experiment.bucket_sizes").split(" ");
		String[] blockSizeTokens = benchmarkParameters.getProperty("experiment.block_sizes").split(" ");
		String[] nConcurrentClientsTokens = benchmarkParameters.getProperty("experiment.concurrent_clients").split(" ");

		//Parse parameters
		int[] treeHeights = new int[treeHeightTokens.length];
		int[] bucketSizes = new int[bucketSizeTokens.length];
		int[] blockSizes = new int[blockSizeTokens.length];
		int[] nAllConcurrentClients = new int[nConcurrentClientsTokens.length];
		for (int i = 0; i < treeHeightTokens.length; i++) {
			treeHeights[i] = Integer.parseInt(treeHeightTokens[i]);
			bucketSizes[i] = Integer.parseInt(bucketSizeTokens[i]);
			blockSizes[i] = Integer.parseInt(blockSizeTokens[i]);
			nAllConcurrentClients[i] = Integer.parseInt(nConcurrentClientsTokens[i]);
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

		//WorkerHandler updateClientWorker = workers[workers.length - 1];

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

		long startTime = System.currentTimeMillis();
		for (int i = 0; i < treeHeights.length; i++) {
			logger.info("============ Strategy Parameters ============");
			treeHeight = treeHeights[i];
			bucketSize = bucketSizes[i];
			blockSize = blockSizes[i];
			int nConcurrentClients = nAllConcurrentClients[i];

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

			int round = 1;
			while (true) {
				try {
					lock.lock();
					logger.info("============ Round: {} ============", round);
					int nClients = clientsPerRound[round - 1];
					measurementWorkers.clear();
					storageFileNamePrefix = String.format("f_%d_height_%d_bucket_%d_block_%d_round_%d_", f,
							treeHeight, bucketSize, blockSize, nClients);
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

					//Start update client to update maximum number of concurrent clients
					/*if (isUpdateConcurrentClients) {
						startUpdateClients(updateInitialDelay, updatePeriod, numberOfUpdates, updateClientWorker);
					}*/

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

			if (i < treeHeights.length - 1) {
				logger.info("Waiting {}s before new setting", sleepBetweenRounds);
				try {
					sleepSeconds(sleepBetweenRounds);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}
		long endTime = System.currentTimeMillis();
		logger.info("Execution duration: {}s", (endTime - startTime) / 1000);
	}

	private void startUpdateClients(int updateInitialDelay, int updatePeriod, int numberOfUpdates, WorkerHandler worker) {
		logger.info("Starting client to update concurrent clients...");
		String command = updateClientCommand + updateInitialDelay + " " + updatePeriod + " " + numberOfUpdates;
		ProcessInformation[] commandInfo = {
				new ProcessInformation(
						command,
						"."
				)
		};
		worker.startWorker(1, commandInfo, this);
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
			String command = serverCommand + nConcurrentClients + " " + i;
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
				String command = clientCommand + clientInitialId + " " + clientsPerProcess
						+ " " + nRequests + " " + treeHeight + " "
						+ bucketSize + " " + blockSize + " " + zipfParameter + " " + isMeasurementWorker;
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
				Files.newOutputStream(Paths.get(fileName))))) {
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