//Required for custom edge rendering
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
		s.refresh();  
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
		// Refresh the instance to refresh the new svg renderer
		s.refresh();
	}
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
			$("<p id=p"+nodeId+" class='tooltip'></p>")
				.text(s.graph.nodes(nodeId).tag)
				.appendTo('body')
				.fadeIn('slow');
			$('#p' + jq(nodeId)).css({ top: y, left: x });
		}
	},function(e){
		var nodeId = $(this)[0].attributes["data-node-id"].value;
		$('#p' + jq(nodeId)).remove();
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
			//$('#'+jq(lineId)).css({stroke-width: 5});
			$("<p id=p"+lineId+" class='tooltip'></p>")
			.text(s.graph.edges(lineId).tag)
			.appendTo('body')
			.fadeIn('slow');
			$('#p' + jq(lineId)).css({ top: y, left: x });
		}
	},function(e){
		var lineId = $(this)[0].attributes["data-edge-id"].value;
		$('#p' + jq(lineId)).remove();
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
	//TODO: Find out way to update the graph without refreshing the entire graph
	s.graph.clear();
	s.graph.read(updated_data);
	s.refresh();  
}
/**
 * Called on click of start button. 
 * It reads the files at path specified in file input box and sends them to Master Node.
 * TODO: Input validation (like file exists or not, valid file type)
 */
function startSimulation(){
	console.log("startSimulation");
	Warp.send({to: "warp://cmu-sv:mdn-manager/start_simulation",data: "start"});
}

function handleWsFileSelect(evt) {
	var files = evt.target.files; // FileList object
	// Loop through the FileList and render image files as thumbnails.
	for (var i = 0, f; f = files[i]; i++) {
		var reader = new FileReader();

		// Closure to capture the file information.
		reader.onload = (function(theFile) {
			return function(e) {
				Warp.send({to: "warp://cmu-sv:mdn-manager/validate_user_spec", data: e.target.result});
				console.log("Hello from handleWsFileSelect");
			};
		})(f);

		// Read in the image file as a data URL.
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
		Warp.send({to: "warp://cmu-sv:mdn-manager/register_webclient", data: Warp.uri});
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
		}		
	});
	/**
	 * Create Resource Handler - used to initialize the graph object 
	 */
	Warp.at("/create").on("message", function(m) {
		console.log("Got initial graph: " + m.object);
		createGraph(m.object);
	});
	/**
	 * Update Resource Handler - used to refresh the graph to show current status
	 */
	Warp.at("/update").on("message", function(m) {
		console.log("Got update: " + m.object);
		refreshGraph(m.object);
	});
}
$(document).ready(function() {
	initWarp();
	$("#btnStart").click(function(e){
		startSimulation();
	});
	
	document.getElementById('wsinput').onchange = function(event) {
		handleWsFileSelect(event);
	};
});
/**
 * Escapes the special characters reserved by jquery 
 * @param myid
 * @returns {String}
 */
function jq( myid ) {	 
    return myid.replace( /(:|\.|\[|\])/g, "\\$1" ); 
}
