/*
 * vmjms - in-memory Jakarta JMS provider
 * https://github.com/matteobaccan/vmjms
 *
 * Copyright (c) 2026 Matteo Baccan
 * SPDX-License-Identifier: MIT
 */
/**
 * The message bodies this provider can carry.
 *
 * <p>{@link it.baccan.vmjms.message.InMemoryTextMessage} and
 * {@link it.baccan.vmjms.message.InMemoryBytesMessage} implement the two body types
 * supported, both extending {@link it.baccan.vmjms.message.InMemoryMessage}, which holds
 * the headers and the properties. Applications create them through
 * {@code Session.createTextMessage(...)} and {@code Session.createBytesMessage()} rather
 * than by calling these constructors.</p>
 *
 * <p>A message is a mutable object and is queued by reference, not copied: the provider
 * creates one envelope per send, so the same instance may be sent more than once, but
 * mutating it after a send also changes what a consumer will read. Treat a sent message
 * as owned by the broker, exactly as a real provider would force you to.</p>
 */
package it.baccan.vmjms.message;
