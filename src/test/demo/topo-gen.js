var createNode = function(nodeType, nodeId, upstreamId) {
	var node = {};
	node.NodeType = nodeType;
	node.NodeId   = nodeId;
	node.UpstreamId = upstreamId;
	return node;
}

var fn = function(streamId) {

	var source = "mdnsim-node" + Math.ceil(Math.random() * 20) + ":source";

	var relay1 = "mdnsim-node" + Math.ceil(Math.random() * 20) + ":relay1";
	
	//Tier 2 relays num: 2 ~ 3
	var relay2Hash = {};
	var relay2Pool = [];
	var relay2Num = Math.ceil(Math.random() * 2 + 1);
	for(var i = 0; i < relay2Num; i++) {
		var tmp = Math.ceil(Math.random()*20);
		while(relay2Hash[tmp] === true) {
		 	tmp = Math.ceil(Math.random()*20);
		}
		relay2Hash[tmp] = true;
	}
	for (var val in relay2Hash) {
		relay2Pool.push("mdnsim-node" + val + ":relay2");
	}

	//Tier 3 Relay Tag: tier 2 num  + 1 ~ 3
	var relay3Hash = {};
	var relay3Pool = [];
	var relay3Num = Math.ceil(Math.random() * 3 + relay2Num);
	for(var i = 0; i < relay3Num; i++) {
		var tmp = Math.ceil(Math.random()*20);
		while(relay3Hash[tmp] === true) {
		 	tmp = Math.ceil(Math.random()*20);
		}
		relay3Hash[tmp] = true;
	}
	for (var val in relay3Hash) {
		relay3Pool.push("mdnsim-node" + val + ":relay3");
	}


	var graph = {};
	graph.upstream = {};

	for (var i = 1; i <= 20; i++) {
		var sink = "mdnsim-node" + i + ":sink";
		if (i < relay3Num) {
			graph.upstream[sink] = relay3Pool[i];
		} else {
			var randomIdx = Math.floor(Math.random() * (relay3Num));
			
			console.log(sink + "is attached to " + randomIdx);
			
			graph.upstream[sink] = relay3Pool[randomIdx];
		}
	}

	for (var i = 0; i < relay3Num; i++) {
		var relay3 = relay3Pool[i];
		if (i < relay2Num) {
			graph.upstream[relay3] = relay2Pool[i];
		} else {
			var randomIdx = Math.floor(Math.random() * (relay2Num));
			graph.upstream[relay3] = relay2Pool[randomIdx];
		}
	}

	for (var i = 0; i < relay2Num; i++) {
		var relay2 = relay2Pool[i];
		graph.upstream[relay2] = relay1;
	}

	graph.upstream[relay1] = source;


	foo = {
		SimId:"demo",
		StreamList:[],

	}

	var stream = {};
	stream.StreamId = "Stream" + streamId;
	stream.DataSize = "80000000";
	stream.KiloBitRate = "5000";
	stream.FlowList = [];

	for (var i = 1; i <= 20; i++) {
		
		var flow = {};
		flow.NodeList = [];
		var sink = "mdnsim-node" + i + ":sink";
		flow.NodeList.push(createNode("SinkNode", sink, graph.upstream[sink]));
		var relay3 = graph.upstream[sink];
		flow.NodeList.push(createNode("RelayNode", relay3, graph.upstream[relay3]));
		var relay2 = graph.upstream[relay3];
		flow.NodeList.push(createNode("RelayNode", relay2, graph.upstream[relay2]));
		var relay1 = graph.upstream[relay2];
		flow.NodeList.push(createNode("RelayNode", relay1, graph.upstream[relay1]));
		var src = graph.upstream[relay1];
		flow.NodeList.push(createNode("SourceNode", src, "NULL"));
		stream.FlowList.push(flow);
	}

	foo.StreamList.push(stream);

	loggerResource.message().method("POST").to("/mdnsim/work_config").data(foo).send();

};



