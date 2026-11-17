package de.raindancer.modules.moderation;

import de.raindancer.core.data.settings.Describe;
import de.raindancer.core.data.settings.In;
import de.raindancer.core.data.settings.Key;
import de.raindancer.core.data.settings.Range;
import de.raindancer.core.data.settings.Settings;
import de.raindancer.core.data.settings.Title;
import de.raindancer.core.data.settings.Topic;
import de.raindancer.modules.moderation.rules.JoinWaveRule;
import de.raindancer.modules.moderation.rules.MacroRule;
import org.bukkit.Material;

import java.util.List;

/** Bot accounts and macros: what counts as suspicious, and what happens then. By default staff are told, nothing more. */
@Settings(id = "moderation-bots", topics = {
        @Topic(path = "moderation/anticheat/bots", title = "Bot accounts", icon = Material.ARMOR_STAND),
        @Topic(path = "moderation/anticheat/macros", title = "Macros and autoclickers", icon = Material.REPEATER),
})
public record BotGuardSettings(

        @In("moderation/anticheat/bots") @Title("Watch for bot accounts")
        @Describe("Watches new accounts: join waves, accounts from one network or with similar names, and what they "
                + "do in their first minutes. Staff with rainsanticheat.alerts are told.")
        @Key("bots.enabled")
        boolean bots,

        @In("moderation/anticheat/bots") @Title("New accounts for a wave") @Range(min = 3, max = 100)
        @Describe("This many new accounts within the window below are a join wave.")
        @Key("bots.wave-joins")
        int waveJoins,

        @In("moderation/anticheat/bots") @Title("Wave window, in seconds") @Range(min = 10, max = 600)
        @Describe("How far back joins count. Network hashes of new accounts are kept in memory for this long and "
                + "then deleted.")
        @Key("bots.wave-seconds")
        int waveSeconds,

        @In("moderation/anticheat/bots") @Title("New accounts from one network") @Range(min = 2, max = 50)
        @Describe("This many new accounts from one network (the same /24, or /48 for IPv6) within the window are "
                + "a signal for each of them.")
        @Key("bots.same-network")
        int sameNetwork,

        @In("moderation/anticheat/bots") @Title("New accounts with similar names") @Range(min = 2, max = 50)
        @Describe("This many new accounts whose names differ only in their trailing digits (Bot_1, Bot_2) within "
                + "the window are a signal for each of them.")
        @Key("bots.same-names")
        int sameNames,

        @In("moderation/anticheat/bots") @Title("Lock down on a bot wave")
        @Describe("A wave in which accounts share a network or similar names locks new accounts out for the time "
                + "below, on every server. Players who played before still join. A wave without either, like a "
                + "stream raid, is only told.")
        @Key("bots.auto-lockdown")
        boolean autoLockdown,

        @In("moderation/anticheat/bots") @Title("Lockdown length, in minutes") @Range(min = 1, max = 240)
        @Describe("How long an automatic lockdown lasts. /anticheat lockdown sets one by hand.")
        @Key("bots.lockdown-minutes")
        int lockdownMinutes,

        @In("moderation/anticheat/bots") @Title("First minutes watched, in seconds") @Range(min = 30, max = 900)
        @Describe("How long a new account is watched after joining: whether it moves or looks around, spams the "
                + "chat, and which client it sent.")
        @Key("bots.watch-seconds")
        int watchSeconds,

        @In("moderation/anticheat/bots") @Title("Bot clients")
        @Describe("Client brands of bot libraries, matched as part of the name, any case. Brands are easy to fake.")
        @Key("bots.brands")
        List<String> botBrands,

        @In("moderation/anticheat/bots") @Title("Action on a bot")
        @Describe("What happens to an account with enough signals: none, kick, freeze (quarantine), freeze 1h, "
                + "ban, ban 7d. None only tells staff.")
        @Key("bots.action")
        String botAction,

        @In("moderation/anticheat/bots") @Title("Signals for the action") @Range(min = 2, max = 6)
        @Describe("How many signals an account needs before the action applies. Staff are told from two.")
        @Key("bots.action-signals")
        int actionSignals,

        @In("moderation/anticheat/bots") @Title("Report bot accounts")
        @Describe("A suspected bot account also goes into the report queue, once while a report is open.")
        @Key("bots.report")
        boolean botReports,

        @In("moderation/anticheat/macros") @Title("Watch for macros")
        @Describe("Measures the gaps between clicks. A click rhythm that barely varies for minutes, or more left "
                + "clicks a second than a hand manages, is told to staff with the numbers.")
        @Key("macros.enabled")
        boolean macros,

        @In("moderation/anticheat/macros") @Title("Minutes of regular clicking") @Range(min = 1, max = 60)
        @Describe("How long the rhythm must stay regular.")
        @Key("macros.minutes")
        int macroMinutes,

        @In("moderation/anticheat/macros") @Title("Clicks needed") @Range(min = 20, max = 500)
        @Describe("At least this many gaps between clicks in that time.")
        @Key("macros.samples")
        int macroSamples,

        @In("moderation/anticheat/macros") @Title("Largest variation, in percent") @Range(min = 1, max = 30)
        @Describe("How much the gaps may vary and still count as a machine. People vary by 10 to 20 percent.")
        @Key("macros.max-variation")
        int maxVariation,

        @In("moderation/anticheat/macros") @Title("Most left clicks a second") @Range(min = 10, max = 50)
        @Describe("More than this, held for three seconds, is an autoclicker.")
        @Key("macros.max-cps")
        int maxCps,

        @In("moderation/anticheat/macros") @Title("Report macros")
        @Describe("A suspected macro also goes into the report queue, once while a report is open.")
        @Key("macros.report")
        boolean macroReports,

        @In("moderation/anticheat/macros") @Title("Action on a macro")
        @Describe("What happens to a suspected macro user: none, kick, freeze, freeze 1h, ban 1d. None only tells "
                + "staff.")
        @Key("macros.action")
        String macroAction,

        @In("moderation/anticheat/bots") @Title("Keep cases, in days") @Range(min = 1, max = 90)
        @Describe("Bot and macro cases are deleted after this many days. They hold name, account id and what was "
                + "seen, never an address.")
        @Key("cases.keep-days")
        int keepDays) {

    public static final BotGuardSettings DEFAULTS = new BotGuardSettings(
            true, 6, 60, 3, 3, true, 10, 120,
            List.of("mineflayer", "mcprotocollib", "minecraft-protocol"), "none", 3, true,
            true, 3, 40, 5, 22, true, "none",
            30);

    public BotGuardSettings {
        botBrands = botBrands == null ? List.of() : List.copyOf(botBrands);
        botAction = botAction == null ? "none" : botAction;
        macroAction = macroAction == null ? "none" : macroAction;
    }

    public JoinWaveRule.Policy wavePolicy() {
        return new JoinWaveRule.Policy(waveJoins, waveSeconds * 1000L, sameNetwork, sameNames);
    }

    public MacroRule.Policy macroPolicy() {
        return new MacroRule.Policy(macroSamples, maxVariation, macroMinutes * 60_000L, maxCps);
    }

    public BotGuardSettings withBots(boolean on) {
        return new BotGuardSettings(on, waveJoins, waveSeconds, sameNetwork, sameNames, autoLockdown, lockdownMinutes,
                watchSeconds, botBrands, botAction, actionSignals, botReports, macros, macroMinutes, macroSamples, maxVariation,
                maxCps, macroReports, macroAction, keepDays);
    }

    public BotGuardSettings withAutoLockdown(boolean on) {
        return new BotGuardSettings(bots, waveJoins, waveSeconds, sameNetwork, sameNames, on, lockdownMinutes,
                watchSeconds, botBrands, botAction, actionSignals, botReports, macros, macroMinutes, macroSamples, maxVariation,
                maxCps, macroReports, macroAction, keepDays);
    }

    public BotGuardSettings withBotAction(String action, int signals) {
        return new BotGuardSettings(bots, waveJoins, waveSeconds, sameNetwork, sameNames, autoLockdown, lockdownMinutes,
                watchSeconds, botBrands, action, signals, botReports, macros, macroMinutes, macroSamples, maxVariation,
                maxCps, macroReports, macroAction, keepDays);
    }

    public BotGuardSettings withMacros(boolean on) {
        return new BotGuardSettings(bots, waveJoins, waveSeconds, sameNetwork, sameNames, autoLockdown, lockdownMinutes,
                watchSeconds, botBrands, botAction, actionSignals, botReports, on, macroMinutes, macroSamples, maxVariation,
                maxCps, macroReports, macroAction, keepDays);
    }

    public BotGuardSettings withMacroReports(boolean on) {
        return new BotGuardSettings(bots, waveJoins, waveSeconds, sameNetwork, sameNames, autoLockdown, lockdownMinutes,
                watchSeconds, botBrands, botAction, actionSignals, botReports, macros, macroMinutes, macroSamples, maxVariation,
                maxCps, on, macroAction, keepDays);
    }

    public BotGuardSettings withMacroAction(String action) {
        return new BotGuardSettings(bots, waveJoins, waveSeconds, sameNetwork, sameNames, autoLockdown, lockdownMinutes,
                watchSeconds, botBrands, botAction, actionSignals, botReports, macros, macroMinutes, macroSamples, maxVariation,
                maxCps, macroReports, action, keepDays);
    }

    public BotGuardSettings withBotReports(boolean on) {
        return new BotGuardSettings(bots, waveJoins, waveSeconds, sameNetwork, sameNames, autoLockdown, lockdownMinutes,
                watchSeconds, botBrands, botAction, actionSignals, on, macros, macroMinutes, macroSamples, maxVariation,
                maxCps, macroReports, macroAction, keepDays);
    }
}
