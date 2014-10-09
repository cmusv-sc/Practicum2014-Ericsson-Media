package edu.cmu.messagebus;

import edu.cmu.messagebus.message.SinkReportMessage;
import edu.cmu.messagebus.message.SourceReportMessage;

public interface NodeContainer {
	
	public void sinkReport(SinkReportMessage sinkRepMsg);
	
	public void sourceReport(SourceReportMessage srcRepMsg);
	
}
