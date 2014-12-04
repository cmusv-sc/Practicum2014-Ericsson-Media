package edu.cmu.mdnsim.nodes;

import java.net.DatagramSocket;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import com.ericsson.research.warp.api.message.Message;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.exception.TerminateTaskBeforeExecutingException;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;

/**
 * 
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
	
	public SinkNode() throws UnknownHostException {
		super();
	}


	@Override
	public void executeTask(Message request, Stream stream) {

		logger.debug(this.getNodeId() + " Sink received a StreamSpec for Stream : " + stream.getStreamId());


		Flow flow = stream.findFlow(this.getFlowId(request));
		//Get the sink node properties
		Map<String, String> nodePropertiesMap = flow.findNodeMap(getNodeId());
		DatagramSocket receiveSocket = getAvailablePort(flow.getStreamId());
		SinkRunnable rcvRunnable = new SinkRunnable(stream, flow, msgBusClient, nodeId, this, receiveSocket);
		Future<?> rcvFuture = NodeContainer.ThreadPool.submit(new MDNTask(rcvRunnable));
		streamIdToRunnableMap.put(stream.getStreamId(), new StreamTaskHandler<SinkRunnable>(rcvFuture, rcvRunnable));
		//Send the stream spec to upstream node
		Map<String, String> upstreamNodePropertiesMap = 
				flow.findNodeMap(nodePropertiesMap.get(Flow.UPSTREAM_ID));
		upstreamNodePropertiesMap.put(Flow.RECEIVER_IP_PORT, 
				super.getHostAddr().getHostAddress()+":"+receiveSocket.getLocalPort());

		try {
			msgBusClient.send("/" + getNodeId() + "/tasks/" + flow.getFlowId(), 
					nodePropertiesMap.get(Flow.UPSTREAM_URI)+"/tasks", "PUT", stream);
		} catch (MessageBusException e) {
			logger.debug("Could not send work config spec to upstream node." + e.toString());
		}
	}

	@Override
	public void terminateTask(Flow flow) {

		StreamTaskHandler<SinkRunnable> streamTaskHandler = streamIdToRunnableMap.get(flow.getStreamId());

		if(streamTaskHandler == null){
			throw new TerminateTaskBeforeExecutingException();
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
