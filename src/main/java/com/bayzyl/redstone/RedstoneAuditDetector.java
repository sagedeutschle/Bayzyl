package com.bayzyl.redstone;

import java.util.List;

/** One static topology rule. Detectors read the snapshot only; they never see or change the world. */
@FunctionalInterface
public interface RedstoneAuditDetector {
    List<AuditFinding> detect(RedstoneAuditSnapshot snapshot);
}
