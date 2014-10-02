package edu.cmu.messagebus;

import java.util.logging.Level;

import com.ericsson.research.trap.utils.JDKLoggerConfig;
import com.ericsson.research.warp.api.Notifications.Listener;
import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpURI;
import com.ericsson.research.warp.api.client.AnonymousClient;
import com.ericsson.research.warp.api.client.Client;
import com.ericsson.research.warp.api.client.Client.ConnectionPolicy;
import com.ericsson.research.warp.api.client.PlaintextAuthenticator;
import com.ericsson.research.warp.api.listeners.AbstractMessageListener;
import com.ericsson.research.warp.api.logging.WarpLogger;
import com.ericsson.research.warp.api.message.Message;
import com.ericsson.research.warp.api.resources.Resource;
import com.ericsson.research.warp.util.JSON;

import edu.cmu.messagebus.message.NodeRegistrationReply;
import edu.cmu.messagebus.message.NodeRegistrationRequest;

public abstract class MDNNode {

	protected AnonymousClient _client;

	protected NodeType _type;

	private String _managerWarpURI;

	public MDNNode(NodeType type) {
		_type = type;
	}

	public void config() throws WarpException {

		/* Initialize the message bus */
		JDKLoggerConfig.initForPrefixes(Level.INFO, "warp");

		String trapCfg = "trap.transport.websocket.wsuri=ws://127.0.0.1:8889\ntrap.transport.http.enabled=false\ntrap.transport.socket.enabled=false\ntrap.transport.loopback.enabled=false";

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
						NodeRegistrationRequest registMsg = new NodeRegistrationRequest();
						registMsg.setType(MDNNode.this._type);
						registMsg.setWarpURI(nodeURI.toString());
						try {

							Warp.request(
									WarpURI.create("warp://cmu-sv:mdn-manager/discover"),
									"POST", JSON.toJSON(registMsg).getBytes(),
									null, new AbstractMessageListener() {

										@Override
										public boolean receiveMessage(
												Message reply, Resource resource) {
											_managerWarpURI = JSON
													.fromJSON(
															new String(reply
																	.getData()),
															NodeRegistrationReply.class)
													.getManagerWarpURI();
											if (ClusterConfig.DEBUG) {
												System.out
														.println("[DEBUG] MDNNode.config(): Get the manager WarpURI:"
																+ _managerWarpURI);
											}
											return true;
										}

									});

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

	}

	public void init() throws WarpException {
		_client.connect();
	}

}
