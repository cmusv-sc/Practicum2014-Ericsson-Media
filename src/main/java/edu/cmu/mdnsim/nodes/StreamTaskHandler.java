package edu.cmu.mdnsim.nodes;

import java.util.concurrent.Future;

class StreamTaskHandler<T extends NodeRunnable> {
	
	Future<?> streamFuture;
	T streamTask;

	public StreamTaskHandler(Future<?> streamFuture, T streamTask) {
		this.streamFuture = streamFuture;
		this.streamTask = streamTask;
	}

	/**
	 * Reset the NodeRunnable. The NodeRunnable should be interrupted (set killed),
	 * and set reset flag as actions for clean up is different from being killed.
	 */
	public void reset() {
		streamTask.reset();
	}

	public void kill() {
		streamTask.kill();
	}

	public boolean isDone() {
		return streamFuture.isDone();
	}

	public void clean() {
		streamTask.clean();
	}

	public String getStreamId() {
		return streamTask.getStreamId();
	}
}