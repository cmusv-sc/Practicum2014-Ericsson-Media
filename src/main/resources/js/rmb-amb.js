(function(target) {

	var Trap = {};
	
	Trap._uidCounter = 0;
	Trap._uid = function()
	{
		return Math.random().toString(16).substring(2) + (Trap._uidCounter++).toString(16);
	};
	
	Trap.EventObject = function()
	{
		this._eventlistenersMap = {};
	};

	Trap.EventObject.prototype.addEventListener = function(type, listener) {
	    if (!this._eventlistenersMap[type])
	        this._eventlistenersMap[type] = [];
	    var eventlisteners = this._eventlistenersMap[type];
	    for (var i = 0; i<eventlisteners.length; i++) {
	        if(listener === eventlisteners[i])
	            return;
	    }
	    eventlisteners[i] = listener;
	};

	Trap.EventObject.prototype.removeEventListener = function(type, listener) {
	    if (!this._eventlistenersMap[type])
	        return;
	    var eventlisteners = this._eventlistenersMap[type];
	    for (var i = 0; i < eventlisteners.length; i++) {
	        if (listener === eventlisteners[i]) {
	            eventlisteners.splice(i,1);
	            break;
	        }
	    }
	};

	Trap.EventObject.prototype.on = Trap.EventObject.prototype.addEventListener;
	Trap.EventObject.prototype.off = Trap.EventObject.prototype.removeEventListener;

	Trap.EventObject.prototype._dispatchEvent = function(evt) {
	    var listeners = this._eventlistenersMap[evt.type];
	    
	    if (!evt.target)
	    	evt.target = this;
	    
	    var found = false;
	    
	    if(!!listeners)
	    {
	    	for (var i = 0; i < listeners.length; i++)
	    	{
	    		try
	    		{
	        		listeners[i](evt);
	        		found = true;
	    		}
	    		catch (e)
	    		{
	    			if (this.logger)
	    			{
	    				this.logger.warn("Exception while dispatching event to listener; ", e, " to ", listeners[i], ". Event was ", evt);
	    			}
	    		}
	    		
	    	}
	    }
	    
	    var f;
		try
		{
		    f = this["on"+evt.type];
		    if (f && typeof(f) == "function") {
		    	f.call(this, evt);
		    	found = true;
		    }
		}
		catch (e)
		{
			if (this.logger)
			{
				this.logger.warn("Exception while dispatching event to listener; ", e, " to ", f, ". Event was ", evt);
			}
		}
		
		return found;
	};
	
	var toJSONByteArray = function(s) {
		var bytes = [];

		for (var i = 0; i < s.length; i++) {
			var c = s.charCodeAt(i);
			bytes.push(c);
		}

		return bytes;
	};
	
	var fromJSONByteArray = function(arr, offset, length)
	{
		var str = "";
		if (typeof(offset) == "undefined")
		{
			offset = 0; length = arr.length;
		}
		
		for (var i=offset; i<length; i++)
			str += String.fromCharCode(arr[i]);
		
		return str;
	};
	
	Trap._compat = {};

	Trap._compat.capitalise = function(str)
	{
		return str.substr(0,1).toUpperCase() + str.substr(1);
	};

	Trap._compat.__defineSetter = function(object, setterName, cb)
	{
		
		var newName = "set" + Trap._compat.capitalise(setterName);
		
		if (!cb)
		{
			var privateName = "_" + setterName;
			cb = function(val) {
				this[privateName] = val;
				return this;
			};
		}
		
		if (object.__defineSetter__)
		{
			try
			{
				object.__defineSetter__(setterName, cb);
			} catch(e){}
		}

		// Also create the getter function as a property of the object
		object[newName] = cb;
	};

	Trap._compat.__defineGetter = function(object, getterName, cb)
	{
		
		var newName = "get" + Trap._compat.capitalise(getterName);
		
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

	Trap._compat.__defineGetterSetter = function(object, publicName, privateName, getter, setter)
	{
		if (!privateName)
			privateName = "_" + publicName;
		
		if (!getter)
		{
			getter = function() {
				return this[privateName];
			};
		}
		
		if (!setter)
		{
			setter = function(val) {
				this[privateName] = val;
				return this;
			};
		}

		Trap._compat.__defineSetter(object, publicName, setter);
		Trap._compat.__defineGetter(object, publicName, getter);
	};
	
	var Location = function(src) {
		var qpos = src.search("\\?");
		
		this.path = "";
		this.query = "";
		this.location = src;
		
		if (qpos > -1)
		{
			this.path = src.substring(0, qpos);
			this.query = src.substring(qpos+1);
		}
		else
		{
			this.path = src;
		}
		
		this.parts = this.path.split("/");
		
	}
	
	var Message = function(){};
	Message.prototype.string = function() {
		return fromJSONByteArray(this.data);
	};
	Message.prototype.object = function() {
		return JSON.parse(this.string);
	};
	
	var dispatchFun = function(msg) {
		var to = msg.location.parts[msg.idx];
		if (this._id == to)
		{
			msg.idx++;
			if (msg.location.parts.length == msg.idx)
			{
				if (!!msg.method)
				{
					msg.type = msg.method;
					if (this._dispatchEvent(msg))
						return true;
				}
				
				msg.type = "message";
				return this._dispatchEvent(msg);
			}
			
			var child = this.children[msg.location.parts[msg.idx]];
			
			if (!!child)
				return child._dispatchEvent(msg);
		}
		
		
		return false;
	};
	
	var RMB = function(amb, options) {
		
		if (!!amb) {
			var rmb = this;
			Trap.EventObject.prototype.constructor.call(this);
			this._amb = amb;
			amb.onopen = function() { rmb._id = amb.id(); rmb._dispatchEvent({type: "open"}); };
			amb.onmessage = function(msg) { 
				var evt = JSON.parse(msg.string);
				evt.type = "dispatch";
				evt.location = new Location(evt.to);
				evt.idx = 1;
				Trap._compat.__defineGetter(evt, "string", Message.prototype.string);
				Trap._compat.__defineGetter(evt, "object", Message.prototype.object);
				rmb._dispatchEvent(evt);
			};
			this.ondispatch = dispatchFun;
			this.children = {};
		}
	};
	
	RMB.prototype = new Trap.EventObject;
	RMB.prototype.constructor = RMB;
	
	RMB.prototype.create = function(id) {
		if (!id)
			id=Trap._uid();
		
		if (!!this.children[id]) 
			return this.children[id];
		
		return this.children[id] = new Child(this, id);
	};
	
	RMB.prototype.get = function(to) {
		var req = this.request().method("GET");
		if (!!to) req.to(to);
		return req;
	};
	
	RMB.prototype.put = function(to) {
		var req = this.request().method("PUT");
		if (!!to) req.to(to);
		return req;
	};
	
	RMB.prototype.post = function(to) {
		var req = this.request().method("POST");
		if (!!to) req.to(to);
		return req;
	};
	
	RMB.prototype.delete = function(to) {
		var req = this.request().method("DELETE");
		if (!!to) req.to(to);
		return req;
	};
	
	RMB.prototype.request = function(src) {
		if (!src) src = this;
		var msg = {};
		var rmb = this;
		return {
			to: function(str) { msg.to = str.location ? str.location : str;  return this;},
			data: function(data) {
				if (typeof(data) == "string")
					msg.data = toJSONByteArray(data);
				if (typeof(data) == "object")
					msg.data = toJSONByteArray(JSON.stringify(data));
				 return this;
			},
			confirmed: function(bool) { msg.confirmed = !!bool; return this; },
			method: function(str) { msg.method = str;  return this;},
			status: function(num) { msg.status = num;  return this;},
			execute: function(cb) {
				var res = src.create();
				res.onmessage = function(msg) {
					cb(msg);
				};
				msg.from = res.id();
				var payload = JSON.stringify(msg);
				var loc = new Location(msg.to);
				rmb._amb.message().to(loc.parts[1]).from(rmb.id()).data(payload).send();
				
			}
		};
	};
	
	RMB.prototype.message = function(src) {
		if (!src) src = this;
		var msg = {};
		var rmb = this;
		return {
			to: function(str) { msg.to = str.location ? str.location : str; return this;},
			data: function(data) {
				if (typeof(data) == "string")
					msg.data = toJSONByteArray(data);
				if (typeof(data) == "object")
					msg.data = toJSONByteArray(JSON.stringify(data));
				return this;
			},
			confirmed: function(bool) { msg.confirmed = !!bool; return this;},
			method: function(str) { msg.method = str; return this;},
			status: function(num) { msg.status = num; return this;},
			send: function() {
				msg.from = src.id();
				var payload = JSON.stringify(msg);
				var loc = new Location(msg.to);
				rmb._amb.message().to(loc.parts[1]).from(rmb.id()).data(payload).send();
				
			}
		};
	};
	
	RMB.prototype.pipe = function() {
		return {
			
		};
	};
	
	RMB.prototype.channel = function() {
		return this._amb.channel();
	};
	
	RMB.prototype.connect = function() {
		// Does not need to be called.
	};
	
	RMB.prototype.disconnect = function() {
		this._amb.disconnect();
	};
	
	RMB.prototype.id = function() {
		return "/"+this._amb.id();
	};
	
	var Child = function(parent, id) {
		Trap.EventObject.prototype.constructor.call(this);
		this.parent = parent;
		this._id = id;
		this.ondispatch = dispatchFun;
		this.children = {};
	}

	Child.prototype = new RMB;
	Child.prototype.constructor = Child;
	
	Child.prototype.id = function() {
		return this.parent.id() + "/" + this._id;
	};
	
	Child.prototype.request = function(src) {
		return this.parent.request(src || this);
	}
	
	Child.prototype.message = function(src) {
		return this.parent.message(src || this);
	}
	
	
	var builder = function() {
		var seedArg, idArg;
		return {
			seed: function(src) { seedArg = src; return this; },
			id: function(src) { idArg = src; return this; },
			build: function() {
				var amb = AMB.builder().seed(seedArg).id(idArg).build();
				return new RMB(amb, {seed: seedArg, id: idArg});
			}
		};
	};
	
	target.RMB = {
			builder: builder
	};
	
})(self);
