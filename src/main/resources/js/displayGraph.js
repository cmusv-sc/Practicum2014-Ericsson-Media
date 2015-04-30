//used to show logs in the web client
var loggerResource = RMB.builder().seed("trap.transport.http.url=/_connectTrap\ntrap.transport.websocket.wsuri=/_connectTrapWS")
		.id("webclient").build();

//Represents Sigma object - Main object which holds the graph
var s = null;
//Represents the camera object used by sigma library
var cam = null;

var clickNode = false;
var nodeIdOnWatcher = null;

var clickEdge = false;
var edgeIdOnWatcher = null;

function createGraph(initial_data){
	
	console.log('createGraph called', initial_data);

	s = new sigma({
		  graph: initial_data,
		  renderer: {
		    container: document.getElementById('svg'),
		    type: 'canvas'
		  },
		  settings: {
		    doubleClickEnabled: false,
		    minEdgeSize: 0.5,
		    maxEdgeSize: 4,
		    enableEdgeHovering: true,
		    edgeHoverColor: 'edge',
		    defaultEdgeHoverColor: '#000',
		    edgeHoverSizeRatio: 2,
		    edgeHoverExtremities: true,
		    edgeHoverPrecision: 5,
		  }
	});
	
	
	
	// Bind the events:
	s.bind('overNode clickNode doubleClickNode rightClickNode', function(e) {
	  console.log(e.type, e.data.node.label, e.data.captor);
	  
	  if (clickNode) {
		  return;
	  }
	  
	  if (e.type === "clickNode" || e.type === "doubleClickNode") {
		  clickNode = true;
		  return;
	  }
	  
	  var 	nodeId = e.data.node.id,
	  		nodes  = s.graph.nodes(),
	  		target = null;
	  
	  function addRow(id, attr, val) {
		  return '<tr id=' + id + '><td>' + attr + '</td><td>' + val + '</td></tr>';
	  }
	  
	  
	  
	  for (var i = 0; i < nodes.length; i++) {
		  if (nodes[i].id === nodeId) {
			  target = nodes[i];
		  }
	  }
	  
	  if (target === null) {
		  console.log("ERR: cannot find the node");
		  return;
	  }
	  
	  
	  
	  for (var stream in target.streamMetricsMap) {
		  $("#node-watcher-table").children().filter("tbody").append(addRow(nodeId+stream+'cpuUsage', 'CUP', target.streamMetricsMap[stream]['cpuUsage']));
		  $("#node-watcher-table").children().filter("tbody").append(addRow(nodeId+stream+'memUsage', 'MEM', target.streamMetricsMap[stream]['memUsage']));
		  $("#node-watcher-table").children().filter("tbody").append(addRow(nodeId+stream+'streamStatus', 'Status', target.streamMetricsMap[stream]['streamStatus']));
	  }
	  
	  $("#node-watcher-table").children().filter("tbody").fadeIn();
	  
	  nodeIdOnWatcher = nodeId;
	  
	});
	
	
	s.bind('overEdge clickEdge doubleClickEdge', function(e) {
	  console.log(e.type, e.data.edge, e.data.captor);
	  
	  if (clickEdge) {
		  return;
	  }
	  
	  if (e.type === "clickEdge" || e.type === "doubleClickEdge") {
		  clickEdge = true;
		  return;
	  }
	  
	  var 	edgeId = e.data.edge.id,
	  		edges  = s.graph.edges(),
	  		target = null,
	  		counter = 0,
	  		head    = "",
	  		values  = null;
	  
	  for (var i = 0; i < edges.length; i++) {
		  if (edges[i].id === edgeId) {
			  target = edges[i];
		  }
	  }
	  
	  if (target === null) {
		  console.log("ERR: cannot find the node");
	  }
	  
	  function addRow(id, attr, vals) {
		  var html = '<tr id=' + id + '><td>' + attr + '</td>';
		  for (var i = 0; i < vals.length; i++) {
			  html += "<td>" + vals[i] + "</td>";
		  }
		  return html + '</tr>';
	  }
	  
	  function findValues(target, attr) {
		  var values = [];
		  for (var stream in target.streamMetricsMap) {
			  values.push(target.streamMetricsMap[stream][attr]);
		  }
		  return values;
	  }
	  
	  head = '<tr>';
	  for (var stream in target.streamMetricsMap) {
		  counter++;
		  head += '<th>' + stream + '</th>';
	  }
	  head += '</tr>';
	  console.log("head: ", head);
	  $("#edge-watcher-table").children().filter('thead').append(head);
	  $('#edge-watcher-table-value').attr("colspan", counter);
	  
	  
	  values = findValues(target, 'averageTransferRate');
	  $("#edge-watcher-table").children().filter("tbody").append(addRow(edgeId+'averageTransferRate', 'Avg Rate', values));
	  
	  values = findValues(target, 'currentTransferRate');
	  $("#edge-watcher-table").children().filter("tbody").append(addRow(edgeId+'currentTransferRate', 'Imm Rate', values));
	  
	  values = findValues(target, 'averagePacketLoss');
	  $("#edge-watcher-table").children().filter("tbody").append(addRow(edgeId+'averagePacketLoss',   'Avg Loss', values));
	  
	  values = findValues(target, 'currentPacketLoss');
	  $("#edge-watcher-table").children().filter("tbody").append(addRow(edgeId+'currentPacketLoss',   'Imm Loss', values));
	  
	  values = findValues(target, 'avrEnd2EndLatency');
	  $("#edge-watcher-table").children().filter("tbody").append(addRow(edgeId+'avrEnd2EndLatency',   'E2E Delay', values));
	  
	  values = findValues(target, 'avrLnk2LnkLatency');
	  $("#edge-watcher-table").children().filter("tbody").append(addRow(edgeId+'avrLnk2LnkLatency',   'L2L Delay', values));
	  
	  
	  $("#edge-watcher-table").children().filter("tbody").fadeIn();
	  
	  edgeIdOnWatcher = edgeId;
	  
	});
	
	
	s.bind('outNode', function(e) {
		
		if (clickNode === false) {
			$("#node-watcher-table").children().filter("tbody").empty();
			nodeIdOnWatcher = null;
		}
		
		
	
	});


	s.bind('outEdge', function(e) {
		
		if (clickEdge === false) {
			$("#edge-watcher-table").children().filter("tbody").empty();
			$('#edge-watcher-table').children().filter("thead").empty().append('<tr><th id="edge-watcher-table-metrics" rowspan="2" style="vertical-align: middle;">Metrics</th><th id="edge-watcher-table-value" colspan="1">Value</th></tr>');
			edgeIdOnWatcher = null;
		}
		
	});
	
	
	s.bind('clickStage', function(e) {
//	  console.log(e.type, e.data.captor);
	  clickNode = false;
	  nodeIdOnWatcher = null;
	  $("#node-watcher-table").children().filter("tbody").empty();
	  
	  
	  clickEdge = false;
	  edgeIdOnWatcher = null;
	  $("#edge-watcher-table").children().filter("tbody").empty();
	  $('#edge-watcher-table').children().filter("thead").empty().append('<tr><th id="edge-watcher-table-metrics" rowspan="2" style="vertical-align: middle;">Metrics</th><th id="edge-watcher-table-value" colspan="1">Value</th></tr>');
	  
	});
	
	
	s.bind('doubleClickStage rightClickStage', function(e) {
	  console.log(e.type, e.data.captor);
	});

	s.refresh(); 

}


/**
 * Refreshes the graph based on updated JSON
 * @param updated_data Graph JSON
 */
function refreshGraph(updated_data){
	
	var nodes = updated_data.nodes;
	var edges = updated_data.edges;	
	
	if(s !== null){		
		if(s.graph.nodes().length === nodes.length){
			//Create a HashMap of updated nodes
			var nodesMap = {};			
			for(var i = 0; i < nodes.length; i++){
				nodesMap[nodes[i].id] = nodes[i];
			}
			//Update the graph nodes with new values for color, size and tooltip (tag)
			for(var i = 0; i < s.graph.nodes().length; i++){
				var graphNode = s.graph.nodes()[i];
				graphNode.color = nodesMap[graphNode.id].color;
				graphNode.size = nodesMap[graphNode.id].size;
				graphNode.streamMetricsMap = nodesMap[graphNode.id].streamMetricsMap;
			}
			
			//This function will update the hover if it is currently being displayed
			refreshNodeWatcher();
			
			//Same things for updating edges. But in edges, we need to manually change the color and size
			var edgesMap = {};
			for(var i=0; i<edges.length; i++){
				edgesMap[edges[i].id] = edges[i];
			}
			var needsToRefresh = false;
			for(var i=0; i<s.graph.edges().length; i++){
				var graphEdge = s.graph.edges()[i];
				if (graphEdge.color !== edgesMap[graphEdge.id].color) {
					needsToRefresh = true;
					graphEdge.color = edgesMap[graphEdge.id].color;
				}
				graphEdge.size = edgesMap[graphEdge.id].size;
				graphEdge.streamMetricsMap = edgesMap[graphEdge.id].streamMetricsMap;
			}
			
			//The color of edge may change
			if (needsToRefresh) {
				s.refresh();
			}
			
			//Refresh the watcher
			refreshEdgeWatcher();
			
		}else{
			//Clear the graph and add new nodes and edges. 
			//we can use lastHoveredElement variable for displaying hover 
			console.log("refreshGraph(): nodes number doesn't match.");
			s.graph.clear();
			
			for(var i = 0; i < nodes.length; i++){	
				s.graph.addNode(nodes[i]);
			}		
			for(var i = 0; i < edges.length; i++){			
				s.graph.addEdge(edges[i]);		
			}
			s.refresh();   
		}		
	} else { //sigma instance is null
		updated_data = {"nodes":nodes,"edges":edges};
		createGraph(updated_data);
	}
}


function refreshNodeWatcher() {
	
	var nodeId = null,
	target = null,
	nodes  = s.graph.nodes();
	
	if (nodeIdOnWatcher === null) {
		return;
	}
	
	for (var i = 0; i < nodes.length; i++) {
		if (nodes[i].id === nodeId) {
			target = nodes[i];
		}
	}
	
	if (target === null) {
		console.log("ERR: cannot find the node");
		return;
	}
	
	for (var stream in target.streamMetricsMap) {
		
		$(jq("#" + nodeId + stream + 'cpuUsage')).children().first().next().text(target.streamMetricsMap[stream]['cpuUsage']);
		$(jq("#" + nodeId + stream + 'memUsage')).children().first().next().text(target.streamMetricsMap[stream]['memUsage']);
		$(jq("#" + nodeId + stream + 'streamStatus')).children().first().next().text(target.streamMetricsMap[stream]['streamStatus']);
		
	}
	
}

function refreshEdgeWatcher() {
	
	var edgeId = null,
		target = null,
		edges  = s.graph.edges();
	
	if (edgeIdOnWatcher === null) {
		return;
	}
	
	edgeId = edgeIdOnWatcher;
	
	for (var i = 0; i < edges.length; i++) {
		if (edges[i].id === edgeId) {
			target = edges[i];
		}
	}
	
	if (target === null) {
		console.log('refreshEdgeWatcher: cannot find edge: ' + edgeId);
		return;
	}
	
	function updateAttr(target, attr) {
		var td = $(jq("#" + edgeId + attr)).children().first().next();
		for (var stream in target.streamMetricsMap) {
			td.text(target.streamMetricsMap[stream][attr]);
			td = td.next();
		}
	}
	
	updateAttr(target, 'averageTransferRate');
	updateAttr(target, 'currentTransferRate');
	updateAttr(target, 'averagePacketLoss');
	updateAttr(target, 'currentPacketLoss');
	updateAttr(target, 'avrEnd2EndLatency');
	updateAttr(target, 'avrLnk2LnkLatency');
	
}


/**
 * Called on "reset" button
 */
function resetSimulation(){
	console.log("Reset Simulation");
	loggerResource.message().to("/mdnsim/simulations").method("DELETE").data("reset").send();
}

var onResponse = function(msg) {
	console.log("OnResponse: " + msg.object);
}




/**
 * Whenever new input file is selected, it asks Master to create/update the simulation parameters
 * @param evt
 */
function handleWsFileSelect(evt, action) {
	var files = evt.target.files; // FileList object
	// Loop through the FileList 
	for (var i = 0, f; f = files[i]; i++) {
		var reader = new FileReader();
		// Closure to capture the file information.
		reader.onload = (function(theFile) {
			return function(e) {
				if (action == 'Start') {
					loggerResource.create("start").request().method("POST").to("/mdnsim/work_config").data(e.target.result).execute(onResponse);
					console.log("Start flows");
				} else if (action == 'Stop') {
					loggerResource.message().method("DELETE").to("/mdnsim/work_config").data(e.target.result).send();
					console.log("Stop flows");
				}
			};
		})(f);
		//Read the file
		reader.readAsBinaryString(f);
	}
}

/**
 * Warp related functions
 */
function initWarp(){
	console.log("start init");
	/**
	 * Registers the WebClient to Master Node when it is connected to the Warp network
	 */
	loggerResource.on("open", function() {
		console.log("Now registered at " + loggerResource.id());
		loggerResource.message().to("/mdnsim/register_webclient").method("POST").data(loggerResource.id()).send();
	});
	/**
	 * Create Resource Handler - used to initialize the graph object 
	 */
	
	loggerResource.create("create").on("POST", function(m) {
		console.log("Got initial graph: ");
		createGraph(m.object);
	});
	
	/**
	 * Update Resource Handler - used to refresh the graph to show current status
	 */

	loggerResource.create("update").on("POST", function(m) {
		console.log("Got update: ", m.object);
		refreshGraph(m.object);
	});
	
	console.log("finish init");

}
/**
 * Init function
 */
$(document).ready(function() {
	initWarp();	
	$("#btnReset").click(function(e){
		resetSimulation();
	});
	$("#flowaction").on('click', 'li a', function(event) {
		var $target = $( event.currentTarget );
		$target.closest( '.btn-group' )
		.find( '[data-bind="flowactionlabel"]' ).text( $target.text() )
		.end()
		.children( '.dropdown-toggle' ).dropdown( 'toggle' );
		/*
		 * After the file is read, the file input element does not
		 * trigger the onchange handler if the same file is selected.
		 * To enable us to use the same file for both starting and 
		 * stopping a flow, we reset the file input (wsinput) every time 
		 * the flow action is changed. This will,
		 * 1) Prevent uploading same file twice for an action selected
		 * 2) Allows use of same file when action is changed
		 */
		var fileInputElement = $("#wsinput");
		fileInputElement.replaceWith(fileInputElement = fileInputElement.clone(true));
		console.log('Reset wsinput');
		return false;
	});

	/*
	 * When the input file element is reset when flow action is changed, the event handlers
	 * for the input file element is lost. But, if we add the event handler on a parent 
	 * element (the span for the input file element), the handler wont be lost.
	 */
	$("#wsinputspan").on("change", "#wsinput", function(event) {
		var span = document.getElementById('flowactionspan');
		var action = '';
		if (span.innerText == 'Start Flows') {
			action = 'Start';
		} else if (span.innerText == 'Stop Flows') {
			action = 'Stop';
		} else {
			$("#messages").prepend("Please Select Flow Action and upload Work Specification<br/>");
			return;
		}
		handleWsFileSelect(event, action);
	});
	
	
});

/**
 * Escapes the special characters reserved by jquery 
 * @param myid
 * @returns {String}
 */
function jq( myid ) {	 
	return myid.replace( /(:|\.|\[|\])/g, "\\$1" ); 
}


var WorkConfig = function (simId, streamList) {
	  this.simId = simId;
	  this.streamList = streamList;
	  console.log('WorkConfig instantiated');
	};
	
var Stream = function(streamId, dataSize, kiloBitRate, flowList){
	this.streamId = streamId;
	this.dataSize = dataSize;
	this.kiloBitRate = kiloBitRate;
	this.flowList = flowList;
	console.log("Stream " + streamId + " created");
};	

var Flow = function(flowId, dataSize, streamId, kiloBitRate, nodeList){
	this.flowId = flowId;
	this.dataSize = dataSize;
	this.streamId = streamId;
	this.kiloBitRate = kiloBitRate;
	this.nodeList = nodeList;
	console.log("Flow " + flowId + " created");
};
var sourceNodeType = "SourceNode";
var sinkNodeType = "SinkNode";
var relayNodeType = "RelayNode";
var processingNodeType = "ProcessingNode";
var sourceNodeContainer = "nc_source";
var sinkNodeContainer = "nc_sink";
var relayNodeContainer = "nc_relay";
var processingNodeContainer = "nc_processing";
var nodeIdSeparator = ":";
var simId = "1";

function startMultipleSinks(nodesCount, streamId,  dataSize, kiloBitRate){
	var flowList = new Array();
	
	var nonSinkNodeList = new Array();
	
	var sourceNodeMap = {};
	sourceNodeMap["NodeType"] = sourceNodeType;
	sourceNodeMap["NodeId"] = sourceNodeContainer + nodeIdSeparator + sourceNodeType;
	sourceNodeMap["UpstreamId"] = "NULL";
	
	var processingNodeMap = {};
	processingNodeMap["NodeType"] = processingNodeType;
	processingNodeMap["NodeId"] = processingNodeContainer + nodeIdSeparator + processingNodeType;
	processingNodeMap["UpstreamId"] = sourceNodeMap["NodeId"];
	processingNodeMap["ProcessingLoop"] = "10000";
	processingNodeMap["ProcessingMemory"] = "100";
	
	var relayNodeMap = {};
	relayNodeMap["NodeType"] = relayNodeType;
	relayNodeMap["NodeId"] = relayNodeContainer + nodeIdSeparator + relayNodeType;
	relayNodeMap["UpstreamId"] = processingNodeMap["NodeId"];
	
	nonSinkNodeList.push(relayNodeMap);
	nonSinkNodeList.push(processingNodeMap);	
	nonSinkNodeList.push(sourceNodeMap);
	var j = 1;
	for(var i=nodesCount-3; i>0; i--){
		var sinkNodeMap = {};
		sinkNodeMap["NodeType"] = sinkNodeType;
		sinkNodeMap["NodeId"] = sinkNodeContainer + nodeIdSeparator + sinkNodeType + j;
		sinkNodeMap["UpstreamId"] = relayNodeMap["NodeId"];
		j++;
		var nodeList = new Array();
		nodeList.push(sinkNodeMap);		
		for(var k=0; k<nonSinkNodeList.length; k++){
			nodeList.push(nonSinkNodeList[k]);
		}
		
		flowList.push(new Flow("", dataSize, streamId,kiloBitRate,nodeList));
	}
	
	var streamList = new Array();
	streamList.push(new Stream(streamId,dataSize, kiloBitRate, flowList));
	
	var wc = new WorkConfig(simId, streamList);
	//console.log(wc);
	Warp.send({to: "warp://embedded:mdn-manager/work_config", data: wc});
	
}

/**
 * Starts new flows as specified in flowList.
 * @param flowList
 * @param streamId
 * @param dataSize
 * @param kiloBitRate
 */
function startFlow(flowList, streamId, dataSize, kiloBitRate){
	for(var i=0; i<flowList.length; i++){
		flowList[i].streamId = streamId;
		flowList[i].dataSize = dataSize;
		flowList[i].kiloBitRate = kiloBitRate;
	}
	var streamList = new Array();
	streamList.push(new Stream(streamId,dataSize, kiloBitRate, flowList));
	
	var wc = new WorkConfig(simId, streamList);
	//console.log(wc);
	Warp.send({to: "warp://embedded:mdn-manager/work_config", data: wc});
}
/**
 * Returns a new flow object with node list initialised.
 * The nodelist has to be in [label,label:nodeType,properties,label:nodeType,label] format 
 * 	starting from source and ending with sink node.
 * @param nodeList
 * @returns {Flow}
 */
function getFlow(nodeList){
	var nodes = new Array();
	var sourceNodeMap = null;
	var prevNodeMap = null;
	for(var i=0; i<nodeList.length; i++){
		var node = nodeList[i];
		if(typeof(node) == "string"){
			//Create new node
			var items = node.split(":");
			if(items.length == 1){
				if(sourceNodeMap == null){
					sourceNodeMap = {};
					sourceNodeMap["NodeType"] = sourceNodeType;
					sourceNodeMap["NodeId"] = items[0] + nodeIdSeparator + sourceNodeType;
					sourceNodeMap["UpstreamId"] = "NULL";
					nodes.push(sourceNodeMap);
					prevNodeMap = sourceNodeMap;
				}else{
					var sinkNodeMap = {};
					sinkNodeMap["NodeType"] = sinkNodeType;
					sinkNodeMap["NodeId"] =  items[0] + nodeIdSeparator + sinkNodeType;
					sinkNodeMap["UpstreamId"] = prevNodeMap["NodeId"];
					nodes.push(sinkNodeMap);
				}
			}else{
				var currentNodeMap = {};
				currentNodeMap["NodeType"] = items[1];
				currentNodeMap["NodeId"] = items[0] + nodeIdSeparator + items[1];
				currentNodeMap["UpstreamId"] = prevNodeMap["NodeId"];
				nodes.push(currentNodeMap);
				prevNodeMap = currentNodeMap;
			}
		}else{
			//Add parameters to prev node
			for(var prop in node){
				prevNodeMap[prop] = node[prop];
			}
		}
	}
	return new Flow("", "", "","",nodes.reverse());
}


function onClick(event) {
    window.console.log("clicked!");
} 

function overEdge(event) {
	console.log('over!');
}
