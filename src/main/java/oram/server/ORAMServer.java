package oram.server;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import confidential.ConfidentialMessage;
import confidential.facade.server.ConfidentialSingleExecutable;
import confidential.server.ConfidentialRecoverable;
import confidential.statemanagement.ConfidentialSnapshot;
import oram.messages.CreateORAMMessage;
import oram.messages.ORAMMessage;
import oram.utils.ServerOperationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.secretsharing.VerifiableShare;

public class ORAMServer implements ConfidentialSingleExecutable, ClientMessageSender {
	private final Logger logger = LoggerFactory.getLogger("oram");
	private final ConfidentialRecoverable confidentialRecoverable;
	private final ORAMService oramService;

	public ORAMServer(int maxClients, int processId) {
		oramService = new ORAMService(maxClients, this);

		//Starting server
		confidentialRecoverable = new ConfidentialRecoverable(processId, this);
		new ServiceReplica(processId, confidentialRecoverable, confidentialRecoverable, confidentialRecoverable, confidentialRecoverable);
	}

	public static void main(String[] args) {
		new ORAMServer(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
	}

	@Override
	public ConfidentialMessage appExecuteOrdered(byte[] plainData, VerifiableShare[] shares, MessageContext msgCtx) {
		byte[] serializedResponse = oramService.executeOrdered(plainData, msgCtx);
		if (serializedResponse == null) {
			return null;
		}
		if (serializedResponse.length == 0) {
			return new ConfidentialMessage();
		}

		ServerOperationType op = ServerOperationType.getOperation(plainData[0]);
		if (op == ServerOperationType.CREATE_ORAM) {
			VerifiableShare encryptionKeyShare = shares.length > 0 ? shares[0] : null;
			CreateORAMMessage request = new CreateORAMMessage();
			request.readExternal(plainData, 1);
			oramService.setEncryptionKeyShare(request.getOramId(), encryptionKeyShare);
			return new ConfidentialMessage(serializedResponse);
		}

		if (op == ServerOperationType.GET_ORAM) {
			ORAMMessage request = new ORAMMessage();
			request.readExternal(plainData, 1);
			VerifiableShare encryptionKeyShare = oramService.getEncryptionKeyShare(request.getOramId());
			return new ConfidentialMessage(serializedResponse, encryptionKeyShare);
		}

		return new ConfidentialMessage(serializedResponse);
	}

	@Override
	public ConfidentialMessage appExecuteUnordered(byte[] plainData, VerifiableShare[] shares, MessageContext msgCtx) {
		byte[] serializedResponse = oramService.executeUnordered(plainData, msgCtx);
		if (serializedResponse == null) {
			return null;
		}
		if (serializedResponse.length == 0) {
			return new ConfidentialMessage();
		}

		ServerOperationType op = ServerOperationType.getOperation(plainData[0]);
		if (op == ServerOperationType.GET_ORAM) {
			ORAMMessage request = new ORAMMessage();
			request.readExternal(plainData, 1);
			VerifiableShare encryptionKeyShare = oramService.getEncryptionKeyShare(request.getOramId());
			return new ConfidentialMessage(serializedResponse, encryptionKeyShare);
		}

		return new ConfidentialMessage(serializedResponse);
	}

	@Override
	public void sendMessageToClient(MessageContext clientMsgCtx, byte[] serializedMessage) {
		ConfidentialMessage message = new ConfidentialMessage(serializedMessage);
		confidentialRecoverable.sendMessageToClient(clientMsgCtx, message);
	}

	@Override
	public ConfidentialSnapshot getConfidentialSnapshot() {
		return new ConfidentialSnapshot(new byte[0]);
	}

	@Override
	public void installConfidentialSnapshot(ConfidentialSnapshot confidentialSnapshot) {

	}
}
