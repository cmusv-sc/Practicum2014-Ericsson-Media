package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;

import com.ericsson.research.warp.util.JSON;
import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.SourceReportMessage;
import edu.cmu.mdnsim.messagebus.test.WorkSpecification;
import edu.cmu.util.Utility;

public class SourceNode extends AbstractNode {
	
	public SourceNode() throws UnknownHostException {
		super();
	}
	
	@Override
	public void config(MessageBusClient msgBus, NodeType nType, String nName) throws MessageBusException {
		super.config(msgBus, nType, nName);
	}

	
	@Override
	public void executeTask(WorkConfig wc) {
		int flowIndex = -1;
		System.out.println("Sink received a work specification: "+JSON.toJSON(wc));
		for (StreamSpec s : wc.getStreamSpecList()) {
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
							msgBusClient.send("/tasks", currentFlow.get("UpstreamUri")+"/tasks", "PUT", wc);
					} catch (MessageBusException e) {
						//TODO: add exception handler
						e.printStackTrace();
					}
					break;
				}
			}
		}				
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
		
		WarpThreadPool.executeCached(new SendDataThread(streamId, destAddrStr, 
			destPort, bytesToTransfer, rate, super.msgBusClient));

	}
	
	private class SendDataThread implements Runnable {
		
		private String streamId;
		private String dstAddrStr;
		private int dstPort;
		private int bytesToTransfer;
		private int rate;
		private MessageBusClient msgBusClient;
		
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
		
		@Override
		public void run() {
			double packetPerSecond = rate / STD_DATAGRAM_SIZE;
			long millisecondPerPacket = (long)(1 / packetPerSecond) * 1000;
			int packetsThisSec = 0;
			long begin = 0;
			System.out.println("Packets per second is "+packetPerSecond);
			
			DatagramSocket sourceSocket = null;
			try {
				InetAddress hostAddr = java.net.InetAddress.getLocalHost();
				sourceSocket = new DatagramSocket(0, hostAddr);
			} catch (UnknownHostException uhe) {
				uhe.printStackTrace();
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
			while (bytesToTransfer > 0) {
				if (begin == 0 )
					begin = System.currentTimeMillis();
				
				buf = new byte[bytesToTransfer <= STD_DATAGRAM_SIZE ? bytesToTransfer : STD_DATAGRAM_SIZE];
				buf[0] = (byte) (bytesToTransfer <= STD_DATAGRAM_SIZE ? 0 : 1);			
				DatagramPacket packet = null;
				try {
					packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(dstAddrStr), dstPort);
					sourceSocket.send(packet);
					packetsThisSec++;
					if (packetsThisSec >= packetPerSecond) {
						long end = System.currentTimeMillis();
						long millisSpent = (end - begin);
						if (millisSpent > 0) {
							try {
								Thread.sleep(1000 - millisSpent);
								System.out.println(packetPerSecond+ " packets sent. woke up after sleeping for "+millisSpent);
							} catch (InterruptedException ie) {
								ie.printStackTrace();
							}
						}
						packetsThisSec = 0;
						begin = 0;
					}
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
				bytesToTransfer -= packet.getLength();
				
//				long end = System.currentTimeMillis();
//				long millisRemaining = millisecondPerPacket - (end - begin);
//				if (millisRemaining > 0) {
//					try {
//						Thread.sleep(millisRemaining);
//					} catch (InterruptedException ie) {
//						ie.printStackTrace();
//					}
//				}
			} 
			sourceSocket.close();
		}
	}

	
}
