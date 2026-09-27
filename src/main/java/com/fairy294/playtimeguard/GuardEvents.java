package com.fairy294.playtimeguard;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
            String reason = admit(player);
            if (reason != null) {
                kick(player, reason);
            }
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

        // 关键：必须遍历玩家列表的「快照」。
        // getPlayers() 返回的是服务端活集合的不可修改视图，循环里踢人会把元素从底层列表摘掉，
        // 直接遍历会抛 ConcurrentModificationException，进而让整个 tick 循环崩溃、服务器被看门狗强杀。
        // 这里先复制一份再遍历，并且把所有踢人动作推迟到循环结束后统一执行，
        // 避免在遍历过程中触发登出事件造成重入。
        List<ServerPlayer> online = List.copyOf(event.getServer().getPlayerList().getPlayers());
        List<ServerPlayer> pendingKick = new ArrayList<>();
        List<String> pendingKickReason = new ArrayList<>();

        // 一秒执行一次时长结算、提醒和 BossBar 更新；实际时长由经过的 tick 数计算。
        for (ServerPlayer player : online) {
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
                String reason = admit(player);
                if (reason != null) {
                    pendingKick.add(player);
                    pendingKickReason.add(reason);
                }
                continue;
            }
            long previousUsed = tracker.usedMillis(id);
            tracker.addTicks(id, tickCount - session.lastTick);
            session.lastTick = tickCount;
            if (GuardConfig.isBlocked(PlaytimeTracker.currentDate().getDayOfWeek())) {
                pendingKick.add(player);
                pendingKickReason.add(GuardConfig.KICK_BLOCKED_DAY_MESSAGE.get());
                continue;
            }
            long limit = limitMillis();
            long remaining = Math.max(0, limit - tracker.usedMillis(id));
            if (remaining == 0) {
                pendingKick.add(player);
                pendingKickReason.add(GuardConfig.KICK_TIME_UP_MESSAGE.get());
                continue;
            }
            long warning = GuardConfig.WARN_BEFORE_MINUTES.get() * 60_000L;
            long previousRemaining = Math.max(0, limit - previousUsed);
            if (warning > 0 && previousRemaining > warning && remaining <= warning) {
                player.sendSystemMessage(Component.literal(render(GuardConfig.WARN_MESSAGE.get(), player, remaining)));
            }
            updateBossBar(player, remaining);
        }

        // 遍历结束后再踢人，此时没有任何迭代器还引用玩家列表。
        for (int i = 0; i < pendingKick.size(); i++) {
            kick(pendingKick.get(i), pendingKickReason.get(i));
        }

        // 每分钟额外落盘一次，服务器意外退出时最多损失少量游玩记录。
        if (tickCount % 1200 == 0) {
            tracker.save();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // 停服时结算所有仍在线的玩家，写入世界目录。
        // 同样先取快照，避免在遍历中因任何回调修改 sessions 而抛并发修改异常。
        for (Map.Entry<UUID, Session> item : Map.copyOf(sessions).entrySet()) {
            if (!disconnecting.contains(item.getKey()) && !isExempt(item.getValue().player)) {
                tracker.addTicks(item.getKey(), tickCount - item.getValue().lastTick);
            }
        }
        tracker.save();
        sessions.clear();
        disconnecting.clear();
        bossBars.clear();
    }

    /**
     * 尝试接纳一个玩家进入计时。
     *
     * @return null 表示正常放行；非 null 表示需要踢出，返回值是踢出消息模板。
     */
    private String admit(ServerPlayer player) {
        UUID id = player.getUUID();
        if (isExempt(player)) {
            return null;
        }
        if (GuardConfig.isBlocked(PlaytimeTracker.currentDate().getDayOfWeek())) {
            return GuardConfig.KICK_BLOCKED_DAY_MESSAGE.get();
        }
        long remaining = Math.max(0, limitMillis() - tracker.usedMillis(id));
        if (remaining == 0) {
            return GuardConfig.KICK_TIME_UP_MESSAGE.get();
        }
        sessions.put(id, new Session(player, tickCount));
        updateBossBar(player, remaining);
        return null;
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
