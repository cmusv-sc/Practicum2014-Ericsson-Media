package edu.cmu.mdnsim.integratedtest;

import java.util.List;
import java.util.Map;

import edu.cmu.mdnsim.messagebus.message.MbMessage;

public class WorkSpecification extends MbMessage {

	private int id;
	private int cIdx;
	private List<Map<String, Object>> configList;
	
	/**
	 * Returns a Map<String, Object> that represents the config 
	 * of the current node in the WorkSpecification
	 * @return
	 */
	public Map<String, Object> getConfig() {
		return configList.get(cIdx);
	}
	
	/**
	 * Returns a Map<String, Object> that represents the config 
	 * of the downstream node in the WorkSpecification
	 * @return
	 */
	public Map<String, Object> getDownstreamConfig() {
		if (cIdx-1 >=0 )
			return configList.get(cIdx-1);
		else
			return null;
	}
	
	/**
	 * Returns the URI of the node that is upstream to current node 
	 * from the WorkSpecification
	 * @return
	 */
	public String getNextNodeURI() {
		if (cIdx == configList.size() - 1) {
			return "";
		} else {
			return (String)configList.get(cIdx + 1).get("WarpURI");
		}
	}
	
	/**
	 * Utility Method for any node to inform the upstream node about 
	 * the dst-ip and dst-port it has to send data to.
	 * @param ip
	 * @param port
	 */
	public void setReceiverIpAndPort(String ip, int port) {
		
		if (cIdx >= configList.size()) {
			return;
		}
		
		Map<String, Object> config = configList.get(cIdx + 1);
		config.put("receiver-ip", ip);
		config.put("receiver-port", port);
		
	}
		
	/**
	 * Adds a key:value pair to the configList at index that 
	 * represents the current node in the WorkSpecification
	 * @param key
	 * @param value
	 */
	public void putConfig(String key, Object value) {
		if (cIdx >= configList.size()) {
			return;
		}
		
		Map<String, Object> config = configList.get(cIdx);
		config.put(key, value);
	}
	
	/**
	 * Utility function to add a key:value pair to the configList 
	 * at the specified index in work specification
	 * @param key
	 * @param value
	 * @param idx
	 */
	public void putConfigAtIndex(String key, Object value, int idx) {
		if (idx >= configList.size() || idx <= 0) {
			return;
		}
		
		Map<String, Object> config = configList.get(idx);
		config.put(key, value);
	}
	
	public void incrementNodeIdx() {
		cIdx++;
	}
	
}
