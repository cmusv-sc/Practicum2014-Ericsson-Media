package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ericsson.research.warp.util.JSON;
import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.SourceReportMessage;
import edu.cmu.util.Utility;

public class SourceNode extends AbstractNode {
	
	Map<String, SendDataThread> runnableMap = new ConcurrentHashMap<String, SendDataThread>();
	
	public SourceNode() throws UnknownHostException {
		super();
	}
	
	@Override
	public void config(MessageBusClient msgBus, NodeType nType, String nName) throws MessageBusException {
		
		super.config(msgBus, nType, nName);
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SourceNode.config(): Subclass config() "
					+ "has been called.");
		}
		
		msgBusClient.addMethodListener("/" + getNodeName() + "tasks", "POST", this, "suspendTask");
		msgBusClient.addMethodListener("/" + getNodeName() + "tasks", "POST", this, "resumeTask");
		
	}

	
	@Override
	public void executeTask(StreamSpec s) {
		int flowIndex = -1;
		System.out.println("Source received a work specification: "+JSON.toJSON(s));
		for (HashMap<String, String> currentFlow : s.Flow) {
			flowIndex++;
			if (!currentFlow.get("NodeId").equals(getNodeName()))
				continue;
			else {
				//System.out.println("FOUND ME!! "+currentFlow.get("NodeId"));
				String[] ipAndPort = currentFlow.get("ReceiverIpPort").split(":");
				String destAddrStr = ipAndPort[0];
				int destPort = Integer.parseInt(ipAndPort[1]);
				int dataSize = Integer.parseInt(s.DataSize);
				int rate = Integer.parseInt(s.ByteRate);
				sendAndReport(s.StreamId, destAddrStr, destPort, 
						dataSize, rate);
				try {
					if (currentFlow.get("UpstreamUri") != null)
						msgBusClient.send("/tasks", currentFlow.get("UpstreamUri")+"/tasks", "PUT", s);
				} catch (MessageBusException e) {
					//TODO: add exception handler
					e.printStackTrace();
				}
				break;
			}
		}
	}
	
	public void suspendTask(String streamId) {
		SendDataThread objectiveRunnable = runnableMap.get(streamId);
		objectiveRunnable.suspend();
	}
	
	public void resumeTask(String streamId) {
		SendDataThread sendDataRunnable = runnableMap.get(streamId);
		sendDataRunnable.resume();
		while (!sendDataRunnable.isStopped());
		WarpThreadPool.executeCached(sendDataRunnable);
	}
	
	/**
	 * 
	 * Starts to send data to specified destination.
	 * 
	 * @param streamId The stream id associated with this stream
	 * @param destAddrStr The destination address
	 * @param destPort The destination port
	 * @param bytesToTransfer Total bytes to be sent
	 * @param rate The rate to send data
	 * 
	 */
	private void sendAndReport(String streamId, String destAddrStr, 
			int destPort, int bytesToTransfer, int rate) {
		
		SendDataThread newThread = new SendDataThread(streamId, destAddrStr, 
				destPort, bytesToTransfer, rate, super.msgBusClient);
		
		synchronized(runnableMap) {
			runnableMap.put(streamId, newThread);
		}
		
		WarpThreadPool.executeCached(newThread);

	}
	
	private class SendDataThread implements Runnable {
		
		private String streamId;
		private String dstAddrStr;
		private int dstPort;
		private int bytesToTransfer;
		private int rate;
		private MessageBusClient msgBusClient;
		
		/* suspended variable is used to indicate whether outside sends a suspend cmd */
		private boolean suspended;
		/* stopped variable is used to indicate whether the runnable has stopped*/
		private boolean stopped;
		
		public SendDataThread(String streamId, String dstAddrStr, 
				int dstPort, int bytesToTransfer, int rate, 
				MessageBusClient msgBusClient) {
			
			SendDataThread.this.streamId = streamId;
			SendDataThread.this.dstAddrStr = dstAddrStr;
			SendDataThread.this.dstPort = dstPort;
			SendDataThread.this.bytesToTransfer = bytesToTransfer;
			SendDataThread.this.rate = rate;
			SendDataThread.this.msgBusClient = msgBusClient;
			
		}
		
		/**
		 * The method will calculate the packet number expected to be sent based on the user specified sending rate.
		 * Then it calculates the time expected to send a package in millisecond.
		 * 
		 * 
		 */
		@Override
		public void run() {
			
			restart();
			double packetPerSecond = rate / STD_DATAGRAM_SIZE;
			long millisecondPerPacket = (long)(1 * 1000 / packetPerSecond); 
			
			DatagramSocket sourceSocket = null;
			try {
				sourceSocket = new DatagramSocket(0, getHostAddr());
			} catch (SocketException se) {
				se.printStackTrace();
			}
			
			System.out.println("[INFO] SourceNode.SendDataThread.run(): " + "Source will start sending data. "
					+ "Record satrt time and report to master");
			
			SourceReportMessage srcReportMsg = new SourceReportMessage();
			srcReportMsg.setStreamId(streamId);
			srcReportMsg.setTotalBytes_transferred(bytesToTransfer);
			srcReportMsg.setStartTime(Utility.currentTime());	
			String fromPath = "/" + SourceNode.this.getNodeName() + "/ready-send";
			try {
				msgBusClient.sendToMaster(fromPath, "/source_report", "POST", srcReportMsg);
			} catch (MessageBusException e) {
				e.printStackTrace();
			};
			
			byte[] buf = null;
			while (bytesToTransfer > 0 && !isSuspended()) {
				
				long begin = System.currentTimeMillis();
				
				buf = new byte[bytesToTransfer <= STD_DATAGRAM_SIZE ? bytesToTransfer : STD_DATAGRAM_SIZE];
				buf[0] = (byte) (bytesToTransfer <= STD_DATAGRAM_SIZE ? 0 : 1);	
	
				DatagramPacket packet = null;
				try {
					packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(dstAddrStr), dstPort);
					sourceSocket.send(packet);
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
				bytesToTransfer -= packet.getLength();
				
				long end = System.currentTimeMillis();
				long millisRemaining = millisecondPerPacket - (end - begin);
				if (millisRemaining > 0) {
					try {
						Thread.sleep(millisRemaining);
					} catch (InterruptedException ie) {
						ie.printStackTrace();
					}
				}
			} 
			sourceSocket.close();
			stop();
			if (ClusterConfig.DEBUG) {
				if (bytesToTransfer <= 0) {
					System.out.println("[DEBUG]SourceNode.SendDataThread.run():"
							+ " This thread has been stopped(not finished yet).");
				} else {
					System.out.println("[DEBUG]SourceNode.SendDataThread.run():"
							+ " This thread hass benn terminated(finished).");
				}
			} 
		}
		
		/**
		 * 
		 * This method is used to send suspend signal to a thread.
		 * 
		 */
		public synchronized void suspend() {
			suspended = true;
		}
		
		/**
		 * This method is to mark the suspended variable in SendDataThread 
		 * runnable as false to indicate that outside user allows it to resume
		 * to execute.
		 */
		private synchronized void resume() {
			suspended = false;
		}
		
		private synchronized boolean isSuspended() {
			return suspended;
		}
		
		/**
		 * This method is called in SendDataThread.run() to show that current
		 * thread has been stopped as all context of the thread has been frozen,
		 * and next thread can restart the current thread.
		 * 
		 */
		private synchronized void stop() {
			stopped = true;
		}
		
		/**
		 * This method is called in SendDataThread.run() to show that current
		 * thread is running.
		 */
		private synchronized void restart() {
			stopped = false;
		}
		
		/**
		 * This method is to test whether the SendDataThread has been stopped
		 * which means all of the context has been protected.
		 * 
		 * @return 
		 */
		public synchronized boolean isStopped() {
			return stopped;
		}
	}	
}
