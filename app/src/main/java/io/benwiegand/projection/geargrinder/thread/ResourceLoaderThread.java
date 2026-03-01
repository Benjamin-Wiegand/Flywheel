package io.benwiegand.projection.geargrinder.thread;

import android.os.Handler;
import android.util.Log;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ResourceLoaderThread {
    private static final String TAG = ResourceLoaderThread.class.getSimpleName();
    private static final String THREAD_NAME = "geargrinder-" + TAG;

    private record Request<T>(Supplier<T> getter, Consumer<T> resultCallback, Consumer<Throwable> failureCallback) {
        // needs to be handled here because generics
        void handle(Executor callbackExecutor) {
            T result;
            try {
                result = getter().get();
            } catch (Throwable t) {
                if (failureCallback() == null) {
                    Log.wtf(TAG, "error while loading resource", t);
                    return;
                }

                Log.w(TAG, "error while loading resource: " + t);
                callbackExecutor.execute(() -> failureCallback().accept(t));
                return;
            }

            callbackExecutor.execute(() -> resultCallback().accept(result));
        }
    }

    private final Object lock = new Object();
    private Thread thread;

    private final Queue<Request<?>> requestQueue = new ConcurrentLinkedQueue<>();
    private final Executor callbackExecutor;
    private final long keepaliveTimeout;

    private boolean dead = false;

    /**
     * constructs a new resource loader thread
     * @param callbackExecutor executor to execute callbacks with
     * @param keepaliveTimeout if non-zero and positive, this is the number of milliseconds to stay alive while doing nothing. if 0, will wait indefinitely. if negative, will exit immediately when there is no more work.
     */
    public ResourceLoaderThread(Executor callbackExecutor, long keepaliveTimeout) {
        this.callbackExecutor = callbackExecutor;
        this.keepaliveTimeout = keepaliveTimeout;
    }

    public ResourceLoaderThread(Handler callbackHandler, long keepaliveTimeout) {
        this(callbackHandler::post, keepaliveTimeout);
    }

    private void wake() {
        synchronized (lock) {
            if (thread == null) {
                Log.i(TAG, "spinning up");
                thread = new Thread(this::threadLoop, THREAD_NAME);
                thread.start();
            } else if (!requestQueue.isEmpty()) {
                lock.notify();
            }
        }
    }

    public void destroy() {
        if (dead) return;
        dead = true;
        synchronized (lock) {
            if (thread != null) thread.interrupt();
        }
    }

    public <T> void execute(Supplier<T> getter, Consumer<T> onLoaded, Consumer<Throwable> onFailed) {
        if (dead) throw new IllegalStateException("resource loader thread is dead");
        requestQueue.add(new Request<>(getter, onLoaded, onFailed));
        wake();
    }

    public <T> void execute(Supplier<T> getter, Consumer<T> onLoaded) {
        execute(getter, onLoaded, null);
    }

    private boolean waitForNextLocked() {
        if (!requestQueue.isEmpty()) return true;
        if (keepaliveTimeout < 0) return false;

        try {
            lock.wait(keepaliveTimeout);
        } catch (InterruptedException e) {
            Log.e(TAG, "interrupted");
        }

        return !requestQueue.isEmpty();
    }
    
    private void threadLoop() {
        Log.d(TAG, "resource loader thread start");
        while (!dead) {

            Request<?> request = requestQueue.poll();

            if (request == null) {
                synchronized (lock) {
                    if (waitForNextLocked()) continue;
                    thread = null;
                    Log.d(TAG, "resource loader thread exiting due to no new jobs");
                    return;
                }
            }

            try {
                request.handle(callbackExecutor);
            } catch (Throwable t) {
                Log.wtf(TAG, "exception while handling request", t);
                assert false;
            }
        }
    }

}
