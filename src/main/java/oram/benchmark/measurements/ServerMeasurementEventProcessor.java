package oram.benchmark.measurements;

import worker.IProcessingResult;

import java.util.LinkedList;

public class ServerMeasurementEventProcessor implements IMeasurementEventProcessor {
	private static final String MEASUREMENT_PATTERN = "M:";

	private final LinkedList<String> rawMeasurements;

	public ServerMeasurementEventProcessor() {
		this.rawMeasurements = new LinkedList<>();
	}

	@Override
	public void process(String line) {
		if (line.contains(MEASUREMENT_PATTERN)) {
			rawMeasurements.add(line);
		}
	}

	@Override
	public void reset() {
		rawMeasurements.clear();
	}

	@Override
	public IProcessingResult getResult() {
		long[] clients = new long[rawMeasurements.size()];
		long[] delta = new long[rawMeasurements.size()];
		long[] nGetPMRequests = new long[rawMeasurements.size()];
		long[] nGetPSRequests = new long[rawMeasurements.size()];
		long[] nEvictionRequests = new long[rawMeasurements.size()];
		long[] outstanding = new long[rawMeasurements.size()];
		long[] getPMAvg = new long[rawMeasurements.size()];
		long[] getPSAvg = new long[rawMeasurements.size()];
		long[] evictionAvg = new long[rawMeasurements.size()];
		int i = 0;
		for (String rawMeasurement : rawMeasurements) {
			String token = rawMeasurement.split(">")[1];
			token = token.substring(1, token.length() - 1);
			String[] strValues = token.split("\\|");
			clients[i] = Long.parseLong(strValues[0]);
			delta[i] = Long.parseLong(strValues[1]);
			nGetPMRequests[i] = Long.parseLong(strValues[2]);
			nGetPSRequests[i] = Long.parseLong(strValues[3]);
			nEvictionRequests[i] = Long.parseLong(strValues[4]);
			outstanding[i] = Long.parseLong(strValues[5]);
			getPMAvg[i] = Long.parseLong(strValues[6]);
			getPSAvg[i] = Long.parseLong(strValues[7]);
			evictionAvg[i] = Long.parseLong(strValues[8]);
			i++;
		}

		return new ServerMeasurements(clients, delta, nGetPMRequests, nGetPSRequests, nEvictionRequests, getPMAvg,
				getPSAvg, evictionAvg, outstanding);
	}
}
