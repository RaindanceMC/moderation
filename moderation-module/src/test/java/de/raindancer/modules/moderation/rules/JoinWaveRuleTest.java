package de.raindancer.modules.moderation.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JoinWaveRuleTest {

    private final JoinWaveRule rule = new JoinWaveRule();
    private final JoinWaveRule.Policy policy = new JoinWaveRule.Policy(6, 60_000, 3, 3);
    private final long now = 5_000_000L;

    private JoinWaveRule.Join join(String name, String network, long ago) {
        return new JoinWaveRule.Join(UUID.randomUUID(), name, network, now - ago);
    }

    @Test
    @DisplayName("a few newcomers from different places are no wave")
    void quietEvening() {
        List<JoinWaveRule.Join> recent = List.of(join("Anna", "a", 30_000), join("Ben", "b", 20_000));
        JoinWaveRule.Wave wave = rule.judge(recent, join("Carla", "c", 0), now, policy);
        assertThat(wave.isWave()).isFalse();
        assertThat(wave.sameNetwork()).isEmpty();
        assertThat(wave.sameNames()).isEmpty();
        assertThat(wave.lockdown()).isFalse();
    }

    @Test
    @DisplayName("many newcomers from everywhere are a wave, but no reason to lock down (a raid looks like that)")
    void raidIsNoLockdown() {
        List<JoinWaveRule.Join> recent = new ArrayList<>();
        String[] names = {"Anna", "Ben", "Carla", "Dieter", "Emil"};
        for (int i = 0; i < names.length; i++) {
            recent.add(join(names[i], "net" + i, 10_000L * i));
        }
        JoinWaveRule.Wave wave = rule.judge(recent, join("Frieda", "other", 0), now, policy);
        assertThat(wave.isWave()).isTrue();
        assertThat(wave.newcomers()).isEqualTo(6);
        assertThat(wave.lockdown()).isFalse();
    }

    @Test
    @DisplayName("a wave with accounts from one network asks for a lockdown")
    void sameNetworkLocksDown() {
        List<JoinWaveRule.Join> recent = new ArrayList<>(List.of(join("Anna", "a", 1000), join("Ben", "b", 2000),
                join("Carla", "c", 3000), join("xQ7", "bad", 4000), join("Lp2", "bad", 5000)));
        JoinWaveRule.Wave wave = rule.judge(recent, join("Zt9", "bad", 0), now, policy);
        assertThat(wave.isWave()).isTrue();
        assertThat(wave.sameNetwork()).containsExactlyInAnyOrder("xQ7", "Lp2", "Zt9");
        assertThat(wave.lockdown()).isTrue();
    }

    @Test
    @DisplayName("names that differ only in their trailing digits are one group")
    void nameStems() {
        assertThat(JoinWaveRule.stem("Bot_123")).isEqualTo("bot");
        assertThat(JoinWaveRule.stem("bot124")).isEqualTo("bot");
        assertThat(JoinWaveRule.stem("Steve")).isEqualTo("steve");
        assertThat(JoinWaveRule.stem("ab12")).isNull();
        assertThat(JoinWaveRule.stem("1234")).isNull();
        List<JoinWaveRule.Join> recent = List.of(join("FreeBot_1", "a", 1000), join("freebot2", "b", 2000));
        JoinWaveRule.Wave wave = rule.judge(recent, join("FreeBot_3", "c", 0), now, policy);
        assertThat(wave.sameNames()).containsExactlyInAnyOrder("FreeBot_1", "freebot2", "FreeBot_3");
        assertThat(wave.stem()).isEqualTo("freebot");
        assertThat(wave.isWave()).isFalse();
        assertThat(wave.lockdown()).isFalse();
    }

    @Test
    @DisplayName("joins older than the window do not count")
    void windowEnds() {
        List<JoinWaveRule.Join> recent = List.of(join("Bot1", "bad", 61_000), join("Bot2", "bad", 70_000));
        JoinWaveRule.Wave wave = rule.judge(recent, join("Bot3", "bad", 0), now, policy);
        assertThat(wave.newcomers()).isEqualTo(1);
        assertThat(wave.sameNetwork()).isEmpty();
        assertThat(wave.sameNames()).isEmpty();
    }

    @Test
    @DisplayName("an address becomes its network: /24 for IPv4, /48 for IPv6, nothing for rubbish")
    void networks() {
        assertThat(JoinWaveRule.network("203.0.113.77")).isEqualTo("203.0.113.0/24");
        assertThat(JoinWaveRule.network("2001:db8:abcd:12::1")).isEqualTo("2001:db8:abcd::/48");
        assertThat(JoinWaveRule.network("/203.0.113.5")).isEqualTo("203.0.113.0/24");
        assertThat(JoinWaveRule.network(null)).isNull();
        assertThat(JoinWaveRule.network("not an address")).isNull();
    }
}
