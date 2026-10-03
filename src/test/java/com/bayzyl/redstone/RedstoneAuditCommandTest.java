package com.bayzyl.redstone;

import com.bayzyl.redstone.RedstoneAuditSnapshotFactory.Capture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedstoneAuditCommandTest {
    private static final RedstoneAuditSnapshot EMPTY_MACHINE =
            RedstoneAuditSnapshot.builder(new AuditPosition(0, 0, 0), new AuditPosition(31, 3, 9)).build();

    @Test
    void findingsAreRankedWithCoordinatesAndClickToHighlight() {
        RedstoneAuditCommand command = command(findings(1), player -> Capture.of(EMPTY_MACHINE));
        Player player = player();

        command.handle(player, new String[0]);

        List<Component> lines = components(player);
        Component first = lines.stream().filter(line -> plain(line).contains("#1")).findFirst().orElseThrow();
        assertTrue(plain(first).contains("HIGH"), plain(first));
        assertTrue(plain(first).contains("Finding 0"), plain(first));
        assertTrue(plain(first).contains("x:0 y:1 z:0"), plain(first));
        ClickEvent click = clickOf(first);
        assertNotNull(click, "each result is clickable");
        assertEquals(ClickEvent.Action.RUN_COMMAND, click.action());
        assertEquals("/redstoneaudit show 1", click.value());
    }

    @Test
    void longResultListsArePaginated() {
        RedstoneAuditCommand command = command(findings(12), player -> Capture.of(EMPTY_MACHINE));
        Player player = player();

        command.handle(player, new String[0]);
        assertEquals(RedstoneAuditCommand.PAGE_SIZE, resultLines(player).size());

        command.handle(player, new String[]{"page", "2"});
        List<String> all = resultLines(player);
        assertEquals(12, all.size(), all.toString());
        assertTrue(all.stream().anyMatch(line -> line.contains("#12")), all.toString());
    }

    @Test
    void cleanMachineSaysSo() {
        RedstoneAuditCommand command = command(findings(0), player -> Capture.of(EMPTY_MACHINE));
        Player player = player();
        command.handle(player, new String[0]);
        assertTrue(texts(player).stream().anyMatch(text -> text.contains("No likely wiring faults")), texts(player).toString());
    }

    @Test
    void refusalIsReportedAndNothingIsAudited() {
        List<RedstoneAuditSnapshot> audited = new ArrayList<>();
        RedstoneAuditService service = new RedstoneAuditService(List.of(snapshot -> {
            audited.add(snapshot);
            return List.of();
        }));
        RedstoneAuditCommand command = new RedstoneAuditCommand(service,
                player -> Capture.refused("Select the machine first."), RedstoneAuditCommand.MarkerSink.NONE);
        Player player = player();

        command.handle(player, new String[0]);

        assertTrue(audited.isEmpty());
        assertTrue(texts(player).stream().anyMatch(text -> text.contains("Select the machine first.")));
    }

    @Test
    void showNeedsAnAuditAndAValidNumber() {
        List<AuditFinding> shown = new ArrayList<>();
        RedstoneAuditCommand command = new RedstoneAuditCommand(new RedstoneAuditService(List.of(snapshot -> findings(3))),
                player -> Capture.of(EMPTY_MACHINE), new RedstoneAuditCommand.MarkerSink() {
                    @Override
                    public void show(Player player, List<AuditFinding> findings, java.util.UUID worldId) {
                        shown.addAll(findings);
                    }

                    @Override
                    public void clear(UUID playerId) {
                    }
                });
        Player player = player();

        command.handle(player, new String[]{"show", "1"});
        assertTrue(texts(player).stream().anyMatch(text -> text.contains("/redstoneaudit")), texts(player).toString());

        command.handle(player, new String[0]);
        shown.clear();
        command.handle(player, new String[]{"show", "4"});
        assertTrue(shown.isEmpty());
        command.handle(player, new String[]{"show", "2"});
        assertEquals(List.of(findings(3).get(1)), shown);
    }

    @Test
    void markersAreGivenTheWorldTheSelectionWasCapturedIn() {
        UUID auditedWorld = UUID.randomUUID();
        List<UUID> worlds = new ArrayList<>();
        RedstoneAuditCommand command = new RedstoneAuditCommand(new RedstoneAuditService(List.of(snapshot -> findings(3))),
                player -> Capture.of(EMPTY_MACHINE, auditedWorld), new RedstoneAuditCommand.MarkerSink() {
                    @Override
                    public void show(Player player, List<AuditFinding> findings, UUID worldId) {
                        worlds.add(worldId);
                    }

                    @Override
                    public void clear(UUID playerId) {
                    }
                });
        Player player = player();

        command.handle(player, new String[0]);
        command.handle(player, new String[]{"show", "2"});

        assertEquals(List.of(auditedWorld, auditedWorld), worlds);
    }

    @Test
    void suggestsSubcommands() {
        assertEquals(List.of("clear", "page", "show"), com.bayzyl.SuggestionUtil.redstoneAuditSuggestions(new String[]{""}));
        assertEquals(List.of("show"), com.bayzyl.SuggestionUtil.redstoneAuditSuggestions(new String[]{"sh"}));
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private static RedstoneAuditCommand command(List<AuditFinding> findings, RedstoneAuditCommand.SelectionCapture capture) {
        return new RedstoneAuditCommand(new RedstoneAuditService(List.of(snapshot -> findings)), capture,
                RedstoneAuditCommand.MarkerSink.NONE);
    }

    private static List<AuditFinding> findings(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new AuditFinding(new AuditPosition(i, 1, 0), AuditConfidence.HIGH, AuditSeverity.BREAKS,
                        "test-" + i, "Finding " + i, "pattern " + i, "fix " + i))
                .toList();
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private static List<Component> components(Player player) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeast(0)).sendMessage(captor.capture());
        return captor.getAllValues();
    }

    private static List<String> resultLines(Player player) {
        return components(player).stream().map(RedstoneAuditCommandTest::plain).filter(line -> line.contains("#")).toList();
    }

    private static List<String> texts(Player player) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player, atLeast(0)).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>(captor.getAllValues());
        components(player).forEach(component -> texts.add(plain(component)));
        return texts;
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static ClickEvent clickOf(Component component) {
        if (component.clickEvent() != null) {
            return component.clickEvent();
        }
        for (Component child : component.children()) {
            ClickEvent nested = clickOf(child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }
}
