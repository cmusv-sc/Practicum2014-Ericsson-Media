package edu.cmu.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.messagebus.NodeContainer;
import edu.cmu.messagebus.message.SinkReportMessage;

public class SinkNodeImp extends MdnAbstractNode implements SinkNode{
	
	private HashMap<String, DatagramSocket> _streamSocketMap;
	private int _threadNum = 10;
	
	
	public SinkNodeImp() {
		super();
		_streamSocketMap = new HashMap<String, DatagramSocket>(); 
	}
	
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

		InetAddress laddr = null;
		try {
			laddr = InetAddress.getByName(hostAddr);
		} catch (UnknownHostException e) {
			//TODO: Handle the exception. We may consider throw this exception
			e.printStackTrace();
		}
		
		DatagramSocket udpSocekt = null;
		try {
			udpSocekt = new DatagramSocket(0, laddr);
		} catch (SocketException e) {
			//TODO: Handle the exception. We may consider throw this exception
			e.printStackTrace();
		}

		
		if (_streamSocketMap.containsKey(streamId)) {
			// TODO handle potential error condition. We may consider throw this exception
			System.out.println("Attempt to add a socket mapping "
					+ "to existing stream!");
			return _streamSocketMap.get(streamId).getPort();
		} else {
			_streamSocketMap.put(streamId, udpSocekt);
			return udpSocekt.getLocalPort();
		}
		
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
	
	public void receiveAndReport(String streamId, NodeContainer mdnSink) {
		
		//TODO: Do i need to collect the thread
		WarpThreadPool.executeCached(new ReceiveDataThread(streamId, mdnSink));
		
	}

	private class ReceiveDataThread implements Runnable {
		
		private String _streamId;
		private NodeContainer _mdnSink;
		
		public ReceiveDataThread(String streamId, NodeContainer mdnSink) {
			_streamId = streamId;
			_mdnSink = mdnSink;
		}
		
		@Override
		public void run() {
			
			boolean started = false;
			long startTime = (long) 0;
			long totalTime = (long) 0;
			int totalBytes = 0;
			
			DatagramSocket socket;
			if ((socket = _streamSocketMap.get(_streamId)) == null) {
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
						sinkReportMsg.setStreamId(_streamId);
						sinkReportMsg.setTotalBytes(totalBytes);
						sinkReportMsg.setEndTime(this.currentTime());
						//sinkReportMsg.setTotalTime(totalTime);
						_mdnSink.sinkReport(sinkReportMsg);
						System.out.println("Sink finished receiving data.. StreamId "+sinkReportMsg.getStreamId()+
								" Total bytes "+sinkReportMsg.getTotalBytes()+ " Total Time "+totalTime);
						// cleanup resources
						socket.close();
						_streamSocketMap.remove(_streamId);
						
						break;
					}
					
				} catch (IOException ioe) {
					// TODO Handle error condition
					ioe.printStackTrace();
				}
			}
			
		}
		
		private String currentTime(){
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US);
			Date date = new Date();
			return dateFormat.format(date);
		}
		
	}
	
}
