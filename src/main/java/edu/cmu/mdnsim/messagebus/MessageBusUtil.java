package edu.cmu.mdnsim.messagebus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import us.yamb.rmb.impl.ReflectionListener;

class MessageBusUtil {

	static Method parseMethod(Object object, String name) throws IllegalArgumentException {
		Method[] methods = object.getClass().getMethods();
		for (Method method : methods) {
			if (method.getName().equals(name)) {
				return method;
			}
		}
		throw new IllegalArgumentException("Cannot found method with name: " + name);
	}
	
	static Field getField(ReflectionListener listener, String fieldName) throws NoSuchFieldException{
		
		for(Field f : listener.getClass().getDeclaredFields()) {
			if (f.getName().equals(fieldName)) {
				return f;
			}
		}
		
		throw new NoSuchFieldException("Cannot find rmb field in " + listener.getClass().getName());
	}
	
	
}
