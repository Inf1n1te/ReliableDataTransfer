package client;

/**
 * Represents the network layer which takes care of sending and receiving data packets.
 * Reliable transmission is not guaranteed.
 * @author Jaco ter Braak, Twente University
 * @version 11-01-2014
 */
public interface INetworkLayerAPI {
	/**
	 * Transmits a data packet over the unreliable medium
	 * @param packet
	 * @return Whether the transmission was successful.
	 */
	TransmissionResult Transmit(Packet packet);
	
	/**
	 * Attempts to receive a packet from the unreliable medium. Returns null if no packet is available.
	 * @return The packet, or null if no packet
	 */
	Packet Receive();
	
	/**
	 * Represents the result of the transmission attempt
	 * @author Jaco ter Braak, Twente University
	 * @version 11-01-2014
	 */
	public enum TransmissionResult{
		Success,
		Failure
	}
}


