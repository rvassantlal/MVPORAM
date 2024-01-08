package oram.benchmark.measurements;

import worker.IProcessingResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * This class detects and processes measurement events from the client.
 * Client program should print measurement events in the following format: M-\<tag\>: \<measurement as number\>.
 * Example: M-test: 42
 */
public class DefaultMeasurementEventProcessor implements IMeasurementEventProcessor {

	private static final String DEFAULT_MEASUREMENT_PATTERN = "M-";
	private final Map<String, LinkedList<String>> rawMeasurements;

	public DefaultMeasurementEventProcessor() {
		this.rawMeasurements = new HashMap<>();
	}

	@Override
	public void process(String line) {
		if (line.contains(DEFAULT_MEASUREMENT_PATTERN)) {
			String[] tokens = line.split(":");
			String[] keyTokens = tokens[0].split(DEFAULT_MEASUREMENT_PATTERN);
			String key = keyTokens[keyTokens.length - 1];
			if (!rawMeasurements.containsKey(key)) {
				rawMeasurements.put(key, new LinkedList<>());
			}
			rawMeasurements.get(key).add(tokens[1].trim());
		}

	}

	@Override
	public void reset() {
		rawMeasurements.clear();
	}

	@Override
	public IProcessingResult getResult() {
		Map<String, long[]> result = new HashMap<>(rawMeasurements.size());

		for (Map.Entry<String, LinkedList<String>> entry : rawMeasurements.entrySet()) {
			long[] measurements = new long[entry.getValue().size()];
			int i = 0;
			for (String measurement : entry.getValue()) {
				measurements[i++] = Long.parseLong(measurement);
			}
			result.put(entry.getKey(), measurements);
		}
		return new DefaultMeasurements(result);
	}
}
