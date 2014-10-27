package edu.cmu.mdnsim.datastructure;

import static org.junit.Assert.*;

public class OneToOneMapTest {

	@org.junit.Test
	public void putAndGetTest() {
		
		OneToOneMap<Integer, String> map = new OneToOneMapImpl<Integer, String>();
		map.put(1, "1");
		map.put(2, "2");
		
		assertEquals("Get method:", map.getValue(2), "2");
		
		assertEquals("Get method:", (Integer)map.getKey("1"), (Integer)1);
		
		
	}
	
}
