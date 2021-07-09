/*
 * Copyright (c) 2013 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.jberet.runtime.runner;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import jakarta.batch.runtime.BatchStatus;

import org.jberet.job.model.Flow;
import org.jberet.job.model.JobElement;
import org.jberet.runtime.context.AbstractContext;
import org.jberet.runtime.context.FlowContextImpl;
import org.jberet.spi.JobTask;

import static org.jberet._private.BatchLogger.LOGGER;

public final class FlowExecutionRunner extends CompositeExecutionRunner<FlowContextImpl> implements JobTask {
    private final Flow flow;
    private final CountDownLatch latch;

    public FlowExecutionRunner(final FlowContextImpl flowContext, final CompositeExecutionRunner enclosingRunner, final CountDownLatch latch) {
        super(flowContext, enclosingRunner);
        this.flow = flowContext.getFlow();
        this.latch = latch;
    }

    @Override
    protected List<? extends JobElement> getJobElements() {
        return flow.getJobElements();
    }

    @Override
    public void run() {
        batchContext.setBatchStatus(BatchStatus.STARTED);
        jobContext.setBatchStatus(BatchStatus.STARTED);

        try {
            runFromHeadOrRestartPoint(null);
        } catch (Throwable e) {
            LOGGER.failToRunJob(e, jobContext.getJobName(), flow.getId(), flow);
            batchContext.setBatchStatus(BatchStatus.FAILED);
            for (final AbstractContext c : batchContext.getOuterContexts()) {
                c.setBatchStatus(BatchStatus.FAILED);
            }
        } finally {
            if (latch != null) {
                latch.countDown();
            }
        }

        if (batchContext.getBatchStatus() == BatchStatus.STARTED) {  //has not been marked as failed, stopped or abandoned
            batchContext.setBatchStatus(BatchStatus.COMPLETED);
        }

        if (batchContext.getBatchStatus() == BatchStatus.COMPLETED) {
            final String next = resolveTransitionElements(flow.getTransitionElements(), flow.getAttributeNext(), false);
            enclosingRunner.runJobElement(next, batchContext.getFlowExecution().getLastStepExecution());
        }
    }
}
