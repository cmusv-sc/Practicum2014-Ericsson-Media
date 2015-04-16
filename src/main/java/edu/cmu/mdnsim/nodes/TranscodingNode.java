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
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.MbMessage;
import edu.cmu.util.UDPHolePunchingServer.UDPInfo;

/**
 * TranscodingNode reduces the stream bit rate to accommodate the bandwidth of downstream.
 *
 * @author Geng Fu
 *
 */
public class TranscodingNode extends AbstractNode implements NodeRunnableCleaner{

	private Map<String, StreamTaskHandler<TranscodingRunnable>> streamIdToRunnableMap 
		= new ConcurrentHashMap<String, StreamTaskHandler<TranscodingRunnable>>();

	
	private String masterIP;
	
	public TranscodingNode(String nodePublicIP, String masterIP) throws UnknownHostException {	
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
		DatagramSocket receiveSocket = super.getAvailableSocket(flow.getStreamId());
		
		if(receiveSocket == null){
			logger.error("TransocdingNode.executeTask(): unable return a receive socket");
			return;
		}
			
		Map<String, String> upstreamNodePropertiesMap = 
				flow.findNodeMap(nodePropertiesMap.get(Flow.UPSTREAM_ID));
		UDPInfo myNetInfo = null;
		try {
			myNetInfo = getUDPInfo(receiveSocket, masterIP);
			upstreamNodePropertiesMap.put(Flow.RECEIVER_PUBLIC_IP_PORT, 
					myNetInfo.getPublicIP()+":"+myNetInfo.getPublicPort());
			upstreamNodePropertiesMap.put(Flow.RECEIVER_LOCAL_IP_PORT, super.getHostAddr().getHostAddress() + ":" + receiveSocket.getLocalPort());
			logger.debug("TranscodingNode.executeTask(): UDP param: " + myNetInfo.getPublicIP() + ":" + myNetInfo.getPublicPort() + "/Native param: " + super.getHostAddr().getHostAddress() + ":" + receiveSocket.getLocalPort());
		} catch (ClassNotFoundException | IOException e1) {
			e1.printStackTrace();
			return;
		}
		
		
		/* Get the IP:port reported by downstream*/
		
		String	downStreamIP	= nodePropertiesMap.get(Flow.RECEIVER_PUBLIC_IP_PORT).split(":")[0];
		int		downStreamPort	= Integer.parseInt(nodePropertiesMap.get(Flow.RECEIVER_PUBLIC_IP_PORT).split(":")[1]);
		
		/* If the downstream node and this node are in the same network, use local IP instead of detected public IP */
		if (myNetInfo != null && myNetInfo.getPublicIP().equals(downStreamIP)) {
			downStreamIP	=	nodePropertiesMap.get(Flow.RECEIVER_LOCAL_IP_PORT).split(":")[0];
			downStreamPort	=	Integer.parseInt(nodePropertiesMap.get(Flow.RECEIVER_LOCAL_IP_PORT).split(":")[1]);
		}
		
		logger.debug(String.format("TranscodingNode.run(): For stream[%s] as node[%s] to downstream at[%s:%d]", stream.getStreamId(),nodePropertiesMap.get(Flow.NODE_ID), downStreamIP, downStreamPort));
		
		/* Get the expected rate */
//		int rate = Integer.parseInt(flow.getKiloBitRate());
		
		try {
			
			
			TranscodingRunnable procRunnable = 
					new TranscodingRunnable(stream, Long.parseLong(stream.getDataSize()), InetAddress.getByName(downStreamIP), downStreamPort, msgBusClient, nodeId, this, receiveSocket);
			Future<?> streamFuture = NodeContainer.ThreadPool.submit(new MDNTask(procRunnable));
			streamIdToRunnableMap.put(stream.getStreamId(), new StreamTaskHandler<TranscodingRunnable>(streamFuture, procRunnable));
			System.out.println("TranscodingNode.run(): Add stream " + stream.getStreamId());

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

	/**
	 * Terminate a task.
	 */
	@Override
	public void terminateTask(Flow flow) {

		StreamTaskHandler<TranscodingRunnable> streamTask = streamIdToRunnableMap.get(flow.getStreamId());
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

		StreamTaskHandler<TranscodingRunnable> streamTaskHandler = streamIdToRunnableMap.get(flow.getStreamId());
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

		for (StreamTaskHandler<TranscodingRunnable> streamTask : streamIdToRunnableMap.values()) {
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
