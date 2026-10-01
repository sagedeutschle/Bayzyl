package com.bayzyl.redstone;

/** What the fault does to the machine if it is real. Declaration order is report order. */
public enum AuditSeverity {
    /** The signal never reaches where it is going. */
    BREAKS,
    /** The signal arrives, but not the way the layout suggests (weakened, locked, mixed). */
    DEGRADES,
    /** Worth a look; often intentional. */
    ADVISORY
}
