package edu.cmu.mdnsim.concurrent;

import com.ericsson.research.warp.util.WarpTask;

/**
 * 
 * MDNTask class is a wrapper class for WarpTask which automatically 
 * sets the context for Warp, so that multi-threading library in Java can 
 * be used.
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class MDNTask extends WarpTask {

	Runnable concreteTask;
	
	public MDNTask(Runnable task) {
		this.concreteTask = task;	
	}

	@Override
	public void perform() {
		this.concreteTask.run();
	}

}
