package oram.testers;

import oram.client.ORAMManager;
import oram.client.ORAMObject;
import oram.utils.Operation;
import vss.facade.SecretSharingException;

import java.util.Scanner;

public class ClientInterface {
	public static void main(String[] args) throws SecretSharingException {
		int clientId = Integer.parseInt(args[0]);
		int bucketSize = 7;
		int blockSize = 64;
		Scanner sc = new Scanner(System.in);

		ORAMManager oramManager = new ORAMManager(clientId);
		ORAMObject oram;
		System.out.println("Do you already have an ORAM? (Y for yes, N for no)");
		String nLine = sc.nextLine();
		if (nLine.toUpperCase().contentEquals("N")) {
			System.out.println("Insert height of the tree (up to 8):");
			int size = sc.nextInt();
			System.out.println("Insert an identification number for your ORAM:");
			int oramId = sc.nextInt();
			oram = oramManager.createORAM(oramId, size, bucketSize, blockSize);
			if (oram == null) {
				System.out.println("There was an error, this ORAM already exists!");
			} else {
				System.out.println("ORAM created! Session automatically opened for you.");
			}
		} else {
			System.out.println("Insert the identification number of your ORAM:");
			int oramId = sc.nextInt();
			oram = oramManager.getORAM(oramId);
			System.out.println("Opening your session");
		}

		if (oram == null) {
			System.err.println("Cannot access oram");
			System.exit(-1);
		}

		while(true) {
			Operation op = null;
			while(op == null) {
				System.out.println("Choose operation (read or write)");
				String nextLine = sc.nextLine();
				if(nextLine.toLowerCase().contentEquals("read"))
					op = Operation.READ;
				if(nextLine.toLowerCase().contentEquals("write"))
					op = Operation.WRITE;
			}
			System.out.println("Insert address (int)");
			int address = sc.nextInt();
			String answer;
			if (op == Operation.READ) {
				byte[] a = oram.readMemory(address);
				if (a == null) {
					System.out.println("Answer from server: <empty>");
				} else {
					answer = new String(a);
					System.out.println("Answer from server: " + answer);
				}
			} else {
				System.out.println("Insert new value");
				String value = sc.nextLine();
				byte[] a = oram.writeMemory(address, value.getBytes());
				if (a == null) {
					System.out.println("Answer from server: <empty>");
				} else {
					answer = new String(a);
					System.out.println("Answer from server: " + answer);
				}
			}
			sc.nextLine();

			System.out.println("Do you want to exit (write yes to exit)?");
			String nextLine = sc.nextLine();
			if(nextLine.toLowerCase().contentEquals("yes")) {
				sc.close();
				System.exit(0);
			}

			oramManager.close();
		}
	}
}
