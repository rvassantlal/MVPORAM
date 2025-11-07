package oram.benchmark.tvd;

import controller.IBenchmarkStrategy;
import controller.IWorkerStatusListener;
import controller.WorkerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import worker.IProcessingResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

public class TVDSigmaBenchmarkStrategy implements IBenchmarkStrategy, IWorkerStatusListener {
	private final Logger logger = LoggerFactory.getLogger("benchmarking");
	private File processedDataDir;

	@Override
	public void executeBenchmark(WorkerHandler[] workerHandlers, Properties benchmarkParameters) {
		int[] treeHeights = Arrays.stream(benchmarkParameters.getProperty("tree_heights").split(" ")).mapToInt(Integer::parseInt).toArray();
		String accessThresholdsTuples = benchmarkParameters.getProperty("access_thresholds");
		int[] clientsPerRound = Arrays.stream(benchmarkParameters.getProperty("clients_per_round").split(" ")).mapToInt(Integer::parseInt).toArray();
		int[] sigmas = Arrays.stream(benchmarkParameters.getProperty("sigmas").split(" ")).mapToInt(Integer::parseInt).toArray();
		String outputPath = benchmarkParameters.getProperty("output.path", ".");

		File outputDir = new File(outputPath, "output");
		processedDataDir = new File(outputDir, "processed_data");
		createFolderIfNotExist(processedDataDir);

		accessThresholdsTuples = accessThresholdsTuples.trim();
		accessThresholdsTuples = accessThresholdsTuples.substring(1, accessThresholdsTuples.length() - 1);
		accessThresholdsTuples = accessThresholdsTuples.replaceAll(" ", "");
		String[] accessThresholdsStrArray = accessThresholdsTuples.split("\\)\\(");
		int nAccessThresholds = accessThresholdsStrArray.length;
		String[] zipfParametersStr = new String[nAccessThresholds];
		String[] accessThresholdsStr = new String[nAccessThresholds];
		for (int i = 0; i < nAccessThresholds; i++) {
			String[] pair = accessThresholdsStrArray[i].split(",");
			zipfParametersStr[i] = pair[0];
			accessThresholdsStr[i] = pair[1];
		}

		TVDBenchmarkClient tvdClient = new TVDBenchmarkClient();

		int strategyParameterIndex = 1;
		int nStrategyParameters = treeHeights.length * nAccessThresholds;
		for (int treeHeight : treeHeights) {
			for (int i = 0; i < nAccessThresholds; i++) {
				String zipfParameterStr = zipfParametersStr[i];
				String thresholdStr = accessThresholdsStr[i];
				double zipfParameter = Double.parseDouble(zipfParameterStr);
				double threshold = Double.parseDouble(thresholdStr);

				logger.info("============ Strategy Parameters: {} out of {} ============",
						strategyParameterIndex, nStrategyParameters);
				strategyParameterIndex++;
				logger.info("Tree Height: {}", treeHeight);
				logger.info("Zipf Parameter: {}", zipfParameter);
				logger.info("Access Threshold: {}", threshold);
				logger.info("Clients: {}", Arrays.toString(clientsPerRound));
				logger.info("Sigmas: {}", Arrays.toString(sigmas));

				logger.info("Calculating statistical distance...");

				BigDecimal[][] tvd = tvdClient.calculateStrongMVPORAMTVD(clientsPerRound,
						treeHeight, zipfParameter, threshold, sigmas);

				String fileName = String.format("tvd_sigma_height_%d_zipf_%s_threshold_%s.dat", treeHeight, zipfParameterStr, thresholdStr);

				storeTVD(tvd, clientsPerRound, sigmas, fileName);
			}
		}
	}

	private void storeTVD(BigDecimal[][] tvd, int[] clientsPerRound, int[] sigmas, String filename) {
		Path path = Paths.get(processedDataDir.getPath(), filename);
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path)))) {
			// Write header
			resultFile.write("clients");
			for (int sigma : sigmas) {
				resultFile.write(String.format("\tsigma=%d", sigma));
			}
			resultFile.newLine();

			// Write data
			for (int i = 0; i < clientsPerRound.length; i++) {
				resultFile.write(String.valueOf(clientsPerRound[i]));
				for (int j = 0; j < sigmas.length; j++) {
					resultFile.write(String.format("\t%.20f", tvd[j][i]));
				}
				resultFile.newLine();
			}
			resultFile.flush();
		} catch (IOException e) {
			logger.error("Could not write TVD results to file: {}", path, e);
		}
	}

	private void createFolderIfNotExist(File dir) {
		if (!dir.exists()) {
			boolean isCreated = dir.mkdirs();
			if (!isCreated) {
				logger.error("Could not create results directory: {}", dir);
			}
		}
	}

	@Override
	public void onReady(int i) {

	}

	@Override
	public void onEnded(int i) {

	}

	@Override
	public void onError(int i, String s) {

	}

	@Override
	public void onResult(int i, IProcessingResult iProcessingResult) {

	}
}
