package oram.benchmark;

import bftsmart.benchmark.Measurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import worker.IProcessingResult;
import worker.IWorkerEventProcessor;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MeasurementEventProcessor implements IWorkerEventProcessor {
	private final Logger logger = LoggerFactory.getLogger("benchmark.oram");
	private static final String SERVER_READY_PATTERN = "Ready to process operations";
	private static final String CLIENT_READY_PATTERN = "Executing experiment";
	private static final String MEASUREMENT_PATTERN = "M:";
	private static final String GET_PM_MEASUREMENT_PATTERN = "MGetPMOP:";
	private static final String GET_PS_MEASUREMENT_PATTERN = "MGetPSOP:";
	private static final String EVICTION_MEASUREMENT_PATTERN = "MEvictionOP:";

	private static final String SAR_READY_PATTERN = "%";

	private boolean isReady;
	private boolean isServerWorker;
	private boolean doMeasurement;
	private final LinkedList<String> rawGlobalMeasurements;
	private final LinkedList<String> rawGetPMMeasurements;
	private final LinkedList<String> rawGetPSMeasurements;
	private final LinkedList<String> rawEvictionMeasurements;

	private final LinkedList<String[]> resourcesMeasurements;

	private final Pattern timePattern;

	public MeasurementEventProcessor() {
		this.rawGlobalMeasurements = new LinkedList<>();
		this.rawGetPMMeasurements = new LinkedList<>();
		this.rawGetPSMeasurements = new LinkedList<>();
		this.rawEvictionMeasurements = new LinkedList<>();
		this.resourcesMeasurements = new LinkedList<>();
		String pattern = "^\\d{2}:\\d{2}:\\d{2}";
		this.timePattern = Pattern.compile(pattern);
	}

	@Override
	public void process(String line) {
		logger.debug(line);
		if (!isReady) {
			if (line.contains(SERVER_READY_PATTERN)) {
				isReady = true;
				isServerWorker = true;
			} else if (line.contains(CLIENT_READY_PATTERN)) {
				isReady = true;
			} else if (line.contains(SAR_READY_PATTERN)) {
				isReady = true;
			}
		}
		if (doMeasurement) {
			Matcher matcher = timePattern.matcher(line);
			if (line.contains(MEASUREMENT_PATTERN)) {
				rawGlobalMeasurements.add(line);
			} else if (line.contains(GET_PM_MEASUREMENT_PATTERN)) {
				rawGetPMMeasurements.add(line);
			} else if (line.contains(GET_PS_MEASUREMENT_PATTERN)) {
				rawGetPSMeasurements.add(line);
			} else if (line.contains(EVICTION_MEASUREMENT_PATTERN)) {
				rawEvictionMeasurements.add(line);
			} else if (matcher.find() && !line.contains("%")) {
				String[] tokens = line.split("\\s+");
				resourcesMeasurements.add(tokens);
			}
		}
	}

	@Override
	public void startProcessing() {
		logger.debug("Measuring");
		rawGlobalMeasurements.clear();
		rawGetPMMeasurements.clear();
		rawGetPSMeasurements.clear();
		rawEvictionMeasurements.clear();
		doMeasurement = true;
	}

	@Override
	public void stopProcessing() {
		logger.debug("Not Measuring");
		doMeasurement = false;
	}

	@Override
	public IProcessingResult getProcessingResult() {
		if(!resourcesMeasurements.isEmpty())
			return processResourcesMeasurements();
		if (isServerWorker) {
			return processServerMeasurements();
		} else {
			return processClientMeasurements();
		}
	}

	private IProcessingResult processResourcesMeasurements() {
		LinkedList<Double> cpuMeasurements = new LinkedList<>();
		LinkedList<Long> memMeasurements = new LinkedList<>();
		Map<String, LinkedList<Double>> netReceivedMeasurements = new HashMap<>();
		Map<String, LinkedList<Double>> netTransmittedMeasurements = new HashMap<>();
		for (String[] tokens : resourcesMeasurements) {
			if (tokens.length <= 9) {
				double cpuUsage = Double.parseDouble(tokens[tokens.length - 4]) +
						Double.parseDouble(tokens[tokens.length - 6]);// %system + %usr
				cpuMeasurements.add(cpuUsage);
			} else if (tokens.length <= 11) {
				String netInterface = tokens[tokens.length - 9];
				if (!netTransmittedMeasurements.containsKey(netInterface)) {
					netReceivedMeasurements.put(netInterface, new LinkedList<>());
					netTransmittedMeasurements.put(netInterface, new LinkedList<>());
				}
				double netReceived = Double.parseDouble(tokens[tokens.length - 6]);// rxkB/s
				double netTransmitted = Double.parseDouble(tokens[tokens.length - 5]);// txkB/s
				netReceivedMeasurements.get(netInterface).add(netReceived);
				netTransmittedMeasurements.get(netInterface).add(netTransmitted);
			} else {
				long memUsage = Long.parseLong(tokens[tokens.length - 9]);// kbmemused
				memMeasurements.add(memUsage);
			}
		}

		long[][] data = new long[netReceivedMeasurements.size() * 2 + 2][];
		int i = 0;
		data[i++] = doubleToLongArray(cpuMeasurements);
		data[i++] = longToLongArray(memMeasurements);
		for (String netInterface : netReceivedMeasurements.keySet()) {
			data[i++] = doubleToLongArray(netReceivedMeasurements.get(netInterface));
			data[i++] = doubleToLongArray(netTransmittedMeasurements.get(netInterface));
		}

		return new Measurement(data);
	}

	private static long[] doubleToLongArray(LinkedList<Double> list) {
		long[] array = new long[list.size()];
		int i = 0;
		for (double measurement : list) {
			array[i++] = (long) (measurement * 100);
		}
		return array;
	}

	private static long[] longToLongArray(LinkedList<Long> list) {
		long[] array = new long[list.size()];
		int i = 0;
		for (long measurement : list) {
			array[i++] = measurement * 100;
		}
		return array;
	}
	private IProcessingResult processClientMeasurements() {
		long[] latencies = parseLatency(rawGlobalMeasurements);
		long[] getPMLatency = parseLatency(rawGetPMMeasurements);
		long[] getPSLatency = parseLatency(rawGetPSMeasurements);
		long[] evictionLatency = parseLatency(rawEvictionMeasurements);
		return new Measurement(latencies, getPMLatency, getPSLatency, evictionLatency);
	}

	private IProcessingResult processServerMeasurements() {
		long[] clients = new long[rawGlobalMeasurements.size()];
		long[] delta = new long[rawGlobalMeasurements.size()];
		long[] nGetPMRequests = new long[rawGlobalMeasurements.size()];
		long[] nGetPSRequests = new long[rawGlobalMeasurements.size()];
		long[] nEvictionRequests = new long[rawGlobalMeasurements.size()];
		long[] outstanding = new long[rawGlobalMeasurements.size()];
		long[] totalVersions = new long[rawGlobalMeasurements.size()];
		long[] getPMAvg = new long[rawGlobalMeasurements.size()];
		long[] getPSAvg = new long[rawGlobalMeasurements.size()];
		long[] evictionAvg = new long[rawGlobalMeasurements.size()];
		int i = 0;
		for (String rawMeasurement : rawGlobalMeasurements) {
			String token = rawMeasurement.split(">")[1];
			token = token.substring(1, token.length() - 1);
			String[] strValues = token.split("\\|");
			clients[i] = Long.parseLong(strValues[0]);
			delta[i] = Long.parseLong(strValues[1]);
			nGetPMRequests[i] = Long.parseLong(strValues[2]);
			nGetPSRequests[i] = Long.parseLong(strValues[3]);
			nEvictionRequests[i] = Long.parseLong(strValues[4]);
			outstanding[i] = Long.parseLong(strValues[5]);
			totalVersions[i] = Long.parseLong(strValues[6]);
			getPMAvg[i] = Long.parseLong(strValues[7]);
			getPSAvg[i] = Long.parseLong(strValues[8]);
			evictionAvg[i] = Long.parseLong(strValues[9]);
			i++;
		}

		return new Measurement(clients, delta, nGetPMRequests, nGetPSRequests, nEvictionRequests, getPMAvg,
				getPSAvg, evictionAvg, outstanding, totalVersions);
	}

	private long[] parseLatency(LinkedList<String> rawMeasurements) {
		long[] latency = new long[rawMeasurements.size()];
		int i = 0;
		for (String rawMeasurement : rawMeasurements) {
			String[] tokens = rawMeasurement.split(" ");
			latency[i++] = Long.parseLong(tokens[tokens.length - 1]);
		}
		return latency;
	}

	@Override
	public boolean isReady() {
		return isReady;
	}

	@Override
	public boolean ended() {
		return false;
	}
}
