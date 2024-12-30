package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.messages.UpdateConcurrentClientsMessage;
import oram.utils.ORAMUtils;
import oram.utils.ServerOperationType;
import oram.utils.Status;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ManagerClient {
	public static void main(String[] args) throws SecretSharingException, InterruptedException {
		if (args.length != 3) {
			System.out.println("Usage: ... oram.client.ManagerClient <initial delay in seconds> <update period in seconds> <number of updates>");
			System.exit(-1);
		}
		Logger logger = LoggerFactory.getLogger("measurements");

		int oramId = 1;
		int specialClientId = 1;
		int initialDelay = Integer.parseInt(args[0]);
		int period = Integer.parseInt(args[1]);
		int nUpdates = Integer.parseInt(args[2]);
		ConfidentialServiceProxy serviceProxy = new ConfidentialServiceProxy(specialClientId);
		int mean = 1;
		PoissonDistribution poissonDistribution = new PoissonDistribution(mean);

		CountDownLatch latch = new CountDownLatch(nUpdates);
		Runnable operation = new Runnable() {
			int update = 1;
			@Override
			public void run() {
				try {
					int sample = poissonDistribution.sample() + 1;
					logger.debug("Concurrent clients for update {}: {}", update, sample);
					update++;

					UpdateConcurrentClientsMessage request = new UpdateConcurrentClientsMessage(oramId, sample);
					byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.UPDATE_CONCURRENT_CLIENTS, request);
					Response response = serviceProxy.invokeOrdered(serializedRequest);
					if (response == null || response.getPlainData() == null) {
						throw new IllegalStateException("Received null response");
					}
					Status status = Status.getStatus(response.getPlainData()[0]);
					if (status != Status.SUCCESS) {
						throw new IllegalStateException("Failed to update the maximum number of concurrent clients");
					}
					latch.countDown();
				} catch (SecretSharingException e) {
					throw new RuntimeException(e);
				}
			}
		};

		ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
		scheduledExecutorService.scheduleAtFixedRate(operation, initialDelay, period, TimeUnit.SECONDS);
		latch.await();
		serviceProxy.close();
		scheduledExecutorService.shutdown();
	}
}
