package com.bayzyl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BayzylRecoveryLifecycleTest {

    @Test
    void quiesceRunsEveryCancellerAndReportsCleanOnlyWhenNoneThrew() {
        List<String> ran = new ArrayList<>();
        assertTrue(Bayzyl.quiesce(List.of(() -> ran.add("edit"), () -> ran.add("history"))));
        assertEquals(List.of("edit", "history"), ran);

        ran.clear();
        assertFalse(Bayzyl.quiesce(List.of(
                () -> { throw new IllegalStateException("stuck"); },
                () -> ran.add("history"))));
        assertEquals(List.of("history"), ran);
    }

    @Test
    void unreadableRecoveryStateDisablesRecoveryWithASevereLogInsteadOfFailingEnable() {
        Logger logger = Logger.getLogger("BayzylRecoveryLifecycleTest");
        logger.setUseParentHandlers(false);
        List<LogRecord> records = new ArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(capture);
        try {
            CrashRecoveryService opened = Bayzyl.openCrashRecovery(() -> {
                throw new IllegalStateException("crash-recovery state is unreadable");
            }, logger);
            assertNull(opened);
            assertEquals(1, records.size());
            assertEquals(Level.SEVERE, records.get(0).getLevel());
            assertTrue(records.get(0).getMessage().contains("crash-recovery state is unreadable"));
        } finally {
            logger.removeHandler(capture);
        }
    }
}
