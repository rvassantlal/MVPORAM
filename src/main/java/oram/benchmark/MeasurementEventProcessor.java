package oram.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import worker.IProcessingResult;
import worker.IWorkerEventProcessor;

import java.util.LinkedList;

public class MeasurementEventProcessor implements IWorkerEventProcessor {
	private final Logger logger = LoggerFactory.getLogger("benchmark.oram");
	private static final String SERVER_READY_PATTERN = "Ready to process operations";
	private static final String CLIENT_READY_PATTERN = "Executing experiment";
	private static final String MEASUREMENT_PATTERN = "M:";
	private static final String GET_PM_MEASUREMENT_PATTERN = "getPositionMap";
	private static final String GET_PS_MEASUREMENT_PATTERN = "getPathStash";
	private static final String EVICTION_MEASUREMENT_PATTERN = "eviction";

	private boolean isReady;
	private boolean isServerWorker;
	private boolean doMeasurement;
	private final LinkedList<String> rawGlobalMeasurements;
	private final LinkedList<String> rawGetPMMeasurements;
	private final LinkedList<String> rawGetPSMeasurements;
	private final LinkedList<String> rawEvictionMeasurements;

	public MeasurementEventProcessor() {
		this.rawGlobalMeasurements = new LinkedList<>();
		this.rawGetPMMeasurements = new LinkedList<>();
		this.rawGetPSMeasurements = new LinkedList<>();
		this.rawEvictionMeasurements = new LinkedList<>();
	}

	@Override
	public void process(String line) {
		if (!isReady) {
			if (line.contains(SERVER_READY_PATTERN)) {
				isReady = true;
				isServerWorker = true;
			} else if (line.contains(CLIENT_READY_PATTERN)) {
				isReady = true;
			}
		}
		if (doMeasurement) {
			if (line.contains(MEASUREMENT_PATTERN)) {
				rawGlobalMeasurements.add(line);
			} else if (line.contains(GET_PM_MEASUREMENT_PATTERN)) {
				rawGetPMMeasurements.add(line);
			} else if (line.contains(GET_PS_MEASUREMENT_PATTERN)) {
				rawGetPSMeasurements.add(line);
			} else if (line.contains(EVICTION_MEASUREMENT_PATTERN)) {
				rawEvictionMeasurements.add(line);
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
		if (isServerWorker) {
			return processServerMeasurements();
		} else {
			return processClientMeasurements();
		}
	}

	private IProcessingResult processClientMeasurements() {
		long[] latencies = parseLatency(rawGlobalMeasurements);
		return new Measurement(latencies);
	}

	private IProcessingResult processServerMeasurements() {
		long[] clients = new long[rawGlobalMeasurements.size()];
		long[] delta = new long[rawGlobalMeasurements.size()];
		long[] nGetPMRequests = new long[rawGlobalMeasurements.size()];
		long[] nGetPSRequests = new long[rawGlobalMeasurements.size()];
		long[] nEvictionRequests = new long[rawGlobalMeasurements.size()];
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
			i++;
		}

		long[] getPMLatency = parseLatency(rawGetPMMeasurements);
		long[] getPSLatency = parseLatency(rawGetPSMeasurements);
		long[] evictionLatency = parseLatency(rawEvictionMeasurements);

		return new Measurement(clients, delta, nGetPMRequests, nGetPSRequests, nEvictionRequests, getPMLatency,
				getPSLatency, evictionLatency);
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
