package edu.cmu.mdnsim.nodes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import com.ericsson.research.warp.api.message.Message;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;

/**
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class SourceNode extends AbstractNode implements NodeRunnableCleaner {

	private Map<String, StreamTaskHandler<SourceRunnable>> streamIdToRunnableMap = new ConcurrentHashMap<String, StreamTaskHandler<SourceRunnable>>();

	public SourceNode() throws UnknownHostException {
		super();
	}	
	/**
	 * Assumptions:
	 * 1. All the flows in the stream should have source node in it.
	 * 2. Properties for source node should be same in all flows.
	 * 3. It is assumed that there will be only one downstream node for one stream
	 * 		even if Source node exists in multiple flows.
	 * 
	 */
	@Override
	public void executeTask(Message request, Stream stream) {
		logger.debug(this.getNodeId() + " received a work specification. StreamId: " + stream.getStreamId());

		Flow flow = stream.findFlow(this.getFlowId(request));
		Map<String, String> nodePropertiesMap = flow.findNodeMap(getNodeId());
		String[] ipAndPort = nodePropertiesMap.get(Flow.RECEIVER_IP_PORT).split(":");
		String destAddrStr = ipAndPort[0];
		int destPort = Integer.parseInt(ipAndPort[1]);
		int dataSizeInBytes = Integer.parseInt(flow.getDataSize());
		int rateInKiloBitsPerSec = Integer.parseInt(flow.getKiloBitRate());
		int rateInBytesPerSec = rateInKiloBitsPerSec * 128;  //Assumed that KiloBits = 1024 bits

		try {
			createAndLaunchSendRunnable(stream, InetAddress.getByName(destAddrStr), destPort, 
					dataSizeInBytes, rateInBytesPerSec, flow);					
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}	
	}

	/**
	 * Create and launch a SendRunnable and put it in the streamId - runnable map
	 * @param streamId
	 * @param destAddrStr
	 * @param destPort
	 * @param bytesToTransfer
	 * @param rate
	 */
	public void createAndLaunchSendRunnable(Stream stream, InetAddress destAddrStr, int destPort, int bytesToTransfer, int rate, Flow flow){
		SourceRunnable sendRunnable = new SourceRunnable(stream, destAddrStr, destPort, bytesToTransfer, rate, flow, msgBusClient, getNodeId(), this);
		Future<?> sendFuture = NodeContainer.ThreadPool.submit(new MDNTask(sendRunnable));
		streamIdToRunnableMap.put(stream.getStreamId(), new StreamTaskHandler<SourceRunnable>(sendFuture, sendRunnable));
	}
	/**
	 * For Source Node, stopping flow and stopping stream is same thing.
	 */
	@Override
	public void terminateTask(Flow flow) {
		StreamTaskHandler<SourceRunnable> sendTaskHanlder = streamIdToRunnableMap.get(flow.getStreamId());
		if(sendTaskHanlder == null){
			throw new IllegalStateException("Terminate task before executing");
		}
		sendTaskHanlder.kill();
		releaseResource(flow);
	}
	/**
	 * For Source Node, stopping flow and stopping stream is same thing.
	 */
	@Override
	public void releaseResource(Flow flow) {
		StreamTaskHandler<SourceRunnable> sndThread = streamIdToRunnableMap.get(flow.getStreamId());

		while (!sndThread.isDone());

		logger.debug(this.getNodeId() + " starts to clean-up resources for flow: " + flow.getFlowId());

		sndThread.clean();
		Map<String, String> nodeMap = flow.findNodeMap(getNodeId());
		try {
			msgBusClient.send("/tasks", nodeMap.get(Flow.DOWNSTREAM_URI) + "/tasks", "DELETE", flow);
		} catch (MessageBusException e) {
			
			e.printStackTrace();
		}
	}

	@Override
	public synchronized void reset() {
		for (StreamTaskHandler<SourceRunnable> streamTask : streamIdToRunnableMap.values()) {
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
