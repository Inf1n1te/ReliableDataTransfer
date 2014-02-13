package client;

/**
 * 
 * Represents a data packet.
 * Is produced and consumed by implementations of INetworkLayerAPI.
 * Serves as a facade towards students for reading and writing data to the network.
 * 
 * @author Jaco ter Braak, Twente University
 * @version 23-01-2014
 *
 */
public class Packet {
	private byte[] data;
	
	/**
	 * Constructs a new packet and associates the data with it
	 * @param data data contained in the packet
	 */
	public Packet(byte[] data){
			this.data = data;
	}
	
	/**
	 * Gets the data associated with this packet
	 * @return data
	 */
	public byte[] GetData(){
		return this.data;
	}
}
