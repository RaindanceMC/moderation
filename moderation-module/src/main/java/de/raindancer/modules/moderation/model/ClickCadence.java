package de.raindancer.modules.moderation.model;

import java.util.Arrays;

/**
 * One player's recent clicks, left and right, as the server saw them: when, and whether the head turned since the
 * click before. Swings that mining, dropping an item or a right click explain are not clicks.
 *
 * <p>A pause longer than {@link #PAUSE_MILLIS} ends a run; a macro does not pause, a person does. Thread-safe.
 */
public final class ClickCadence {

    public enum Hand { LEFT, RIGHT }

    /** A pause this long starts a new run. */
    public static final long PAUSE_MILLIS = 30_000;
    /** How long after a block stops being mined its swings still count as mining. */
    static final long AFTER_DIGGING = 300;
    /** Dropping an item swings the arm right after. */
    static final long AFTER_DROP = 100;
    /** Using something with the right button swings the arm in the same tick. */
    static final long AFTER_RIGHT = 40;
    /** What the rate is measured over. */
    public static final long RECENT_MILLIS = 3_000;

    /** What a hand's clicks in a window look like. */
    public record Sample(Hand hand, long[] intervals, long runMillis, int recent, int still, int clicks) {
    }

    private final Track left;
    private final Track right;
    private boolean digging;
    private long lastDig = Long.MIN_VALUE / 2;
    private long lastDrop = Long.MIN_VALUE / 2;
    private long lastRight = Long.MIN_VALUE / 2;

    public ClickCadence(int capacity) {
        this.left = new Track(capacity);
        this.right = new Track(capacity);
    }

    /** An arm swing. @return whether it was counted as a left click */
    public synchronized boolean swing(long at, float yaw, float pitch) {
        if (digging || at - lastDig <= AFTER_DIGGING || at - lastDrop <= AFTER_DROP || at - lastRight <= AFTER_RIGHT) {
            return false;
        }
        left.add(at, yaw, pitch);
        return true;
    }

    public synchronized void rightClick(long at, float yaw, float pitch) {
        lastRight = at;
        right.add(at, yaw, pitch);
    }

    /** A block started ({@code true}) or stopped being mined, or broke. */
    public synchronized void digging(long at, boolean on) {
        digging = on;
        lastDig = at;
    }

    public synchronized void dropped(long at) {
        lastDrop = at;
    }

    /** Clicks counted on this hand since the measuring began. */
    public synchronized int counted(Hand hand) {
        return track(hand).total;
    }

    /** The clicks of the current run inside the last {@code window} ms. */
    public synchronized Sample sample(Hand hand, long now, long window) {
        return track(hand).sample(hand, now, window);
    }

    private Track track(Hand hand) {
        return hand == Hand.LEFT ? left : right;
    }

    private static final class Track {
        private final long[] times;
        private final boolean[] still;
        private int size;
        private int next;
        private int total;
        private long runStart;
        private float yaw;
        private float pitch;

        Track(int capacity) {
            this.times = new long[Math.max(4, capacity)];
            this.still = new boolean[times.length];
        }

        void add(long at, float newYaw, float newPitch) {
            if (size > 0 && at - times[(next - 1 + times.length) % times.length] > PAUSE_MILLIS) {
                size = 0;
            }
            if (size == 0) {
                runStart = at;
            }
            still[next] = size > 0 && Math.abs(newYaw - yaw) < 0.01f && Math.abs(newPitch - pitch) < 0.01f;
            times[next] = at;
            next = (next + 1) % times.length;
            size = Math.min(size + 1, times.length);
            total++;
            yaw = newYaw;
            pitch = newPitch;
        }

        Sample sample(Hand hand, long now, long window) {
            if (size > 0 && now - times[(next - 1 + times.length) % times.length] > PAUSE_MILLIS) {
                return new Sample(hand, new long[0], 0, 0, 0, 0);
            }
            long[] gaps = new long[size];
            int gapCount = 0;
            int recent = 0;
            int stillCount = 0;
            int clicks = 0;
            long previous = Long.MIN_VALUE;
            for (int i = 0; i < size; i++) {
                int index = (next - size + i + times.length) % times.length;
                long at = times[index];
                if (now - at <= RECENT_MILLIS) {
                    recent++;
                }
                if (now - at > window) {
                    previous = at;
                    continue;
                }
                clicks++;
                if (previous != Long.MIN_VALUE) {
                    gaps[gapCount++] = at - previous;
                    if (still[index]) {
                        stillCount++;
                    }
                }
                previous = at;
            }
            return new Sample(hand, Arrays.copyOf(gaps, gapCount), size == 0 ? 0 : now - runStart, recent, stillCount, clicks);
        }
    }
}
