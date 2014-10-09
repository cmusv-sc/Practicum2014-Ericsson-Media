package edu.cmu.messagebus;

import java.util.logging.Level;

import com.ericsson.research.trap.utils.JDKLoggerConfig;
import com.ericsson.research.warp.api.Notifications.Listener;
import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpContext;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpURI;
import com.ericsson.research.warp.api.client.AnonymousClient;
import com.ericsson.research.warp.api.client.Client;
import com.ericsson.research.warp.api.client.Client.ConnectionPolicy;
import com.ericsson.research.warp.api.client.PlaintextAuthenticator;
import com.ericsson.research.warp.api.logging.WarpLogger;
import com.ericsson.research.warp.util.JSON;

import edu.cmu.messagebus.message.NodeRegistrationRequest;
import edu.cmu.messagebus.message.PrepRcvDataMessage;
import edu.cmu.messagebus.message.SinkReportMessage;
import edu.cmu.messagebus.message.SndDataMessage;
import edu.cmu.messagebus.message.SourceReportMessage;
import edu.cmu.nodes.SinkNode;
import edu.cmu.nodes.SinkNodeImp;
import edu.cmu.nodes.SourceNode;
import edu.cmu.nodes.SourceNodeImp;

public class NodeContainerImp implements NodeContainer {

	protected AnonymousClient _client;
	
	private SinkNode _sinkNode = new SinkNodeImp();
	
	private SourceNode _sourceNode = new SourceNodeImp();

	public void config() throws WarpException {

		/* Initialize the message bus */
		JDKLoggerConfig.initForPrefixes(Level.INFO, "warp");

		String trapCfg = "trap.transport.websocket.wsuri=ws://127.0.0.1:8889\n"
				+ "trap.transport.http.enabled=false\n"
				+ "trap.transport.socket.enabled=false\n"
				+ "trap.transport.loopback.enabled=false";

		_client = Warp
				.init()
				.client()
				.setRemoteConfig(trapCfg)
				.setAuth(
						new PlaintextAuthenticator(WarpURI
								.create("warp:anon/foo"), "secret"))
				.setPolicy(ConnectionPolicy.CLOSE_ON_DISCONNECT)
				.createAnonymous();

		_client.notifications().registerForNotification(
				Client.ConnectedNotification, new Listener() {
					
					@Override
					public void receiveNotification(String name, Object sender,
							Object attachment) {
						WarpLogger
								.info("Connection successful. Time to do stuff!");
						WarpURI nodeURI = Warp.uri();
						NodeRegistrationRequest registMsg = 
								new NodeRegistrationRequest();
						registMsg.setWarpURI(nodeURI.toString());
						
						try {
							Warp.send("/", WarpURI.create("warp://cmu-sv:mdn-manager/discover"),
									"POST", JSON.toJSON(registMsg).getBytes());
						} catch (WarpException e) {
							e.printStackTrace();
						}
					}
				}, true);

		_client.notifications().registerForNotification(
				Client.DisconnectedNotification, new Listener() {

					@Override
					public void receiveNotification(String name, Object sender,
							Object attachment) {
						WarpLogger
								.info("We have been disconnected from the server");
					}
				}, true);
		
		Warp.addMethodListener("/sink/prep", "POST", this, "prepRcvData");
		
		Warp.addMethodListener("/source/snd_data", "POST", this, "sendData");
		

	}

	public void init() throws WarpException {
		_client.connect();
	}
	
	public void prepRcvData(PrepRcvDataMessage msg) throws WarpException {
		
		final String streamId = msg.getStreamID();
		
		
		//require sink node get ready to accept data
		int sinkPort = _sinkNode.bindAvailablePortToStream(streamId);
		
		//Obtain the IP address of sink
		String sinkIP = _sinkNode.getHostAddr();
		
		_sinkNode.receiveAndReport(streamId, NodeContainerImp.this);

		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG] MDNSink.prepRcvData(): Ready to receive data. Sending control message to:" + msg.getSourceWarpURI());
		}
		
		SndDataMessage sndDataMsg = new SndDataMessage(sinkIP, sinkPort);
		sndDataMsg.setDataSize(msg.getDataSize());
		sndDataMsg.setDataRate(msg.getDataRate());
		sndDataMsg.setStreamID(msg.getStreamID());
		
		WarpContext.setApplication(_client);
		
		Warp.send("/source/prep", WarpURI.create(msg.getSourceWarpURI()), "POST", JSON.toJSON(sndDataMsg).getBytes());

	}
	
	public void sendData(SndDataMessage msg) {
		String streamId = msg.getStreamID();
		int destPort = msg.getSinkPort();
		int bytesToTransfer = msg.getDataSize();
		int rate = msg.getDataRate();
		String destAddr = msg.getSinkIP();
		
		if (ClusterConfig.DEBUG) {
			String info = String.format("[DEBUG] MDNSource.sendData(): Start to send data to sink[%s:]", msg.getSinkIP(), msg.getSinkPort());
			System.out.println(info);
		}
		
		NodeContainerImp.this._sourceNode.
			sendAndReport(streamId, destAddr, destPort, bytesToTransfer, rate, NodeContainerImp.this);

	}
	
	
	
	
	public void sinkReport(SinkReportMessage sinkRepMsg) {
		try {
			Warp.send("/", WarpURI.create("warp://cmu-sv:mdn-manager/sink_report"),"POST", JSON.toJSON(sinkRepMsg).getBytes());
		} catch (WarpException e) {
			e.printStackTrace();
		}
	}
	
	public void sourceReport(SourceReportMessage srcRepMsg) {
		try {
			Warp.send("/", WarpURI.create("warp://cmu-sv:mdn-manager/source_report"),"POST", JSON.toJSON(srcRepMsg).getBytes());
		} catch (WarpException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws WarpException {
		NodeContainerImp node = new NodeContainerImp();
		node.config();
		node.init();
	}

}
