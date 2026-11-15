package de.raindancer.modules.moderation.rules;

import de.raindancer.modules.moderation.model.ClickCadence;

import java.util.Arrays;
import java.util.Optional;

/**
 * Whether a player's clicks look made by a machine. Two findings:
 *
 * <ul>
 *   <li><b>Regular</b>: for the whole window, the gaps between clicks barely vary. People drift by 10 to 20 percent;
 *   a macro by the tick it lands on. Measured as the median absolute deviation, so a few odd gaps (a lag spike, a
 *   tick boundary) do not hide a macro. Only gaps of four ticks and more: below that the server's ticks make every
 *   rhythm look alike. A right button held down repeats every four ticks in vanilla and is left out.</li>
 *   <li><b>Fast</b>: more left clicks a second than a hand manages, for at least three seconds.</li>
 * </ul>
 */
public final class MacroRule implements IModerationRule {

    /** Gaps shorter than this are mostly tick noise. */
    public static final long SHORTEST_GAP = 200;
    /** Vanilla's repeat for a held right button: four ticks. */
    public static final long HOLD_REPEAT = 200;
    private static final long HOLD_TOLERANCE = 25;
    /** Scales the median absolute deviation to a standard deviation for normally spread gaps. */
    private static final double MAD_TO_SD = 1.4826;

    public record Policy(int samples, int maxVariationPercent, long windowMillis, int maxCps) {
    }

    /**
     * @param fast             found by rate rather than rhythm
     * @param variationPercent how much the gaps vary, against their median
     * @param stillPercent     how many clicks came without the head turning at all
     */
    public record Verdict(ClickCadence.Hand hand, boolean fast, int intervals, long medianMillis,
                          double variationPercent, double cps, long minutes, int stillPercent) {
    }

    public Optional<Verdict> judge(ClickCadence.Sample sample, Policy policy) {
        long[] gaps = sample.intervals();
        int stillPercent = gaps.length == 0 ? 0 : (int) Math.round(100.0 * sample.still() / gaps.length);
        double cps = sample.recent() / (ClickCadence.RECENT_MILLIS / 1000.0);
        if (sample.hand() == ClickCadence.Hand.LEFT && cps > policy.maxCps()
                && sample.runMillis() >= ClickCadence.RECENT_MILLIS) {
            return Optional.of(new Verdict(sample.hand(), true, gaps.length, median(gaps), 0, cps,
                    Math.round(sample.runMillis() / 60_000.0), stillPercent));
        }
        if (sample.runMillis() < policy.windowMillis() || gaps.length < policy.samples()) {
            return Optional.empty();
        }
        long median = median(gaps);
        if (median < SHORTEST_GAP) {
            return Optional.empty();
        }
        if (sample.hand() == ClickCadence.Hand.RIGHT && Math.abs(median - HOLD_REPEAT) <= HOLD_TOLERANCE) {
            return Optional.empty();
        }
        long[] deviations = new long[gaps.length];
        for (int i = 0; i < gaps.length; i++) {
            deviations[i] = Math.abs(gaps[i] - median);
        }
        double variation = 100.0 * MAD_TO_SD * median(deviations) / median;
        if (variation > policy.maxVariationPercent()) {
            return Optional.empty();
        }
        return Optional.of(new Verdict(sample.hand(), false, gaps.length, median, variation, cps,
                Math.round(sample.runMillis() / 60_000.0), stillPercent));
    }

    private static long median(long[] values) {
        if (values.length == 0) {
            return 0;
        }
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    @Override
    public String describe() {
        return "whether clicks look made by a machine";
    }
}
