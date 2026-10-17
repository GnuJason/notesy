package org.stickynotefx;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;

public final class ApplicationInstance implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private WatchService watcher;
    private volatile boolean closed;

    private ApplicationInstance(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static ApplicationInstance open(Path directory, Runnable activate) throws IOException {
        Files.createDirectories(directory);
        FileChannel channel = FileChannel.open(directory.resolve("application.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException alreadyOwned) {
            lock = null;
        } catch (IOException failure) {
            channel.close();
            throw failure;
        }
        var instance = new ApplicationInstance(channel, lock);
        try {
            Path request = directory.resolve("activation.request");
            if (lock == null) {
                Files.writeString(request, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                instance.watcher = directory.getFileSystem().newWatchService();
                directory.register(instance.watcher, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
                Thread listener = new Thread(() -> instance.listen(request, activate), "notesy-activation");
                listener.setDaemon(true);
                listener.start();
            }
            return instance;
        } catch (IOException | RuntimeException failure) {
            instance.close();
            throw failure;
        }
    }

    private void listen(Path request, Runnable activate) {
        try {
            while (!closed) {
                if (Files.deleteIfExists(request)) activate.run();
                var events = watcher.take();
                events.pollEvents();
                if (!events.reset()) return;
            }
        } catch (ClosedWatchServiceException shutdown) {
            return;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException failure) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                    "Could not receive Notesy activation requests", failure);
        }
    }

    public boolean isPrimary() { return lock != null; }

    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            if (watcher != null) watcher.close();
        } finally {
            try {
                if (lock != null) lock.release();
            } finally {
                channel.close();
            }
        }
    }
}