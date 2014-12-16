package edu.cmu.mdnsim.nodes;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import com.ericsson.research.warp.api.message.Message;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;

/**
 * A node which can process packets with some amout of resource such as CPU and memory.
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class ProcessingNode extends AbstractNode implements NodeRunnableCleaner{

	private Map<String, StreamTaskHandler<ProcessRunnable>> streamIdToRunnableMap = new HashMap<String, StreamTaskHandler<ProcessRunnable>>();

	public ProcessingNode(String nodePublicIP) throws UnknownHostException {	
		super(nodePublicIP);
	}

	/**
	 * Executes a task.
	 * It is assumed that there will be only one downstream node for one stream
	 * even if Processing node exists in multiple flows.
	 */
	@Override
	public void executeTask(Message request, Stream stream) {

		Flow flow = stream.findFlow(this.getFlowId(request));
		Map<String, String> nodePropertiesMap = flow.findNodeMap(getNodeId());
		/* Open a socket for receiving data from upstream node */
		DatagramSocket receiveSocket = this.getAvailableSocket(flow.getStreamId());
		if(receiveSocket == null){
			//TODO: report to the management layer, we failed to bind a port to a socket
		}else{
			/* Get processing parameters */
			long processingLoop = Long.valueOf(nodePropertiesMap.get(Flow.PROCESSING_LOOP));
			int processingMemory = Integer.valueOf(nodePropertiesMap.get(Flow.PROCESSING_MEMORY));
			/* Get the IP:port */
			String[] addressAndPort = nodePropertiesMap.get(Flow.RECEIVER_IP_PORT).split(":");
			/* Get the expected rate */
			int rate = Integer.parseInt(flow.getKiloBitRate());

			InetAddress targetAddress = null;
			try {
				targetAddress = InetAddress.getByName(addressAndPort[0]);
				int targetPort = Integer.valueOf(addressAndPort[1]);

				this.launchProcessRunnable(stream, 
						Integer.valueOf(stream.getDataSize()), targetAddress, targetPort, 
						processingLoop, processingMemory, rate);
				
				ProcessRunnable procRunnable = 
						new ProcessRunnable(stream, Integer.valueOf(stream.getDataSize()), InetAddress.getByName(addressAndPort[0]), Integer.valueOf(addressAndPort[1]), processingLoop, processingMemory, rate, msgBusClient, nodeId, this, receiveSocket);
				Future<?> procFuture = NodeContainer.ThreadPool.submit(new MDNTask(procRunnable));
				streamIdToRunnableMap.put(stream.getStreamId(), new StreamTaskHandler<ProcessRunnable>(procFuture, procRunnable));

				//Send the stream specification to upstream node
				Map<String, String> upstreamNodePropertiesMap = 
						flow.findNodeMap(nodePropertiesMap.get(Flow.UPSTREAM_ID));
				upstreamNodePropertiesMap.put(Flow.RECEIVER_IP_PORT, 
						super.getHostAddr().getHostAddress()+":"+receiveSocket.getLocalPort());
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
	 * Creates a ReceiveProcessAndSendRunnable and launch it and record it in the map
	 * @param streamId
	 * @param totalData
	 * @param destAddress
	 * @param destPort
	 * @param processingLoop
	 * @param processingMemory
	 * @param rate
	 */
	public void launchProcessRunnable(Stream stream, int totalData, 
			InetAddress destAddress, int destPort, long processingLoop, int processingMemory, int rate){
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
		this.streamIdToRunnableMap.remove(streamId);
		
	}
}
