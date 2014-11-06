package edu.cmu.mdnsim.nodes;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import com.ericsson.research.warp.api.message.Message;

import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeRequest;
import edu.cmu.mdnsim.messagebus.message.StopSimulationRequest;

public abstract class AbstractNode {
	
	protected MessageBusClient msgBusClient;
	
	protected String nodeName;
	
	private InetAddress hostAddr;
	
	private boolean registered = false;
	
	
	/* This instance variable is used to control whether print out info and report to management layer which is used in unit test. */
	protected boolean unitTest = false;
	
	protected HashMap<String, DatagramSocket> streamIdToSocketMap;
	
	public static final int MILLISECONDS_PER_SECOND = 1000;
	
	public static final int MAX_WAITING_TIME_IN_MILLISECOND = 5000;
	
	private static final int RETRY_CREATING_SOCKET_NUMBER = 3;
	
	/**
	 * Used for reporting purposes. 
	 * Key = Stream Id, Value = UpStreamNodeId
	 */
	protected Map<String,String> upStreamNodes = new HashMap<String,String>();
	/**
	 * Used for reporting purposes.
	 * Key = Stream Id, Value = DownStreamNodeId
	 */
	protected Map<String,String> downStreamNodes = new HashMap<String,String>();
	
	public AbstractNode() throws UnknownHostException {
		/* 
		 * Note: This may not be sufficient for proper DNS resolution
		 * Refer http://stackoverflow.com/questions/7348711/recommended-way-to-get-hostname-in-java?lq=1
		 */
		hostAddr = java.net.InetAddress.getLocalHost();
		
		streamIdToSocketMap = new HashMap<String, DatagramSocket>();
	}
	
	public void config(MessageBusClient msgBusClient, NodeType nType, String nName) throws MessageBusException {
		this.msgBusClient = msgBusClient;
		nodeName = nName;
		
		msgBusClient.addMethodListener("/" + getNodeName() + "/tasks", "PUT", this, "executeTask");
		//TODO: The resource names and method need to be properly named 
		msgBusClient.addMethodListener("/" + getNodeName() + "/tasks", "POST", this, "terminateTask");
		
		msgBusClient.addMethodListener("/" + getNodeName() + "/tasks", "DELETE", this, "releaseResource");
		
		msgBusClient.addMethodListener("/" + getNodeName() + "/confirm_node", "PUT", this, "setRegistered");
	}
	
	public void register() {
		RegisterNodeRequest req = new RegisterNodeRequest();
		req.setNodeName(getNodeName());
		req.setURI(msgBusClient.getURI()+"/"+getNodeName());
		try {
			msgBusClient.sendToMaster("/", "/nodes", "PUT", req);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}
	
	public InetAddress getHostAddr() {
		return hostAddr;
	}
	
	public String getNodeName() {
		return nodeName;
	}
	
	public void setNodeName(String name) {
		this.nodeName = name;
	}	

	public String currentTime(){
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US);
		Date date = new Date();
		return dateFormat.format(date);
	}

	public synchronized void setRegistered(Message msg) {
		registered = true;
		if (ClusterConfig.DEBUG) {
			System.out.println("AbstractNode.setRegistered(): " + getNodeName() + " successfully registered");
		}
	}
	
	public synchronized boolean isRegistered() {
		return registered;
	}
	
	public void setUnitTest(boolean unitTest){
		this.unitTest = unitTest;
	}

	/**
	 * 
	 * This methods is to start a stream.
	 * It is supposed to receive/send data and also inform its upstream to 
	 * start appropriate behaviors.
	 * 
	 * @param s
	 */
	public abstract void executeTask(StreamSpec s);
	
	/**
	 * 
	 * This method is to stop sending/receiving data at functional node.
	 * It is supposed send a message to inform its upstream to stop
	 * sending data as well.
	 * 
	 * @param streamSpec
	 */
	public abstract void terminateTask(StreamSpec streamSpec);
	
	/**
	 * 
	 * This method is to clean up all resources related to this stream, such 
	 * as Datagram socket. It is supposed to send a message to inform its 
	 * downstream to clean up resources as well.
	 * 
	 * @param streamSpec
	 */
	public abstract void releaseResource(StreamSpec streamSpec);
	
	/**
	 * Creates a DatagramSocket and binds it to any available port
	 * The streamId and the DatagramSocket are added to a 
	 * HashMap<streamId, DatagramSocket> in the MdnSinkNode object
	 * 
	 * @param streamId
	 * @return port number to which the DatagramSocket is bound to
	 * -1 if DatagramSocket creation failed
	 * 0 if DatagramSocket is created but is not bound to any port
	 */

	public int bindAvailablePortToStream(String streamId) {

		if (streamIdToSocketMap.containsKey(streamId)) {
			// TODO handle potential error condition. We may consider throw this exception
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] SinkeNode.bindAvailablePortToStream():" + "[Exception]Attempt to add a socket mapping to existing stream!");
			}
			return streamIdToSocketMap.get(streamId).getPort();
		} else {
			
			DatagramSocket udpSocket = null;
			for(int i = 0; i < RETRY_CREATING_SOCKET_NUMBER; i++){
				try {
					udpSocket = new DatagramSocket(0, getHostAddr());
				} catch (SocketException e) {
					if (ClusterConfig.DEBUG) {
						System.out.println("Failed" + (i + 1) + "times to bind a port to a socket");
					}
					e.printStackTrace();
					continue;
				}
				break;
			}
			
			if(udpSocket == null){
				return -1;
			}
			
			streamIdToSocketMap.put(streamId, udpSocket);
			return udpSocket.getLocalPort();
		}
	}
	
	protected class NodeRunnable implements Runnable {
		
		protected String streamId;
		
		protected long startedTime = 0;
		protected int totalBytes = 0;
		protected Semaphore totalBytesSemaphore = new Semaphore(1);
		
		protected boolean killed = false;
		protected boolean stopped = false;
		
		public NodeRunnable(String streamId){
			this.streamId = streamId;
			
			ReportTransportationRateTask task = new ReportTransportationRateTask();
			Timer timer = new Timer();
			timer.schedule(task, 0, 1000);
		}
		
		public void run(){
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
		
		protected class ReportTransportationRateTask extends TimerTask{  
			  
			protected int lastRecordedTotalBytes = 0;
			// -1 to avoid time difference to be 0 when used as a divider
			protected long lastRecordedTime = System.currentTimeMillis() - 1;

			@Override  
			public void run() {  
				long currentTime = System.currentTimeMillis();
				
				try {
					totalBytesSemaphore.acquire();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				int localTotalBytes = totalBytes;
				totalBytesSemaphore.release();
				
				long timeDiffInMillisecond = currentTime - lastRecordedTime;
				int bytesDiff = localTotalBytes - lastRecordedTotalBytes;
				long instantRate = (long)(bytesDiff * 1.0 / timeDiffInMillisecond * 1000) ;
				
				long totalTimeDiffInMillisecond = currentTime - startedTime;
				long totalRate = (long)(localTotalBytes * 1.0 / totalTimeDiffInMillisecond * 1000) ;
						
				lastRecordedTotalBytes = localTotalBytes;
				lastRecordedTime = currentTime;
				
				if(startedTime != 0){
					reportTransportationRate(totalRate, instantRate);
				}				
			}  
			
			private void reportTransportationRate(long totalRate, long instantRate){
				System.out.println("[RATE]" + totalRate + " " + instantRate);
			}
		} 
	}

}
