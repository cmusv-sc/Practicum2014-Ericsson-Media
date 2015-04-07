package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Future;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.StreamReportMessage;
import edu.cmu.mdnsim.reporting.CPUUsageTracker;
import edu.cmu.mdnsim.reporting.MemUsageTracker;
import edu.cmu.mdnsim.reporting.NodeReporter;
import edu.cmu.mdnsim.reporting.NodeReporter.NodeReporterBuilder;
import edu.cmu.mdnsim.reporting.PacketLatencyTracker;
import edu.cmu.mdnsim.reporting.PacketLostTracker;

/**
 * A runnable that is to process a stream for a SinkNode.
 * <p>Each flow is received in a separate WarpPoolThread.
 * After receiving all packets from the source, this thread 
 * reports the total time and total number of bytes received by the 
 * sink node back to the master using the message bus.
 * 
 * @param streamId The flowId is bind to a socket and stored in the map
 * @param msgBus The message bus used to report to the master
 * 
 */
class SinkRunnable extends NodeRunnable {

	private DatagramSocket receiveSocket;
	private DatagramPacket packet;
	/**
	 * The flow for which the Sink is receiving data. 
	 * Each Sink Node will have only 1 flow associated with 1 stream.
	 */
	private Flow flow;
	public SinkRunnable(Stream stream, Flow flow, MessageBusClient msgBusClient, String nodeId, NodeRunnableCleaner cleaner, DatagramSocket receiveSocket) {
		super(stream, msgBusClient, nodeId, cleaner);
		this.flow = flow;
		this.receiveSocket = receiveSocket;
	}

	@Override
	public void run() {
		
		if(!initializeSocketAndPacket()){
			return;
		}
		
		PacketLostTracker packetLostTracker = null;
		PacketLatencyTracker packetLatencyTracker = null;
		
		boolean isFinalWait = false;			
		ReportTaskHandler reportTaksHandler = null;

		while (!isKilled()) {
			try{
				receiveSocket.receive(packet);
				
			} catch(SocketTimeoutException ste){
				if(this.isUpstreamDone()){
					if(!isFinalWait){
						isFinalWait = true;
						continue;
					}else{
						break;		
					}
				}	

				continue;
			} catch (IOException e) {
				logger.error(e.toString());
				break;
			}

			setTotalBytesTranfered(getTotalBytesTranfered() + packet.getLength());

			NodePacket nodePacket = new NodePacket(packet.getData());


			if(reportTaksHandler == null){
				
				int windowSize = PacketLostTracker.calculateWindowSize(Integer.parseInt(this.getStream().getKiloBitRate()), TIMEOUT_FOR_PACKET_LOSS, NodePacket.MAX_PACKET_LENGTH);
				
				CPUUsageTracker cpuTracker = new CPUUsageTracker();
				MemUsageTracker memTracker = new MemUsageTracker();
				
				packetLostTracker = new PacketLostTracker(windowSize);
				packetLatencyTracker = new PacketLatencyTracker();
				
				reportTaksHandler = createAndLaunchReportTransportationRateRunnable(cpuTracker, memTracker, packetLostTracker, packetLatencyTracker);					
				StreamReportMessage streamReportMessage = 
						new StreamReportMessage.Builder(EventType.RECEIVE_START, this.getUpStreamId(), "N/A", "N/A")
												.flowId(flow.getFlowId())
												.build();
				streamReportMessage.from(this.getNodeId());
				this.sendStreamReport(streamReportMessage);
				
			}
			
			packetLostTracker.updatePacketLost(nodePacket.getMessageId());
			packetLatencyTracker.newPacket(nodePacket);
			
			if(nodePacket.isLast()){
				super.setUpstreamDone();
				break;
			}
			
		}	

		/*
		 * The reportTask might be null when the NodeRunnable thread is 
		 * killed before enters the while loop.
		 * 
		 */
		if(reportTaksHandler != null){

			reportTaksHandler.kill();

			/*
			 * Wait for reportTask completely finished.
			 */

			while (!reportTaksHandler.isDone());
		}

		/*
		 * No mater what final state is, the NodeRunnable should always
		 * report to Master that it is going to end.
		 * 
		 */
		StreamReportMessage streamReportMessage = 
				new StreamReportMessage.Builder(EventType.RECEIVE_END, this.getUpStreamId(), "N/A", "N/A")
		.flowId(flow.getFlowId())
		.totalBytesTransferred(this.getTotalBytesTranfered())
		.build();
		streamReportMessage.from(this.getNodeId());
		this.sendStreamReport(streamReportMessage);

		if (isUpstreamDone()) { //Simulation completes as informed by upstream.

			clean();

			if (ClusterConfig.DEBUG) {

				System.out.println("[DEBUG]SinkNode.SinkRunnable.run():" + " This thread has finished.");

			}
		} else if (isReset()) {

			clean();

			if (ClusterConfig.DEBUG) {

				System.out.println("[DEBUG]SinkNode.SinkRunnable.run():" + " This thread has been reset.");

			}

		} else {
			if (ClusterConfig.DEBUG) {

				System.out.println("[DEBUG]SinkNode.SinkRunnable.run():" + " This thread has been killed.");

			}
		}
		if (ClusterConfig.DEBUG) {
			if (isKilled()) {
				System.out.println("[DEBUG]SinkNode.ReceiveThread.run(): Killed.");
			} else {
				System.out.println("[DEBUG]SinkNode.ReceiveThread.run(): Finish receiving.");
			}
		}

	}


	/**
	 * Initializes the receive socket and the DatagramPacket 
	 * @return true, successfully done
	 * 		   false, failed in some part
	 */
	private boolean initializeSocketAndPacket(){
		
		try {
			receiveSocket.setSoTimeout(TIMEOUT_FOR_PACKET_LOSS * 1000);
		} catch (SocketException e1) {
			return false;
		}

		byte[] buf = new byte[NodePacket.MAX_PACKET_LENGTH]; 
		packet = new DatagramPacket(buf, buf.length);

		return true;
	}


	/**
	 * Creates and Launches a thread to report to the management layer.
	 * @return Future of the report thread
	 */

	private ReportTaskHandler createAndLaunchReportTransportationRateRunnable(CPUUsageTracker cpuTracker, MemUsageTracker memTracker, PacketLostTracker packetLostTracker, PacketLatencyTracker packetLatencyTracker){	

		NodeReporter reportThread = new NodeReporterBuilder(INTERVAL_IN_MILLISECOND, this, cpuTracker, memTracker).packetLostTracker(packetLostTracker).packetLatencyTracker(packetLatencyTracker).build();
		Future<?> reportFuture = NodeContainer.ThreadPool.submit(new MDNTask(reportThread));
		return new ReportTaskHandler(reportFuture, reportThread);
	}

	void clean() {
		if(receiveSocket != null && !receiveSocket.isClosed()){
			receiveSocket.close();
		}

		cleaner.removeNodeRunnable(getStreamId());
	}

	@Override
	protected void sendEndMessageToDownstream() {
	}

}
