package de.raindancer.modules.moderation.util;

import de.raindancer.core.platform.network.NetworkBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncPublisherTest {

    private static final class Recording implements NetworkBus {
        final List<String> published = Collections.synchronizedList(new ArrayList<>());
        final List<Thread> threads = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch gate = new CountDownLatch(0);

        @Override public String server() { return "t"; }
        @Override public boolean publish(String topic, String key, String payload) {
            try {
                gate.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            threads.add(Thread.currentThread());
            published.add(topic + "/" + key + "/" + payload);
            return true;
        }
        @Override public Subscription subscribe(String topic, java.util.function.Consumer<de.raindancer.core.platform.network.NetworkEvent> h) { return () -> { }; }
        @Override public void close() { }
    }

    @Test
    @DisplayName("publishing returns at once, the bus is written to from another thread, in order")
    void offTheCallersThread() {
        Recording bus = new Recording();
        bus.gate = new CountDownLatch(1);
        AsyncPublisher publisher = new AsyncPublisher(bus, "test");

        publisher.publish("t", "k", "1");
        publisher.publish("t", "k", "2");
        assertThat(bus.published).isEmpty();
        bus.gate.countDown();
        publisher.close();

        assertThat(bus.published).containsExactly("t/k/1", "t/k/2");
        assertThat(bus.threads).doesNotContain(Thread.currentThread());
    }

    @Test
    @DisplayName("a flood is bounded: the oldest give way and the newest survive")
    void floodIsBounded() {
        Recording bus = new Recording();
        bus.gate = new CountDownLatch(1);
        AsyncPublisher publisher = new AsyncPublisher(bus, "test", 5);

        for (int i = 0; i < 50; i++) {
            publisher.publish("t", "k", String.valueOf(i));
        }
        bus.gate.countDown();
        publisher.close();

        assertThat(bus.published.size()).isLessThanOrEqualTo(6);
        assertThat(bus.published.getLast()).isEqualTo("t/k/49");
    }

    @Test
    @DisplayName("identical payloads waiting together go out once")
    void identicalCoalesce() {
        Recording bus = new Recording();
        bus.gate = new CountDownLatch(1);
        AsyncPublisher publisher = new AsyncPublisher(bus, "test");

        for (int i = 0; i < 10; i++) {
            publisher.publish("t", "k", "same");
        }
        bus.gate.countDown();
        publisher.close();

        assertThat(bus.published.stream().filter("t/k/same"::equals).count()).isLessThanOrEqualTo(2);
    }
}
