package com.bayzyl;

import com.bayzyl.security.BayzylAccess;
import com.bayzyl.security.CommandAccessPolicy;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EditCommandBusyTest {
    @Test
    void namespacedUndoCannotRaceThePlayersChunkedPaste() throws Exception {
        EditService edits = mock(EditService.class);
        HistoryService history = mock(HistoryService.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(edits.hasPasteTask(id)).thenReturn(true);
        when(history.hasUndo(player)).thenReturn(true);
        Constructor<?> ctor = BayzylCommand.class.getConstructors()[0];
        Class<?>[] types = ctor.getParameterTypes();
        Object[] dependencies = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            dependencies[i] = types[i] == EditService.class ? edits
                    : types[i] == HistoryService.class ? history
                    : types[i] == BayzylAccess.class ? new BayzylAccess()
                    : types[i] == CommandAccessPolicy.class ? new CommandAccessPolicy() : mock(types[i]);
        }
        BayzylCommand handler = (BayzylCommand) ctor.newInstance(dependencies);
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("undo");
        assertTrue(handler.onCommand(player, command, "bayzyl:undo", new String[0]));
        verify(history, never()).undo(any(), anyInt());
    }
}
