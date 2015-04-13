package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.MbMessage;
import edu.cmu.util.UDPHolePunchingServer.UDPInfo;

/**
 * A node which can process packets with some amout of resource such as CPU and memory.
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class ProcessingNode extends AbstractNode implements NodeRunnableCleaner{

	private Map<String, StreamTaskHandler<ProcessRunnable>> streamIdToRunnableMap = new ConcurrentHashMap<String, StreamTaskHandler<ProcessRunnable>>();

	
	private String masterIP;
	
	public ProcessingNode(String nodePublicIP, String masterIP) throws UnknownHostException {	
		super(nodePublicIP);
		this.masterIP = masterIP;
	}

	/**
	 * Executes a task.
	 * It is assumed that there will be only one downstream node for one stream
	 * even if Processing node exists in multiple flows.
	 */
	@Override
	public void executeTask(MbMessage request, Stream stream) {

		Flow flow = stream.findFlow(getFlowId(request));
		Map<String, String> nodePropertiesMap = flow.findNodeMap(getNodeId());
		/* Open a socket for receiving data from upstream node */
		DatagramSocket receiveSocket = this.getAvailableSocket(flow.getStreamId());
		
		if(receiveSocket == null){
			//TODO: report to the management layer, we failed to bind a port to a socket
			logger.error("ProcessionNode.executeTask(): unable return a receive socket");
		}else{
			
			Map<String, String> upstreamNodePropertiesMap = 
					flow.findNodeMap(nodePropertiesMap.get(Flow.UPSTREAM_ID));
			UDPInfo udpInfo = null;
			try {
				udpInfo = getUDPInfo(receiveSocket, masterIP);
				upstreamNodePropertiesMap.put(Flow.RECEIVER_PUBLIC_IP_PORT, 
						udpInfo.getYourPublicIP()+":"+udpInfo.getYourPublicPort());
				upstreamNodePropertiesMap.put(Flow.RECEIVER_LOCAL_IP_PORT, super.getHostAddr().getHostAddress() + ":" + receiveSocket.getLocalPort());
				logger.debug("UDPINFO: " + udpInfo.getYourPublicIP() + ":" + udpInfo.getYourPublicPort() + "/NATIVEINFO: " + super.getHostAddr().getHostAddress() + ":" + receiveSocket.getLocalPort());
			} catch (ClassNotFoundException | IOException e1) {
				logger.warn("UDPINFO EXCEPTION: ");
				e1.printStackTrace();
				upstreamNodePropertiesMap.put(Flow.RECEIVER_LOCAL_IP_PORT, 
						super.getHostAddr().getHostAddress()+":"+receiveSocket.getLocalPort());
				
			}
			
			/* Get processing parameters */
			long processingLoop = Long.valueOf(nodePropertiesMap.get(Flow.PROCESSING_LOOP));
			int processingMemory = Integer.valueOf(nodePropertiesMap.get(Flow.PROCESSING_MEMORY));
			
			/* Get the IP:port reported by downstream*/
			
			String[] addressAndPort = nodePropertiesMap.get(Flow.RECEIVER_PUBLIC_IP_PORT).split(":");
			if (udpInfo != null && udpInfo.getYourPublicIP().equals(addressAndPort[0])) {
				addressAndPort = nodePropertiesMap.get(Flow.RECEIVER_LOCAL_IP_PORT).split(":");
			}
			
			logger.debug(String.format("ProcessingNode.run(): For stream[%s] as node[%s] to downstream at[%s:%s]", stream.getStreamId(),nodePropertiesMap.get(Flow.NODE_ID), addressAndPort[0], addressAndPort[1]));
			
			/* Get the expected rate */
			int rate = Integer.parseInt(flow.getKiloBitRate());
			
			try {
				ProcessRunnable procRunnable = 
						new ProcessRunnable(stream, Long.parseLong(stream.getDataSize()), InetAddress.getByName(addressAndPort[0]), Integer.valueOf(addressAndPort[1]), processingLoop, processingMemory, rate, msgBusClient, nodeId, this, receiveSocket);
				Future<?> procFuture = NodeContainer.ThreadPool.submit(new MDNTask(procRunnable));
				streamIdToRunnableMap.put(stream.getStreamId(), new StreamTaskHandler<ProcessRunnable>(procFuture, procRunnable));
				System.out.println("ProcessingNode.run(): Add stream " + stream.getStreamId());

				//Send the stream specification to upstream node
				
				
				try {
					msgBusClient.send("/" + getNodeId() + "/tasks/" + flow.getFlowId(), 
							nodePropertiesMap.get(Flow.UPSTREAM_URI)+"/tasks", "PUT", stream);
					
				} catch (MessageBusException e) {
					e.printStackTrace();
				}

			} catch (UnknownHostException e) {
				logger.error(e.toString());
			}
		}
	}

	/**
	 * Terminate a task.
	 */
	@Override
	public void terminateTask(Flow flow) {

		StreamTaskHandler<ProcessRunnable> streamTask = streamIdToRunnableMap.get(flow.getStreamId());
		if(streamTask == null){
			throw new IllegalStateException("Terminate Task Before Executing");
		}
		streamTask.kill();

		/* Notify the Upstream node */
		Map<String, String> nodeMap = flow.findNodeMap(getNodeId());
		try {
			msgBusClient.send("/tasks", nodeMap.get(Flow.UPSTREAM_URI) + "/tasks", "POST", flow);
		} catch (MessageBusException e) {
			logger.error(e.toString());
		}
	}

	/**
	 * Releases the resource of a flow.
	 */
	@Override
	public void releaseResource(Flow flow) {

		logger.debug(this.getNodeId() + " received clean resource request.");

		StreamTaskHandler<ProcessRunnable> streamTaskHandler = streamIdToRunnableMap.get(flow.getStreamId());
		while (!streamTaskHandler.isDone());

		streamTaskHandler.clean();

		Map<String, String> nodeMap = flow.findNodeMap(getNodeId());
		try {
			msgBusClient.send("/tasks", nodeMap.get(Flow.DOWNSTREAM_URI) + "/tasks", "DELETE", flow);
		} catch (MessageBusException e) {
			logger.error(e.toString());
		}

	}

	/**
	 * Reset tasks.
	 */
	@Override
	public synchronized void reset() {

		for (StreamTaskHandler<ProcessRunnable> streamTask : streamIdToRunnableMap.values()) {
			streamTask.reset();
			while(!streamTask.isDone());
			streamTask.clean();
		}

		msgBusClient.removeResource("/" + getNodeId());


	}


	/**
	 * Removes a NodeRunnable for a specific stream.
	 * @param streamId the stream id of the target stream
	 */
	@Override
	public void removeNodeRunnable(String streamId) {
		System.out.println("ProcessingNode.removeNodeRunnable(): " + streamId);
		this.streamIdToRunnableMap.remove(streamId);
		
	}
}
