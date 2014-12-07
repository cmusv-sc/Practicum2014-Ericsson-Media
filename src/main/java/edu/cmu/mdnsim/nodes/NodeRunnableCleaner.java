package edu.cmu.mdnsim.nodes;

/**
 * 
 * NodeRunnableCleaner interface provides the way to allow subclasses of 
 * {@link NodeRunnable} to remove itself from table in {@link AbstractNode}
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public interface NodeRunnableCleaner {
	
	public void removeNodeRunnable(String streamId);
	
}
