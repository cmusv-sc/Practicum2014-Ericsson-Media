package edu.cmu.mdnsim.nodes;

import java.util.concurrent.Future;

import edu.cmu.mdnsim.nodes.NodeRunnable.ReportRateRunnable;

class ReportTaskHandler {

	Future<?> reportFuture;
	ReportRateRunnable reportRunnable;

	public ReportTaskHandler(Future<?> future, ReportRateRunnable runnable) {
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