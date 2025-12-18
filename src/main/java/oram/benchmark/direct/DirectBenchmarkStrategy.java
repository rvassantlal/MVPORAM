package oram.benchmark.direct;

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DirectBenchmarkStrategy implements IBenchmarkStrategy, IWorkerStatusListener {
	private final Logger logger = LoggerFactory.getLogger("benchmarking");
	private final Lock lock;
	private final Condition sleepCondition;
	private final String clientCommand;
	private final String sarCommand;
	private WorkerHandler worker;
	private CountDownLatch workersReadyCounter;
	private CountDownLatch measurementDeliveredCounter;
	private String storageFileNamePrefix;
	private final Semaphore loadORAMSemaphore;
	private File rawDataDir;

	public DirectBenchmarkStrategy() {
		this.lock = new ReentrantLock(true);
		this.loadORAMSemaphore = new Semaphore(0);
		this.sleepCondition = lock.newCondition();
		String initialCommand = "java -Xmx60g -Djava.security.properties=./config/java" +
				".security -Dlogback.configurationFile=./config/logback.xml -cp lib/* ";
		this.clientCommand = initialCommand + "oram.benchmark.direct.DirectBenchmarkClient ";
		this.sarCommand = "sar -u -r -n DEV 1";
	}

	@Override
	public void executeBenchmark(WorkerHandler[] workers, Properties benchmarkParameters) {
		long startTime = System.currentTimeMillis();

		int[] clientsPerRound = Arrays.stream(benchmarkParameters.getProperty("clients_per_round").split(" ")).mapToInt(Integer::parseInt).toArray();
		boolean measureResources = Boolean.parseBoolean(benchmarkParameters.getProperty("measure_resources"));
		int measurementDuration = Integer.parseInt(benchmarkParameters.getProperty("measurement_duration"));
		int[] treeHeights = Arrays.stream(benchmarkParameters.getProperty("tree_heights").split(" ")).mapToInt(Integer::parseInt).toArray();
		int[] bucketSizes = Arrays.stream(benchmarkParameters.getProperty("bucket_sizes").split(" ")).mapToInt(Integer::parseInt).toArray();
		int[] blockSizes = Arrays.stream(benchmarkParameters.getProperty("block_sizes").split(" ")).mapToInt(Integer::parseInt).toArray();
		String[] zipfParametersStr = benchmarkParameters.getProperty("zipf_parameters").split(" ");
		String outputPath = benchmarkParameters.getProperty("output.path", ".");

		File outputDir = new File(outputPath, "output");
		rawDataDir = new File(outputDir, "raw_data");
		createFolderIfNotExist(rawDataDir);

		int nRequests = 2_000_000_000;
		int sleepBetweenRounds = 30;
		worker = workers[0];

		int strategyParameterIndex = 1;
		int nStrategyParameters = treeHeights.length * bucketSizes.length * blockSizes.length * zipfParametersStr.length;

		try {
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
									strategyParameterIndex, nStrategyParameters);							logger.info("Tree height: {}", treeHeight);
							logger.info("Bucket size: {}", bucketSize);
							logger.info("Block size: {}", blockSize);
							logger.info("Zipf parameter: {}", zipfParameter);
							logger.info("Slots: {}", ORAMUtils.computeNumberOfSlots(treeHeight, bucketSize));
							logger.info("ORAM size: {} blocks", ORAMUtils.computeNumberOfNodes(treeHeight));
							logger.info("Database size: {} bytes", ORAMUtils.computeDatabaseSize(treeHeight, blockSize));
							logger.info("Path length: {} slots", ORAMUtils.computePathLength(treeHeight, bucketSize));
							logger.info("Path size: {} bytes", ORAMUtils.computePathSize(treeHeight, bucketSize, blockSize));

							int nRounds = clientsPerRound.length;

							int round = 1;
							while (true) {
								try {
									lock.lock();
									logger.info("============ Round: {} out of {}  ============", round, nRounds);									int nClients = clientsPerRound[round - 1];
									logger.info("Number of clients: {}", nClients);
									storageFileNamePrefix = String.format("f_%d_height_%d_bucket_%d_block_%d_zipf_%s_clients_%d_", 0,
											treeHeight, bucketSize, blockSize, zipfParameterStr, nClients);

									//Start experiment
									startExperiment(worker, nClients, nRequests, treeHeight, bucketSize, blockSize, zipfParameter);

									//Start resource measurement
									if (measureResources) {
										startResourceMeasurements();
									}

									//Wait for system to stabilize
									logger.info("Waiting 5s...");
									sleepSeconds(5);

									//Get measurements
									getMeasurements(measureResources, measurementDuration);

									//Stop processes
									Arrays.stream(workers).forEach(WorkerHandler::stopWorker);

									//Wait between round
									if (treeHeightIndex < treeHeights.length - 1 ||
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

	private void createFolderIfNotExist(File dir) {
		if (!dir.exists()) {
			boolean isCreated = dir.mkdirs();
			if (!isCreated) {
				logger.error("Could not create results directory: {}", dir);
			}
		}
	}

	private void startResourceMeasurements() throws InterruptedException {
		logger.info("Starting resource measurements...");
		workersReadyCounter = new CountDownLatch(1);
		ProcessInformation[] commands = {
				new ProcessInformation(sarCommand, ".")
		};
		worker.startWorker(0, commands, this);

		workersReadyCounter.await();
	}

	private void startExperiment(WorkerHandler worker, int nClients, int nRequests, int treeHeight, int bucketSize,
								 int blockSize, double zipfParameter) throws InterruptedException {
		logger.info("Starting experiment...");
		workersReadyCounter = new CountDownLatch(1);
		String command = clientCommand + nClients + " " + nRequests + " " + treeHeight + " "
				+ bucketSize + " " + blockSize + " " + zipfParameter;
		ProcessInformation[] commandInfo = {
				new ProcessInformation(command, ".")
		};
		worker.startWorker(50, commandInfo, this);

		workersReadyCounter.await();
	}

	private void getMeasurements(boolean measureResources, int measurementDuration) throws InterruptedException {
		//Start measurements
		logger.debug("Starting measurements...");
		worker.startProcessing();

		//Wait for measurements
		logger.info("Measuring during {}s", measurementDuration);
		sleepSeconds(measurementDuration);

		//Stop measurements
		worker.stopProcessing();

		//Get measurement results
		int nMeasurements;
		if (measureResources) {
			nMeasurements = 2;
		} else {
			nMeasurements = 1;
		}
		logger.debug("Getting {} measurements from the worker...", nMeasurements);
		measurementDeliveredCounter = new CountDownLatch(nMeasurements);

		worker.requestProcessingResult();

		measurementDeliveredCounter.await();
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
		if (workerId == worker.getWorkerId()) {
			logger.error("Error in worker: {}", errorMessage);
		} else {
			logger.error("Error in unused worker {}: {}", workerId, errorMessage);
		}
	}

	@Override
	public synchronized void onResult(int workerId, IProcessingResult processingResult) {
		if (workerId != worker.getWorkerId()) {
			logger.warn("Received measurements results from unused worker {}", workerId);
			return;
		}

		if (processingResult instanceof DefaultMeasurements && workerId == worker.getWorkerId()) {
			logger.debug("Received experiment measurement results from the worker");
			DefaultMeasurements measurements = (DefaultMeasurements) processingResult;
			processExperimentMeasurement(measurements);
			measurementDeliveredCounter.countDown();
		} else if (processingResult instanceof ResourcesMeasurements) {
			String tag = null;
			if (workerId == worker.getWorkerId()) {
				logger.debug("Received resources usage results from the worker");
				tag = "direct_benchmark";
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
		Path path = Paths.get(rawDataDir.toString(), fileName);
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path)))) {
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


	private void processExperimentMeasurement(DefaultMeasurements clientMeasurements) {
		saveExperimentMeasurements(clientMeasurements.getMeasurements());

		long[] receivedPM = clientMeasurements.getMeasurements("receivedPM");
		long[] receivedPMLocations = clientMeasurements.getMeasurements("receivedPMLocations");
		long[] receivedStashes = clientMeasurements.getMeasurements("receivedStashes");
		long[] receivedStashBlocks = clientMeasurements.getMeasurements("receivedStashBlocks");
		long[] receivedPathSize = clientMeasurements.getMeasurements("receivedPathSize");
		long[] sentPMLocations = clientMeasurements.getMeasurements("sentPMLocations");
		long[] sentStashBlocks = clientMeasurements.getMeasurements("sentStashBlocks");
		long[] sentPathSize = clientMeasurements.getMeasurements("sentPathSize");

		Storage receivedPMStorage = new Storage(receivedPM);
		Storage receivedPMLocationsStorage = new Storage(receivedPMLocations);
		Storage receivedStashesStorage = new Storage(receivedStashes);
		Storage receivedStashBlocksStorage = new Storage(receivedStashBlocks);
		Storage receivedPathSizeStorage = new Storage(receivedPathSize);
		Storage sentPMLocationsStorage = new Storage(sentPMLocations);
		Storage sentStashBlocksStorage = new Storage(sentStashBlocks);
		Storage sentPathSizeStorage = new Storage(sentPathSize);

		String sb = String.format("Direct benchmark measurements [%d samples]:\n", receivedPM.length) +
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

	private void saveExperimentMeasurements(Map<String, long[]> measurements) {
		String fileName = storageFileNamePrefix + "direct_benchmark_global.csv";
		String header = "";
		PrimitiveIterator.OfLong[] iterators = new PrimitiveIterator.OfLong[measurements.size()];

		header += "receivedPM[#pms]";
		iterators[0] = Arrays.stream(measurements.get("receivedPM")).iterator();

		header += ",receivedPMLocations[#locations]";
		iterators[1] = Arrays.stream(measurements.get("receivedPMLocations")).iterator();

		header += ",receivedStashes[#stashes]";
		iterators[2] = Arrays.stream(measurements.get("receivedStashes")).iterator();

		header += ",receivedStashBlocks[#blocks]";
		iterators[3] = Arrays.stream(measurements.get("receivedStashBlocks")).iterator();

		header += ",receivedPathSize[#buckets]";
		iterators[4] = Arrays.stream(measurements.get("receivedPathSize")).iterator();

		header += ",sentPMLocations[#locations]";
		iterators[5] = Arrays.stream(measurements.get("sentPMLocations")).iterator();

		header += ",sentStashBlocks[#blocks]";
		iterators[6] = Arrays.stream(measurements.get("sentStashBlocks")).iterator();

		header += ",sentPathSize[#buckets]";
		iterators[7] = Arrays.stream(measurements.get("sentPathSize")).iterator();

		saveGlobalMeasurements(fileName, header, iterators);
	}

	private void saveGlobalMeasurements(String fileName, String header, PrimitiveIterator.OfLong[] dataIterators) {
		Path path = Paths.get(rawDataDir.toString(), fileName);
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
}