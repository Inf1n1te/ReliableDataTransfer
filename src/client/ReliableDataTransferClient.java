package client;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.net.Socket;
import java.util.LinkedList;

import client.INetworkLayerAPI.TransmissionResult;

/**
 * 
 * Class for maintaining communications with the challenge server
 * 
 * @author Jaco ter Braak, Twente University
 * @version 23-01-2014
 * 
 */
public class ReliableDataTransferClient {
	private String protocolString = "RDTCHALLENGE/1.0";

	private String host;
	private int port;
	private int groupId;
	private String password;

	private Socket socket;
	private InputStream inputStream;
	private String currentControlMessage = null;
	private String inputBuffer = "";

	private LinkedList<Packet> packetBuffer = new LinkedList<Packet>();
	private int checksumChallenge = -1;
	private boolean closed = false;
	private boolean success = false;

	public ReliableDataTransferClient(String serverAddress, int serverPort,
			int groupId, String password) throws IOException {
		this.groupId = groupId;
		this.password = password;
		this.host = serverAddress;
		this.port = serverPort;

		connect();
	}

	/**
	 * Connects to the challenge server
	 * 
	 * @throws IOException
	 *             if the connection failed
	 */
	private void connect() throws IOException {
		try {
			socket = new Socket(host, port);
			inputStream = socket.getInputStream();

			if (!getControlMessageBlocking().equals("REGISTER")) {
				throw new ProtocolException(
						"Did not get expected hello from server");
			}
			clearControlMessage();

			sendControlMessage("REGISTER " + this.groupId + " " + this.password);

			if (!getControlMessageBlocking().equals("OK")) {
				throw new ProtocolException("Could not register with server");
			}
			clearControlMessage();

		} catch (IOException e) {
			throw e;
		}
	}

	public void Finish(){
		try {
			sendControlMessage("FINISH");
			socket.getOutputStream().flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void UploadChecksum(int checksum){
		try {
			sendControlMessage("CHECKSUM " + checksum);
			socket.getOutputStream().flush();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	public void Close() {
		try {
			closed = true;
			sendControlMessage("CLOSED");
			socket.getOutputStream().flush();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Handles communication between the server and the protocol implementation
	 */
	public void Tick() {
		ProcessPackets();
	}

	/**
	 * Transmits a packet to another node
	 */
	public TransmissionResult Transmit(Packet packet) {
		byte[] packetData = packet.GetData();

		if (packetData.length > NetworkLayerAPI.MAX_PACKET_SIZE) {
			return TransmissionResult.Failure;
		}

		if (!sendControlMessage("TRANSMIT " + packetData.length)) {
			return TransmissionResult.Failure;
		}

		if (!sendRaw(packetData)) {
			return TransmissionResult.Failure;
		}

		return TransmissionResult.Success;
	}

	/**
	 * Sends raw data to the server
	 * 
	 * @param data
	 * @param length
	 * @return whether successful
	 */
	private boolean sendRaw(byte[] data) {
		try {
			socket.getOutputStream().write(data, 0, data.length);
			socket.getOutputStream().flush();
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * Receives a packet from another node
	 */
	public Packet Receive() {
		return packetBuffer.poll();
	}

	/**
	 * Process a packet transmission, wraps raw data into a packet for further
	 * processing
	 * 
	 * @param iface
	 */
	private void ProcessPackets() {
		try {
			String message;
			message = getControlMessage();
			if (message == null) {
				return;
			}

			String[] splitMessage = message.split(" ");

			if (message.startsWith("TRANSMIT") && splitMessage.length == 2) {
				int length = Integer.parseInt(splitMessage[1]);

				byte[] packetData = new byte[length];

				if (!receiveRaw(packetData, length)) {
					return;
				}

				Packet packet = new Packet(packetData);

				packetBuffer.add(packet);
			} else if (message.startsWith("CHECKSUM")
					&& splitMessage.length == 2) {

				this.checksumChallenge = Integer.parseInt(splitMessage[1]);
			} else if (message.startsWith("FINISH") && splitMessage.length == 2){
				if (splitMessage[1].startsWith("SUCCESS")){
					this.success = true;
				}
				this.Close();
			}

			clearControlMessage();

		} catch (ProtocolException e) {
			return;
		}
	}
	
	public int getChecksumChallenge(){
		return this.checksumChallenge;
	}
	
	public boolean getClosed(){
		return closed;
	}
	
	public boolean getSuccess(){
		return success;
	}

	/**
	 * Receives raw data from the server
	 * 
	 * @param data
	 * @param length
	 * @return whether successful
	 */
	private boolean receiveRaw(byte[] data, int length) {
		try {
			int receivedLength = 0;
			int tries = 0;
			byte[] inputBuf = new byte[length];

			while (receivedLength < length && tries < 100) {
				receivedLength += inputStream.read(inputBuf, receivedLength,
						length - receivedLength);
				tries++;
				Thread.sleep(1);
			}

			for (int i = 0; i < length; i++) {
				data[i] = (byte) inputBuf[i];
			}

			return true;
		} catch (IOException e) {
			return false;
		} catch (InterruptedException e) {
			return false;
		}
	}

	/**
	 * Waits for a control message from the server
	 * 
	 * @return the message
	 * @throws ProtocolException
	 *             if a corrupt message was received
	 */
	private String getControlMessageBlocking() throws ProtocolException {
		// Block while waiting for message
		String controlMessage = getControlMessage();
		while (controlMessage == null) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			controlMessage = getControlMessage();
		}

		return controlMessage;
	}

	/**
	 * Removes the first message from the queue Call this when you have
	 * processed a message
	 */
	private void clearControlMessage() {
		this.currentControlMessage = null;
	}

	/**
	 * Obtains a message from the server, if any exists.
	 * 
	 * @return the message, null if no message was present
	 * @throws ProtocolException
	 *             if there was a protocol mismatch
	 */
	private String getControlMessage() throws ProtocolException {
		try {
			if (this.currentControlMessage == null
					&& (inputStream.available() > 0)) {

				char currentChar = ' ';
				while (inputStream.available() > 0 && currentChar != '\n') {
					currentChar = (char) inputStream.read();
					inputBuffer += currentChar;
				}

				if (inputBuffer.endsWith("\n")) {
					String line = inputBuffer.substring(0,
							inputBuffer.length() - 1);
					inputBuffer = "";
					if (line.startsWith(protocolString)) {
						this.currentControlMessage = line
								.substring(protocolString.length() + 1);
					} else {
						throw new ProtocolException(
								"Protocol mismatch with server");
					}
				}
			}
		} catch (IOException e) {
		}
		return this.currentControlMessage;
	}

	/**
	 * Sends a message to the server
	 * 
	 * @param message
	 */
	private boolean sendControlMessage(String message) {
		try {
			socket.getOutputStream().write(
					(protocolString + " " + message + "\n").getBytes());
			socket.getOutputStream().flush();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
