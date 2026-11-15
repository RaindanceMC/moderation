package de.raindancer.modules.moderation.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Whether new accounts arrive as a wave, and whether it looks like bots. A wave alone is also what a stream raid
 * looks like, so it is only told. A wave with accounts from one network, or with names that differ only in their
 * digits, asks for a lockdown.
 */
public final class JoinWaveRule implements IModerationRule {

    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");
    private static final int SHORTEST_STEM = 3;

    /** A new account joined. {@code network} is whatever stands for its network (a keyed hash in production). */
    public record Join(UUID player, String name, String network, long at) {
    }

    public record Policy(int waveJoins, long windowMillis, int sameNetwork, int sameNames) {
    }

    /** @param sameNetwork names sharing the joiner's network, the joiner included; empty below the threshold */
    public record Wave(int newcomers, boolean isWave, List<String> sameNetwork, List<String> sameNames, String stem) {

        public boolean lockdown() {
            return isWave && (!sameNetwork.isEmpty() || !sameNames.isEmpty());
        }
    }

    public Wave judge(List<Join> recent, Join joining, long now, Policy policy) {
        List<String> network = new ArrayList<>();
        List<String> names = new ArrayList<>();
        String stem = stem(joining.name());
        int newcomers = 1;
        for (Join join : recent) {
            if (join.player().equals(joining.player()) || now - join.at() > policy.windowMillis()) {
                continue;
            }
            newcomers++;
            if (joining.network() != null && joining.network().equals(join.network())) {
                network.add(join.name());
            }
            if (stem != null && stem.equals(stem(join.name()))) {
                names.add(join.name());
            }
        }
        network.add(joining.name());
        names.add(joining.name());
        return new Wave(newcomers, newcomers >= policy.waveJoins(),
                network.size() >= policy.sameNetwork() ? List.copyOf(network) : List.of(),
                names.size() >= policy.sameNames() ? List.copyOf(names) : List.of(),
                names.size() >= policy.sameNames() ? stem : null);
    }

    /** A name without its trailing digits and underscores, lower case; null if too little is left to compare. */
    public static String stem(String name) {
        if (name == null) {
            return null;
        }
        String stem = name.toLowerCase(Locale.ROOT).replaceAll("[0-9_]+$", "");
        return stem.length() < SHORTEST_STEM ? null : stem;
    }

    /**
     * The network an address belongs to: its /24 for IPv4, its /48 for IPv6. Never resolves a name; null for
     * anything that is not an address literal.
     */
    public static String network(String address) {
        if (address == null) {
            return null;
        }
        String text = address.trim();
        if (text.startsWith("/")) {
            text = text.substring(1);
        }
        var v4 = IPV4.matcher(text);
        if (v4.matches()) {
            for (int i = 1; i <= 4; i++) {
                if (Integer.parseInt(v4.group(i)) > 255) {
                    return null;
                }
            }
            return v4.group(1) + "." + v4.group(2) + "." + v4.group(3) + ".0/24";
        }
        if (!text.contains(":") || !text.matches("[0-9a-fA-F:.%a-zA-Z0-9]+")) {
            return null;
        }
        try {
            // A literal with a colon is parsed, never looked up.
            byte[] bytes = java.net.InetAddress.getByName(text).getAddress();
            if (bytes.length != 16) {
                return null;
            }
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < 6; i += 2) {
                int group = ((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff);
                prefix.append(i == 0 ? "" : ":").append(Integer.toHexString(group));
            }
            return prefix + "::/48";
        } catch (java.net.UnknownHostException notAnAddress) {
            return null;
        }
    }

    @Override
    public String describe() {
        return "whether new accounts arrive as a bot wave";
    }
}
