package com.dimchig.bedwarsbro;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;

public final class ClientScheduler {
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2, new ThreadFactory() {
        private int threadNumber;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "BedwarsBro-Worker-" + (++threadNumber));
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread ignored, Throwable throwable) {
                    throwable.printStackTrace();
                }
            });
            return thread;
        }
    });

    private ClientScheduler() {
    }

    public static void schedule(Runnable task, long delayMillis) {
        if (task == null) return;
        EXECUTOR.schedule(new Runnable() {
            @Override
            public void run() {
                runOnClientThread(task);
            }
        }, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
    }

    public static void runAsync(Runnable task) {
        if (task != null) EXECUTOR.execute(task);
    }

    public static void runOnClientThread(Runnable task) {
        if (task == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) return;
        if (minecraft.isCallingFromMinecraftThread()) {
            task.run();
        } else {
            minecraft.addScheduledTask(task);
        }
    }
}
