package de.raindancer.modules.moderation.util;

import de.raindancer.core.platform.log.Log;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.network.NetworkBus;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Publishes to the bus off the caller's thread. {@link NetworkBus#publish} is a database round trip (INSERT,
 * pg_notify, commit), which the thread that saw a flag or a join must never wait for.
 *
 * <p>Order is kept. The backlog is bounded — on a lag spike the oldest give way — and an identical message
 * waiting twice goes out once, so a flood of the same alert cannot grow without limit.
 */
public final class AsyncPublisher {

    public static final int DEFAULT_BACKLOG = 2_000;

    private static final LogChannel log = Log.of("moderation");

    private record Message(String topic, String key, String payload) { }

    private final NetworkBus bus;
    private final Executor executor;
    private final ExecutorService owned;
    private final int backlog;
    private final ArrayDeque<Message> queue = new ArrayDeque<>();
    private final Set<Message> queued = new HashSet<>();
    private boolean draining;
    private boolean warned;

    public AsyncPublisher(NetworkBus bus, String name) {
        this(bus, name, DEFAULT_BACKLOG);
    }

    public AsyncPublisher(NetworkBus bus, String name, int backlog) {
        this(bus, backlog, null, Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, name + "-bus-publisher");
            thread.setDaemon(true);
            return thread;
        }));
    }

    /** With the work run by {@code executor} — a direct one makes publishing synchronous, for tests. */
    public AsyncPublisher(NetworkBus bus, Executor executor) {
        this(bus, DEFAULT_BACKLOG, executor, null);
    }

    private AsyncPublisher(NetworkBus bus, int backlog, Executor executor, ExecutorService owned) {
        this.bus = bus == null ? NetworkBus.NONE : bus;
        this.backlog = Math.max(1, backlog);
        this.owned = owned;
        this.executor = owned != null ? owned : executor;
    }

    public void publish(String topic, String key, String payload) {
        Message message = new Message(topic, key, payload);
        synchronized (this) {
            if (!queued.add(message)) {
                return;
            }
            queue.add(message);
            while (queue.size() > backlog) {
                queued.remove(queue.poll());
                if (!warned) {
                    warned = true;
                    log.warn("More than {} bus messages are waiting to be published; dropping the oldest.", backlog);
                }
            }
            if (draining) {
                return;
            }
            draining = true;
        }
        try {
            executor.execute(this::drain);
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            synchronized (this) {
                draining = false;
            }
        }
    }

    private void drain() {
        while (true) {
            Message next;
            synchronized (this) {
                next = queue.poll();
                if (next == null) {
                    draining = false;
                    return;
                }
                queued.remove(next);
            }
            try {
                bus.publish(next.topic(), next.key(), next.payload());
            } catch (RuntimeException failed) {
                log.warn("A bus message on {} could not be published: {}", next.topic(), failed.getMessage());
            }
        }
    }

    /** Publishes what is waiting, then stops. */
    public void close() {
        if (owned == null) {
            return;
        }
        owned.shutdown();
        try {
            if (!owned.awaitTermination(5, TimeUnit.SECONDS)) {
                owned.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            owned.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
