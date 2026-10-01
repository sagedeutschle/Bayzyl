package com.bayzyl.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * A small, snapshot-only persistence primitive. Callers must detach server state before submitting it.
 * Encoding and all file I/O run outside this store's monitor.
 */
public final class AtomicYamlStore<T> {
    public enum State {
        IDLE,
        RETRYABLE,
        SCHEDULED,
        WRITING,
        FLUSHING
    }

    public enum LoadStatus {
        PRIMARY,
        BACKUP_RECOVERED,
        MISSING,
        FAILED_CLOSED
    }

    enum Presence { PRESENT, ABSENT }

    public record FlushResult(boolean success, long persistedRevision, State state) {
    }

    public record LoadResult<T>(LoadStatus status, T snapshot, boolean abnormal) {
    }

    /** Encodes detached snapshots and rejects malformed persisted input without permissive defaults. */
    public interface SnapshotCodec<T> {
        byte[] encode(T snapshot) throws IOException;

        T decodeStrict(byte[] encoded) throws IOException;
    }

    /** Package-visible seam for deterministic storage failure tests. */
    interface FileOperations {
        Path createTempFile(Path directory, String prefix, String suffix) throws IOException;

        void createDirectories(Path directory) throws IOException;

        void writeAndForce(Path path, byte[] content) throws IOException;

        byte[] readAllBytes(Path path) throws IOException;

        Presence presence(Path path) throws IOException;

        void move(Path source, Path destination, CopyOption... options) throws IOException;

        void deleteIfExists(Path path) throws IOException;
    }

    private final Object lock = new Object();
    private final Path target;
    private final Path backup;
    private final SnapshotCodec<T> codec;
    private final Executor executor;
    private final FileOperations files;
    private final Consumer<Long> successCallback;
    private final Consumer<Exception> failureCallback;

    private Entry<T> active;
    private Entry<T> pending;
    private long latestRevision = -1;
    private long persistedRevision = -1;
    private State state = State.IDLE;
    private boolean flushing;
    private FlushResult lastFlushResult;
    private int callbacksInFlight;

    public AtomicYamlStore(Path target, SnapshotCodec<T> codec, Executor executor) {
        this(target, codec, executor, new SystemFileOperations(), ignored -> { }, ignored -> { });
    }

    public AtomicYamlStore(Path target, SnapshotCodec<T> codec, Executor executor,
                           Consumer<Long> successCallback, Consumer<Exception> failureCallback) {
        this(target, codec, executor, new SystemFileOperations(), successCallback, failureCallback);
    }

    AtomicYamlStore(Path target, SnapshotCodec<T> codec, Executor executor, FileOperations files,
                    Consumer<Long> successCallback, Consumer<Exception> failureCallback) {
        this.target = Objects.requireNonNull(target, "target").toAbsolutePath();
        this.backup = this.target.resolveSibling(this.target.getFileName() + ".bak");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.files = Objects.requireNonNull(files, "files");
        this.successCallback = Objects.requireNonNull(successCallback, "successCallback");
        this.failureCallback = Objects.requireNonNull(failureCallback, "failureCallback");
    }

    /**
     * Stores only a strictly newer snapshot. Repeating a retained revision schedules a retry but cannot replace it.
     */
    public boolean submit(long revision, T snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Entry<T> scheduled;
        synchronized (lock) {
            if (revision < latestRevision) {
                return false;
            }
            if (revision == latestRevision) {
                if (!isRetainedRevision(revision)) {
                    return false;
                }
            } else {
                latestRevision = revision;
                pending = new Entry<>(revision, snapshot);
            }
            scheduled = reserveWorkerIfPossible();
        }
        return scheduled == null || executeReserved(scheduled);
    }

    /** Drains the retained newest snapshot once; persistent failures remain retryable instead of spinning. */
    public FlushResult flush() {
        return flush(Duration.ofSeconds(10));
    }

    public FlushResult flush(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        long deadline = System.nanoTime() + Math.max(0, timeout.toNanos());
        synchronized (lock) {
            boolean waitedForOwner = false;
            while (flushing) {
                waitedForOwner = true;
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return new FlushResult(false, persistedRevision, state);
                }
                try {
                    lock.wait(Math.max(1, remaining / 1_000_000L));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return new FlushResult(false, persistedRevision, state);
                }
            }
            if (waitedForOwner) {
                return lastFlushResult == null
                        ? new FlushResult(false, persistedRevision, state)
                        : lastFlushResult;
            }
            State priorState = state;
            flushing = true;
            state = State.FLUSHING;
            lastFlushResult = null;
            if (active != null && priorState == State.SCHEDULED) {
                retain(active);
                active = null;
            }
            while (active != null || callbacksInFlight != 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return finishFlush(false);
                }
                try {
                    long millis = Math.max(1, remaining / 1_000_000L);
                    lock.wait(millis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return finishFlush(false);
                }
            }
        }

        while (true) {
            Entry<T> next;
            synchronized (lock) {
                if (pending == null) {
                    return finishFlush(true);
                }
                if (System.nanoTime() >= deadline) {
                    return finishFlush(false);
                }
                active = pending;
                pending = null;
                next = active;
            }
            try {
                write(next);
                completeSuccess(next);
            } catch (Exception exception) {
                completeFailure(next, exception);
                return finishFlush(false);
            }
        }
    }

    /** Strictly loads primary first, then a valid backup; corrupt inputs never become a permissive empty state. */
    public LoadResult<T> load() {
        Presence primaryPresence;
        try {
            primaryPresence = files.presence(target);
        } catch (IOException exception) {
            return new LoadResult<>(LoadStatus.FAILED_CLOSED, null, true);
        }
        if (primaryPresence == Presence.PRESENT) {
            byte[] primary;
            try {
                primary = files.readAllBytes(target);
            } catch (IOException primaryFailure) {
                return new LoadResult<>(LoadStatus.FAILED_CLOSED, null, true);
            }
            try {
                return new LoadResult<>(LoadStatus.PRIMARY, decodeStrict(primary), false);
            } catch (IOException corruptPrimary) {
                LoadResult<T> backupResult = loadBackup();
                if (backupResult.status() != LoadStatus.BACKUP_RECOVERED) {
                    return new LoadResult<>(LoadStatus.FAILED_CLOSED, null, true);
                }
                return quarantineCorruptPrimary(primary)
                        ? backupResult
                        : new LoadResult<>(LoadStatus.FAILED_CLOSED, null, true);
            }
        }
        try {
            if (files.presence(backup) == Presence.PRESENT) {
                LoadResult<T> backupResult = loadBackup();
                return backupResult.status() == LoadStatus.BACKUP_RECOVERED
                        ? backupResult
                        : new LoadResult<>(LoadStatus.FAILED_CLOSED, null, true);
            }
        } catch (IOException exception) {
            return new LoadResult<>(LoadStatus.FAILED_CLOSED, null, true);
        }
        return new LoadResult<>(LoadStatus.MISSING, null, false);
    }

    public Path backupPath() {
        return backup;
    }

    public long persistedRevision() {
        synchronized (lock) {
            return persistedRevision;
        }
    }

    public long pendingRevision() {
        synchronized (lock) {
            return pending == null ? -1 : pending.revision;
        }
    }

    public State state() {
        synchronized (lock) {
            return state;
        }
    }

    private boolean isRetainedRevision(long revision) {
        return (active != null && active.revision == revision) || (pending != null && pending.revision == revision);
    }

    private Entry<T> reserveWorkerIfPossible() {
        if (flushing || active != null || pending == null || (state != State.IDLE && state != State.RETRYABLE)) {
            return null;
        }
        active = pending;
        pending = null;
        active = new Entry<>(active.revision, active.snapshot);
        state = State.SCHEDULED;
        return active;
    }

    private boolean executeReserved(Entry<T> reserved) {
        try {
            executor.execute(() -> writeActive(reserved));
            return true;
        } catch (RuntimeException exception) {
            rollbackReservation(reserved);
            notifyFailure(exception);
            return false;
        } catch (Error error) {
            rollbackReservation(reserved);
            throw error;
        }
    }

    private void rollbackReservation(Entry<T> reserved) {
        synchronized (lock) {
            if (active == reserved) {
                retain(active);
                active = null;
                state = State.RETRYABLE;
                lock.notifyAll();
            }
        }
    }

    private void writeActive(Entry<T> reserved) {
        synchronized (lock) {
            if (active != reserved || flushing) {
                return;
            }
            if (!flushing) {
                state = State.WRITING;
            }
        }
        try {
            write(reserved);
            completeSuccess(reserved);
        } catch (Exception exception) {
            completeFailure(reserved, exception);
        }
    }

    private void write(Entry<T> entry) throws IOException {
        byte[] encoded = encode(entry.snapshot);
        decodeStrict(encoded);
        writeReplacement(encoded);
    }

    private void writeReplacement(byte[] content) throws IOException {
        Path directory = target.getParent();
        if (directory == null) {
            throw new IOException("target must have a parent directory");
        }
        files.createDirectories(directory);
        Path temporary = files.createTempFile(directory, target.getFileName() + ".tmp-", ".yml");
        boolean moved = false;
        byte[] priorTarget = null;
        boolean priorPresent = false;
        try {
            files.writeAndForce(temporary, content);
            if (files.presence(target) == Presence.PRESENT) {
                priorPresent = true;
                byte[] formerTarget = files.readAllBytes(target);
                priorTarget = formerTarget;
                try {
                    decodeStrict(formerTarget);
                } catch (IOException corruptTarget) {
                    if (!quarantineCorruptPrimary(formerTarget)) {
                        throw new IOException("could not preserve corrupt primary", corruptTarget);
                    }
                    formerTarget = null;
                }
                if (formerTarget != null) {
                    replaceBackup(directory, formerTarget);
                }
            }
            try {
                moveReplacing(temporary, target);
            } catch (IOException replacementFailure) {
                try {
                    restorePriorTarget(directory, priorTarget, priorPresent);
                } catch (IOException restorationFailure) {
                    restorationFailure.addSuppressed(replacementFailure);
                    throw restorationFailure;
                }
                throw replacementFailure;
            }
            moved = true;
        } finally {
            if (!moved) {
                deleteBestEffort(temporary);
            }
        }
    }

    private void replaceBackup(Path directory, byte[] formerTarget) throws IOException {
        Path backupTemporary = files.createTempFile(directory, backup.getFileName() + ".tmp-", ".yml");
        boolean moved = false;
        try {
            files.writeAndForce(backupTemporary, formerTarget);
            moveReplacing(backupTemporary, backup);
            moved = true;
        } finally {
            if (!moved) {
                deleteBestEffort(backupTemporary);
            }
        }
    }

    private void moveReplacing(Path source, Path destination) throws IOException {
        try {
            files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void restorePriorTarget(Path directory, byte[] priorTarget, boolean priorPresent) throws IOException {
        if (!priorPresent) {
            try {
                files.deleteIfExists(target);
                if (files.presence(target) != Presence.ABSENT) {
                    throw new IOException("target remains present");
                }
            } catch (IOException exception) {
                if (isAbsent()) {
                    return;
                }
                throw new IOException("failed to restore prior target absence", exception);
            }
            return;
        }
        if (priorTarget == null) {
            throw new IOException("failed to restore prior target: missing prior bytes");
        }
        Path restore = null;
        try {
            restore = files.createTempFile(directory, target.getFileName() + ".restore-", ".yml");
            files.writeAndForce(restore, priorTarget);
            moveReplacing(restore, target);
            restore = null;
        } catch (IOException restoreMoveFailure) {
            if (targetMatches(priorTarget)) {
                return;
            }
            try {
                files.writeAndForce(target, priorTarget);
            } catch (IOException directWriteFailure) {
                if (targetMatches(priorTarget)) {
                    return;
                }
                throw new IOException("failed to restore prior target", directWriteFailure);
            }
            if (!targetMatches(priorTarget)) {
                throw new IOException("failed to restore prior target", restoreMoveFailure);
            }
        } finally {
            if (restore != null) {
                deleteBestEffort(restore);
            }
        }
    }

    private boolean targetMatches(byte[] expected) {
        try {
            return files.presence(target) == Presence.PRESENT
                    && Arrays.equals(expected, files.readAllBytes(target));
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean isAbsent() {
        try {
            return files.presence(target) == Presence.ABSENT;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void completeSuccess(Entry<T> completed) {
        Entry<T> scheduled;
        boolean flushOwned;
        synchronized (lock) {
            if (active != completed) {
                return;
            }
            active = null;
            persistedRevision = Math.max(persistedRevision, completed.revision);
            if (!flushing) {
                state = State.IDLE;
            }
            scheduled = flushing ? null : reserveWorkerIfPossible();
            flushOwned = flushing;
            callbacksInFlight++;
        }
        Error callbackError = null;
        try {
            notifySuccess(completed.revision);
        } catch (Error error) {
            callbackError = error;
        } finally {
            synchronized (lock) {
                callbacksInFlight--;
                lock.notifyAll();
            }
        }
        if (scheduled != null) {
            try {
                executeReserved(scheduled);
            } catch (Error schedulingError) {
                if (callbackError == null) {
                    throw schedulingError;
                }
                callbackError.addSuppressed(schedulingError);
            }
        }
        if (callbackError != null) {
            if (flushOwned) {
                notifyFailure(new IOException("success callback failed during flush", callbackError));
            } else {
                throw callbackError;
            }
        }
    }

    private void completeFailure(Entry<T> failed, Exception exception) {
        synchronized (lock) {
            if (active == failed) {
                retain(active);
                active = null;
                if (!flushing) {
                    state = State.RETRYABLE;
                }
                lock.notifyAll();
            }
        }
        notifyFailure(exception);
    }

    private void retain(Entry<T> entry) {
        if (entry != null && (pending == null || entry.revision > pending.revision)) {
            pending = entry;
        }
    }

    private FlushResult finishFlush(boolean success) {
        synchronized (lock) {
            flushing = false;
            state = success ? State.IDLE : State.RETRYABLE;
            lock.notifyAll();
            lastFlushResult = new FlushResult(success, persistedRevision, state);
            return lastFlushResult;
        }
    }

    private T decode(Path path) throws IOException {
        return decodeStrict(files.readAllBytes(path));
    }

    private LoadResult<T> loadBackup() {
        try {
            return new LoadResult<>(LoadStatus.BACKUP_RECOVERED, decode(backup), true);
        } catch (IOException backupFailure) {
            return new LoadResult<>(LoadStatus.FAILED_CLOSED, null, true);
        }
    }

    private byte[] encode(T snapshot) throws IOException {
        try {
            return codec.encode(snapshot);
        } catch (RuntimeException exception) {
            throw new IOException("snapshot encoder failed", exception);
        }
    }

    private T decodeStrict(byte[] encoded) throws IOException {
        try {
            T decoded = codec.decodeStrict(encoded);
            if (decoded == null) {
                throw new IOException("strict snapshot validator returned null");
            }
            return decoded;
        } catch (RuntimeException exception) {
            throw new IOException("strict snapshot validator failed", exception);
        }
    }

    private boolean quarantineCorruptPrimary(byte[] corruptPrimary) {
        Path quarantine = null;
        try {
            Path directory = target.getParent();
            if (directory == null) {
                return false;
            }
            quarantine = files.createTempFile(directory, target.getFileName() + ".corrupt-", ".yml");
            files.writeAndForce(quarantine, corruptPrimary);
            return true;
        } catch (IOException ignored) {
            if (quarantine != null) {
                deleteBestEffort(quarantine);
            }
            return false;
        }
    }

    private void notifySuccess(long revision) {
        try {
            successCallback.accept(revision);
        } catch (RuntimeException ignored) {
            // Persistence already completed; observer failure must not alter retained state.
        }
    }

    private void notifyFailure(Exception exception) {
        try {
            failureCallback.accept(exception);
        } catch (RuntimeException | Error ignored) {
            // Failure observers are diagnostic only.
        }
    }

    private void deleteBestEffort(Path path) {
        try {
            files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort temp cleanup must not mask the write failure.
        }
    }

    private record Entry<T>(long revision, T snapshot) {
    }

    private static final class SystemFileOperations implements FileOperations {
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
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
        }

        @Override
        public byte[] readAllBytes(Path path) throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public Presence presence(Path path) throws IOException {
            if (Files.exists(path)) {
                return Presence.PRESENT;
            }
            if (Files.notExists(path)) {
                return Presence.ABSENT;
            }
            throw new IOException("could not establish file presence");
        }

        @Override
        public void move(Path source, Path destination, CopyOption... options) throws IOException {
            Files.move(source, destination, options);
        }

        @Override
        public void deleteIfExists(Path path) throws IOException {
            Files.deleteIfExists(path);
        }
    }
}
