package com.bayzyl;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoryRecordingRetryTest {
    @Test
    void retryAfterPersistenceThrowsDoesNotPushTheActionTwice() {
        EditHistory stack = new EditHistory();
        PersistentEditHistoryService persistence = mock(PersistentEditHistoryService.class);
        HistoryService history = new HistoryService(stack, new SelectionManager(), persistence, mock(JavaPlugin.class));
        UUID player = UUID.randomUUID();
        BlockData data = mock(BlockData.class);
        BlockChange change = new BlockChange(new Location(mock(World.class), 0, 64, 0), data, data);
        EntityChange entity = mock(EntityChange.class);
        HistoryService.RecordAttempt attempt = new HistoryService.RecordAttempt();
        doThrow(new IllegalStateException("serialization failed")).doNothing().when(persistence).save(eq(player), anyList(), anyList());

        assertThrows(IllegalStateException.class, () -> history.recordOnce(player, List.of(change), List.of(entity), attempt));
        history.recordOnce(player, List.of(change), List.of(entity), attempt);

        assertEquals(1, stack.snapshotUndo(player, 10).size());
        assertEquals(List.of(entity), stack.peekUndo(player).getEntityChanges());
        verify(persistence, times(2)).save(eq(player), anyList(), anyList());
    }
}
