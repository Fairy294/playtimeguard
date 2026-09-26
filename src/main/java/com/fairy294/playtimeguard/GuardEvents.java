package com.fairy294.playtimeguard;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class GuardEvents {
    private final PlaytimeTracker tracker = new PlaytimeTracker();
    private final BossBarManager bossBars = new BossBarManager();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> disconnecting = new HashSet<>();
    private long tickCount;

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 世界目录已经确定，先载入旧数据再接受玩家。
        tickCount = 0;
        sessions.clear();
        disconnecting.clear();
        bossBars.clear();
        tracker.load(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            admit(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID id = player.getUUID();
            Session session = sessions.remove(id);
            if (session != null && !disconnecting.contains(id) && !isExempt(player)) {
                // 保存尚未凑满一秒的在线 tick，避免反复上下线丢失时长。
                tracker.addTicks(id, tickCount - session.lastTick);
            }
            disconnecting.remove(id);
            bossBars.remove(player);
            tracker.save();
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCount++;
        if (tickCount % 20 != 0) {
            return;
        }
        // 一秒执行一次时长结算、提醒和 BossBar 更新；实际时长由经过的 tick 数计算。
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (disconnecting.contains(id)) {
                continue;
            }
            if (isExempt(player)) {
                sessions.remove(id);
                bossBars.remove(player);
                continue;
            }
            Session session = sessions.get(id);
            if (session == null) {
                admit(player);
                continue;
            }
            long previousUsed = tracker.usedMillis(id);
            tracker.addTicks(id, tickCount - session.lastTick);
            session.lastTick = tickCount;
            if (GuardConfig.isBlocked(PlaytimeTracker.currentDate().getDayOfWeek())) {
                kick(player, GuardConfig.KICK_BLOCKED_DAY_MESSAGE.get());
                continue;
            }
            long limit = limitMillis();
            long remaining = Math.max(0, limit - tracker.usedMillis(id));
            if (remaining == 0) {
                kick(player, GuardConfig.KICK_TIME_UP_MESSAGE.get());
                continue;
            }
            long warning = GuardConfig.WARN_BEFORE_MINUTES.get() * 60_000L;
            long previousRemaining = Math.max(0, limit - previousUsed);
            if (warning > 0 && previousRemaining > warning && remaining <= warning) {
                player.sendSystemMessage(Component.literal(render(GuardConfig.WARN_MESSAGE.get(), player, remaining)));
            }
            updateBossBar(player, remaining);
        }
        // 每分钟额外落盘一次，服务器意外退出时最多损失少量游玩记录。
        if (tickCount % 1200 == 0) {
            tracker.save();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // 停服时结算所有仍在线的玩家，写入世界目录。
        for (Map.Entry<UUID, Session> item : sessions.entrySet()) {
            if (!disconnecting.contains(item.getKey()) && !isExempt(item.getValue().player)) {
                tracker.addTicks(item.getKey(), tickCount - item.getValue().lastTick);
            }
        }
        tracker.save();
        sessions.clear();
        disconnecting.clear();
        bossBars.clear();
    }

    private void admit(ServerPlayer player) {
        UUID id = player.getUUID();
        if (isExempt(player)) {
            return;
        }
        if (GuardConfig.isBlocked(PlaytimeTracker.currentDate().getDayOfWeek())) {
            kick(player, GuardConfig.KICK_BLOCKED_DAY_MESSAGE.get());
            return;
        }
        long remaining = Math.max(0, limitMillis() - tracker.usedMillis(id));
        if (remaining == 0) {
            kick(player, GuardConfig.KICK_TIME_UP_MESSAGE.get());
            return;
        }
        sessions.put(id, new Session(player, tickCount));
        updateBossBar(player, remaining);
    }

    private void updateBossBar(ServerPlayer player, long remaining) {
        bossBars.update(player, render(GuardConfig.BOSS_BAR_TEXT.get(), player, remaining),
                remaining, limitMillis());
    }

    private void kick(ServerPlayer player, String message) {
        UUID id = player.getUUID();
        disconnecting.add(id);
        bossBars.remove(player);
        tracker.save();
        // 26.1.2.84 的 ServerGamePacketListenerImpl 继承了 disconnect(Component)。
        player.connection.disconnect(Component.literal(render(message, player,
                Math.max(0, limitMillis() - tracker.usedMillis(id)))));
    }

    private static boolean isExempt(ServerPlayer player) {
        return GuardConfig.isExempt(player.getName().getString());
    }

    private static long limitMillis() {
        return GuardConfig.DAILY_LIMIT_MINUTES.get() * 60_000L;
    }

    private String render(String template, ServerPlayer player, long remaining) {
        LocalDate date = PlaytimeTracker.currentDate();
        DayOfWeek day = date.getDayOfWeek();
        return template.replace("%remaining%", DurationFormat.formatDuration(remaining))
                .replace("%used%", DurationFormat.formatDuration(tracker.usedMillis(player.getUUID())))
                .replace("%player%", player.getName().getString())
                .replace("%day%", chineseDay(day))
                .replace("%limit%", DurationFormat.formatDuration(limitMillis()));
    }

    private static String chineseDay(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }

    private static final class Session {
        private final ServerPlayer player;
        private long lastTick;

        private Session(ServerPlayer player, long lastTick) {
            this.player = player;
            this.lastTick = lastTick;
        }
    }
}
