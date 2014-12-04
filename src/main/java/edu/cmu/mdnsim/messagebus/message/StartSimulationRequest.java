package edu.cmu.mdnsim.messagebus.message;

/**
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class StartSimulationRequest extends MbMessage {
	
	private String _sourceNodeName;
	private String _sinkNodeName;
	private int _dataSize;
	private int _streamRate;
	private String _streamID;
	
	public StartSimulationRequest() {
		_sourceNodeName = "";
		_sinkNodeName = "";
	}
	
	public StartSimulationRequest (String sourceNodeName, String sinkNodeName) {
		_sourceNodeName = sourceNodeName;
		_sinkNodeName = sinkNodeName;
	}
	
	public String getSourceNodeName() {
		return _sourceNodeName;
	}
	
	public String getSinkNodeName() {
		return _sinkNodeName;
	}
	
	public int getDataSize() {
		return _dataSize;
	}
	
	public int getStreamRate() {
		return _streamRate;
	}
	
	public String getStreamID() {
		return _streamID;
	}
	
	public void setSourceNodeName(String sourceNodeName) {
		_sourceNodeName = sourceNodeName;
	}
	
	public void setSinkNodeName(String sinkNodeName) {
		_sinkNodeName = sinkNodeName;
	}
	
	public void setDataSize(int dataSize) {
		_dataSize = dataSize;
	}
	
	public void setStreamRate(int streamRate) {
		_streamRate = streamRate;
	}
	
	public void setStreamID(String streamID) {
		_streamID = streamID;
	}
	
}
