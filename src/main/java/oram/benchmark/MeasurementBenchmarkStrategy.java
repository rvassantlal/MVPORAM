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
import java.nio.file.Path;
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
	private final String initialCommand;
	private String serverCommand;
	private String clientCommand;
	private final String loadClientCommand;
	private final String updateClientCommand;
	private final String sarCommand;
	private int dataBinSize;
	private WorkerHandler[] clientWorkers;
	private WorkerHandler[] serverWorkers;
	private final Map<Integer,WorkerHandler> measurementWorkers;
	private CountDownLatch workersReadyCounter;
	private CountDownLatch measurementDeliveredCounter;
	private String storageFileNamePrefix;
	private String performanceFileNamePrefix;
	private final Semaphore loadORAMSemaphore;
	private int round;
	private int measurementDuration;
	private File rawDataDir;
	private File processedDataDir;

	//Storing data for plotting
	private double[] latencyValues;
	private double[] latencyStdValues;
	private double[] throughputValues;
	private double[] throughputStdValues;

	public MeasurementBenchmarkStrategy() {
		this.lock = new ReentrantLock(true);
		this.loadORAMSemaphore = new Semaphore(0);
		this.sleepCondition = lock.newCondition();
		this.serverWorkersIds = new HashSet<>();
		this.clientWorkersIds = new HashSet<>();
		this.measurementWorkers = new HashMap<>();
		this.initialCommand = "java -Xmx8g -Djava.security.properties=./config/java" +
				".security -Dlogback.configurationFile=./config/logback.xml -cp lib/* ";

		this.loadClientCommand = initialCommand + "oram.testers.LoadORAM ";
		this.updateClientCommand = initialCommand + "oram.client.ManagerClient ";
		this.sarCommand = "sar -u -r -n DEV 1";
	}

	@Override
	public void executeBenchmark(WorkerHandler[] workers, Properties benchmarkParameters) {
		long startTime = System.currentTimeMillis();

		int[] faultThresholds = Arrays.stream(benchmarkParameters.getProperty("fault_thresholds").split(" ")).mapToInt(Integer::parseInt).toArray();
		String serverIpsInput = benchmarkParameters.getProperty("server_ips", "");
		int[] clientsPerRound = Arrays.stream(benchmarkParameters.getProperty("clients_per_round").split(" ")).mapToInt(Integer::parseInt).toArray();
		boolean measureResources = Boolean.parseBoolean(benchmarkParameters.getProperty("measure_resources"));
		measurementDuration = Integer.parseInt(benchmarkParameters.getProperty("measurement_duration"));
		boolean isLoadORAM = Boolean.parseBoolean(benchmarkParameters.getProperty("load_oram"));
		//boolean isUpdateConcurrentClients = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.update_concurrent_clients"));
		//int updateInitialDelay = Integer.parseInt(benchmarkParameters.getProperty("experiment.initial_delay"));
		//int updatePeriod = Integer.parseInt(benchmarkParameters.getProperty("experiment.update_period"));
		//int numberOfUpdates = Integer.parseInt(benchmarkParameters.getProperty("experiment.number_of_updates"));
		int[] treeHeights = Arrays.stream(benchmarkParameters.getProperty("tree_heights").split(" ")).mapToInt(Integer::parseInt).toArray();
		int[] bucketSizes = Arrays.stream(benchmarkParameters.getProperty("bucket_sizes").split(" ")).mapToInt(Integer::parseInt).toArray();
		int[] blockSizes = Arrays.stream(benchmarkParameters.getProperty("block_sizes").split(" ")).mapToInt(Integer::parseInt).toArray();
		int[] allNConcurrentClients = Arrays.stream(benchmarkParameters.getProperty("concurrent_clients").split(" ")).mapToInt(Integer::parseInt).toArray();
		String[] zipfParametersStr = benchmarkParameters.getProperty("zipf_parameters").split(" ");
		int nLoadClients = Integer.parseInt(benchmarkParameters.getProperty("load_clients"));
		String outputPath = benchmarkParameters.getProperty("output.path", ".");

		File outputDir = new File(outputPath, "output");
		rawDataDir = new File(outputDir, "raw_data");
		processedDataDir = new File(outputDir, "processed_data");
		createFolderIfNotExist(rawDataDir);
		createFolderIfNotExist(processedDataDir);

		this.dataBinSize = Math.max(1, (int) (60.0/3600.0 * measurementDuration));
		int maxClientsPerProcess = 3;
		int nRequests = 2_000_000_000;
		int sleepBetweenRounds = 30;

		int strategyParameterIndex = 1;
		int nStrategyParameters = faultThresholds.length * allNConcurrentClients.length *
				treeHeights.length * bucketSizes.length * blockSizes.length * zipfParametersStr.length;
		//WorkerHandler updateClientWorker = workers[workers.length - 1];
		try {
			for (int faultThresholdIndex = 0; faultThresholdIndex < faultThresholds.length; faultThresholdIndex++) {
				int faultThreshold = faultThresholds[faultThresholdIndex];
				if (faultThreshold == 0) {
					serverCommand = initialCommand + "oram.single.server.ORAMSingleServer ";
					clientCommand = initialCommand + "oram.benchmark.SingleServerBenchmarkClient ";
				} else {
					serverCommand = initialCommand + "oram.server.ORAMServer ";
					clientCommand = initialCommand + "oram.benchmark.MultiServerBenchmarkClient ";
				}

				int nServerWorkers = 3 * faultThreshold + 1;
				int nClientWorkers = workers.length - nServerWorkers;//-1 (for update client)

				//Separate workers
				serverWorkers = new WorkerHandler[nServerWorkers];
				clientWorkers = new WorkerHandler[nClientWorkers];
				System.arraycopy(workers, 0, serverWorkers, 0, nServerWorkers);
				System.arraycopy(workers, nServerWorkers, clientWorkers, 0, nClientWorkers);
				//Sort client workers to use the same worker as measurement client
				Arrays.sort(clientWorkers, (o1, o2) -> -Integer.compare(o1.getWorkerId(), o2.getWorkerId()));

				serverWorkersIds.clear();
				clientWorkersIds.clear();
				Arrays.stream(serverWorkers).forEach(w -> serverWorkersIds.add(w.getWorkerId()));
				Arrays.stream(clientWorkers).forEach(w -> clientWorkersIds.add(w.getWorkerId()));

				printWorkersInfo();
				String serverIps;

				//Setup workers
				if (serverIpsInput.isEmpty()) {
					serverIps = generateLocalhostIPs(nServerWorkers);
				} else {
					serverIps = selectServerIPs(serverIpsInput, nServerWorkers);
				}

				if (faultThreshold > 0) {
					logger.debug("Setting up workers...");
					String setupInformation = String.format("%b\t%d\t%s", true, faultThreshold, serverIps);
					Arrays.stream(workers).forEach(w -> w.setupWorker(setupInformation));
				}

				for (int nConcurrentClientsIndex = 0; nConcurrentClientsIndex < allNConcurrentClients.length; nConcurrentClientsIndex++) {
					int nConcurrentClients = allNConcurrentClients[nConcurrentClientsIndex];
					for (int treeHeightIndex = 0; treeHeightIndex < treeHeights.length; treeHeightIndex++) {
						int treeHeight = treeHeights[treeHeightIndex];
						for (int bucketSizeIndex = 0; bucketSizeIndex < bucketSizes.length; bucketSizeIndex++) {
							int bucketSize = bucketSizes[bucketSizeIndex];
							for (int blockSizeIndex = 0; blockSizeIndex < blockSizes.length; blockSizeIndex++) {
								int blockSize = blockSizes[blockSizeIndex];
								for (int zipfParameterStrIndex = 0; zipfParameterStrIndex < zipfParametersStr.length; zipfParameterStrIndex++) {
									String zipfParameterStr = zipfParametersStr[zipfParameterStrIndex];
									double zipfParameter = Double.parseDouble(zipfParameterStr);

									logger.info("============ Strategy Parameters: {} out of {} ============",
											strategyParameterIndex, nStrategyParameters);
									strategyParameterIndex++;
									logger.info("Servers IPs: {}", serverIps);
									logger.info("Tree height: {}", treeHeight);
									logger.info("Bucket size: {}", bucketSize);
									logger.info("Block size: {}", blockSize);
									logger.info("Slots: {}", ORAMUtils.computeNumberOfSlots(treeHeight, bucketSize));
									logger.info("ORAM size: {} blocks", ORAMUtils.computeNumberOfNodes(treeHeight));
									logger.info("Database size: {} bytes", ORAMUtils.computeDatabaseSize(treeHeight, blockSize));
									logger.info("Path length: {} slots", ORAMUtils.computePathLength(treeHeight, bucketSize));
									logger.info("Path size: {} bytes", ORAMUtils.computePathSize(treeHeight, bucketSize, blockSize));
									logger.info("Concurrent clients: {} clients", nConcurrentClients);
									logger.info("Zipf parameter: {}", zipfParameterStr);

									int nRounds = clientsPerRound.length;
									latencyValues = new double[nRounds];
									latencyStdValues = new double[nRounds];
									throughputValues = new double[nRounds];
									throughputStdValues = new double[nRounds];

									performanceFileNamePrefix = String.format("f_%d_height_%d_bucket_%d_block_%d_zipf_%s_c_max_%d_",
											faultThreshold, treeHeight, bucketSize, blockSize, zipfParameterStr,
											nConcurrentClients);

									round = 1;
									while (true) {
										try {
											lock.lock();
											logger.info("============ Round: {} out of {}  ============", round, nRounds);
											int nClients = clientsPerRound[round - 1];
											measurementWorkers.clear();
											storageFileNamePrefix = String.format("f_%d_height_%d_bucket_%d_block_%d_zipf_%s_c_max_%d_clients_%d_",
													faultThreshold, treeHeight, bucketSize, blockSize, zipfParameterStr,
													nConcurrentClients, nClients);

											//Distribute clients per workers
											int[] clientsPerWorker = distributeClientsPerWorkers(nClientWorkers, nClients);
											String vector = Arrays.toString(clientsPerWorker);
											int total = Arrays.stream(clientsPerWorker).sum();
											logger.info("Clients per worker: {} -> Total: {}", vector, total);

											//Start servers
											startServers(serverWorkers, serverIps, faultThreshold, nConcurrentClients);

											//Load oram
											if (isLoadORAM) {
												long s = System.currentTimeMillis();
												loadORAM(nLoadClients, clientWorkers[0], treeHeight, bucketSize, blockSize);
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
											startClients(maxClientsPerProcess, nRequests, clientWorkers, clientsPerWorker,
													serverIps, faultThreshold, treeHeight, bucketSize, blockSize, zipfParameter);

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

											//Wait between round
											if (faultThresholdIndex < faultThresholds.length - 1 ||
													nConcurrentClientsIndex < allNConcurrentClients.length - 1 ||
													treeHeightIndex < treeHeights.length - 1 ||
													bucketSizeIndex < bucketSizes.length - 1 ||
													blockSizeIndex < blockSizes.length - 1 ||
													zipfParameterStrIndex < zipfParametersStr.length - 1 ||
													round < nRounds) {
												logger.info("Waiting {}s before new round", sleepBetweenRounds);
												sleepSeconds(sleepBetweenRounds);
											}
											if (round == nRounds) {
												break;
											}
											round++;
										} finally {
											lock.unlock();
										}
									}

									storeProcessedResults(clientsPerRound);
								}
							}
						}
					}
				}
			}
		} catch (InterruptedException e) {
			logger.error("Benchmark interrupted", e);
		}
		long endTime = System.currentTimeMillis();
		logger.info("Execution duration: {}s", (endTime - startTime) / 1000);
	}

	private String selectServerIPs(String serverIps, int nServerWorkers) {
		StringBuilder sb = new StringBuilder();
		String[] ips = serverIps.split(" ");
		if (ips.length < nServerWorkers) {
			logger.warn("Not enough server IPs provided. Using localhost for remaining servers.");
		}
		for (int i = 0; i < nServerWorkers; i++) {
			if (i < ips.length) {
				sb.append(ips[i]).append(" ");
			} else {
				sb.append("127.0.0.1 ");
			}
		}
		return sb.toString().trim();
	}

	private String generateLocalhostIPs(int nServerWorkers) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < nServerWorkers; i++) {
			sb.append("127.0.0.1 ");
		}
		return sb.toString().trim();
	}

	private void createFolderIfNotExist(File dir) {
		if (!dir.exists()) {
			boolean isCreated = dir.mkdirs();
			if (!isCreated) {
				logger.error("Could not create results directory: {}", dir);
			}
		}
	}

	private void storeProcessedResults(int[] clientsPerRound) {
		String fileName = performanceFileNamePrefix + "throughput_latency_results.dat";
		Path path = Paths.get(processedDataDir.getPath(), fileName);
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path)))) {
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

	private void loadORAM(int nLoadClients, WorkerHandler worker, int treeHeight, int bucketSize, int blockSize) throws InterruptedException {
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

	private void startServers(WorkerHandler[] serverWorkers, String serverIp, int faultThreshold,
							  int nConcurrentClients) throws InterruptedException {
		logger.info("Starting servers...");
		workersReadyCounter = new CountDownLatch(serverWorkers.length);
		measurementWorkers.put(serverWorkers[0].getWorkerId(), serverWorkers[0]);

		for (int i = 0; i < serverWorkers.length; i++) {
			WorkerHandler serverWorker = serverWorkers[i];
			logger.debug("Using server worker {}", serverWorker.getWorkerId());
			String command;
			if (faultThreshold == 0) {
				command = serverCommand + nConcurrentClients + " " + serverIp + " 11000 " + i;
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
							  int[] clientsPerWorker, String serverIp, int faultThreshold, int treeHeight, int bucketSize,
							  int blockSize, double zipfParameter) throws InterruptedException {
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
							+ zipfParameter + " " + serverIp + " 11000 " + isMeasurementWorker;
				} else {
					command = clientCommand + clientInitialId + " " + clientsPerProcess
							+ " " + nRequests + " " + treeHeight + " " + bucketSize + " " + blockSize + " "
							+ zipfParameter + " " + isMeasurementWorker;
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
		if (errorMessage.contains("Impossible to connect to client")) {
			return;
		}
		if (errorMessage.contains("Replica disconnected. Connection reset by peer.")) {
			return;
		}
		if (errorMessage.contains("Connection reset by the client")) {
			return;
		}
		if (errorMessage.contains("Connection refused")) {
			return;
		}

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
		Path path = Paths.get(rawDataDir.getPath(), fileName);
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path)))) {
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
						receivedPMStorage.getAverage(false), receivedPMStorage.getDP(false),
						receivedPMStorage.getMax(false)) +
				String.format("\tReceived PM locations: avg:%.3f std:%.3f max:%d\n",
						receivedPMLocationsStorage.getAverage(false), receivedPMLocationsStorage.getDP(false),
						receivedPMLocationsStorage.getMax(false)) +
				String.format("\tReceived stashes: avg:%.3f std:%.3f max:%d\n",
						receivedStashesStorage.getAverage(false), receivedStashesStorage.getDP(false),
						receivedStashesStorage.getMax(false)) +
				String.format("\tReceived stash blocks: avg:%.3f std:%.3f max:%d\n",
						receivedStashBlocksStorage.getAverage(false), receivedStashBlocksStorage.getDP(false),
						receivedStashBlocksStorage.getMax(false)) +
				String.format("\tReceived path size: avg:%.3f std:%.3f max:%d\n",
						receivedPathSizeStorage.getAverage(false), receivedPathSizeStorage.getDP(false),
						receivedPathSizeStorage.getMax(false)) +
				String.format("\tSent PM locations: avg:%.3f std:%.3f max:%d\n",
						sentPMLocationsStorage.getAverage(false), sentPMLocationsStorage.getDP(false),
						sentPMLocationsStorage.getMax(false)) +
				String.format("\tSent stash blocks: avg:%.3f std:%.3f max:%d\n",
						sentStashBlocksStorage.getAverage(false), sentStashBlocksStorage.getDP(false),
						sentStashBlocksStorage.getMax(false)) +
				String.format("\tSent path size: avg:%.3f std:%.3f max:%d",
						sentPathSizeStorage.getAverage(false), sentPathSizeStorage.getDP(false),
						sentPathSizeStorage.getMax(false));
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

		String sb = String.format("Server-side measurements [%d samples]:\n", evictionThroughput.length) +
				String.format("\tClients[#]: min:%d max:%d\n", minClients, maxClients) +
				String.format("\tOutstanding trees[#]: min:%d max:%d\n", minOutstanding, maxOutstanding) +
				String.format("\tGet PM[ops/s]: avg:%.3f dev:%.3f max: %d\n",
						getPMThroughputStorage.getAverage(true), getPMThroughputStorage.getDP(true),
						getPMThroughputStorage.getMax(true)) +
				String.format("\tGet PS[ops/s]: avg:%.3f dev:%.3f max: %d\n",
						getPSThroughputStorage.getAverage(true), getPSThroughputStorage.getDP(true),
						getPSThroughputStorage.getMax(true)) +
				String.format("\tEviction[ops/s]: avg:%.3f dev:%.3f max: %d\n",
						evictionThroughputStorage.getAverage(true), evictionThroughputStorage.getDP(true),
						evictionThroughputStorage.getMax(true)) +
				String.format("\tGet PM latency[ms]: avg:%.3f\n",
						getPMLatenciesStorage.getAverage(true) / 1_000_000.0) +
				String.format("\tGet PS latency[ms]: avg:%.3f\n",
						getPSLatenciesStorage.getAverage(true) / 1_000_000.0) +
				String.format("\tEviction latency[ms]: avg:%.3f",
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
		Path path = Paths.get(rawDataDir.getPath(), fileName);
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path)))) {
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

		String fileName = storageFileNamePrefix + "stashes.dat";
		storeTimedData(fileName, averages, stds);
	}

	private void storeTimedData(String fileName, double[] averages, double[] stds) {
		Path path = Paths.get(processedDataDir.getPath(), fileName);
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path)))) {
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