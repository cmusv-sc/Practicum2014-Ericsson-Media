(function(target) {
	
	var AMB = function(uri, id) {
		var tmb = new TMB.Client(id, uri);
		var channels = {};
		tmb.onchannel = function(m) {
			try
			{
				channels[m.channel](m);
			}
			catch(e)
			{
				console.error(e.stack);
			}
		}
		tmb.ondirect = function(m) { if (!!amb.onmessage) amb.onmessage(m); };
		tmb.onopen = function(m) { if(!!amb.onopen) amb.onopen(m); };
		var amb = {
			id: function() { return tmb.name; },
			channel: function() {
				var chname;
				return {
					name: function(name) {
						chname = name; return this;
					},
					build: function() {
						var rv = {
							name: chname,
							send: function(msg) {
								tmb.publish(chname, msg);
							},
							join: function() {
								tmb.subscribe(chname);
							},
							leave: function() {
								tmb.unsubscribe(chname);
							},
							send: function(msg) {
								
							}
						};
						
						rv.__defineSetter__("onmessage", function(cb) {
							channels[chname]=cb;
						});
						
						return rv;
					}
				};
			},
			message: function() {
				var to, from, data;
				return {
					to: function(str) { to = str; return this; },
					from: function(str) { from = str; return this; },
					data: function(str) { data = str; return this; },
					send: function() {
						tmb.send(to, data);
					}
				};
			}
		};
		return amb;
	};
	
	var builder = function() {
		var uri, id;
		return {
			seed: function(seedPeerInfo) {
				uri = seedPeerInfo;
				return this;
			},
			id: function(requestedId) {
				id=requestedId;
				return this;
			},
			configure: function() {
				return this;
			},
			build: function() {
				return AMB(uri, id);
			}
		};
	};
	
	var __defineGetter = function(object, getterName, cb)
	{
		
		var newName = "get" + getterName.substr(0,1).toUpperCase() + getterName.substr(1);
		
		if (!cb)
		{
			var privateName = "_" + getterName;
			
			cb = function() {
				return this[privateName];
			};
		}
		
		if (object.__defineGetter__)
		{
			try
			{
				object.__defineGetter__(getterName, cb);
			} catch(e){}
		}
		
		// Also create the getter function as a property of the object
		object[newName] = cb;
	};
	
	target.AMB = { builder: builder };
	
})(self);
