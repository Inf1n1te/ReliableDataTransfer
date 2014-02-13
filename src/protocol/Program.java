package protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import protocol.IDataTransferProtocol.TransferMode;

import java.util.zip.CRC32;

import client.*;

/**
 * 
 * Entry point of the program. Starts the client and links the used data
 * transfer protocol.
 * 
 * @author Jaco ter Braak, Twente University
 * @version 23-01-2014
 * 
 */
public class Program {

	// Change to your group number (e.g. use a student number)
	private static int groupId = 123;

	// Change to your group password (doesn't matter what it is,
	// as long as everyone in the group uses the same string)
	private static String password = "testpass";

	// Change to your protocol implementation
	private static IDataTransferProtocol protocol = new NaiveTransferProtocol();

	// Whether this program should send or receive
	private static TransferMode transferMode = TransferMode.Send;

	// Challenge server address
	private static String serverAddress = "dacs-stud03.ewi.utwente.nl";

	// Challenge server port
	private static int serverPort = 8003;

	/*
	 * 
	 * 
	 * 
	 * 
	 * 
	 * DO NOT EDIT BELOW THIS LINE
	 */
	public static void main(String[] args) throws IOException {
		ReliableDataTransferClient client = new ReliableDataTransferClient(
				serverAddress, serverPort, groupId, password);

		System.out.print("Connected to the challenge server. Initializing... ");

		Utils.Timeout.Start();

		INetworkLayerAPI networkLayer = new NetworkLayerAPI(client);

		protocol.SetNetworkLayerAPI(networkLayer);
		protocol.Initialize(transferMode);

		System.out.println("Done. Starting work.");

		boolean workDone = false;
		while (!workDone) {
			client.Tick();
			workDone = protocol.Tick();
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
			}
		}

		System.out
				.print("Protocol has signaled work completion. Cleaning up... ");

		Utils.Timeout.Stop();

		client.Finish();

		while (client.getChecksumChallenge() == -1) {
			try {
				client.Tick();
				Thread.sleep(1);
			} catch (InterruptedException e) {
			}
		}

		long fileLength;
		FileInputStream input;
		if (transferMode == TransferMode.Receive) {
			fileLength = new File("received.dat").length();
			input = new FileInputStream("received.dat");
		} else {
			fileLength = new File("tobesent.dat").length();
			input = new FileInputStream("tobesent.dat");
		}
		byte[] fileContent = new byte[(int) fileLength + 4];
		input.read(fileContent, 4, (int) fileLength);

		fileContent[0] = (byte) (client.getChecksumChallenge() % 256);
		fileContent[1] = (byte) ((client.getChecksumChallenge() / 256) % 256);
		fileContent[2] = (byte) ((client.getChecksumChallenge() / (256 * 256)) % 256);
		fileContent[3] = (byte) ((client.getChecksumChallenge() / (256 * 256 * 256)) % 256);

		CRC32 crc = new CRC32();
		crc.update(fileContent);
		int checksum = (int) crc.getValue();

		client.UploadChecksum(checksum);

		while (!client.getClosed()) {
			try {
				client.Tick();
				Thread.sleep(1);
			} catch (InterruptedException e) {
			}
		}

		if (client.getSuccess()) {
			System.out
					.println("Done. File was transferred successfully! Check your performance on the web interface.");
		} else {
			System.out
					.println("Done. Transferred data was corrupt or incomplete. Please try again.");
		}
	}
}
