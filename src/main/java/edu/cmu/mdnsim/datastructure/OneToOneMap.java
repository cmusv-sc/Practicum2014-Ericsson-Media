package edu.cmu.mdnsim.datastructure;

import java.util.Map;

/**
 * This OneToOneMap interface is used for maintain key-value pair whose relationship
 * is one-to-one. This interface eases to look up key based on value and the time
 * complexity of this method is O(1).
 * 
 * @author JeremyFu
 *
 * @param <K>
 * @param <V>
 */
public interface OneToOneMap<K, V> extends Map<K, V> {
	
	public K getKey(V value);
	
	public V getValue(K key);
	
}