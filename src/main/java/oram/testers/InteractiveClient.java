package oram.testers;

import oram.client.manager.MultiServerORAMManager;
import oram.client.ORAMObject;
import oram.client.manager.ORAMManager;

import java.util.Scanner;

public class InteractiveClient {
	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);
		int clientId = 10000;
		int oramId = 1;
		ORAMManager oramManager = new MultiServerORAMManager(clientId);
		ORAMObject oram = oramManager.getORAM(oramId);
		oram.writeMemory(0, "Hello, World!".getBytes());
		while (true) {
			System.out.print("Enter a command (<w|r> <address> | e): ");
			String command = scanner.nextLine();
			String[] parts = command.split(" ");
			String operation = parts[0];
			if (operation.equals("e")) {
				break;
			}
			int address = Integer.parseInt(parts[1]);
			if (operation.equals("w")) {
				System.out.print("Enter a value: ");
				byte[] value = scanner.nextLine().getBytes();
				oram.writeMemory(address, value);
			} else if (operation.equals("r")) {
				System.out.println("Value at address " + address + ": " + new String(oram.readMemory(address)));
			} else {
				System.out.println("Invalid command.");

			}
		}

		scanner.close();
		oramManager.close();
	}
}
