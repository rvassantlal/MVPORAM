package oram.benchmark.measurements;

import worker.IProcessingResult;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResourcesMeasurementEventProcessor implements IMeasurementEventProcessor {
	private static final String SAR_READY_PATTERN = "%";
	private final Pattern timePattern;

	private final LinkedList<String[]> resourcesMeasurements;

	public ResourcesMeasurementEventProcessor() {
		this.resourcesMeasurements = new LinkedList<>();
		String pattern = "^\\d{2}:\\d{2}:\\d{2}";
		this.timePattern = Pattern.compile(pattern);
	}

	@Override
	public void process(String line) {
		Matcher matcher = timePattern.matcher(line);
		if (matcher.find() && !line.contains("%")) {
			String[] tokens = line.split("\\s+");
			resourcesMeasurements.add(tokens);
		}
	}

	@Override
	public void reset() {
		resourcesMeasurements.clear();
	}

	@Override
	public IProcessingResult getResult() {
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

		long[] cpu = doubleToLongArray(cpuMeasurements);
		long[] mem = longToLongArray(memMeasurements);
		long[][] netReceived = new long[netReceivedMeasurements.size()][];
		long[][] netTransmitted = new long[netTransmittedMeasurements.size()][];
		int i = 0;
		for (String netInterface : netReceivedMeasurements.keySet()) {
			netReceived[i] = doubleToLongArray(netReceivedMeasurements.get(netInterface));
			netTransmitted[i] = doubleToLongArray(netTransmittedMeasurements.get(netInterface));
			i++;
		}

		return new ResourcesMeasurements(cpu, mem, netReceived, netTransmitted);
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
}
