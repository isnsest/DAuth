package org.isnsest.dauth;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

public class LogoutTimerManager {

    public static List<String> ipList = new CopyOnWriteArrayList<>();

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final ConcurrentHashMap<UUID, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    public static void startTimer(UUID playerId, Runnable onExpire, long delaySeconds) {
        cancelTimer(playerId);

        ScheduledFuture<?> scheduled = scheduler.schedule(() -> {
            ScheduledFuture<?> removed = timers.remove(playerId);

            if (removed != null) {
                onExpire.run();
            }
        }, delaySeconds, TimeUnit.SECONDS);

        timers.put(playerId, scheduled);
    }

    public static void cancelTimer(UUID playerId) {
        ScheduledFuture<?> scheduled = timers.remove(playerId);
        if (scheduled != null) {
            scheduled.cancel(false);
        }
    }

}