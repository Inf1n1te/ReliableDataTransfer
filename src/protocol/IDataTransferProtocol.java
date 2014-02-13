package protocol;

import client.INetworkLayerAPI;
import client.ITimeoutEventHandler;

/**
 * 
 * Interface for the transfer protocol.
 * 
 * @author Jaco ter Braak, Twente University
 * @version 11-01-2014
 * 
 */
public interface IDataTransferProtocol extends ITimeoutEventHandler {
	/**
	 * Is called by the framework, to set the network layer API reference.
	 * @param networkLayer
	 */
	void SetNetworkLayerAPI(INetworkLayerAPI networkLayer);
	
	/**
	 * Is called by the framework, in order to initialize the transfer
	 */
	void Initialize(TransferMode transferMode);
	
	/**
	 * Is called periodically by the framework, in order to do work.
	 * @return Whether the transfer is completed
	 */
	boolean Tick();
	
	/**
	 * The transfer mode this instance of the protocol is in.
	 * @author Jaco ter Braak, Twente University
	 * @version 12-01-2014
	 */
	public static enum TransferMode{
		Send,
		Receive
	}
}
