package edu.cmu.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;

import edu.cmu.messagebus.MDNSink;
import edu.cmu.messagebus.message.SinkReportMessage;

public class MdnSinkNode extends MdnAbstractNode {
	
	HashMap<String, DatagramSocket> streamSocketMap;
	
	public MdnSinkNode() {
		super();
		streamSocketMap = new HashMap<String, DatagramSocket>(); 
	}
	
	/**
	 * Creates a DatagramSocket and binds it to any available port
	 * The streamId and the DatagramSocket are added to a 
	 * HashMap<streamId, DatagramSocket> in the MdnSinkNode object
	 * @param streamId
	 * @return port number to which the DatagramSocket is bound to
	 * -1 if DatagramSocket creation failed
	 * 0 if DatagramSocket is created but is not bound to any port
	 */
	public int bindAvailablePortToStream(String streamId) {
		int port = 0;
		try {
			InetAddress laddr = InetAddress.getByName(hostAddr);
			DatagramSocket ds = new DatagramSocket(0, laddr);
			port = ds.getLocalPort();
			if (port < 0) {
				System.out.println("No way! the DatagramSocket seems to be closed");
			} else if (port == 0) {
				System.out.println("Socket is not bound to a port. Closing the socket");
				ds.close();
			} else {
				if (streamSocketMap.put(streamId, ds) != null) {
					// TODO handle potential error condition
					System.out.println("Attempt to add a socket mapping "
							+ "to existing stream!");
				}
			}
			return port;
		} catch (SocketException se) {
			se.printStackTrace();
		} catch (UnknownHostException uhe) {
			uhe.printStackTrace();
		}
		return port;
	}
	
	/**
	 * This function runs as a separate thread from the WarpThreadPool, 
	 * so that a client can request multiple streams at the same time.
	 * Each stream is received in a separate WarpPoolThread.
	 * Receives datagram packets from the source and reports
	 * the total time and total number of bytes received by the 
	 * sink node back to the master using the MDNSink object.
	 * @param streamId
	 * @param mdnSink
	 */
	public void receiveAndReport(String streamId, MDNSink mdnSink) {
		
		boolean started = false;
		long startTime = (long) 0;
		long totalTime = (long) 0;
		int totalBytes = 0;
		
		DatagramSocket socket;
		if ((socket = streamSocketMap.get(streamId)) == null) {
			// TODO handle potential error condition
			System.out.println("Attempt to receive data for non existent stream");
			return;
		}
		
		byte[] buf = new byte[STD_DATAGRAM_SIZE]; 
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		
		while (true) {
			try {
				socket.receive(packet);
				if(!started) {
					startTime = System.currentTimeMillis();
					started = true;
				}
				totalBytes += packet.getLength();
				
				if (packet.getData()[0] == 0) {
					totalTime = System.currentTimeMillis() - startTime;
					//TODO report the time taken and total bytes received 
					SinkReportMessage sinkReportMsg = new SinkReportMessage();
					sinkReportMsg.setStreamId(streamId);
					sinkReportMsg.setTotalBytes(totalBytes);
					sinkReportMsg.setTotalTime(totalTime);
					mdnSink.sinkReport(sinkReportMsg);
					System.out.println("Sink finished receiving data.. StreamId "+sinkReportMsg.getStreamId()+
							" Total bytes "+sinkReportMsg.getTotalBytes()+ " Total Time "+sinkReportMsg.getTotalTime());
					// cleanup resources
					socket.close();
					streamSocketMap.remove(streamId);
					
					break;
				}
				
			} catch (IOException ioe) {
				// TODO Handle error condition
				ioe.printStackTrace();
			}
		}
		
	}
	
}
