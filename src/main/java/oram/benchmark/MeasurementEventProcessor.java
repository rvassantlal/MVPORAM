package oram.benchmark;

import oram.benchmark.measurements.ClientMeasurementEventProcessor;
import oram.benchmark.measurements.IMeasurementEventProcessor;
import oram.benchmark.measurements.ResourcesMeasurementEventProcessor;
import oram.benchmark.measurements.ServerMeasurementEventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import worker.IProcessingResult;
import worker.IWorkerEventProcessor;

public class MeasurementEventProcessor implements IWorkerEventProcessor {
	private final Logger logger = LoggerFactory.getLogger("benchmark.oram");
	private static final String SERVER_READY_PATTERN = "Ready to process operations";
	private static final String CLIENT_READY_PATTERN = "Executing experiment";
	private static final String SAR_READY_PATTERN = "%";

	private IMeasurementEventProcessor measurementEventProcessor;
	private boolean isReady;
	private boolean doMeasurement;

	public MeasurementEventProcessor() {}

	@Override
	public void process(String line) {
		logger.debug(line);
		if (!isReady) {
			if (line.contains(SERVER_READY_PATTERN)) {
				isReady = true;
				measurementEventProcessor = new ServerMeasurementEventProcessor();
			} else if (line.contains(CLIENT_READY_PATTERN)) {
				isReady = true;
				measurementEventProcessor = new ClientMeasurementEventProcessor();
			} else if (line.contains(SAR_READY_PATTERN)) {
				isReady = true;
				measurementEventProcessor = new ResourcesMeasurementEventProcessor();
			}
		}
		if (doMeasurement) {
			measurementEventProcessor.process(line);
		}
	}

	@Override
	public void startProcessing() {
		logger.debug("Measuring");
		measurementEventProcessor.reset();
		doMeasurement = true;
	}

	@Override
	public void stopProcessing() {
		logger.debug("Not Measuring");
		doMeasurement = false;
	}

	@Override
	public IProcessingResult getProcessingResult() {
		return measurementEventProcessor.getResult();
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
