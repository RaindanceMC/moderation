package de.raindancer.modules.moderation.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClickCadenceTest {

    @Test
    @DisplayName("left clicks are counted with the gaps between them")
    void counts() {
        ClickCadence cadence = new ClickCadence(100);
        for (int i = 0; i < 5; i++) {
            assertThat(cadence.swing(1000 + i * 500L, 0, 0)).isTrue();
        }
        ClickCadence.Sample sample = cadence.sample(ClickCadence.Hand.LEFT, 3000, 60_000);
        assertThat(sample.intervals()).containsExactly(500, 500, 500, 500);
        assertThat(sample.runMillis()).isEqualTo(2000);
        assertThat(sample.still()).isEqualTo(4);
        assertThat(cadence.counted(ClickCadence.Hand.LEFT)).isEqualTo(5);
    }

    @Test
    @DisplayName("a swing that mining, dropping an item or a right click explains is not a click")
    void explainedSwings() {
        ClickCadence cadence = new ClickCadence(100);
        cadence.digging(1000, true);
        assertThat(cadence.swing(1050, 0, 0)).isFalse();
        assertThat(cadence.swing(1100, 0, 0)).isFalse();
        cadence.digging(1150, false);
        assertThat(cadence.swing(1200, 0, 0)).isFalse();   // the last swing of the block
        assertThat(cadence.swing(1600, 0, 0)).isTrue();
        cadence.dropped(2000);
        assertThat(cadence.swing(2010, 0, 0)).isFalse();
        cadence.rightClick(3000, 0, 0);
        assertThat(cadence.swing(3005, 0, 0)).isFalse();
        assertThat(cadence.counted(ClickCadence.Hand.LEFT)).isEqualTo(1);
        assertThat(cadence.counted(ClickCadence.Hand.RIGHT)).isEqualTo(1);
    }

    @Test
    @DisplayName("a long pause ends a run, so a break resets the clock")
    void pauseResets() {
        ClickCadence cadence = new ClickCadence(100);
        cadence.swing(0, 0, 0);
        cadence.swing(500, 0, 0);
        cadence.swing(40_000, 0, 0);
        cadence.swing(40_500, 0, 0);
        ClickCadence.Sample sample = cadence.sample(ClickCadence.Hand.LEFT, 40_500, 600_000);
        assertThat(sample.intervals()).containsExactly(500);
        assertThat(sample.runMillis()).isEqualTo(500);
    }

    @Test
    @DisplayName("turning the head between clicks is noted, and the recent clicks are counted for the click rate")
    void turnsAndRate() {
        ClickCadence cadence = new ClickCadence(100);
        cadence.swing(0, 10, 0);
        cadence.swing(100, 10, 0);
        cadence.swing(200, 40, 5);
        cadence.swing(300, 40, 5);
        ClickCadence.Sample sample = cadence.sample(ClickCadence.Hand.LEFT, 300, 60_000);
        assertThat(sample.still()).isEqualTo(2);
        assertThat(sample.recent()).isEqualTo(4);
    }

    @Test
    @DisplayName("the ring keeps only its capacity, the run start stays")
    void capacity() {
        ClickCadence cadence = new ClickCadence(10);
        for (int i = 0; i < 50; i++) {
            cadence.swing(i * 1000L, 0, 0);
        }
        ClickCadence.Sample sample = cadence.sample(ClickCadence.Hand.LEFT, 49_000, 600_000);
        assertThat(sample.intervals()).hasSize(9);
        assertThat(sample.runMillis()).isEqualTo(49_000);
    }
}
