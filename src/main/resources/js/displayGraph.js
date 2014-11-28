//Required for custom edge rendering - works only with canvas renderer
sigma.utils.pkg('sigma.canvas.edges');
/**
 * The following function defines new type of edge render. 
 * The name of new type = t
 * Use the name of new type in the graph JSON when creating/refreshing the graph
 */
sigma.canvas.edges.t = function(edge, source, target, context, settings) {
	var color = edge.color,
	prefix = settings('prefix') || '',
	edgeColor = settings('edgeColor'),
	defaultNodeColor = settings('defaultNodeColor'),
	defaultEdgeColor = settings('defaultEdgeColor');
	if (!color)
		switch (edgeColor) {
		case 'source':
			color = source.color || defaultNodeColor;
			break;
		case 'target':
			color = target.color || defaultNodeColor;
			break;
		default:
			color = defaultEdgeColor;
		break;
		}
	context.strokeStyle = color;
	context.lineWidth = edge[prefix + 'size'] || 1;
	context.lineWidth = 5;
	context.beginPath();
	context.moveTo(
			source[prefix + 'x'],
			source[prefix + 'y']
	);
	context.lineTo(
			target[prefix + 'x'],
			target[prefix + 'y']
	);
	// context.lineTo(
	//   source[prefix + 'x'],
	//   target[prefix + 'y']
	// );
	// context.lineTo(
	//   target[prefix + 'x'],
	//   target[prefix + 'y']
	// );
	context.stroke();
};
//used to show logs in the web client
var loggerResource = Warp.rootResource.createResource("/logger");
//Represents Sigma object
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
 * Called on click of start button. 
 * It reads the files at path specified in file input box and sends them to Master Node. 
 */
function startSimulation(){
	console.log("startSimulation");
	Warp.send({to: "warp://embedded:mdn-manager/start_simulation", data: "start"});
}
/**
 * Called on "reset" button
 */
function resetSimulation(){
	console.log("Reset Simulation");
	Warp.send({to: "warp://embedded:mdn-manager/simulations", method: "DELETE", data: "reset"});
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
					Warp.send({to: "warp://embedded:mdn-manager/work_config", data: e.target.result});
					console.log("Start flows");
				} else if (action == 'Stop') {
					Warp.send({to: "warp://embedded:mdn-manager/work_config", method: "DELETE", data: e.target.result});
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
	Warp.on("connect", function() {
		console.log("Now registered at " + Warp.uri);
		Warp.send({to: "warp://embedded:mdn-manager/register_webclient", data: Warp.uri});
	});
	/**
	 * Generic Post and Message Handlers
	 */
	Warp.on({		
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
			}
		}
	});
	/**
	 * Create Resource Handler - used to initialize the graph object 
	 */
	Warp.at("/create").on("message", function(m) {
		console.log("Got initial graph: ");
		console.log(m.object);
		createGraph(m.object);
	});
	/**
	 * Update Resource Handler - used to refresh the graph to show current status
	 */
	Warp.at("/update").on("message", function(m) {
		//console.log("Got update: " );
		//console.log(m.object);
		refreshGraph(m.object);
		//createGraph(m.object);
	});
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
	loggerResource.sendTo("warp://embedded:mdn-manager/_warp/plugins/logger", "POST");
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
