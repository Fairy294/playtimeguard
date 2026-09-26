package com.fairy294.playtimeguard;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

public final class BossBarManager {
    private final Map<UUID, ServerBossEvent> bars = new HashMap<>();

    // 每个玩家只绑定自己的 BossBar，文本和进度互不影响。
    public void update(ServerPlayer player, String text, long remainingMillis, long limitMillis) {
        if (!GuardConfig.BOSS_BAR_ENABLED.get()) {
            remove(player);
            return;
        }
        ServerBossEvent bar = bars.computeIfAbsent(player.getUUID(), id -> {
            ServerBossEvent created = new ServerBossEvent(UUID.randomUUID(), Component.literal(text),
                    color(), BossEvent.BossBarOverlay.PROGRESS);
            created.addPlayer(player);
            return created;
        });
        bar.setName(Component.literal(text));
        bar.setColor(color());
        float progress = GuardConfig.BOSS_BAR_SHOW_PROGRESS.get()
                ? (float) Math.max(0, Math.min(1, (double) remainingMillis / limitMillis))
                : 1.0F;
        bar.setProgress(progress);
    }

    public void remove(ServerPlayer player) {
        ServerBossEvent bar = bars.remove(player.getUUID());
        if (bar != null) {
            bar.removePlayer(player);
        }
    }

    public void clear() {
        bars.values().forEach(ServerBossEvent::removeAllPlayers);
        bars.clear();
    }

    private static BossEvent.BossBarColor color() {
        return BossEvent.BossBarColor.valueOf(GuardConfig.BOSS_BAR_COLOR.get().toUpperCase(Locale.ROOT));
    }
}
