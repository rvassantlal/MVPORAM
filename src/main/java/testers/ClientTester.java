package testers;

import pathoram.Client;
import utils.Operation;
import vss.facade.SecretSharingException;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Random;

public class ClientTester {
	// ARGS: clientId, pass, oramName, testSize
	public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeySpecException, SecretSharingException {
		Random r = new Random();
		Client me = new Client(Integer.parseInt(args[0]), args[1]);
		int oramSize = 6;
		me.createOram(oramSize, Integer.parseInt(args[2]));
		int testSize = Integer.parseInt(args[3]);
		try {
			for (short i = 0; i < testSize; i++) {
				Operation op = null;
				while (op == null) {
					op = r.nextBoolean() ? Operation.READ : Operation.WRITE;
				}
				Short key = (short) r.nextInt(Short.MAX_VALUE);
				Short value = null;
				if (op.equals(Operation.WRITE)) {
					value = (short) r.nextInt(Short.MAX_VALUE);
				}
				byte[] val = value == null ? new byte[]{} : new byte[]{value.byteValue()};
				byte[] answer = me.access(op, Integer.valueOf(key),val);
				System.out.println("Answer from server: "+answer);
			}
			System.exit(0);

		} catch (IOException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
}
