package oram.benchmark.measurements;

import worker.IProcessingResult;

import java.util.Map;

public class DefaultMeasurements implements IProcessingResult {
	private final Map<String, long[]> measurements;

	public DefaultMeasurements(Map<String, long[]> measurements) {

		this.measurements = measurements;
	}

	public long[] getMeasurements(String tag) {
		return measurements.get(tag);
	}

	public Map<String, long[]> getMeasurements() {
		return measurements;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, long[]> entry : measurements.entrySet()) {
			sb.append(entry.getKey()).append(": ");
			sb.append(entry.getValue().length);
			sb.append("\n");
		}
		return sb.toString();
	}
}
