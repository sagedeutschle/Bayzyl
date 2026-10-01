package com.bayzyl.redstone;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** The audit runs on the main thread, so a selection at the size cap must finish quickly even when it is messy. */
class RedstoneAuditScaleTest {

    @Test
    void auditAtTheSizeCapWithManyBackwardsRepeatersStaysFast() {
        // 64 x 32 x 64 = the cap. Every row repeats [dust][repeater facing east][air]: each repeater has nothing
        // behind it and dust in front, the shape that asks "does the signal arrive from the front?".
        RedstoneAuditSnapshot.Builder builder = RedstoneAuditSnapshot.builder(
                new AuditPosition(0, 0, 0), new AuditPosition(63, 31, 63));
        for (int y = 0; y < 32; y++) {
            for (int z = 0; z < 64; z++) {
                for (int x = 0; x < 64; x++) {
                    switch (x % 3) {
                        case 0 -> builder.put(new AuditPosition(x, y, z), Layout.eastWestDust());
                        case 1 -> builder.put(new AuditPosition(x, y, z), AuditCell.repeater(Side.EAST));
                        default -> {
                        }
                    }
                }
            }
        }
        RedstoneAuditSnapshot snapshot = builder.build();

        assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> assertFalse(RedstoneAuditService.standard().audit(snapshot).isEmpty()));
    }
}
