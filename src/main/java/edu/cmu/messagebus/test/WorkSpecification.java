package edu.cmu.messagebus.test;

import java.util.List;
import java.util.Map;

import edu.cmu.messagebus.message.Message;

public class WorkSpecification extends Message {

	private int id;
	private int cIdx;
	private List<Map<String, Object>> configList;
	
	public Map<String, Object> getConfig() {
		return configList.get(cIdx);
	}
	
	public String getNextNodeURI() {
		if (cIdx == configList.size() - 1) {
			return "";
		} else {
			return (String)configList.get(cIdx + 1).get("WarpURI");
		}
	}
	
	public void setReceiver(String ip, int port) {
		
		if (cIdx >= configList.size()) {
			return;
		}
		
		Map<String, Object> config = configList.get(cIdx + 1);
		config.put("receiver-ip", ip);
		config.put("receiver-port", port);
		
	}
	
	public void incrementNodeIdx() {
		cIdx++;
	}
	
}
