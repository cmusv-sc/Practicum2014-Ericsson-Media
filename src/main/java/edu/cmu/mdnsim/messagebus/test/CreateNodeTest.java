package edu.cmu.mdnsim.messagebus.test;

import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.CreateNodeRequest;
import edu.cmu.mdnsim.nodes.NodeType;


public class CreateNodeTest implements MessageBusTestCase {
	
	private MessageBusClient msgBusClient;
	
	public CreateNodeTest(MessageBusClient client) {
		msgBusClient = client;
	}
	
	@Override
	public void execute() {
		
		String sinkNodeClass = "edu.cmu.mdnsim.nodes.SinkNode";
		String sourceNodeClass = "edu.cmu.mdnsim.nodes.SourceNode";
		CreateNodeRequest sinkReq = new CreateNodeRequest(NodeType.SINK, "default", sinkNodeClass);
		try {
			msgBusClient.send("stimulus/create-node", "warp://cmu-sv:mdn-manager/nodes", "POST", sinkReq);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
		
		CreateNodeRequest srcReq = new CreateNodeRequest(NodeType.SOURCE, "default", sourceNodeClass);
		try {
			msgBusClient.send("stimulus/create-node", "warp://cmu-sv:mdn-manager/nodes", "POST", srcReq);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}

	
}

	
