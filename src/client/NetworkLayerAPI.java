package client;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 
 * Simple implementation of the Network Layer API.
 * Serves as a facade towards students for accessing the network.
 * 
 * @author Jaco ter Braak, Twente University
 * @version 23-01-2014
 *
 */
public class NetworkLayerAPI implements INetworkLayerAPI {

	public static int MAX_PACKET_SIZE = 1024 + 1;
	private ReliableDataTransferClient client;
	private ReentrantLock lock = new ReentrantLock();

	public NetworkLayerAPI(ReliableDataTransferClient client) {
		this.client = client;
	}

	@Override
	public TransmissionResult Transmit(Packet packet) {
		if (packet.GetData().length > MAX_PACKET_SIZE) {
			return TransmissionResult.Failure;
		}
//		System.out.println(Arrays.toString(packet.GetData()));
		lock.lock();
		TransmissionResult result = client.Transmit(packet);
		lock.unlock();
		
		return result;
	}

	@Override
	public Packet Receive() {
		return client.Receive();
	}

}
