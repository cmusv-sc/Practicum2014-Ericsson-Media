//used to show logs in the web client
// var loggerResource = Warp.rootResource.createResource("/logger");
var loggerResource = RMB.builder().seed("http://127.0.0.1:8888/_connectTrap\nws://127.0.0.1:8888/_connectTrapWS").id("webclient").build();
//Represents Sigma object - Main object which holds the graph
var s = null;
//Represents the camera object used by sigma library
var cam = null;
/**
 * Renders Graph for first time 
 * @param initial_data The graph object in JSON format (sample given below)
 * //	initial_data = {"edges":
//	[{"source":"1","target":"2","id":"100"},{"source":"1","target":"3","id":"101"}],
//	"nodes":[{"label":"Source","x":0.1,"y":0.1,"id":"1","color":"rgb(0,204,0)","size":6,"tag":"This is source node"},
//	{"label":"Client","x":0.5,"y":0.5,"id":"2","color":"rgb(0,204,204)","size":6,"tag":"This is client node"},
//	{"label":"Client2","x":0.2,"y":0.6,"id":"3","color":"rgb(204,0,0)","size":6,"tag":"This is client node2"}
//	]}
 */
function createGraph(initial_data){
	if(s){
		s.graph.clear();		
		s.graph.read(initial_data);		
	}else{
		s = new sigma({
			graph: initial_data
		});
		cam = s.addCamera();
		s.addRenderer({
			container: $("#svg")[0],
			type: 'svg',
			camera: cam
		});	
	}
	s.refresh();   
	attachNodeEvents();
	attachEdgeEvents();
}
/**
 * Attaches event handlers for all Events (currently mouse over and out) for each Node in the graph
 */
function attachNodeEvents(){
	//First function handles mouse over and second one handles mouse out
	//On Mouse Over a tooltip is shown which can be customized as required
	//And on mouse out, the tooltip is removed
	$("circle").hover(function(e){
		var nodeId = $(this)[0].attributes["data-node-id"].value;
		var x = e.pageX - this.offsetLeft;
		var y = e.pageY - this.offsetTop;

		if(s.graph.nodes(nodeId).tag){
			$("<div id=d"+nodeId+" class='tag'></div>")
			.html(s.graph.nodes(nodeId).tag)
			.appendTo('body')
			.fadeIn('slow');
			$('#d' + jq(nodeId)).css({ top: y, left: x, opacity:1 });
		}
		//This is used to update the tooltip automatically on hover
		self.updateNodeTag = function() {
			$(".tag").html(s.graph.nodes(nodeId).tag);
		}

	},function(e){
		var nodeId = $(this)[0].attributes["data-node-id"].value;
		$('#d' + jq(nodeId)).remove();
		delete self.updateNodeTag;
	}
	);   
}


/**
 * Attaches event handlers for all Events (currently mouse over and out) for each Edge in the graph
 * TODO: Find a way to increase width of edges
 */
function attachEdgeEvents(){
	//First function handles mouse over and second one handles mouse out
	//On Mouse Over a tooltip is shown which can be customized as required
	//And on mouse out, the tooltip is removed
	$("line").hover(function(e){
		var lineId = $(this)[0].attributes["data-edge-id"].value;
		var x = e.pageX - this.offsetLeft;
		var y = e.pageY - this.offsetTop;
		if(s.graph.edges(lineId).tag){			
			$("<div id=d"+lineId+" class='tag'></div>")
			.html(s.graph.edges(lineId).tag)
			.appendTo('body')
			.fadeIn('fast');
			$('#d' + jq(lineId)).css({ top: y, left: x, opacity:1 });
		}
		//This is used to update the tooltip automatically on hover
		self.updateEdgeTag = function() {
			$(".tag").html(s.graph.edges(lineId).tag);
		}
	},function(e){
		var lineId = $(this)[0].attributes["data-edge-id"].value;
		$('#d' + jq(lineId)).remove();
		delete self.updateEdgeTag;
	}
	);
}

/**
 * Refreshes the graph based on updated JSON
 * @param updated_data Graph JSON
 */
function refreshGraph(updated_data){
//	updated_data = {"edges":[{"source":"1","target":"2","id":"100","type":"t"},{"source":"1","target":"3","id":"101"}],
//	"nodes":[{"label":"Source","x":0.1,"y":0.1,"id":"1","color":"rgb(0,204,0)","size":6,"tag":"This is source node"},
//	{"label":"Client","x":0.5,"y":0.5,"id":"2","color":"rgb(0,204,204)","size":6,"tag":"This is client node"},
//	{"label":"Client2","x":0.2,"y":0.6,"id":"3","color":"rgb(204,0,0)","size":6,"tag":"This is client node2"}]};
	//console.log(updated_data);
	var nodes = updated_data.nodes;
	var edges = updated_data.edges;	
	if(s != null){		
		if(s.graph.nodes().length == nodes.length){
			//Create a HashMap of updated nodes
			var nodesMap = {};			
			for(var i=0; i<nodes.length; i++){
				nodesMap[nodes[i].id] = nodes[i];
			}
			//Update the graph nodes with new values for color, size and tooltip (tag)
			for(var i=0; i<s.graph.nodes().length; i++){
				var graphNode = s.graph.nodes()[i];
				graphNode.color = nodesMap[graphNode.id].color;
				graphNode.size = nodesMap[graphNode.id].size;
				graphNode.tag = nodesMap[graphNode.id].tag;
			}
			//This function will update the hover if it is currently being displayed
			if(self.updateNodeTag)
				self.updateNodeTag();
			//Same things for updating edges. But in edges, we need to manually change the color and size
			var edgesMap = {};
			for(var i=0; i<edges.length; i++){
				edgesMap[edges[i].id] = edges[i];
			}
			for(var i=0; i<s.graph.edges().length; i++){
				var graphEdge = s.graph.edges()[i];
				graphEdge.color = edgesMap[graphEdge.id].color;
				graphEdge.size = edgesMap[graphEdge.id].size;
				graphEdge.tag = edgesMap[graphEdge.id].tag;
				$( "line[data-edge-id='"+graphEdge.id+"']" ).css("stroke",graphEdge.color);
				$( "line[data-edge-id='"+graphEdge.id+"']" ).css("stroke-width",graphEdge.size);	
			}
			if(self.updateEdgeTag)
				self.updateEdgeTag();
		}else{
			//Clear the graph and add new nodes and edges. 
			//we can use lastHoveredElement variable for displaying hover 
			s.graph.clear();
			for(var i=0; i<nodes.length; i++){			
				s.graph.addNode(nodes[i]);
			}		
			for(var i=0; i<edges.length; i++){			
				s.graph.addEdge(edges[i]);
				$( "line[data-edge-id='"+edges[i].id+"']" ).css("stroke",edges[i].color);
				$( "line[data-edge-id='"+edges[i].id+"']" ).css("stroke-width",edges[i].size);			
			}
			//s.graph.read(updated_data);		
			//s.refresh();   
			attachNodeEvents();
			attachEdgeEvents();
		}		
	}else{
		updated_data = {"nodes":nodes,"edges":edges};
		createGraph(updated_data);
	}
}
/**
 * Called on "reset" button
 */
function resetSimulation(){
	console.log("Reset Simulation");
	loggerResource.message().to("/mdnsim/simulations").method("DELETE").data("reset").send();
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
					loggerResource.message().to("/mdnsim/work_config").data(e.target.result).send();
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
	/**
	 * Registers the WebClient to Master Node when it is connected to the Warp network
	 */
	loggerResource.on("open", function() {
		console.log("Now registered at " + loggerResource.id());
		loggerResource.message().to("/mdnsim/register_webclient").method("POST").data(loggerResource.id()).send();
	});
	/**
	 * Generic Post and Message Handlers
	 */
	loggerResource.on({		
		post: function(m) {
			console.log("POST data: " + m.text); 
		},
		message: function(m) {
			console.log("Got uncaught message: " + m.text); 			
		},
		delete: function(m) {
			if(s != null) {		
				s.graph.clear();
				s.refresh();
				$("#messages").html(""); //Clear the log messages
			}
		}
	});
	/**
	 * Create Resource Handler - used to initialize the graph object 
	 */
	loggerResource.create("create").on("message", function(m) {
		console.log("Got initial graph: ");
		console.log(m.object);
		createGraph(m.object);
	});
	
	/**
	 * Update Resource Handler - used to refresh the graph to show current status
	 */

	loggerResource.create("update").on("message", function(m) {
		console.log("Got update: ");
		console.log(m.object);
		refreshGraph(m.object);
	})
	/**
	 * Log Resource Handler - used to display log messages
	 */
	loggerResource.onmessage = function(m){
		//console.log(m);
		var entries = JSON.parse(m.dataAsString);
		//console.log(entries);
		for(var i=0; i<entries.length; i++){			
			if(entries[i].logger === "webclientgraph" || entries[i].logger === "master")
				$("#messages").prepend(entries[i].message + "<br/>");
		}		
	}
	//Subscribe to logger plugin
//	loggerResource.sendTo("warp://embedded:mdn-manager/_warp/plugins/logger", "POST");
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

