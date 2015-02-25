package edu.cmu.mdnsim.concurrent;


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
public class MDNTask implements Runnable {

	Runnable concreteTask;
	
	public MDNTask(Runnable task) {
		this.concreteTask = task;	
	}

	public void run() {
		this.concreteTask.run();
	}

}
