package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ericsson.research.warp.util.JSON;
import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.mdnsim.nodes.AbstractNode;
import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.SourceReportMessage;
import edu.cmu.util.Utility;

public class SourceNode extends AbstractNode {
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
	private void sendAndReport(String streamId, InetAddress destAddrStr, int destPort, int bytesToTransfer, int rate) {
		WarpThreadPool.executeCached(new SendThread(streamId, destAddrStr, destPort, bytesToTransfer, rate));
	}
	
	public void sendAndReportTest(String streamId, InetAddress destAddrStr, int destPort, int bytesToTransfer, int rate){
		ExecutorService executorService = Executors.newCachedThreadPool();
		executorService.execute(new SendThread(streamId, destAddrStr, destPort, bytesToTransfer, rate));
	}
	
	
	private class SendThread implements Runnable {
		
		private String streamId;
		private InetAddress dstAddrStr;
		private int dstPort;
		private int bytesToTransfer;
		private int rate;

		public SendThread(String streamId, InetAddress dstAddrStr, int dstPort, int bytesToTransfer, int rate) {
			
			SendThread.this.streamId = streamId;
			SendThread.this.dstAddrStr = dstAddrStr;
			SendThread.this.dstPort = dstPort;
			SendThread.this.bytesToTransfer = bytesToTransfer;
			SendThread.this.rate = rate;	
		}
		
		/**
		 * The method will send packet in the following order:
		 * 1. Calculate the packet number per second based on the user specified sending rate.
		 * 2. Calculates the time expected to send one package in millisecond.
		 * 3. Send one packet, if the actual sending time is less than expected time, it will sleep for the gap
		 * 					   else, do nothing. In this case, the use specified rate is higher than the highest rate in real
		 * 
		 */
		@Override
		public void run() {

			double packetPerSecond = rate / STD_DATAGRAM_SIZE;
			long millisecondPerPacket = (long)(1 * edu.cmu.mdnsim.nodes.AbstractNode.MILLISECONDS_PER_SECOND / packetPerSecond); 
			
			DatagramSocket sourceSocket = null;
			try {
				sourceSocket = new DatagramSocket();
			} catch (SocketException socketException) {
				socketException.printStackTrace();
			}
			
			//report();

			
			byte[] buf = null;
			while (bytesToTransfer > 0) {
				
				long begin = System.currentTimeMillis();
				
				buf = new byte[bytesToTransfer <= STD_DATAGRAM_SIZE ? bytesToTransfer : STD_DATAGRAM_SIZE];
				buf[0] = (byte) (bytesToTransfer <= STD_DATAGRAM_SIZE ? 0 : 1);	
	
				DatagramPacket packet = null;
				try {
					packet = new DatagramPacket(buf, buf.length, dstAddrStr, dstPort);
					sourceSocket.send(packet);
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
				bytesToTransfer -= packet.getLength();
				System.out.println("[Source] " + bytesToTransfer + " " + currentTime());
				
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
			System.out.println("[Source] finish sending");
			sourceSocket.close();
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
		
		private void report(){
			System.out.println("[INFO] SourceNode.SendDataThread.run(): " + "Source will start sending data. " + "Record satrt time and report to master");
			SourceReportMessage srcReportMsg = new SourceReportMessage();
			srcReportMsg.setStreamId(streamId);
			srcReportMsg.setTotalBytesTransferred(bytesToTransfer);
			srcReportMsg.setStartTime(Utility.currentTime());	
			String fromPath = "/" + SourceNode.this.getNodeName() + "/ready-send";
			try {
				msgBusClient.sendToMaster(fromPath, "reports", "POST", srcReportMsg);
			} catch (MessageBusException e) {
				e.printStackTrace();
			};
		}
	}


	@Override
	public void executeTask(StreamSpec streamSpec) {
		int flowIndex = -1;
		System.out.println("Sink received a work specification: "+JSON.toJSON(streamSpec));
		for (HashMap<String, String> currentFlow : streamSpec.Flow) {
			flowIndex++;
			if (!currentFlow.get("NodeId").equals(getNodeName()))
				continue;
			else {
				//System.out.println("FOUND ME!! "+currentFlow.get("NodeId"));
				String[] ipAndPort = currentFlow.get("ReceiverIpPort").split(":");
				String destAddrStr = ipAndPort[0];
				int destPort = Integer.parseInt(ipAndPort[1]);
				int dataSize = Integer.parseInt(streamSpec.DataSize);
				int rate = Integer.parseInt(streamSpec.ByteRate);
				try {
					sendAndReport(streamSpec.StreamId, InetAddress.getByName(destAddrStr), destPort, dataSize, rate);
					if (currentFlow.get("UpstreamUri") != null)
						msgBusClient.send("/tasks", currentFlow.get("UpstreamUri")+"/tasks", "PUT", streamSpec);
				} catch (MessageBusException e) {
					e.printStackTrace();
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
				break;
			}
		}
	}	
}
