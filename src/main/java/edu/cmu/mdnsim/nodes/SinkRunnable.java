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
import edu.cmu.mdnsim.reporting.PacketLostTracker;

/**
 * 
 * Each flow is received in a separate WarpPoolThread.
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

		//			long startedTime = 0;
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
				//					startedTime = System.currentTimeMillis();
				packetLostTracker = new PacketLostTracker(Integer.parseInt(this.getStream().getDataSize()),
						Integer.parseInt(this.getStream().getKiloBitRate()),
						NodePacket.MAX_PACKET_LENGTH, MAX_WAITING_TIME_IN_MILLISECOND, nodePacket.getMessageId());
				reportTaksHandler = createAndLaunchReportTransportationRateRunnable(packetLostTracker);					
				StreamReportMessage streamReportMessage = 
						new StreamReportMessage.Builder(EventType.RECEIVE_START, this.getUpStreamId())
												.flowId(flow.getFlowId())
												.build();
				this.sendStreamReport(streamReportMessage);
				
			}
			packetLostTracker.updatePacketLost(nodePacket.getMessageId());

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
				new StreamReportMessage.Builder(EventType.RECEIVE_END, this.getUpStreamId())
		.flowId(flow.getFlowId())
		.totalBytesTransferred(this.getTotalBytesTranfered())
		.build();
		this.sendStreamReport(streamReportMessage);

		packetLostTracker.updatePacketLostForLastTime();

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
	 * Initialize the receive socket and the DatagramPacket 
	 * @return true, successfully done
	 * 		   false, failed in some part
	 */
	private boolean initializeSocketAndPacket(){
		
		try {
			receiveSocket.setSoTimeout(MAX_WAITING_TIME_IN_MILLISECOND);
		} catch (SocketException e1) {
			return false;
		}

		byte[] buf = new byte[NodePacket.MAX_PACKET_LENGTH]; 
		packet = new DatagramPacket(buf, buf.length);

		return true;
	}


	/**
	 * Create and Launch a report thread
	 * @return Future of the report thread
	 */

	private ReportTaskHandler createAndLaunchReportTransportationRateRunnable(PacketLostTracker packetLostTracker){	

		ReportRateRunnable reportTransportationRateRunnable = new ReportRateRunnable(INTERVAL_IN_MILLISECOND, packetLostTracker);
		Future<?> reportFuture = NodeContainer.ThreadPool.submit(new MDNTask(reportTransportationRateRunnable));
		return new ReportTaskHandler(reportFuture, reportTransportationRateRunnable);
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