package de.raindancer.modules.moderation.model;

import de.raindancer.core.platform.permission.Duty;
import de.raindancer.core.platform.permission.GroupSeed;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The staff ranks, each one a permission group of the same name.
 *
 * <p>Two kinds. The <b>ladder</b> — support, mod, admin, owner — is who may act on players; each rung
 * inherits the one below, so a promotion never takes a power away. The <b>departments</b> — builder,
 * creator, dev, tester — are jobs next to it, held on their own or on top of a rung.
 *
 * <p>{@link #nodes()} is only what a group <em>starts</em> with ({@link #seed()}). Once the group exists
 * it is edited in LuckPerms and this list is not consulted again.
 */
public enum StaffRank {

    SUPPORT("Support", 1, "green", Material.LIME_DYE,
            "Answers questions and helps new players: warn, read a record, handle reports. Stops nobody."),
    MOD("Moderator", 2, "aqua", Material.IRON_HELMET,
            "The full working set, chat and anticheat included: mute, kick, tempban, freeze, vanish, claims."),
    ADMIN("Admin", 3, "gold", Material.DIAMOND_HELMET,
            "Everything a mod has, plus permanent bans, the settings, the anticheat's checks and the bypasses."),
    /** An operator: every permission of every plugin, which is why it seeds no nodes of its own. */
    OWNER("Owner", 4, "red", Material.NETHERITE_HELMET,
            "The server operator: every permission of every plugin, and the vanilla commands."),

    BUILDER("Builder", 0, "yellow", Material.BRICKS,
            "Builds the hub, the maps and the event stages. Creative in the build world, and creative and WorldEdit anywhere in admin mode."),
    CREATOR("Creator", 0, "light_purple", Material.SPYGLASS,
            "Makes videos and streams about the server."),
    DEV("Developer", 0, "dark_aqua", Material.COMMAND_BLOCK,
            "Works on the plugins: performance tools, maintenance mode, and every permission while /dev is on."),
    TESTER("Tester", 0, "gray", Material.TARGET,
            "Tries new features before everybody else does.");

    /** Claim administration — acting on somebody else's claim. Not an exemption from anything. */
    public static final String CLAIM_ADMIN = "rec.admin";
    /** Holding more claims than anybody else; the other claim exemptions are claims' own bypass toggle. */
    public static final List<String> CLAIM_BYPASSES = List.of("rec.maxclaims.unlimited");
    /** Every warp marked staff-only: the staff room, the build world, the event stage. */
    public static final String WARP_STAFF = "rainswarps.warp.staff";
    /** Making and moving warps — a decision about the server's shape, so an admin's. */
    public static final String WARP_MANAGE = "rainswarps.warp.manage";
    /** A moderator walking to a grief is the one person a teleport warm-up works against. */
    public static final List<String> TELEPORT_BYPASSES = List.of(
            "tpa.bypass.warmup", "tpa.bypass.cooldown", "tpa.back",
            "homes.bypass.warmup", "homes.bypass.cooldown");
    public static final String HOMES_UNLIMITED = "homes.unlimited";
    /** Running a round. Not the arena or the loot — that is {@link #HUNGERGAMES_ADMIN}. */
    public static final String HUNGERGAMES_GAMEMASTER = "hungergames.gamemaster";
    public static final String HUNGERGAMES_ADMIN = "hungergames.admin";
    public static final String TELEPORT = "minecraft.command.teleport";
    /** Vanilla /gamemode. Essentials' essentials.gamemode.<mode> nodes only narrow it down; they never grant it. */
    public static final String GAME_MODE = "minecraft.command.gamemode";
    public static final String GAME_MODES_PREFIX = "essentials.gamemode.";
    public static final String ANTICHEAT_LOOK = "rainsanticheat.command";
    public static final String ANTICHEAT_ALERTS = "rainsanticheat.alerts";
    public static final String ANTICHEAT_MANAGE = "rainsanticheat.manage";
    /** The world a builder's creative and world tools are limited to (LuckPerms context {@code world}). */
    public static final String BUILD_WORLD = "build";
    /** Switching into admin mode (essentials) — always, or nobody could go on duty. */
    public static final String ADMIN_MODE = "essentials.admin";
    /** Switching somebody else's admin mode. */
    public static final String ADMIN_MODE_OTHERS = "essentials.admin.others";
    public static final String WHITELIST = "minecraft.command.whitelist";
    /** Switching dev mode ({@code /dev}), in which a dev holds every permission. */
    public static final String DEV_MODE = "rains.moderation.dev";
    /** What a rung holds off duty too; everything else of theirs needs {@code rains:powers=on}. */
    public static final Set<String> ALWAYS = Set.of(ModerationPermission.STAFF_CHAT.node(), ADMIN_MODE);
    /** Every WorldEdit command — a builder's, in admin mode. */
    public static final String WORLDEDIT = "worldedit.*";
    /** Building in a locked world (hub, maps) while in creative — Core's LockedWorlds. */
    public static final String LOCKED_WORLDS_BUILD = "rainscore.lockedworlds.build";

    // rec.admin.nofee is deliberately absent: an admin pays a claim's toll like everybody else.

    private final String title;
    private final int weight;
    private final String colour;
    private final Material icon;
    private final String description;

    StaffRank(String title, int weight, String colour, Material icon, String description) {
        this.title = title;
        this.weight = weight;
        this.colour = colour;
        this.icon = icon;
        this.description = description;
    }

    /** Everything the rank's group gives by default, inherited nodes included. */
    public Set<String> nodes() {
        Set<String> everything = new LinkedHashSet<>();
        if (onLadder()) {
            for (ModerationPermission permission : ModerationPermission.values()) {
                if (permission.fromTier() <= weight) {
                    everything.add(permission.node());
                }
            }
            everything.add(ADMIN_MODE);
            if (weight >= MOD.weight) {
                everything.add(CLAIM_ADMIN);
                everything.add(WARP_STAFF);
                everything.addAll(TELEPORT_BYPASSES);
                everything.add(HUNGERGAMES_GAMEMASTER);
                everything.add(TELEPORT);
                everything.add(ANTICHEAT_LOOK);
                everything.add(ANTICHEAT_ALERTS);
                everything.add(WHITELIST);
                everything.add(GAME_MODE);
                everything.add(GAME_MODES_PREFIX + "survival");
                everything.add(GAME_MODES_PREFIX + "spectator");
            }
            if (weight >= ADMIN.weight) {
                everything.add(WARP_MANAGE);
                everything.add(HOMES_UNLIMITED);
                everything.add(HUNGERGAMES_ADMIN);
                everything.add(ANTICHEAT_MANAGE);
                everything.add(ADMIN_MODE_OTHERS);
                everything.add(GAME_MODES_PREFIX + "creative");
                everything.addAll(CLAIM_BYPASSES);
            }
            // The owner adds nothing: an operator already holds everything.
            if (this == OWNER) {
                everything.clear();
                everything.addAll(ADMIN.nodes());
            }
            return Set.copyOf(everything);
        }
        switch (this) {
            case BUILDER -> {
                everything.add(ModerationPermission.STAFF_CHAT.node());
                everything.add(WARP_STAFF);
                everything.add(ModerationPermission.FLY.node());
                everything.add(LOCKED_WORLDS_BUILD);
                // Admin mode is where a builder's WorldEdit lives (see seed()).
                everything.add(ADMIN_MODE);
            }
            case DEV -> {
                everything.add(ModerationPermission.STAFF_CHAT.node());
                everything.add("rainsperformance.inspect");
                everything.add("rainsperformance.notify");
                everything.add("rainsperformance.teleport");
                everything.add("rainscore.maintenance");
                everything.add(DEV_MODE);
            }
            case TESTER -> everything.add(ModerationPermission.STAFF_CHAT.node());
            default -> { }
        }
        return Set.copyOf(everything);
    }

    /** What the group is created with: its own nodes on top of the rung below, which it inherits. */
    public GroupSeed seed() {
        GroupSeed seed = GroupSeed.named(group()).displayName(title).weight(groupWeight());
        Optional<StaffRank> below = below();
        if (below.isPresent()) {
            seed = seed.inheriting(below.get().group());
        }
        if (this != OWNER) {
            List<String> own = new ArrayList<>(nodes());
            below.ifPresent(lower -> own.removeAll(lower.nodes()));
            for (String node : own) {
                // A rung's powers only on duty — see Duty; with the server's switch off that is always.
                seed = onLadder() && !ALWAYS.contains(node)
                        ? seed.grantingIn(node, Duty.POWERS, Duty.ON)
                        : seed.granting(node);
            }
        }
        if (this == BUILDER) {
            seed = seed.grantingIn(GAME_MODE, "world", BUILD_WORLD);
            // WorldEdit only while in admin mode: a builder's own survival side never carries a wand.
            seed = seed.grantingIn(WORLDEDIT, Duty.ADMIN, Duty.ON);
            // Creative anywhere, but in admin mode only: its inventory is kept apart, and leaving puts their own
            // game mode back. Survival too, or holding only creative would lock them in it (essentials narrows
            // /gamemode to the modes held).
            seed = seed.grantingIn(GAME_MODE, Duty.ADMIN, Duty.ON)
                    .grantingIn(GAME_MODES_PREFIX + "creative", Duty.ADMIN, Duty.ON)
                    .grantingIn(GAME_MODES_PREFIX + "survival", Duty.ADMIN, Duty.ON);
        }
        if (this == DEV) {
            // Everything in dev mode — except WorldEdit, which nobody gets unless the owner hands it out.
            seed = seed.grantingIn("*", Duty.DEV, Duty.ON).denyingIn("worldedit.*", Duty.DEV, Duty.ON);
        }
        return seed;
    }

    /** LuckPerms' weight: the ladder above every department, so a mod who also builds shows as a mod. */
    private int groupWeight() {
        return onLadder() ? 100 + weight * 10 : 50 + (values().length - ordinal());
    }

    public boolean onLadder() {
        return weight > 0;
    }

    /** The LuckPerms group this rank is. */
    public String group() {
        return key();
    }

    public String title() {
        return title;
    }

    public String describe() {
        return description;
    }

    /** The ladder rung, 1 (support) to 4 (owner); 0 for a department. */
    public int weight() {
        return weight;
    }

    public boolean isOperator() {
        return this == OWNER;
    }

    public Optional<StaffRank> below() {
        List<StaffRank> ladder = ladder();
        int at = ladder.indexOf(this);
        return at <= 0 ? Optional.empty() : Optional.of(ladder.get(at - 1));
    }

    public Optional<StaffRank> above() {
        List<StaffRank> ladder = ladder();
        int at = ladder.indexOf(this);
        return at < 0 || at + 1 >= ladder.size() ? Optional.empty() : Optional.of(ladder.get(at + 1));
    }

    /** Ladder comparison. A department is at least nothing. */
    public boolean isAtLeast(StaffRank other) {
        return other != null && onLadder() && weight >= other.weight;
    }

    public Material icon() {
        return icon;
    }

    public String colour() {
        return colour;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static List<StaffRank> ladder() {
        return Arrays.stream(values()).filter(StaffRank::onLadder).toList();
    }

    public static List<StaffRank> departments() {
        return Arrays.stream(values()).filter(rank -> !rank.onLadder()).toList();
    }

    public static Optional<StaffRank> byName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (StaffRank rank : values()) {
            if (rank.key().equals(wanted)) {
                return Optional.of(rank);
            }
        }
        return switch (wanted) {
            case "op", "operator" -> Optional.of(OWNER);
            // trial_mod was the rung below mod until there was support; staff.yml may still say it.
            case "trial", "trialmod", "trial_mod", "helper" -> Optional.of(SUPPORT);
            case "moderator" -> Optional.of(MOD);
            case "developer" -> Optional.of(DEV);
            case "build" -> Optional.of(BUILDER);
            case "content", "media", "streamer", "youtuber" -> Optional.of(CREATOR);
            case "test", "qa" -> Optional.of(TESTER);
            default -> Optional.empty();
        };
    }

    /** What somebody types, ladder first — for tab completion. */
    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (StaffRank rank : values()) {
            names.add(rank.key());
        }
        return names;
    }

    /** Every node any rank starts with — for the list of this module's nodes. */
    public static List<String> everyGrantableNode() {
        Set<String> everything = new LinkedHashSet<>();
        for (StaffRank rank : values()) {
            everything.addAll(rank.nodes());
        }
        return List.copyOf(everything);
    }
}
