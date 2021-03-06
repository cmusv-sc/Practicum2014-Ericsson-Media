package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramSocket;
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
 * A node that can receive packets. 
 * <p> This node represents the end clients that is the consumers of the media over the network.
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class SinkNode extends AbstractNode implements NodeRunnableCleaner{
	/**
	 *  Key: FlowId; Value: ReceiveThread 
	 */
	private Map<String, StreamTaskHandler<SinkRunnable>> streamIdToRunnableMap = new ConcurrentHashMap<String, StreamTaskHandler<SinkRunnable>>();
	
	private String masterIP;
	
	/**
	 * 
	 * @param nodePublicIP This IP address is used to bound to a socket to transfer media data.
	 * 
	 * @throws UnknownHostException
	 */
	public SinkNode(String nodePublicIP, String masterIP) throws UnknownHostException {
		super(nodePublicIP);
		this.masterIP = masterIP;
	}
	

	@Override
	public synchronized void executeTask(MbMessage request, Stream stream) {

		logger.debug(this.getNodeId() + " Sink received a StreamSpec for Stream : " + stream.getStreamId());

		Flow flow = stream.findFlow(getFlowId(request));
		
		
		//Get the sink node properties
		Map<String, String> nodePropertiesMap = flow.findNodeMap(getNodeId());
		
		DatagramSocket receiveSocket = getAvailableSocket(flow.getStreamId());
		
		
		Map<String, String> upstreamNodePropertiesMap = 
				flow.findNodeMap(nodePropertiesMap.get(Flow.UPSTREAM_ID));
		UDPInfo udpInfo;
		try {
			udpInfo = getUDPInfo(receiveSocket, masterIP);
			upstreamNodePropertiesMap.put(Flow.RECEIVER_PUBLIC_IP_PORT, 
					udpInfo.getPublicIP()+":"+udpInfo.getPublicPort());
			upstreamNodePropertiesMap.put(Flow.RECEIVER_LOCAL_IP_PORT, 
					super.getHostAddr().getHostAddress() + ":" + receiveSocket.getLocalPort());
//			logger.debug("UDPINFO: " + udpInfo.getPublicIP() + ":" + udpInfo.getPublicPort() + "/NATIVEINFO: " + super.getHostAddr().getHostAddress() + ":" + receiveSocket.getLocalPort());
		} catch (ClassNotFoundException | IOException e1) {
			upstreamNodePropertiesMap.put(Flow.RECEIVER_LOCAL_IP_PORT, 
					super.getHostAddr().getHostAddress()+":"+receiveSocket.getLocalPort());
		}
		
		
		SinkRunnable rcvRunnable = new SinkRunnable(stream, flow, msgBusClient, nodeId, this, receiveSocket);
		Future<?> rcvFuture = NodeContainer.ThreadPool.submit(new MDNTask(rcvRunnable));
		streamIdToRunnableMap.put(stream.getStreamId(), new StreamTaskHandler<SinkRunnable>(rcvFuture, rcvRunnable));
		
		/*Send the stream spec to upstream node */
		try {
			msgBusClient.send("/" + getNodeId() + "/tasks/" + flow.getFlowId(), 
					nodePropertiesMap.get(Flow.UPSTREAM_URI)+"/tasks", "PUT", stream);
			logger.debug("SinkNode.executeTask(): " + this.nodeId + " started the stream[" + stream.getStreamId() + "]");
		} catch (MessageBusException e) {
			logger.debug("Could not send work config spec to upstream node." + e.toString());
		}
	}

	@Override
	public void terminateTask(Flow flow) {

		StreamTaskHandler<SinkRunnable> streamTaskHandler = streamIdToRunnableMap.get(flow.getStreamId());

		if(streamTaskHandler == null){
			throw new IllegalStateException("Terminate task before executing");
		}
		streamTaskHandler.kill();

		Map<String, String> nodeMap = flow.findNodeMap(getNodeId());

		try {
			msgBusClient.send("/tasks", nodeMap.get(Flow.UPSTREAM_URI) + "/tasks", "POST", flow);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}

	}

	@Override
	public void releaseResource(Flow flow) {

		StreamTaskHandler<SinkRunnable> streamTaskHandler = streamIdToRunnableMap.get(flow.getStreamId());
		while (!streamTaskHandler.isDone());
		streamTaskHandler.clean();
		streamIdToRunnableMap.remove(flow.getStreamId());
	}

	@Override
	public synchronized void reset() {

		for (StreamTaskHandler<SinkRunnable> streamTask : streamIdToRunnableMap.values()) {

			streamTask.reset();
			while(!streamTask.isDone());
			streamTask.clean();

		}

		msgBusClient.removeResource("/" + getNodeId());

	}

	@Override
	public void removeNodeRunnable(String streamId) {
		
		this.streamIdToRunnableMap.remove(streamId);
	}
}
