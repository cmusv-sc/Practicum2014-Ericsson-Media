package edu.cmu.mdnsim.messagebus.message;

/**
 * 
 * MbMessage is a abstract class that marks all interprocess messages to be 
 * sent in the system.
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class MbMessage {
	
	private String from;
	
	private String to;
	
	public void from(String from) {
		this.setFrom(from);
	}
	
	public void to(String to) {
		this.setTo(to);
	}
	
	public String source() {
		return getFrom();
	}
	
	public String destination() {
		return getTo();
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public String getTo() {
		return to;
	}

	public void setTo(String to) {
		this.to = to;
	}
	
}
