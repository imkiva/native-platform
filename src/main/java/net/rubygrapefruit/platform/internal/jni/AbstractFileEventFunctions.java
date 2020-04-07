package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.file.FileWatcher;
import net.rubygrapefruit.platform.file.FileWatcherCallback;

import java.io.File;
import java.util.Collection;

public abstract class AbstractFileEventFunctions implements NativeIntegration {
    public static String getVersion() {
        return getVersion0();
    }

    private static native String getVersion0();

    /**
     * Forces the native backend to drop the cached JUL log level and thus
     * re-query it the next time it tries to log something to the Java side.
     */
    public void invalidateLogLevelCache() {
        invalidateLogLevelCache0();
    }

    private native void invalidateLogLevelCache0();

    public abstract static class AbstractWatcherBuilder {
        protected final FileWatcherCallback callback;

        public AbstractWatcherBuilder(FileWatcherCallback callback) {
            this.callback = callback;
        }

        public abstract FileWatcher start();
    }

    /**
     * Configures a new watcher using a builder. Call {@link AbstractWatcherBuilder#start()} to
     * actually start the {@link FileWatcher}.
     */
    public abstract AbstractWatcherBuilder newWatcher(FileWatcherCallback callback);

    protected static class NativeFileWatcherCallback {
        private final FileWatcherCallback delegate;

        public NativeFileWatcherCallback(FileWatcherCallback delegate) {
            this.delegate = delegate;
        }

        // Called from the native side
        @SuppressWarnings("unused")
        public void pathChanged(int type, String path) {
            delegate.pathChanged(FileWatcherCallback.Type.values()[type], path);
        }

        // Called from the native side
        @SuppressWarnings("unused")
        public void reportError(Throwable ex) {
            delegate.reportError(ex);
        }
    }

    // Instantiated from native code
    @SuppressWarnings("unused")
    protected static class NativeFileWatcher implements FileWatcher {
        /**
         * A Java object wrapper around the native server object.
         */
        private Object server;

        public NativeFileWatcher(Object server) {
            this.server = server;
        }

        @Override
        public void startWatching(Collection<File> paths) {
            if (server == null) {
                throw new IllegalStateException("Watcher already closed");
            }
            startWatching0(server, toAbsolutePaths(paths));
        }

        private native void startWatching0(Object server, String[] absolutePaths);

        @Override
        public boolean stopWatching(Collection<File> paths) {
            if (server == null) {
                throw new IllegalStateException("Watcher already closed");
            }
            return stopWatching0(server, toAbsolutePaths(paths));
        }

        private native boolean stopWatching0(Object server, String[] absolutePaths);

        private static String[] toAbsolutePaths(Collection<File> files) {
            String[] paths = new String[files.size()];
            int index = 0;
            for (File file : files) {
                paths[index++] = file.getAbsolutePath();
            }
            return paths;
        }

        @Override
        public void close() {
            if (server == null) {
                throw new NativeException("Closed already");
            }
            close0(server);
            server = null;
        }

        protected native void close0(Object details);
    }
}
