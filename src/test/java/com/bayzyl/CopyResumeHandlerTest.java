package com.bayzyl;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CopyResumeHandlerTest {
    private record Run(Selection selection, String mask) {
    }

    @Test
    void reRunsCopyWithTheRestoredSelectionAndMask() {
        List<Run> runs = new ArrayList<>();
        CopyResumeHandler handler = new CopyResumeHandler((player, selection, mask) -> runs.add(new Run(selection, mask.getRaw())));
        Selection selection = new Selection(CrashRecoveryTransactionTest.location(0, 0, 0),
                CrashRecoveryTransactionTest.location(3, 3, 3), SelectionType.CUBOID);

        handler.resume(mock(Player.class), session(Map.of("selection", selection, "mask", BlockMask.deferred("stone"))));

        assertEquals(1, runs.size());
        assertSame(selection, runs.get(0).selection());
        assertEquals("stone", runs.get(0).mask());
    }

    @Test
    void withoutARestorableSelectionItAsksThePlayerInsteadOfCopying() {
        List<Run> runs = new ArrayList<>();
        CopyResumeHandler handler = new CopyResumeHandler((player, selection, mask) -> runs.add(new Run(selection, mask.getRaw())));
        Player player = mock(Player.class);

        handler.resume(player, session(Map.of()));
        handler.resume(player, session(Map.of("selection",
                new Selection(CrashRecoveryTransactionTest.location(0, 0, 0), null, SelectionType.CUBOID))));

        assertTrue(runs.isEmpty());
        verify(player, org.mockito.Mockito.times(2)).sendMessage(anyString());
    }

    private static CrashRecoveryService.ActiveCommandSession session(Map<String, Object> data) {
        return new CrashRecoveryService.ActiveCommandSession(UUID.randomUUID(), "copy", "starting", data, 0L, null, true);
    }
}
