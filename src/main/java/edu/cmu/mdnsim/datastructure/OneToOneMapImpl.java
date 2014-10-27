package edu.cmu.mdnsim.datastructure;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OneToOneMapImpl<K, V> implements OneToOneMap<K, V> {
	
	private Map<K, V> keyToValueMap = new ConcurrentHashMap<K, V>();
	private Map<V, K> valueToKeyMap = new ConcurrentHashMap<V, K>();
	
	@Override
	public void clear() {
		keyToValueMap.clear();
		valueToKeyMap.clear();
	}

	@Override
	public boolean containsKey(Object key) {
		return keyToValueMap.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return keyToValueMap.containsValue(value);
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return keyToValueMap.entrySet();
	}

	@Override
	public V get(Object key) {
		return keyToValueMap.get(key);
	}

	@Override
	public boolean isEmpty() {
		return keyToValueMap.isEmpty();
	}

	@Override
	public Set<K> keySet() {
		return keyToValueMap.keySet();
	}

	@Override
	public V put(K key, V value) {
		this.keyToValueMap.put(key, value);
		this.valueToKeyMap.put(value, key);
		return value;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		keyToValueMap.putAll(m);
		valueToKeyMap.clear();
		for(K key : keyToValueMap.keySet()) {
			V value= keyToValueMap.get(key);
			valueToKeyMap.put(value, key);
		}
	}

	@Override
	public V remove(Object key) {
		V rst = keyToValueMap.remove(key);
		if (rst != null) {
			valueToKeyMap.remove(rst);
		}
		return rst;
	}

	@Override
	public int size() {
		return keyToValueMap.size();
	}

	@Override
	public Collection<V> values() {
		return keyToValueMap.values();
	}
	
	public K getKey(V value) {
		return valueToKeyMap.get(value);
	}
	
	public V getValue(K key) {
		return keyToValueMap.get(key);
	}
	
}
