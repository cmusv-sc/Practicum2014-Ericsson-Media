package edu.cmu.mdnsim.nodes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.ericsson.research.warp.api.message.Message;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.ProcReportMessage;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeRequest;
import edu.cmu.mdnsim.messagebus.message.SinkReportMessage;

public abstract class AbstractNode {
	
	protected MessageBusClient msgBusClient;
	
	String nodeId;
	
	InetAddress hostAddr;
	
	private boolean registered = false;
	
	
	/* This instance variable is used to control whether print out info and report to management layer which is used in unit test. */
	public boolean integratedTest = false;
	
	public static final int MILLISECONDS_PER_SECOND = 1000;
	
	public static final int MAX_WAITING_TIME_IN_MILLISECOND = 5000;
	
	public static final int INTERVAL_IN_MILLISECOND = 1000;
	
	/**
	 * Used for reporting purposes. 
	 * Key = FlowId, Value = UpStreamNodeId
	 */
	Map<String,String> upStreamNodes = new HashMap<String,String>();
	/**
	 * Used for reporting purposes.
	 * Key = FlowId, Value = DownStreamNodeId
	 */
	Map<String,String> downStreamNodes = new HashMap<String,String>();
	
	public AbstractNode() throws UnknownHostException {
		/* 
		 * Note: This may not be sufficient for proper DNS resolution
		 * Refer http://stackoverflow.com/questions/7348711/recommended-way-to-get-hostname-in-java?lq=1
		 */
		hostAddr = java.net.InetAddress.getLocalHost();
	}
	
	public void config(MessageBusClient msgBusClient, String nType, String nodeId) throws MessageBusException {
		this.msgBusClient = msgBusClient;
		this.nodeId = nodeId;
		
		msgBusClient.addMethodListener("/" + getNodeId() + "/tasks", "PUT", this, "executeTask");
		//TODO: The resource names and method need to be properly named 
		msgBusClient.addMethodListener("/" + getNodeId() + "/tasks", "POST", this, "terminateTask");
		
		msgBusClient.addMethodListener("/" + getNodeId() + "/tasks", "DELETE", this, "releaseResource");
		
		msgBusClient.addMethodListener("/" + getNodeId() + "/confirm_node", "PUT", this, "setRegistered");
	}
	
	public void register() {
		RegisterNodeRequest req = new RegisterNodeRequest();
		req.setNodeName(getNodeId());
		req.setURI(msgBusClient.getURI()+"/"+getNodeId());
		try {
			msgBusClient.sendToMaster("/", "/nodes", "PUT", req);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}
	
	public InetAddress getHostAddr() {
		return hostAddr;
	}
	
	public String getNodeId() {
		return nodeId;
	}
	
	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}	

	public synchronized void setRegistered(Message msg) {
		registered = true;
		if (ClusterConfig.DEBUG) {
			System.out.println("AbstractNode.setRegistered(): " + getNodeId() + " successfully registered");
		}
	}
	
	public synchronized boolean isRegistered() {
		return registered;
	}
	
	public void setUnitTest(boolean unitTest){
		this.integratedTest = unitTest;
	}

	/**
	 * 
	 * This methods is to start a stream.
	 * It is supposed to receive/send data and also inform its upstream to 
	 * start appropriate behaviors.
	 * 
	 * @param flow
	 */
	public abstract void executeTask(Flow flow);
	
	/**
	 * 
	 * This method is to stop sending/receiving data at functional node.
	 * It is supposed send a message to inform its upstream to stop
	 * sending data as well.
	 * 
	 * @param flow
	 */
	public abstract void terminateTask(Flow flow);
	
	/**
	 * 
	 * This method is to clean up all resources related to this stream, such 
	 * as Datagram socket. It is supposed to send a message to inform its 
	 * downstream to clean up resources as well.
	 * 
	 * @param flow
	 */
	public abstract void releaseResource(Flow flow);
	
	protected abstract class NodeRunnable implements Runnable {
		
		private String flowId;
		private AtomicInteger totalBytesTransfered = new AtomicInteger(0);
		private AtomicInteger lostPacketNum = new AtomicInteger(0);
		
		private boolean killed = false;
		private boolean stopped = false;
		
		public NodeRunnable(String flowId){
			this.flowId = flowId;
		}
		
		public abstract void run();

		public String getFlowId() {
			return flowId;
		}

		public void setFlowId(String flowId) {
			this.flowId = flowId;
		}
		
		public int getTotalBytesTranfered() {
			return totalBytesTransfered.get();
		}
		
		public void setTotalBytesTranfered(int totalBytesTranfered) {
			this.totalBytesTransfered.set(totalBytesTranfered);
		}

		public synchronized int getLostPacketNum() {
			return lostPacketNum.get();
		}

		public synchronized void setLostPacketNum(int lostPacketNum) {
			this.lostPacketNum.set(lostPacketNum);
		}
		
		public synchronized void kill() {
			killed = true;
		}

		public synchronized boolean isKilled() {
			return killed;
		}

		public synchronized void stop() {
			stopped = true;
		}

		public synchronized boolean isStopped() {
			return stopped;
		}
		
		/**
		 * Package private class could not be accessed by outside subclasses
		 * 
		 *
		 */
		protected class ReportRateRunnable implements Runnable{  
  
			private int  lastRecordedTotalBytes = 0;
			
			private int lastRecordedPacketLost = 0;
			
			// -1 to avoid time difference to be 0 when used as a divider
			private long lastRecordedTime = System.currentTimeMillis() - 1;
			
			private final long startedTime;

			private int intervalInMillisecond;

			public ReportRateRunnable(int intervalInMillisecond){
				this.intervalInMillisecond = intervalInMillisecond;
				startedTime  = System.currentTimeMillis();
			}
			
			/**
			 * Make a while loop as long as the current thread is not interrupted
			 * Call the calculateAndReport method after the while loop to report the status for the last moment
			 * Note, the packet lost in the last moment(several millisecond could be very large, since time spent is short)
			 */
			@Override  
			public void run() {  
				
				while(!Thread.currentThread().isInterrupted()){
					calculateAndReport();
					try {
						Thread.sleep(intervalInMillisecond);
					} catch (InterruptedException e) {
						
						Thread.currentThread().interrupt();
					}
				}
				
				System.out.println("I m interrupted :(");
				calculateAndReport();
			}  
			
			/**
			 *  Calculate the transfer rate and packet lost rate。
			 *  Call the report methods
			 *  Update the last records
			 */
			private void calculateAndReport(){
				long currentTime = System.currentTimeMillis();	
				long timeDiffInMillisecond = currentTime - lastRecordedTime;
				long totalTimeDiffInMillisecond = currentTime - startedTime;
				
				int localToTalBytesTransfered = getTotalBytesTranfered();
				int bytesDiff = localToTalBytesTransfered - lastRecordedTotalBytes;
				long transportationInstantRate = (long)(bytesDiff * 1.0 / timeDiffInMillisecond * 1000) ;
				long transportationAverageRate = (long)(localToTalBytesTransfered * 1.0 / totalTimeDiffInMillisecond * 1000) ;
						
				int localPacketLostNum = getLostPacketNum();
				int lostDiff = localPacketLostNum - lastRecordedPacketLost; 
				long dataLostInstantRate = (long) (lostDiff * NodePacket.PACKET_MAX_LENGTH * 1.0 / timeDiffInMillisecond * 1000);
				long dateLostAverageRate = (long) (localPacketLostNum * NodePacket.PACKET_MAX_LENGTH * 1.0 / totalTimeDiffInMillisecond * 1000);
				
				lastRecordedTotalBytes = localToTalBytesTransfered;
				lastRecordedPacketLost = localPacketLostNum;
				lastRecordedTime = currentTime;
				
				if(startedTime != 0){
					if(!integratedTest){
						reportTransportationRate(transportationAverageRate, transportationInstantRate);
					}
					reportPacketLost(dateLostAverageRate, dataLostInstantRate);
				}
			}
			
			private void reportPacketLost(long packetLostAverageRate, long dataLostInstantRate){
				System.out.println("[Packet Lost] " + packetLostAverageRate + " " + dataLostInstantRate);
			}
			
			private void reportTransportationRate(long averageRate, long instantRate){
				System.out.println("[RATE]" + " " + averageRate + " " + instantRate);
				NodeType nodeType = getNodeType();
				System.out.println("NodeType:" + nodeType);
				String fromPath = AbstractNode.this.getNodeId() + "/progress-report";
				if (nodeType == NodeType.SINK) {
					SinkReportMessage msg = new SinkReportMessage();
					msg.setEventType(EventType.PROGRESS_REPORT);
					msg.setFlowId(NodeRunnable.this.flowId);
					msg.setDestinationNodeId(getUpStreamId());
					msg.setAverageRate("" + averageRate);
					msg.setCurrentRate("" + instantRate);
					try {
						AbstractNode.this.msgBusClient.sendToMaster(fromPath, "/sink_report", "POST", msg);
					} catch (MessageBusException e) {
						e.printStackTrace();
					} 
				} else if (nodeType == NodeType.PROC) {
					ProcReportMessage msg = new ProcReportMessage();
					msg.setEventType(EventType.PROGRESS_REPORT);
					msg.setFlowId(NodeRunnable.this.flowId);
					msg.setDestinationNodeId(getUpStreamId());
					msg.setAverageRate("" + averageRate);
					msg.setCurrentRate("" + instantRate);
					try {
						AbstractNode.this.msgBusClient.sendToMaster(fromPath, "/processing_report", "POST", msg);
					} catch (MessageBusException e) {
						e.printStackTrace();
					} 
				}
			}
			
			//TODO: Add Node Type instead of using the parser
			private NodeType getNodeType() {
				String nodeTypeStr = AbstractNode.this.nodeId.split(":")[1];
				nodeTypeStr = nodeTypeStr.substring(0, nodeTypeStr.length() - 1);
				if (nodeTypeStr.toLowerCase().equals("sink")) {
					return NodeType.SINK;
				} else if (nodeTypeStr.toLowerCase().equals("processing")) {
					return NodeType.PROC;
				} else if (nodeTypeStr.toLowerCase().equals("source")) {
					return NodeType.SOURCE;
				} else {
					return NodeType.UNDEF;
				}
			}
			
			private String getUpStreamId() {
				String nodeIdStr = AbstractNode.this.nodeId;
				String[] nodeIds = NodeRunnable.this.flowId.split("-");
				for (int i = 1; i < nodeIds.length; i++) {
					if (nodeIdStr.equals(nodeIds[i])) {
						if (i < nodeIds.length - 1) {
							return nodeIds[i + 1];
						}
					}
				}
				throw new RuntimeException("Cannot find the upstream ID");
			}
		} 
	}

}
