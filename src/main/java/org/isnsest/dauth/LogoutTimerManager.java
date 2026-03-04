package org.isnsest.dauth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class LogoutTimerManager {

    private static final Map<UUID, String> sessionMap = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final ConcurrentHashMap<UUID, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<UUID, Long> expiryTimes = new ConcurrentHashMap<>();

    public static void updateSession(UUID playerId, String ip) {
        sessionMap.put(playerId, ip);
    }

    public static boolean checkSession(UUID playerId, String ip) {
        String storedIp = sessionMap.get(playerId);
        return storedIp != null && storedIp.equals(ip);
    }

    public static void removeSession(UUID playerId) {
        sessionMap.remove(playerId);
        expiryTimes.remove(playerId);
    }

    public static void startTimer(UUID playerId, Runnable onExpire, long delaySeconds) {
        cancelTimer(playerId);

        expiryTimes.put(playerId, System.currentTimeMillis() + (delaySeconds * 1000));

        ScheduledFuture<?> scheduled = scheduler.schedule(() -> {
            ScheduledFuture<?> removed = timers.remove(playerId);
            expiryTimes.remove(playerId);
            if (removed != null) {
                onExpire.run();
            }
        }, delaySeconds, TimeUnit.SECONDS);

        timers.put(playerId, scheduled);
    }

    public static void cancelTimer(UUID playerId) {
        expiryTimes.remove(playerId);
        ScheduledFuture<?> scheduled = timers.remove(playerId);
        if (scheduled != null) {
            scheduled.cancel(false);
        }
    }

    public static void saveSessionsToDb(Database db, int defaultDelay) {
        Map<UUID, String> toSave = new HashMap<>();
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            String ip = sessionMap.get(player.getUniqueId());
            if (ip != null) {
                toSave.put(player.getUniqueId(), ip + ":" + defaultDelay);
            }
        }

        for (UUID uuid : timers.keySet()) {
            if (toSave.containsKey(uuid)) continue;

            String ip = sessionMap.get(uuid);
            Long expireAt = expiryTimes.get(uuid);

            if (ip != null && expireAt != null) {
                long remaining = (expireAt - now) / 1000;
                if (remaining > 0) {
                    toSave.put(uuid, ip + ":" + remaining);
                }
            }
        }

        db.saveBatchTimers(toSave);
    }

    public static void loadSessionsFromDb(Database db, java.util.function.Consumer<UUID> expireAction) {
        Map<UUID, String> data = db.loadAllTimers();

        data.forEach((uuid, value) -> {
            try {
                String[] parts = value.split(":");
                if (parts.length < 2) return;

                String ip = parts[0];
                long remainingSeconds = Long.parseLong(parts[1]);

                updateSession(uuid, ip);
                startTimer(uuid, () -> expireAction.accept(uuid), remainingSeconds);
            } catch (Exception ignored) {}
        });

        db.clearAllTimers();
    }
}