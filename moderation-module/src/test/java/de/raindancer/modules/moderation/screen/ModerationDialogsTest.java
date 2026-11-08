package de.raindancer.modules.moderation.screen;

import de.raindancer.core.moderation.punishment.Punishments;
import de.raindancer.core.moderation.punishment.PunishmentKind;
import de.raindancer.core.moderation.vanish.Vanish;
import de.raindancer.core.platform.rule.Verdict;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.chat.Brand;
import de.raindancer.core.ui.dialog.DialogAnswers;
import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.ModerationPermission;
import de.raindancer.modules.moderation.model.Reason;
import de.raindancer.modules.moderation.model.Report;
import de.raindancer.modules.moderation.model.ReportGroup;
import de.raindancer.modules.moderation.model.ReportPreset;
import de.raindancer.modules.moderation.model.Sentence;
import de.raindancer.modules.moderation.model.Severity;
import de.raindancer.modules.moderation.rules.BanLimitRule;
import de.raindancer.modules.moderation.rules.StaffRule;
import de.raindancer.modules.moderation.service.FineService;
import de.raindancer.modules.moderation.service.NoteService;
import de.raindancer.modules.moderation.service.PunishmentService;
import de.raindancer.modules.moderation.service.ReportService;
import de.raindancer.modules.moderation.service.RuleBreachService;
import de.raindancer.modules.moderation.store.ReportRegistry;
import de.raindancer.modules.moderation.store.Reasons;
import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The moderation dialogs, pressed through as a moderator would: what they show, what each press leads to. */
class ModerationDialogsTest {

    private final UUID modId = UUID.nameUUIDFromBytes("Mod".getBytes());
    private final UUID bobId = UUID.nameUUIDFromBytes("Bob".getBytes());
    private final Player mod = mock(Player.class);
    private final ModerationServices services = mock(ModerationServices.class);
    private final Messages messages = mock(Messages.class);
    private final PunishmentService punishing = mock(PunishmentService.class);
    private final FineService fines = mock(FineService.class);
    private final NoteService notes = mock(NoteService.class);
    private final ReportService reporting = mock(ReportService.class);
    private final ReportRegistry reports = mock(ReportRegistry.class);
    private final Reasons reasons = mock(Reasons.class);
    private final BanLimitRule banLimit = mock(BanLimitRule.class);
    private final Set<ModerationPermission> held = EnumSet.noneOf(ModerationPermission.class);
    private final List<DialogPage> shown = new ArrayList<>();

    private final Reason spam = new Reason("spam", "Spamming", PunishmentKind.MUTE, Severity.MINOR,
            List.of(Sentence.of(Duration.ofHours(1))));
    private final Reason rude = new Reason("rude", "Being rude", PunishmentKind.KICK, Severity.MINOR,
            List.of(Sentence.forEver()));

    @BeforeEach
    void wire() {
        when(mod.getUniqueId()).thenReturn(modId);
        when(mod.getName()).thenReturn("Mod");
        when(mod.isOnline()).thenReturn(true);
        StaffRule staff = mock(StaffRule.class);
        when(staff.may(eq(modId), any())).thenAnswer(call -> held.contains(call.getArgument(1)));
        when(staff.canAct(eq(modId), any(), any())).thenAnswer(call -> held.contains(call.getArgument(2))
                ? Verdict.allowed() : Verdict.refused("moderation.no-permission"));
        Punishments punishments = mock(Punishments.class);
        when(punishments.active(any(), any())).thenReturn(Optional.empty());
        RuleBreachService breaches = mock(RuleBreachService.class);
        when(breaches.rules()).thenReturn(List.of());
        when(fines.hasEconomy()).thenReturn(true);
        when(fines.owed(any())).thenReturn(Money.ZERO);
        when(fines.limitFor(modId)).thenReturn(Optional.of(de.raindancer.core.social.economy.Fees.amount("500")));
        when(banLimit.mayBanFor(any(), any())).thenReturn(Verdict.allowed());
        when(banLimit.longestFor(any())).thenReturn(Optional.empty());
        when(banLimit.capDescribed()).thenReturn("a week");
        when(punishing.suggest(any(), any())).thenReturn(Sentence.of(Duration.ofHours(1)));
        when(reasons.forKind(PunishmentKind.MUTE)).thenReturn(List.of(spam));
        when(reasons.forKind(PunishmentKind.KICK)).thenReturn(List.of(rude));
        when(services.brand()).thenReturn(new Brand("Moderation"));
        when(services.messages()).thenReturn(messages);
        when(services.staffRule()).thenReturn(staff);
        when(services.punishments()).thenReturn(punishments);
        when(services.punishmentService()).thenReturn(punishing);
        when(services.ruleBreaches()).thenReturn(breaches);
        when(services.fines()).thenReturn(fines);
        when(services.banLimitRule()).thenReturn(banLimit);
        when(services.reasons()).thenReturn(reasons);
        when(services.noteService()).thenReturn(notes);
        when(services.reportService()).thenReturn(reporting);
        when(services.reports()).thenReturn(reports);
        when(services.vanish()).thenReturn(mock(Vanish.class));
        when(services.server()).thenReturn(mock(Server.class));

        DialogPage.runtime(new DialogPage.Runtime() {
            @Override
            public void show(Player player, DialogPage page) {
                shown.add(page);
            }

            @Override
            public void onPlayerThread(Player player, Runnable task) {
                task.run();
            }
        });
    }

    @AfterEach
    void realClient() {
        DialogPage.runtime(null);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private DialogPage last() {
        return shown.getLast();
    }

    private static DialogAnswers typed(Map<String, String> fields) {
        DialogResponseView view = mock(DialogResponseView.class);
        fields.forEach((key, value) -> when(view.getText(key)).thenReturn(value));
        return DialogAnswers.of(view);
    }

    @Test
    @DisplayName("a helper who may only warn sees every kind of punishment, the others greyed with the reason")
    void punishRoomGreysWhatIsNotTheirs() {
        held.add(ModerationPermission.WARN);
        DialogPage page = new PlayerPunishDialog(services, mod, () -> { }, bobId, "Bob").page();

        assertThat(plain(page.title())).isEqualTo("Moderation › Bob › Punish");
        assertThat(page.button("Warnings").allowed()).isTrue();
        assertThat(page.button("Mutes").allowed()).isFalse();
        assertThat(plain(page.button("Mutes").tooltip())).contains("You may not mute");
        assertThat(page.button("Ban for ever").allowed()).isFalse();
        assertThat(plain(page.button("Ban for ever").tooltip())).contains("an admin's decision");

        page.button("Mutes").press(mod, DialogAnswers.none());
        assertThat(last().notice()).isEqualTo("You may not mute");
    }

    @Test
    @DisplayName("mute: reason, then length, then the question — and only Yes hands it out")
    void muteGoesReasonLengthConfirm() {
        held.add(ModerationPermission.MUTE);
        AtomicInteger category = new AtomicInteger();
        new ReasonDialog(services, mod, category::incrementAndGet, bobId, "Bob", PunishmentKind.MUTE).open();
        assertThat(last().plainText()).contains("Why is Bob being muted?");

        last().button("Spamming").press(mod, DialogAnswers.none());
        assertThat(plain(last().title())).endsWith("How long");
        last().button("1 hour").press(mod, DialogAnswers.none());
        DialogPage question = last();
        assertThat(question.isConfirmation()).isTrue();
        verify(punishing, never()).punish(any(), anyString(), any(), anyString(), any(PunishmentKind.class),
                any(Sentence.class), anyString());

        question.button("Yes").press(mod, DialogAnswers.none());
        verify(punishing).punish(eq(modId), eq("Mod"), eq(bobId), eq("Bob"), eq(PunishmentKind.MUTE),
                any(Sentence.class), eq("Spamming"));
        assertThat(category).as("back to the mutes, which now show it in force").hasValue(1);
    }

    @Test
    @DisplayName("a typed length that is not one is said so, and nobody is muted for ever by a typo")
    void unreadableLength() {
        held.add(ModerationPermission.MUTE);
        new DurationDialog(services, mod, () -> { }, () -> { }, bobId, "Bob", spam).open();
        last().button("✎ The length I typed").press(mod, typed(Map.of(DurationDialog.TYPED, "soonish")));
        assertThat(last().notice()).contains("is not a length");
        assertThat(last().isConfirmation()).isFalse();
    }

    @Test
    @DisplayName("an own reason: empty is refused on the window, typed goes to the question for a kick")
    void ownReason() {
        held.add(ModerationPermission.KICK);
        new ReasonDialog(services, mod, () -> { }, bobId, "Bob", PunishmentKind.KICK).open();
        last().button("✎ Use my own reason").press(mod, typed(Map.of()));
        assertThat(last().notice()).isEqualTo("Type a reason in the box first.");

        last().button("✎ Use my own reason").press(mod, typed(Map.of(ReasonDialog.OWN, "flooding the chat")));
        assertThat(last().isConfirmation()).isTrue();
        last().button("Yes").press(mod, DialogAnswers.none());
        verify(punishing).punish(eq(modId), eq("Mod"), eq(bobId), eq("Bob"), eq(PunishmentKind.KICK),
                any(Sentence.class), eq("flooding the chat"));
    }

    @Test
    @DisplayName("a fine over the viewer's limit is refused on the window; one within it is asked about, then charged")
    void fine() {
        held.add(ModerationPermission.FINE);
        new FineDialog(services, mod, () -> { }, bobId, "Bob").open();
        last().button("Fine them").press(mod, typed(Map.of(FineDialog.AMOUNT, "900", FineDialog.WHY, "griefing")));
        assertThat(last().notice()).startsWith("You may fine up to");

        last().button("Fine them").press(mod, typed(Map.of(FineDialog.AMOUNT, "200", FineDialog.WHY, "griefing")));
        assertThat(last().isConfirmation()).isTrue();
        when(fines.fine(any(), anyString(), any(), anyString(), any(), anyString(), any(), any()))
                .thenReturn(new FineService.Result(FineService.Status.NO_ECONOMY, null, null, null, null));
        last().button("Yes").press(mod, DialogAnswers.none());
        verify(fines).fine(eq(modId), eq("Mod"), eq(bobId), eq("Bob"), eq(de.raindancer.core.social.economy.Fees.amount("200")), eq("griefing"), any(),
                eq(FineService.Kind.FINE));
    }

    @Test
    @DisplayName("a note is written from the box; without the right it is greyed and nothing is written")
    void notes() {
        when(notes.about(bobId)).thenReturn(List.of());
        DialogPage locked = new NotesDialog(services, mod, () -> { }, bobId, "Bob").page();
        assertThat(locked.button("Save the note").allowed()).isFalse();
        locked.button("Save the note").press(mod, typed(Map.of(NotesDialog.NOTE, "x")));
        verify(notes, never()).add(any(), anyString(), any(), anyString(), anyString());

        held.add(ModerationPermission.NOTES);
        new NotesDialog(services, mod, () -> { }, bobId, "Bob").page().button("Save the note")
                .press(mod, typed(Map.of(NotesDialog.NOTE, "  alt of Steve  ")));
        verify(notes).add(bobId, "Bob", modId, "Mod", "alt of Steve");
    }

    @Test
    @DisplayName("a report is closed with the typed sentence the reporter is told, and not without one")
    void reportClosesWithASentence() {
        held.add(ModerationPermission.REPORTS);
        Report report = Report.filed("R7", UUID.randomUUID(), "Ayla", bobId, "Bob", "Griefing — burnt my house",
                Instant.now().minusSeconds(60));
        when(reports.byId("R7")).thenReturn(Optional.of(report));
        when(reports.all()).thenReturn(List.of(report));
        DialogPage card = new ReportDialog(services, mod, () -> { }, "R7").page();
        assertThat(card.plainText()).contains("burnt my house");

        card.button("✔ Dealt with").press(mod, typed(Map.of()));
        assertThat(last().notice()).contains("Say in the box what you did");
        verify(reporting, never()).resolve(any(), any(), any(), any());

        card.button("✔ Dealt with").press(mod, typed(Map.of(ReportDialog.OUTCOME, "banned for a week")));
        verify(reporting).resolve("R7", modId, "Mod", "banned for a week");
    }

    @Test
    @DisplayName("filing a report: the exact reason from the list, what happened, one press")
    void filingAReport() {
        when(reporting.file(any(), anyString(), any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(reporting.mayFile(any(), any(), anyString())).thenReturn(Verdict.allowed());
        DialogPage form = new ReportFormDialog(services, mod, () -> { }, bobId, "Bob", ReportGroup.CHEATING).page();
        assertThat(form.fields()).containsExactly(ReportFormDialog.REASON, ReportFormDialog.WHAT);

        // Where both stood is read from the world; there is none in a test.
        try (var places = org.mockito.Mockito.mockStatic(ReportService.class)) {
            form.button("Send the report").press(mod, typed(Map.of(ReportFormDialog.REASON, "xray",
                    ReportFormDialog.WHAT, "straight to diamonds")));
        }
        verify(reporting).file(eq(modId), eq("Mod"), eq(bobId), eq("Bob"), eq("X-ray — straight to diamonds"),
                eq(ReportPreset.XRAY), any(), any());
    }
}
