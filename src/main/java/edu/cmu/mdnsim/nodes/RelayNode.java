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
 * A Node can send data to multiple flows for the same stream. 
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 */
public class RelayNode extends AbstractNode implements NodeRunnableCleaner {

	private Map<String, StreamTaskHandler<RelayRunnable>> streamIdToRunnableMap = new ConcurrentHashMap<String, StreamTaskHandler<RelayRunnable>>();

	private String masterIP;
	
	public RelayNode(String nodePublicIP, String masterIP) throws UnknownHostException {
		super(nodePublicIP);
		this.masterIP = masterIP;
		
	}
	/**
	 * Executes a task.
	 * For the same stream, execute task might be called multiple times.
	 * But it should use only one thread to handle that.
	 */
	@Override
	public synchronized void executeTask(MbMessage request, Stream stream) {

		Flow flow = stream.findFlow(getFlowId(request));
		//Get the relay node properties
		Map<String, String> nodePropertiesMap = flow.findNodeMap(getNodeId());
		//Open a socket for receiving data only if it is not already open
		DatagramSocket receiveSocket = this.getAvailableSocket(flow.getStreamId());
		if (receiveSocket == null) {
			//TODO: this is an exception
			return;
		}
		
		Map<String, String> upstreamNodePropertiesMap = 
				flow.findNodeMap(nodePropertiesMap.get(Flow.UPSTREAM_ID));
		UDPInfo udpInfo = null;
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

		String[] addressAndPort = nodePropertiesMap.get(Flow.RECEIVER_PUBLIC_IP_PORT).split(":");
		if (udpInfo != null && udpInfo.getPublicIP().equals(addressAndPort[0])) {
			addressAndPort = nodePropertiesMap.get(Flow.RECEIVER_LOCAL_IP_PORT).split(":");
		}
		
		logger.debug(String.format("RelayNode.run(): For stream[%s] as node[%s] to downstream at[%s:%s]", stream.getStreamId(),nodePropertiesMap.get(Flow.NODE_ID), addressAndPort[0], addressAndPort[1]));
		
		InetAddress destAddress = null;
		int destPort;
		try {
			destAddress = InetAddress.getByName(addressAndPort[0]);
			destPort = Integer.valueOf(addressAndPort[1]);
			String downStreamUri = nodePropertiesMap.get(Flow.DOWNSTREAM_URI);

			if(streamIdToRunnableMap.get(stream.getStreamId()) != null){
				//Add new flow to the stream object maintained by NodeRunable
				streamIdToRunnableMap.get(stream.getStreamId()).streamTask.getStream().replaceFlow(flow);
				//A new downstream node is connected to relay, just add it to existing runnable
				streamIdToRunnableMap.get(stream.getStreamId()).streamTask.addNewDestination(downStreamUri, destAddress, destPort);
			}else{
				//For the first time, create a new Runnable and send stream spec to upstream node
				RelayRunnable relayRunnable = 
						new RelayRunnable(stream,downStreamUri, destAddress, destPort, msgBusClient, getNodeId(), this, receiveSocket);
				Future<?> relayFuture = NodeContainer.ThreadPool.submit(new MDNTask(relayRunnable));
				streamIdToRunnableMap.put(stream.getStreamId(), new StreamTaskHandler<RelayRunnable>(relayFuture, relayRunnable));

				
				try {
					msgBusClient.send("/" + getNodeId() + "/tasks/" + flow.getFlowId(), 
							nodePropertiesMap.get(Flow.UPSTREAM_URI)+"/tasks", "PUT", stream);
				} catch (MessageBusException e) {
					e.printStackTrace();
				}
			}
		} catch (UnknownHostException e) {
			logger.error(e.toString());
		}
	}

	/**
	 * Terminate the task that associated with a flow
	 */
	@Override
	public synchronized void terminateTask(Flow flow) {

		logger.debug( this.getNodeId() + " Trying to terminate flow: " +  flow.getFlowId());

		StreamTaskHandler<RelayRunnable> streamTaskHandler = streamIdToRunnableMap.get(flow.getStreamId());

		if(streamTaskHandler == null){ //terminate a task that hasn't been started. (before executeTask is executed).
			throw new IllegalStateException("Terminate task Before Executing");
		}

		if(streamTaskHandler.streamTask.getDownStreamCount() == 1){ 

			streamTaskHandler.kill();
			/* Notify the Upstream node */
			Map<String, String> nodeMap = flow.findNodeMap(getNodeId());
			try {
				msgBusClient.send("/tasks", nodeMap.get(Flow.UPSTREAM_URI) + "/tasks", "POST", flow);
			} catch (MessageBusException e) {
				logger.error(e.toString());
			}	
		} else {
			streamTaskHandler.streamTask.removeDownStream(
					flow.findNodeMap(getNodeId()).get(Flow.DOWNSTREAM_URI));
			//Send release resource command to downstream node 
			try {
				msgBusClient.send("/tasks", 
						flow.findNodeMap(getNodeId()).get(Flow.DOWNSTREAM_URI) + "/tasks", "DELETE", flow);
			} catch (MessageBusException e) {
				logger.error(e.toString());
			}

			logger.debug(this.getNodeId() + 
					String.format(" terminateTask(): Ask downstream node(%s) to release resouces.\n", 
							flow.findNodeMap(getNodeId()).get(Flow.DOWNSTREAM_ID)));
		}
	}

	/**
	 * Releases resource associated with a flow
	 */
	@Override
	public void releaseResource(Flow flow) {

		logger.debug("%s [DEBUG]RelayNode.releaseResource(): try to release flow %s\n", this.getNodeId(), flow.getFlowId());
		StreamTaskHandler<RelayRunnable> streamTaskHandler = streamIdToRunnableMap.get(flow.getStreamId());
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
	 * Resets a task.
	 */
	@Override
	public synchronized void reset() {
		for (StreamTaskHandler<RelayRunnable> streamTask : streamIdToRunnableMap.values()) {
			streamTask.reset();
			while(!streamTask.isDone());
			streamTask.clean();
			logger.debug("Reset streamTask:" + streamTask.getStreamId());
		}

		msgBusClient.removeResource("/" + getNodeId());
	}

	/**
	 * Removes the node runnable associated with the streamId
	 */
	@Override
	public void removeNodeRunnable(String streamId) {
		
		this.streamIdToRunnableMap.remove(streamId);
		
	}
}
