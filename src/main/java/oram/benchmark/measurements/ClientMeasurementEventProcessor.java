package oram.benchmark.measurements;

import worker.IProcessingResult;

import java.util.LinkedList;

public class ClientMeasurementEventProcessor implements IMeasurementEventProcessor {
	private static final String GLOBAL_MEASUREMENT_PATTERN = "MGlobal:";
	private static final String GET_PM_MEASUREMENT_PATTERN = "MGetPMOP:";
	private static final String GET_PS_MEASUREMENT_PATTERN = "MGetPSOP:";
	private static final String EVICTION_MEASUREMENT_PATTERN = "MEvictionOP:";
	private static final String RECEIVED_PM_MEASUREMENT_PATTERN = "MReceivedPM:";
	private static final String RECEIVED_STASHES_MEASUREMENT_PATTERN = "MReceivedStashes:";
	private static final String RECEIVED_STASH_BLOCKS_MEASUREMENT_PATTERN = "MReceivedStashBlocks:";
	private static final String SENT_STASH_BLOCKS_MEASUREMENT_PATTERN = "MSentStashBlocks:";
	private static final String RECEIVED_PATH_SIZE_MEASUREMENT_PATTERN = "MReceivedPathSize:";
	private static final String SENT_PATH_SIZE_MEASUREMENT_PATTERN = "MSentPathSize:";

	private final LinkedList<String> rawGlobalMeasurements;
	private final LinkedList<String> rawGetPMMeasurements;
	private final LinkedList<String> rawGetPSMeasurements;
	private final LinkedList<String> rawEvictionMeasurements;
	private final LinkedList<String> rawReceivedPMMeasurements;
	private final LinkedList<String> rawReceivedStashesMeasurements;
	private final LinkedList<String> rawReceivedStashBlocksMeasurements;
	private final LinkedList<String> rawSentStashBlocksMeasurements;
	private final LinkedList<String> rawReceivedPathSizeMeasurements;
	private final LinkedList<String> rawSentPathSizeMeasurements;

	public ClientMeasurementEventProcessor() {
		this.rawGlobalMeasurements = new LinkedList<>();
		this.rawGetPMMeasurements = new LinkedList<>();
		this.rawGetPSMeasurements = new LinkedList<>();
		this.rawEvictionMeasurements = new LinkedList<>();
		this.rawReceivedPMMeasurements = new LinkedList<>();
		this.rawReceivedStashesMeasurements = new LinkedList<>();
		this.rawReceivedStashBlocksMeasurements = new LinkedList<>();
		this.rawSentStashBlocksMeasurements = new LinkedList<>();
		this.rawReceivedPathSizeMeasurements = new LinkedList<>();
		this.rawSentPathSizeMeasurements = new LinkedList<>();
	}

	@Override
	public void process(String line) {
		if (line.contains(GLOBAL_MEASUREMENT_PATTERN)) {
			rawGlobalMeasurements.add(line);
		} else if (line.contains(GET_PM_MEASUREMENT_PATTERN)) {
			rawGetPMMeasurements.add(line);
		} else if (line.contains(GET_PS_MEASUREMENT_PATTERN)) {
			rawGetPSMeasurements.add(line);
		} else if (line.contains(EVICTION_MEASUREMENT_PATTERN)) {
			rawEvictionMeasurements.add(line);
		} else if (line.contains(RECEIVED_PM_MEASUREMENT_PATTERN)) {
			rawReceivedPMMeasurements.add(line);
		} else if (line.contains(RECEIVED_STASHES_MEASUREMENT_PATTERN)) {
			rawReceivedStashesMeasurements.add(line);
		} else if (line.contains(RECEIVED_STASH_BLOCKS_MEASUREMENT_PATTERN)) {
			rawReceivedStashBlocksMeasurements.add(line);
		} else if (line.contains(SENT_STASH_BLOCKS_MEASUREMENT_PATTERN)) {
			rawSentStashBlocksMeasurements.add(line);
		} else if (line.contains(RECEIVED_PATH_SIZE_MEASUREMENT_PATTERN)) {
			rawReceivedPathSizeMeasurements.add(line);
		} else if (line.contains(SENT_PATH_SIZE_MEASUREMENT_PATTERN)) {
			rawSentPathSizeMeasurements.add(line);
		}
	}

	@Override
	public void reset() {
		rawGlobalMeasurements.clear();
		rawGetPMMeasurements.clear();
		rawGetPSMeasurements.clear();
		rawEvictionMeasurements.clear();
		rawReceivedPMMeasurements.clear();
		rawReceivedStashesMeasurements.clear();
		rawReceivedStashBlocksMeasurements.clear();
		rawSentStashBlocksMeasurements.clear();
		rawReceivedPathSizeMeasurements.clear();
		rawSentPathSizeMeasurements.clear();
	}

	@Override
	public IProcessingResult getResult() {
		long[] globalLatency = parseValues(rawGlobalMeasurements);
		long[] getPMLatency = parseValues(rawGetPMMeasurements);
		long[] getPSLatency = parseValues(rawGetPSMeasurements);
		long[] evictionLatency = parseValues(rawEvictionMeasurements);
		long[] receivedPMSize = parseValues(rawReceivedPMMeasurements);
		long[] receivedStashes = parseValues(rawReceivedStashesMeasurements);
		long[] receivedStashBlocks = parseValues(rawReceivedStashBlocksMeasurements);
		long[] sentStashBlocks = parseValues(rawSentStashBlocksMeasurements);
		long[] receivedPathSize = parseValues(rawReceivedPathSizeMeasurements);
		long[] sentPathSize = parseValues(rawSentPathSizeMeasurements);
		return new ClientMeasurements(globalLatency, getPMLatency, getPSLatency, evictionLatency, receivedPMSize,
				receivedStashes, receivedStashBlocks, sentStashBlocks, receivedPathSize, sentPathSize);
	}

	private long[] parseValues(LinkedList<String> rawMeasurements) {
		long[] latency = new long[rawMeasurements.size()];
		int i = 0;
		for (String rawMeasurement : rawMeasurements) {
			String[] tokens = rawMeasurement.split(" ");
			latency[i++] = Long.parseLong(tokens[tokens.length - 1]);
		}
		return latency;
	}
}
