package com.bayzyl.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicYamlStoreTest {
    private static final AtomicYamlStore.SnapshotCodec<String> CODEC = new AtomicYamlStore.SnapshotCodec<>() {
        @Override
        public byte[] encode(String snapshot) {
            return snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public String decodeStrict(byte[] encoded) throws IOException {
            String value = new String(encoded, java.nio.charset.StandardCharsets.UTF_8);
            if (!value.startsWith("valid:")) {
                throw new IOException("invalid test snapshot");
            }
            return value;
        }
    };

    @TempDir
    Path temporaryDirectory;

    @Test
    void replacementWritesNewCompleteSnapshotAndPreservesValidFormerTargetAsBackup() throws Exception {
        Path target = temporaryDirectory.resolve("recovery.yml");
        Files.writeString(target, "valid:old");
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run);

        assertTrue(store.submit(1, "valid:new"));
        assertTrue(store.flush().success());

        assertEquals("valid:new", Files.readString(target));
        assertEquals("valid:old", Files.readString(store.backupPath()));
        assertEquals(1, store.persistedRevision());
    }

    @Test
    void publicObserverOverloadDeliversSuccessAndFailureCallbacks() {
        Path successfulTarget = temporaryDirectory.resolve("success.yml");
        AtomicReference<Long> successfulRevision = new AtomicReference<>();
        AtomicReference<Exception> unexpectedFailure = new AtomicReference<>();
        AtomicYamlStore<String> successful = new AtomicYamlStore<>(successfulTarget, CODEC, Runnable::run,
                successfulRevision::set, unexpectedFailure::set);

        assertTrue(successful.submit(7, "valid:seven"));
        assertEquals(7L, successfulRevision.get());
        assertNull(unexpectedFailure.get());

        AtomicReference<Exception> rejection = new AtomicReference<>();
        AtomicYamlStore<String> rejected = new AtomicYamlStore<>(temporaryDirectory.resolve("rejected.yml"), CODEC,
                runnable -> { throw new java.util.concurrent.RejectedExecutionException("test rejection"); },
                ignored -> { }, rejection::set);
        assertFalse(rejected.submit(8, "valid:eight"));
        assertTrue(rejection.get() instanceof java.util.concurrent.RejectedExecutionException);
    }

    @Test
    void newerSnapshotSubmittedWhileWriteIsBlockedIsDrainedInSecondPass() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        QueuedExecutor executor = new QueuedExecutor();
        CountDownLatch encoderStarted = new CountDownLatch(1);
        CountDownLatch releaseEncoder = new CountDownLatch(1);
        AtomicYamlStore.SnapshotCodec<String> blockingCodec = new AtomicYamlStore.SnapshotCodec<>() {
            @Override
            public byte[] encode(String snapshot) throws IOException {
                if (snapshot.equals("valid:one")) {
                    encoderStarted.countDown();
                    try {
                        if (!releaseEncoder.await(1, TimeUnit.SECONDS)) {
                            throw new IOException("test encoder timed out");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException(exception);
                    }
                }
                return CODEC.encode(snapshot);
            }

            @Override
            public String decodeStrict(byte[] bytes) throws IOException {
                return CODEC.decodeStrict(bytes);
            }
        };
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, blockingCodec, executor);
        assertTrue(store.submit(1, "valid:one"));

        Thread writer = new Thread(executor::runNext);
        writer.start();
        assertTrue(encoderStarted.await(1, TimeUnit.SECONDS));
        assertTrue(store.submit(2, "valid:two"));
        releaseEncoder.countDown();
        writer.join(1_000);
        executor.runAll();

        assertEquals("valid:two", Files.readString(target));
        assertEquals(2, store.persistedRevision());
    }

    @Test
    void rejectionAndPersistentFailureKeepNewestRevisionWithoutSpinningFlush() {
        Path target = temporaryDirectory.resolve("state.yml");
        AtomicInteger failures = new AtomicInteger();
        Executor rejectingExecutor = runnable -> {
            throw new java.util.concurrent.RejectedExecutionException("no worker");
        };
        AtomicYamlStore<String> rejected = new AtomicYamlStore<>(target, CODEC, rejectingExecutor);

        assertFalse(rejected.submit(1, "valid:one"));
        assertEquals(1, rejected.pendingRevision());
        assertEquals(AtomicYamlStore.State.RETRYABLE, rejected.state());

        AtomicYamlStore.FileOperations failingWrites = new DelegatingFileOperations() {
            @Override
            public void writeAndForce(Path path, byte[] content) throws IOException {
                failures.incrementAndGet();
                throw new IOException("disk full");
            }
        };
        AtomicYamlStore<String> failing = new AtomicYamlStore<>(target, CODEC, Runnable::run,
                failingWrites, ignored -> { }, ignored -> { });
        assertTrue(failing.submit(2, "valid:two"));

        AtomicYamlStore.FlushResult result = failing.flush(Duration.ofMillis(100));
        assertFalse(result.success());
        assertEquals(2, failing.pendingRevision());
        assertEquals(2, failures.get(), "one worker attempt and one bounded flush attempt");
    }

    @Test
    void executorErrorRollsBackReservationForEqualRevisionRetry() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        AtomicBoolean fail = new AtomicBoolean(true);
        Executor executor = runnable -> {
            if (fail.getAndSet(false)) {
                throw new AssertionError("executor broke");
            }
            runnable.run();
        };
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, executor);

        assertThrows(AssertionError.class, () -> store.submit(1, "valid:original"));
        assertEquals(AtomicYamlStore.State.RETRYABLE, store.state());
        assertEquals(1, store.pendingRevision());
        assertTrue(store.submit(1, "valid:replacement-must-not-win"));

        assertEquals("valid:original", Files.readString(target));
        assertEquals(1, store.persistedRevision());
    }

    @Test
    void flushStealsScheduledWorkWhenAcceptedExecutorHasNotStartedIt() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        QueuedExecutor executor = new QueuedExecutor();
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, executor);
        assertTrue(store.submit(1, "valid:one"));

        AtomicYamlStore.FlushResult result = store.flush(Duration.ofSeconds(1));

        assertTrue(result.success());
        assertEquals("valid:one", Files.readString(target));
        executor.runAll();
        assertEquals(1, store.persistedRevision());
    }

    @Test
    void staleQueuedReservationCannotRunAfterFailedFlushAndEqualRevisionRetry() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        QueuedExecutor executor = new QueuedExecutor();
        AtomicInteger writes = new AtomicInteger();
        AtomicYamlStore.FileOperations failFirstWrite = new DelegatingFileOperations() {
            @Override
            public void writeAndForce(Path path, byte[] content) throws IOException {
                if (writes.incrementAndGet() == 1) {
                    throw new IOException("first flush write failed");
                }
                super.writeAndForce(path, content);
            }
        };
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, executor,
                failFirstWrite, ignored -> { }, ignored -> { });
        assertTrue(store.submit(1, "valid:original"));
        assertFalse(store.flush(Duration.ofSeconds(1)).success());
        assertTrue(store.submit(1, "valid:replacement-must-not-win"));

        executor.runNext();
        assertFalse(Files.exists(target), "stale queued reservation executed the retried entry");
        executor.runNext();

        assertEquals("valid:original", Files.readString(target));
        assertEquals(1, store.persistedRevision());
    }

    @Test
    void concurrentFlushWaitsForTheOwningFlushInsteadOfReportingEarlySuccess() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        QueuedExecutor executor = new QueuedExecutor();
        CountDownLatch encoding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicYamlStore.SnapshotCodec<String> blockingCodec = new AtomicYamlStore.SnapshotCodec<>() {
            @Override
            public byte[] encode(String snapshot) throws IOException {
                encoding.countDown();
                try {
                    if (!release.await(1, TimeUnit.SECONDS)) {
                        throw new IOException("timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException(exception);
                }
                return CODEC.encode(snapshot);
            }

            @Override
            public String decodeStrict(byte[] encoded) throws IOException {
                return CODEC.decodeStrict(encoded);
            }
        };
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, blockingCodec, executor);
        assertTrue(store.submit(1, "valid:one"));
        AtomicReference<AtomicYamlStore.FlushResult> first = new AtomicReference<>();
        AtomicReference<AtomicYamlStore.FlushResult> second = new AtomicReference<>();
        Thread firstFlush = new Thread(() -> first.set(store.flush(Duration.ofSeconds(1))));
        Thread secondFlush = new Thread(() -> second.set(store.flush(Duration.ofSeconds(1))));
        firstFlush.start();
        assertTrue(encoding.await(1, TimeUnit.SECONDS));
        secondFlush.start();
        release.countDown();
        firstFlush.join(1_000);
        secondFlush.join(1_000);

        assertTrue(first.get().success());
        assertTrue(second.get().success());
        assertEquals("valid:one", Files.readString(target));
    }

    @Test
    void genericMoveFailureDoesNotFallbackOrReplaceTarget() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        Files.writeString(target, "valid:old");
        AtomicInteger targetMoves = new AtomicInteger();
        AtomicInteger nonAtomicTargetMoves = new AtomicInteger();
        AtomicYamlStore.FileOperations failingMove = new DelegatingFileOperations() {
            @Override
            public void move(Path source, Path destination, java.nio.file.CopyOption... options) throws IOException {
                if (destination.equals(target)) {
                    targetMoves.incrementAndGet();
                    boolean atomic = java.util.Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE);
                    if (!atomic) {
                        nonAtomicTargetMoves.incrementAndGet();
                    }
                    throw new IOException("ordinary move failure");
                }
                super.move(source, destination, options);
            }
        };
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run,
                failingMove, ignored -> { }, ignored -> { });

        assertTrue(store.submit(1, "valid:new"));

        assertEquals("valid:old", Files.readString(target));
        assertEquals(0, nonAtomicTargetMoves.get(), "generic failure must not invoke fallback move");
        assertTrue(targetMoves.get() >= 1, "replacement failure must be observed");
        assertEquals(1, store.pendingRevision());
    }

    @Test
    void atomicMoveUnsupportedUsesFallbackWithoutDeletingTargetFirst() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        Files.writeString(target, "valid:old");
        AtomicInteger targetMoves = new AtomicInteger();
        AtomicYamlStore.FileOperations atomicUnsupported = new DelegatingFileOperations() {
            @Override
            public void move(Path source, Path destination, java.nio.file.CopyOption... options) throws IOException {
                if (destination.equals(target) && targetMoves.incrementAndGet() == 1) {
                    throw new AtomicMoveNotSupportedException(source.toString(), destination.toString(), "test");
                }
                super.move(source, destination, options);
            }
        };
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run,
                atomicUnsupported, ignored -> { }, ignored -> { });

        assertTrue(store.submit(1, "valid:new"));

        assertEquals("valid:new", Files.readString(target));
        assertEquals(2, targetMoves.get(), "only target replacement may use the non-atomic fallback");
    }

    @Test
    void backupTempFailureBlocksTargetReplacement() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        Files.writeString(target, "valid:old");
        AtomicYamlStore.FileOperations backupFailure = new DelegatingFileOperations() {
            @Override
            public void writeAndForce(Path path, byte[] content) throws IOException {
                if (path.getFileName().toString().startsWith("state.yml.bak.tmp-")) {
                    throw new IOException("backup force failure");
                }
                super.writeAndForce(path, content);
            }
        };
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run,
                backupFailure, ignored -> { }, ignored -> { });

        assertTrue(store.submit(1, "valid:new"));

        assertEquals("valid:old", Files.readString(target));
        assertEquals(1, store.pendingRevision());
        assertEquals(AtomicYamlStore.State.RETRYABLE, store.state());
    }

    @Test
    void corruptPrimaryLoadsValidBackupAndQuarantinesPrimaryWithoutReplacingBackup() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        Files.writeString(target, "corrupt-primary");
        Files.writeString(target.resolveSibling("state.yml.bak"), "valid:backup");
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run);

        AtomicYamlStore.LoadResult<String> result = store.load();

        assertEquals(AtomicYamlStore.LoadStatus.BACKUP_RECOVERED, result.status());
        assertTrue(result.abnormal());
        assertEquals("valid:backup", result.snapshot());
        assertEquals("valid:backup", Files.readString(store.backupPath()));
        try (var paths = Files.list(temporaryDirectory)) {
            Path quarantine = paths.filter(path -> path.getFileName().toString().startsWith("state.yml.corrupt-"))
                    .findFirst().orElse(null);
            assertNotNull(quarantine);
            assertEquals("corrupt-primary", Files.readString(quarantine));
        }
    }

    @Test
    void corruptPrimaryRecoveryFailsClosedWhenQuarantineCannotPreserveEvidence() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        Files.writeString(target, "corrupt-primary");
        Files.writeString(target.resolveSibling("state.yml.bak"), "valid:backup");
        AtomicYamlStore.FileOperations rejectQuarantine = new DelegatingFileOperations() {
            @Override
            public void writeAndForce(Path path, byte[] content) throws IOException {
                if (path.getFileName().toString().startsWith("state.yml.corrupt-")) {
                    throw new IOException("cannot preserve corrupt primary");
                }
                super.writeAndForce(path, content);
            }
        };
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run,
                rejectQuarantine, ignored -> { }, ignored -> { });

        AtomicYamlStore.LoadResult<String> result = store.load();

        assertEquals(AtomicYamlStore.LoadStatus.FAILED_CLOSED, result.status());
        assertEquals("corrupt-primary", Files.readString(target));
        assertEquals("valid:backup", Files.readString(store.backupPath()));
        try (var paths = Files.list(temporaryDirectory)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith("state.yml.corrupt-")));
        }
    }

    @Test
    void writeQuarantinesCorruptPrimaryBeforeReplacingItAndDoesNotTouchValidBackup() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        Files.writeString(target, "corrupt-primary");
        Files.writeString(target.resolveSibling("state.yml.bak"), "valid:backup");
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run);

        assertTrue(store.submit(1, "valid:new"));

        assertEquals("valid:new", Files.readString(target));
        assertEquals("valid:backup", Files.readString(store.backupPath()));
        try (var paths = Files.list(temporaryDirectory)) {
            Path quarantine = paths.filter(path -> path.getFileName().toString().startsWith("state.yml.corrupt-"))
                    .findFirst().orElse(null);
            assertNotNull(quarantine);
            assertEquals("corrupt-primary", Files.readString(quarantine));
        }
    }

    @Test
    void failedFinalReplacementKeepsCorruptTargetAfterItsQuarantineCopyWasForced() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        Files.writeString(target, "corrupt-primary");
        Files.writeString(target.resolveSibling("state.yml.bak"), "valid:backup");
        AtomicYamlStore.FileOperations finalMoveFailure = new DelegatingFileOperations() {
            @Override
            public void move(Path source, Path destination, java.nio.file.CopyOption... options) throws IOException {
                if (destination.equals(target)) {
                    throw new IOException("final target move failed");
                }
                super.move(source, destination, options);
            }
        };
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run,
                finalMoveFailure, ignored -> { }, ignored -> { });

        assertTrue(store.submit(1, "valid:new"));

        assertEquals("corrupt-primary", Files.readString(target));
        assertEquals("valid:backup", Files.readString(store.backupPath()));
        assertEquals(1, store.pendingRevision());
        try (var paths = Files.list(temporaryDirectory)) {
            Path quarantine = paths.filter(path -> path.getFileName().toString().startsWith("state.yml.corrupt-"))
                    .findFirst().orElse(null);
            assertNotNull(quarantine);
            assertEquals("corrupt-primary", Files.readString(quarantine));
        }
    }

    @Test
    void bothCorruptFilesFailClosedAndMissingPrimaryWithBackupIsAbnormal() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        Files.writeString(target, "broken");
        Files.writeString(target.resolveSibling("state.yml.bak"), "also-broken");
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run);

        AtomicYamlStore.LoadResult<String> corrupt = store.load();
        assertEquals(AtomicYamlStore.LoadStatus.FAILED_CLOSED, corrupt.status());
        assertNull(corrupt.snapshot());

        Path backupOnlyTarget = temporaryDirectory.resolve("backup-only.yml");
        AtomicYamlStore<String> backupOnlyStore = new AtomicYamlStore<>(backupOnlyTarget, CODEC, Runnable::run);
        Files.writeString(backupOnlyStore.backupPath(), "valid:backup");
        AtomicYamlStore.LoadResult<String> backupOnly = backupOnlyStore.load();
        assertEquals(AtomicYamlStore.LoadStatus.BACKUP_RECOVERED, backupOnly.status());
        assertTrue(backupOnly.abnormal());
    }

    @Test
    void validPrimaryWinsOverBackupAndBrandNewStoreIsNotAbnormal() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        AtomicYamlStore<String> fresh = new AtomicYamlStore<>(target, CODEC, Runnable::run);
        AtomicYamlStore.LoadResult<String> missing = fresh.load();
        assertEquals(AtomicYamlStore.LoadStatus.MISSING, missing.status());
        assertFalse(missing.abnormal());

        Files.writeString(target, "valid:primary");
        Files.writeString(fresh.backupPath(), "valid:backup");
        AtomicYamlStore.LoadResult<String> primary = fresh.load();
        assertEquals(AtomicYamlStore.LoadStatus.PRIMARY, primary.status());
        assertEquals("valid:primary", primary.snapshot());
    }

    @Test
    void indeterminatePresenceFailsClosedInsteadOfReportingMissing() {
        Path target = temporaryDirectory.resolve("state.yml");
        AtomicYamlStore.FileOperations indeterminate = new DelegatingFileOperations() {
            @Override
            public AtomicYamlStore.Presence presence(Path path) throws IOException {
                throw new IOException("access denied");
            }
        };
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run,
                indeterminate, ignored -> { }, ignored -> { });

        AtomicYamlStore.LoadResult<String> result = store.load();

        assertEquals(AtomicYamlStore.LoadStatus.FAILED_CLOSED, result.status());
        assertTrue(result.abnormal());
    }

    @Test
    void uncheckedAndNullStrictDecoderResultsFailClosed() throws Exception {
        Path uncheckedTarget = temporaryDirectory.resolve("unchecked.yml");
        Files.writeString(uncheckedTarget, "valid:primary");
        AtomicYamlStore.SnapshotCodec<String> unchecked = new AtomicYamlStore.SnapshotCodec<>() {
            @Override
            public byte[] encode(String snapshot) throws IOException {
                return CODEC.encode(snapshot);
            }

            @Override
            public String decodeStrict(byte[] encoded) {
                throw new IllegalStateException("parser defect");
            }
        };
        assertEquals(AtomicYamlStore.LoadStatus.FAILED_CLOSED,
                new AtomicYamlStore<>(uncheckedTarget, unchecked, Runnable::run).load().status());

        Path nullTarget = temporaryDirectory.resolve("null.yml");
        Files.writeString(nullTarget, "valid:primary");
        AtomicYamlStore.SnapshotCodec<String> nullDecoder = new AtomicYamlStore.SnapshotCodec<>() {
            @Override
            public byte[] encode(String snapshot) throws IOException {
                return CODEC.encode(snapshot);
            }

            @Override
            public String decodeStrict(byte[] encoded) {
                return null;
            }
        };
        assertEquals(AtomicYamlStore.LoadStatus.FAILED_CLOSED,
                new AtomicYamlStore<>(nullTarget, nullDecoder, Runnable::run).load().status());
    }

    @Test
    void destructiveFallbackFailureRestoresForcedPriorTargetBytes() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        Files.writeString(target, "valid:old");
        AtomicYamlStore.FileOperations destructiveFallback = destructiveTargetMove(target);
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run,
                destructiveFallback, ignored -> { }, ignored -> { });

        assertTrue(store.submit(1, "valid:new"));

        assertEquals("valid:old", Files.readString(target));
        assertEquals("valid:old", Files.readString(store.backupPath()));
        assertEquals(1, store.pendingRevision());
    }

    @Test
    void destructiveFallbackFailureRestoresPriorAbsence() {
        Path target = temporaryDirectory.resolve("absent.yml");
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run,
                destructiveTargetMove(target), ignored -> { }, ignored -> { });

        assertTrue(store.submit(1, "valid:new"));

        assertFalse(Files.exists(target));
        assertEquals(1, store.pendingRevision());
    }

    @Test
    void restoreMoveThatDamagesThenThrowsIsAcceptedOnlyAfterByteVerification() throws Exception {
        Path target = temporaryDirectory.resolve("restore-verified.yml");
        Files.writeString(target, "valid:old");
        AtomicInteger targetMoves = new AtomicInteger();
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicYamlStore.FileOperations movedThenThrew = new DelegatingFileOperations() {
            @Override
            public void move(Path source, Path destination, java.nio.file.CopyOption... options) throws IOException {
                if (destination.equals(target)) {
                    int attempt = targetMoves.incrementAndGet();
                    if (attempt == 1) {
                        throw new AtomicMoveNotSupportedException(source.toString(), destination.toString(), "test fallback");
                    }
                    Files.move(source, destination, options);
                    if (attempt == 2) {
                        throw new IOException("fallback damaged destination");
                    }
                    if (attempt == 3) {
                        throw new IOException("restore move reported failure after moving");
                    }
                } else {
                    super.move(source, destination, options);
                }
            }
        };
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run,
                movedThenThrew, ignored -> { }, failure::set);

        assertTrue(store.submit(1, "valid:new"));

        assertEquals("valid:old", Files.readString(target));
        assertNotNull(failure.get());
        assertEquals("fallback damaged destination", failure.get().getMessage());
        assertEquals(1, store.pendingRevision());
    }

    @Test
    void unrecoverableRestorationReportsSpecificFailureAndNeverSignalsSuccess() throws Exception {
        Path target = temporaryDirectory.resolve("restore-impossible.yml");
        Files.writeString(target, "valid:old");
        AtomicInteger targetMoves = new AtomicInteger();
        AtomicInteger successes = new AtomicInteger();
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicYamlStore.FileOperations impossibleRestore = new DelegatingFileOperations() {
            @Override
            public void writeAndForce(Path path, byte[] content) throws IOException {
                if (path.equals(target)) {
                    throw new IOException("direct restoration refused");
                }
                super.writeAndForce(path, content);
            }

            @Override
            public void move(Path source, Path destination, java.nio.file.CopyOption... options) throws IOException {
                if (destination.equals(target)) {
                    int attempt = targetMoves.incrementAndGet();
                    if (attempt == 1) {
                        throw new AtomicMoveNotSupportedException(source.toString(), destination.toString(), "test fallback");
                    }
                    if (attempt == 2) {
                        Files.move(source, destination, options);
                        throw new IOException("fallback damaged destination");
                    }
                    throw new IOException("restore move refused");
                }
                super.move(source, destination, options);
            }
        };
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run,
                impossibleRestore, ignored -> successes.incrementAndGet(), failure::set);

        assertTrue(store.submit(1, "valid:new"));

        assertEquals(0, successes.get());
        assertEquals(AtomicYamlStore.State.RETRYABLE, store.state());
        assertEquals(1, store.pendingRevision());
        assertNotNull(failure.get());
        assertTrue(failure.get().getMessage().contains("failed to restore prior target"));
        assertEquals(1, failure.get().getSuppressed().length);
        assertEquals("fallback damaged destination", failure.get().getSuppressed()[0].getMessage());
        assertEquals("valid:old", Files.readString(store.backupPath()));
    }

    @Test
    void equalRevisionCannotReplaceRetainedContentAndStaleRevisionIsRejected() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        QueuedExecutor executor = new QueuedExecutor();
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, executor);

        assertTrue(store.submit(10, "valid:first"));
        assertTrue(store.submit(10, "valid:replacement-attempt"));
        assertFalse(store.submit(9, "valid:stale"));
        executor.runAll();

        assertEquals("valid:first", Files.readString(target));
        assertEquals(10, store.persistedRevision());
    }

    @Test
    void nullSnapshotCannotAdvanceAcceptedRevision() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run);

        assertThrows(NullPointerException.class, () -> store.submit(2, null));
        assertTrue(store.submit(1, "valid:one"));

        assertEquals("valid:one", Files.readString(target));
        assertEquals(1, store.persistedRevision());
    }

    @Test
    void successCallbackErrorCannotWedgeLaterFlush() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, Runnable::run,
                new DelegatingFileOperations(), ignored -> {
                    throw new AssertionError("observer failed");
                }, ignored -> { });

        assertThrows(AssertionError.class, () -> store.submit(1, "valid:one"));

        assertTrue(store.flush(Duration.ofMillis(100)).success());
        assertEquals("valid:one", Files.readString(target));
    }

    @Test
    void flushOwnedSuccessCallbackErrorIsReportedWithoutWedgingCurrentOrLaterFlush() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        AtomicReference<Exception> failure = new AtomicReference<>();
        Executor rejectingExecutor = runnable -> {
            throw new java.util.concurrent.RejectedExecutionException("retain for flush");
        };
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, rejectingExecutor,
                new DelegatingFileOperations(), ignored -> {
                    throw new AssertionError("observer failed during flush");
                }, failure::set);
        assertFalse(store.submit(1, "valid:one"));

        AtomicYamlStore.FlushResult first = store.flush(Duration.ofSeconds(1));
        AtomicYamlStore.FlushResult second = store.flush(Duration.ofSeconds(1));

        assertTrue(first.success());
        assertTrue(second.success());
        assertEquals(AtomicYamlStore.State.IDLE, store.state());
        assertEquals("valid:one", Files.readString(target));
        assertNotNull(failure.get());
        assertTrue(failure.get().getCause() instanceof AssertionError);
    }

    @Test
    void flushOwnedSuccessAndFailureCallbackErrorsCannotWedgeFlush() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        QueuedExecutor executor = new QueuedExecutor();
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, executor,
                new DelegatingFileOperations(), ignored -> {
                    throw new AssertionError("success observer failed");
                }, ignored -> {
                    throw new AssertionError("failure observer failed");
                });
        assertTrue(store.submit(1, "valid:one"));

        AtomicYamlStore.FlushResult first = store.flush(Duration.ofSeconds(1));
        AtomicYamlStore.FlushResult second = store.flush(Duration.ofSeconds(1));

        assertTrue(first.success());
        assertTrue(second.success());
        assertEquals(AtomicYamlStore.State.IDLE, store.state());
        assertEquals("valid:one", Files.readString(target));
    }

    @Test
    void flushWriteFailureAndFailureCallbackErrorRemainRetryableWithoutWedge() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        QueuedExecutor executor = new QueuedExecutor();
        AtomicBoolean failWrite = new AtomicBoolean(true);
        AtomicYamlStore.FileOperations firstWriteFails = new DelegatingFileOperations() {
            @Override
            public void writeAndForce(Path path, byte[] content) throws IOException {
                if (failWrite.get()) {
                    throw new IOException("write failed");
                }
                super.writeAndForce(path, content);
            }
        };
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, executor,
                firstWriteFails, ignored -> { }, ignored -> {
                    throw new AssertionError("failure observer failed");
                });
        assertTrue(store.submit(1, "valid:one"));

        AtomicYamlStore.FlushResult failed = store.flush(Duration.ofSeconds(1));
        assertFalse(failed.success());
        assertEquals(AtomicYamlStore.State.RETRYABLE, store.state());
        assertEquals(1, store.pendingRevision());

        failWrite.set(false);
        assertTrue(store.flush(Duration.ofSeconds(1)).success());
        assertEquals("valid:one", Files.readString(target));
    }

    @Test
    void successCallbackErrorCannotLeaveAlreadyPendingRevisionScheduledWithoutExecutorTask() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        QueuedExecutor executor = new QueuedExecutor();
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, executor,
                new DelegatingFileOperations(), revision -> {
                    if (revision == 1) {
                        throw new AssertionError("observer failed");
                    }
                }, ignored -> { });
        assertTrue(store.submit(1, "valid:one"));
        assertTrue(store.submit(2, "valid:two"));

        assertThrows(AssertionError.class, executor::runNext);
        executor.runAll();

        assertEquals("valid:two", Files.readString(target));
        assertEquals(2, store.persistedRevision());
        assertEquals(AtomicYamlStore.State.IDLE, store.state());
    }

    @Test
    void successCallbackRunsOutsideStoreLockSoSecondThreadCanSubmit() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        QueuedExecutor executor = new QueuedExecutor();
        AtomicBoolean secondThreadFinished = new AtomicBoolean();
        AtomicReference<AtomicYamlStore<String>> holder = new AtomicReference<>();
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, CODEC, executor,
                new DelegatingFileOperations(), revision -> {
                    if (revision != 1) {
                        return;
                    }
                    Thread submitter = new Thread(() -> {
                        holder.get().submit(revision + 1, "valid:second");
                        secondThreadFinished.set(true);
                    });
                    submitter.start();
                    try {
                        submitter.join(500);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    assertTrue(secondThreadFinished.get(), "callback held the store lock against another thread");
                }, ignored -> { });
        holder.set(store);

        assertTrue(store.submit(1, "valid:first"));
        executor.runNext();
        executor.runAll();

        assertEquals("valid:second", Files.readString(target));
        assertEquals(2, store.persistedRevision());
    }

    @Test
    void activeWriteFailureRetainsNewerPendingRevisionForExplicitRetry() throws Exception {
        Path target = temporaryDirectory.resolve("state.yml");
        QueuedExecutor executor = new QueuedExecutor();
        CountDownLatch firstEncoding = new CountDownLatch(1);
        CountDownLatch releaseFailure = new CountDownLatch(1);
        AtomicYamlStore.SnapshotCodec<String> firstWriteFails = new AtomicYamlStore.SnapshotCodec<>() {
            @Override
            public byte[] encode(String snapshot) throws IOException {
                if (snapshot.equals("valid:one")) {
                    firstEncoding.countDown();
                    try {
                        if (!releaseFailure.await(1, TimeUnit.SECONDS)) {
                            throw new IOException("test write timed out");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException(exception);
                    }
                    throw new IOException("first write failed");
                }
                return CODEC.encode(snapshot);
            }

            @Override
            public String decodeStrict(byte[] bytes) throws IOException {
                return CODEC.decodeStrict(bytes);
            }
        };
        AtomicYamlStore<String> store = new AtomicYamlStore<>(target, firstWriteFails, executor);
        assertTrue(store.submit(1, "valid:one"));
        Thread worker = new Thread(executor::runNext);
        worker.start();
        assertTrue(firstEncoding.await(1, TimeUnit.SECONDS));
        assertTrue(store.submit(2, "valid:two"));
        releaseFailure.countDown();
        worker.join(1_000);

        assertEquals(2, store.pendingRevision());
        assertEquals(AtomicYamlStore.State.RETRYABLE, store.state());
        assertTrue(store.submit(2, "valid:replacement-must-not-win"));
        executor.runAll();
        assertEquals("valid:two", Files.readString(target));
    }

    private static class DelegatingFileOperations implements AtomicYamlStore.FileOperations {
        @Override
        public Path createTempFile(Path directory, String prefix, String suffix) throws IOException {
            return Files.createTempFile(directory, prefix, suffix);
        }

        @Override
        public void createDirectories(Path directory) throws IOException {
            Files.createDirectories(directory);
        }

        @Override
        public void writeAndForce(Path path, byte[] content) throws IOException {
            try (var channel = java.nio.channels.FileChannel.open(path,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(java.nio.ByteBuffer.wrap(content));
                channel.force(true);
            }
        }

        @Override
        public byte[] readAllBytes(Path path) throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public AtomicYamlStore.Presence presence(Path path) throws IOException {
            return Files.exists(path) ? AtomicYamlStore.Presence.PRESENT : AtomicYamlStore.Presence.ABSENT;
        }

        @Override
        public void move(Path source, Path destination, java.nio.file.CopyOption... options) throws IOException {
            Files.move(source, destination, options);
        }

        @Override
        public void deleteIfExists(Path path) throws IOException {
            Files.deleteIfExists(path);
        }
    }

    private static AtomicYamlStore.FileOperations destructiveTargetMove(Path target) {
        return new DelegatingFileOperations() {
            private final AtomicInteger targetMoves = new AtomicInteger();

            @Override
            public void move(Path source, Path destination, java.nio.file.CopyOption... options) throws IOException {
                if (destination.equals(target)) {
                    int attempt = targetMoves.incrementAndGet();
                    if (attempt == 1) {
                        throw new AtomicMoveNotSupportedException(source.toString(), destination.toString(), "test fallback");
                    }
                    if (attempt == 2) {
                        Files.move(source, destination, options);
                        throw new IOException("fallback damaged destination");
                    }
                }
                super.move(source, destination, options);
            }
        };
    }

    private static final class QueuedExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        void runNext() {
            Runnable task = tasks.removeFirst();
            task.run();
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                runNext();
            }
        }
    }
}
