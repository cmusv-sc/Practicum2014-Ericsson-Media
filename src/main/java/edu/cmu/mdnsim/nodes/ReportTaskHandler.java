package edu.cmu.mdnsim.nodes;

import java.util.concurrent.Future;

import edu.cmu.mdnsim.reporting.NodeReporter;

class ReportTaskHandler {

	Future<?> reportFuture;
	NodeReporter reportRunnable;

	public ReportTaskHandler(Future<?> future, NodeReporter runnable) {
		this.reportFuture = future;
		reportRunnable = runnable;
	}

	public void kill() {
		reportRunnable.kill();
	}

	public boolean isDone() {
		return reportFuture.isDone();
	}

}