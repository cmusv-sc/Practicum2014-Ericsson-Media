package edu.cmu.nodes;

import edu.cmu.messagebus.NodeContainer;

public interface SourceNode {
		
		/**
		 * Transfers bytesToTransfer number of bytes at rate kb/second 
		 * to destination with address destAddr and port destPort with streamId 
		 * as identifier
		 * @param streamId
		 * @param destAddr
		 * @param destPort
		 * @param bytesToTransfer
		 * @param rate - kb/sec
		 * @param mdnSource
		 */
		public void sendAndReport(String streamId, String destAddrStr, 
				int destPort, int bytesToTransfer, int rate, 
				NodeContainer mdnSource);
}
