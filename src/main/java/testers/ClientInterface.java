package testers;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Scanner;

import pathoram.Client;
import utils.Operation;
import vss.facade.SecretSharingException;

public class ClientInterface {
	public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeySpecException, SecretSharingException {
		Scanner sc=new Scanner(System.in);
		System.out.println("Insert your password please:");
		String pass = sc.nextLine();
		Client me = new Client(Integer.parseInt(args[0]),pass);
		System.out.println("Do you already have an ORAM? (Y for yes, N for no)");
		String nLine = sc.nextLine();
		if(nLine.toUpperCase().contentEquals("N")) {
			System.out.println("Insert height of the tree (up to 8):");
			int size = sc.nextInt();
			System.out.println("Insert an identification number for your ORAM:");
			int oramName = sc.nextInt();
			String msg=me.createOram(size,oramName)?"ORAM created! Session automatically opened for you.":"There was an error, this ORAM already exists!";
			System.out.println(msg);
		}else {
			System.out.println("Insert the identification number of your ORAM:");
			int oramName = sc.nextInt();
			me.associateOram(oramName);
			System.out.println("Opening your session");
		}
		//openSession();
		while(true) {
			Operation op=null;
			while(op==null) {
				System.out.println("Choose operation (read or write)");
				String nextLine = sc.nextLine();
				if(nextLine.toLowerCase().contentEquals("read"))
					op=Operation.READ;
				if(nextLine.toLowerCase().contentEquals("write"))
					op=Operation.WRITE;
			}
			System.out.println("Insert key (is a short)");
			Short key = sc.nextShort();
			Short value = null;
			if(op.equals(Operation.WRITE)) {
				System.out.println("Insert new value (is a short)");
				value = sc.nextShort();
			}
			sc.nextLine();
			try {
				byte[] val = value == null ? new byte[]{} : new byte[]{value.byteValue()};
				byte[] answer = me.access(op, Integer.valueOf(key),val);
				System.out.println("Answer from server: "+answer);
				
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("Your session is closed! Please open your session, please");
			}		
			System.out.println("Do you want to exit (write yes to exit)?");
			String nextLine = sc.nextLine();
			if(nextLine.toLowerCase().contentEquals("yes")) {
				//closeSession();
				sc.close();
				System.exit(0);
			}
		}
	}
}
