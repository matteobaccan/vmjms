/*
 * vmjms - in-memory Jakarta JMS provider
 * https://github.com/matteobaccan/vmjms
 *
 * Copyright (c) 2026 Matteo Baccan
 * SPDX-License-Identifier: MIT
 */
package it.baccan.vmjms.broker;

import jakarta.jms.Message;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Envelope wrapping a message for as long as it waits in a broker queue.
 *
 * <p>A new envelope is created for every send, because JMS allows a sender to reuse the
 * same {@link Message} instance across several sends: per-message state cannot live on
 * the message itself without two sends sharing it.</p>
 *
 * <p>The absolute expiration is cached as a primitive so that the expiry sweep does not
 * have to call {@code getJMSExpiration()}, which declares {@code throws JMSException},
 * inside its predicate.</p>
 */
class QueuedMessage {

    private final Message payload;
    private final long expiresAt;
    private final AtomicBoolean claimed = new AtomicBoolean(false);

    QueuedMessage(Message payload, long expiresAt) {
        this.payload = payload;
        this.expiresAt = expiresAt;
    }

    Message getPayload() {
        return payload;
    }

    /**
     * Absolute expiration in milliseconds, or 0 when the message never expires.
     */
    long getExpiresAt() {
        return expiresAt;
    }

    boolean canExpire() {
        return expiresAt > 0;
    }

    boolean isExpired(long now) {
        return expiresAt > 0 && expiresAt <= now;
    }

    /**
     * Acquires the exclusive right to account for this message leaving its queue.
     *
     * <p>A message can leave in three ways - delivery, expiry reclamation and purge - and
     * they race. Ownership of the release is decided here rather than inferred from who
     * unlinked the node, because {@code LinkedBlockingQueue.removeIf} evaluates its
     * predicate with the queue lock released and may then find the node already gone.
     * Exactly one caller wins, so the capacity accounting is exact.</p>
     *
     * @return true for the single caller that owns the release
     */
    boolean claim() {
        return claimed.compareAndSet(false, true);
    }

    /**
     * Whether the release of this message has already been claimed.
     *
     * <p>Informational only: a caller deciding whether to account for a removal must use
     * the atomic {@link #claim()} instead, because this answer can be stale as soon as it
     * is returned.</p>
     */
    boolean isClaimed() {
        return claimed.get();
    }
}
