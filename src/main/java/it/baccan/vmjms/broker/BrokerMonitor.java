/*
 * vmjms - in-memory Jakarta JMS provider
 * https://github.com/matteobaccan/vmjms
 *
 * Copyright (c) 2026 Matteo Baccan
 * SPDX-License-Identifier: MIT
 */
package it.baccan.vmjms.broker;

import java.util.Map;

/**
 * Standard MBean implementation reading the state of one {@link InMemoryBroker}.
 *
 * <p>Separate from the broker so that enabling monitoring adds no JMX concern to the
 * messaging API, and so that only the attributes and operations declared by
 * {@link BrokerMonitorMBean} are exposed.</p>
 */
public class BrokerMonitor implements BrokerMonitorMBean {

    private final InMemoryBroker broker;

    BrokerMonitor(InMemoryBroker broker) {
        this.broker = broker;
    }

    @Override
    public String getBrokerId() {
        return broker.getBrokerId();
    }

    @Override
    public int getMaxMessages() {
        return broker.getMaxMessages();
    }

    @Override
    public int getMessagesWaiting() {
        return broker.getMessagesWaiting();
    }

    @Override
    public int getQueueCount() {
        return broker.getQueueCount();
    }

    @Override
    public Map<String, Integer> getQueueDepths() {
        return broker.getQueueDepths();
    }

    @Override
    public long getEnqueuedCount() {
        return broker.getEnqueuedCount();
    }

    @Override
    public long getDeliveredCount() {
        return broker.getDeliveredCount();
    }

    @Override
    public long getExpiredCount() {
        return broker.getExpiredCount();
    }

    @Override
    public long getPurgedCount() {
        return broker.getPurgedCount();
    }

    @Override
    public long getRejectedCount() {
        return broker.getRejectedCount();
    }

    @Override
    public long getSweepFailureCount() {
        return broker.getSweepFailureCount();
    }

    @Override
    public String getLastSweepError() {
        return broker.getLastSweepError();
    }

    @Override
    public int purgeQueue(String queueName) {
        return broker.purgeQueue(queueName);
    }

    @Override
    public void sweepExpiredNow() {
        broker.sweepExpired();
    }
}
