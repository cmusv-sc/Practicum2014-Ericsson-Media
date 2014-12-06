package edu.cmu.mdnsim.messagebus.exception;

/**
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class MessageBusException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7084948098528918527L;
	
	public MessageBusException() {
		super();
	}
	
	public MessageBusException(String msg) {
		super(msg);
	}
	
	public MessageBusException(Throwable cause) {
		super(cause);
	}
	
	public MessageBusException(String msg, Throwable cause) {
		super(msg, cause);
	}
	
}
