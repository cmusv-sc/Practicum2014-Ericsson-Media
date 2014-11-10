/**
 * 
 */
package edu.cmu.mdnsim.messagebus.message;

/**
 * Indicates what type of event occurred & how to interpret Report Message
 */
public enum EventType {
	/**
	 * Used when a node starts sending data for a stream 
	 */
	SEND_START,
	/**
	 * Used when a node is done with sending data for a stream
	 */
	SEND_END,
	/**
	 * Used when a node has started receiving data for a stream
	 */
	RECEIVE_START,
	/**
	 * Used when a node is done receiving data for a stream
	 */
	RECEIVE_END,
	/**
	 * Used for intermediate reports
	 */
	PROGRESS_REPORT
	
}
