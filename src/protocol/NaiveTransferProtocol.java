package protocol;

import java.io.*;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;

import client.*;
import client.INetworkLayerAPI.TransmissionResult;

/**
 * 
 * Very naive implementation of IDataTransferProtocol
 * 
 * @author Jaco ter Braak, Twente University
 * @version 23-01-2014
 * 
 */
public class NaiveTransferProtocol implements IDataTransferProtocol,
		ITimeoutEventHandler {
	private INetworkLayerAPI networkLayer;
	private int bytesSent = 0;
	private TransferMode transferMode;
	private int seq = -128;
	private LinkedList<Byte> seqnums = new LinkedList<Byte>();
	byte[] finalArray;
	private boolean sent = false;
	private boolean timeOut = false;
	private Packet lastPacket;
	FileInputStream inputStream;
	FileOutputStream outputStream;

	@Override
	public void TimeoutElapsed(Object tag) {
		if ((int) tag == (seq - 1)) {
			seq--;
			sent = false;
			timeOut = true;
		}
	}

	@Override
	public void SetNetworkLayerAPI(INetworkLayerAPI networkLayer) {
		this.networkLayer = networkLayer;
	}

	@Override
	public void Initialize(TransferMode transferMode) {
		this.transferMode = transferMode;

		// Send mode
		if (this.transferMode == TransferMode.Send) {
			try {
				// Open the input file
				inputStream = new FileInputStream(Paths.get("")
						.toAbsolutePath() + "/tobesent.dat");
			} catch (FileNotFoundException e) {
				throw new IllegalStateException("File not found");
			}

			// Receive mode
		} else {
			try {
				// Open the output file
				outputStream = new FileOutputStream(Paths.get("")
						.toAbsolutePath() + "/received.dat");
			} catch (FileNotFoundException e) {
				throw new IllegalStateException("File could not be created");
			}
		}
	}

	@Override
	public boolean Tick() {
		if (this.transferMode == TransferMode.Send && sent) {
			// Send mode
			return ReceiveAck();
		} else if (this.transferMode == TransferMode.Send) {
			return SendData();
		} else {
			// Receive mode
			return ReceiveData();
		}
	}

	/**
	 * Handles sending of data from the input file
	 * 
	 * @return whether work has been completed
	 */
	private boolean SendData() {
		// Max packet size is 1024
		byte[] readDataNew = new byte[1024];
		byte[] readData = new byte[1024];
		try {
			int readSize;
			if (!timeOut) {
				readSize = inputStream.read(readDataNew);

				if (readSize >= 0) {
					if (!timeOut) {
						if (readSize < 1024) {
							readData = new byte[readSize];
						}
						System.arraycopy(readDataNew, 0, readData, 0, readSize);
						// We read some bytes, send the packet
						System.out.println(Arrays.toString(readData));
						byte[] totalPacket = new byte[readData.length + 1];
						totalPacket[0] = (byte) seq;
						System.arraycopy(readData, 0, totalPacket, 1,
								readData.length);
						Packet toBeSent = new Packet(totalPacket);
						if (networkLayer.Transmit(toBeSent) == TransmissionResult.Failure) {
							System.out.println("Failure transmitting");
							return true;
						}
						Utils.Timeout.SetTimeout(100, this, seq);
						sent = true;
						seq++;
						lastPacket = toBeSent;
					}
				} else {
					// readSize == -1 means End-Of-File
					try {
						// Send empty packet, to signal transmission end. Send
						// it a
						// bunch of times to make sure it arrives
						networkLayer.Transmit(new Packet(new byte[] {}));
						networkLayer.Transmit(new Packet(new byte[] {}));
						networkLayer.Transmit(new Packet(new byte[] {}));
						networkLayer.Transmit(new Packet(new byte[] {}));
						networkLayer.Transmit(new Packet(new byte[] {}));

						// Close the file
						inputStream.close();

					} catch (IOException e) {
						e.printStackTrace();
					}

					// Return true to signal work done
					return true;
				}

				// Print how far along we are
				bytesSent += readSize;

				// Get the file size
				File file = new File(Paths.get("").toAbsolutePath()
						+ "/tobesent.dat");

				// Print the percentage of file transmitted
				System.out.println("Sent: "
						+ (int) (bytesSent * 100 / (double) file.length())
						+ "%");

			} else if (timeOut) {
				if (networkLayer.Transmit(lastPacket) == TransmissionResult.Failure) {
					System.out.println("Failure transmitting");
					return true;
				}
				Utils.Timeout.SetTimeout(100, this, seq);
				sent = true;
				timeOut = false;
				seq++;
			}
		} catch (IOException e) {
			// We encountered an error while reading the file. Stop work.
			System.out.println("Error reading the file: " + e.getMessage());
			return true;
		}
		// Signal that work is not completed yet
		return false;
	}

	/**
	 * Handles receiving of data packets and writing data to the output file
	 * 
	 * @return Whether work has been completed
	 */
	private boolean ReceiveData() {
		// Receive a data packet
		Packet receivedPacket = networkLayer.Receive();
		if (receivedPacket != null) {
			byte[] data = receivedPacket.GetData();
			if (data.length != 0) {
				// receiver sends back
				byte[] responsePacket = new byte[] { data[0] };
				Packet response = new Packet(responsePacket);
				if (networkLayer.Transmit(response) == TransmissionResult.Failure) {
					System.out.println("Failure transmitting");
					return true;
				}
			}
			if (data.length > 0 && !seqnums.contains(data[0])) {
				seqnums.add(data[0]);
				System.out.println("Current packet: " + data[0]);
				byte[] finalArray = new byte[data.length - 1];
				System.arraycopy(data, 1, finalArray, 0, data.length - 1);
				// System.out.println(Arrays.toString(data));
				// System.out.println(Arrays.toString(finalArray));

				try {
					System.out.println(Arrays.toString(finalArray));
					outputStream.write(finalArray, 0, finalArray.length);
					outputStream.flush();
				} catch (IOException e) {
					System.out.println("Failure writing to file: "
							+ e.getMessage());
					return true;
				}

			}
			// If the data packet was empty, we are done
			if (data.length == 0) {
				try {
					// Close the file
					outputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}

				// Signal that work is done
				return true;
			}

			// Write the data to the output file

		}

		return false;
	}

	private boolean ReceiveAck() {
		// Receive a data packet
		Packet receivedPacket = networkLayer.Receive();
		if (receivedPacket != null) {
			byte[] data = receivedPacket.GetData();
			if (data[0] == seq - 1) {
				sent = false;
			}
		}

		return false;
	}

}
