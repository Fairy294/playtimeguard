package com.fairy294.playtimeguard;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.BossEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class GuardConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXEMPT_PLAYERS = BUILDER
            .comment("完全不受禁玩日、时长和 BossBar 限制的玩家名列表，不区分大小写。")
            .defineListAllowEmpty("exemptPlayers", List.of("CMC4624"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCKED_DAYS = BUILDER
            .comment("禁止游玩的星期列表，使用 MONDAY 到 SUNDAY；空列表表示不禁止。")
            .defineListAllowEmpty("blockedDays", List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY"),
                    () -> "MONDAY", value -> value instanceof String day && isDay(day));
    public static final ModConfigSpec.IntValue DAILY_LIMIT_MINUTES = BUILDER
            .comment("每个非豁免玩家每天最多游玩的分钟数，范围 1 到 1440。")
            .defineInRange("dailyLimitMinutes", 120, 1, 1440);
    public static final ModConfigSpec.IntValue WARN_BEFORE_MINUTES = BUILDER
            .comment("剩余多少分钟时提醒一次，0 表示不提醒，范围 0 到 120。")
            .defineInRange("warnBeforeMinutes", 10, 0, 120);
    public static final ModConfigSpec.IntValue RESET_HOUR = BUILDER
            .comment("服务器本地时间每天几点重置时长，范围 0 到 23。")
            .defineInRange("resetHour", 4, 0, 23);
    public static final ModConfigSpec.BooleanValue BOSS_BAR_ENABLED = BUILDER
            .comment("是否给非豁免玩家显示剩余游玩时长 BossBar。")
            .define("bossBarEnabled", true);
    public static final ModConfigSpec.ConfigValue<String> BOSS_BAR_TEXT = BUILDER
            .comment("BossBar 文本模板，可用 %remaining%、%used%、%player%、%day%、%limit%。")
            .define("bossBarText", "今日剩余：%remaining%");
    public static final ModConfigSpec.ConfigValue<String> BOSS_BAR_COLOR = BUILDER
            .comment("BossBar 颜色：PINK、BLUE、RED、GREEN、YELLOW、PURPLE 或 WHITE。")
            .define("bossBarColor", "RED", value -> value instanceof String color && isBossBarColor(color));
    public static final ModConfigSpec.BooleanValue BOSS_BAR_SHOW_PROGRESS = BUILDER
            .comment("是否让 BossBar 进度随剩余时间递减；关闭后显示满格静态条。")
            .define("bossBarShowProgress", true);
    public static final ModConfigSpec.ConfigValue<String> KICK_TIME_UP_MESSAGE = BUILDER
            .comment("今日时长用完时的踢出消息，支持所有消息占位符。")
            .define("kickTimeUpMessage", "今日游玩时间已用完，明天再来吧。");
    public static final ModConfigSpec.ConfigValue<String> KICK_BLOCKED_DAY_MESSAGE = BUILDER
            .comment("命中禁玩日时的踢出消息，支持所有消息占位符。")
            .define("kickBlockedDayMessage", "今天（%day%）不允许游玩，明天见。");
    public static final ModConfigSpec.ConfigValue<String> WARN_MESSAGE = BUILDER
            .comment("剩余时长提醒消息，支持所有消息占位符。")
            .define("warnMessage", "注意：今日剩余游玩时间仅剩 %remaining%。");
    public static final ModConfigSpec.ConfigValue<String> DATA_FILE_NAME = BUILDER
            .comment("世界目录中保存游玩时长的 JSON 文件名，不允许路径分隔符；修改后重启世界生效。")
            .worldRestart()
            .define("dataFileName", "playtimeguard.json", value -> value instanceof String name
                    && !name.isBlank() && !name.equals(".") && !name.equals("..")
                    && !name.contains("/") && !name.contains("\\"));

    public static final ModConfigSpec SPEC = BUILDER.build();

    private GuardConfig() {}

    public static boolean isExempt(String playerName) {
        return EXEMPT_PLAYERS.get().stream().anyMatch(name -> name.equalsIgnoreCase(playerName));
    }

    public static boolean isBlocked(DayOfWeek day) {
        return BLOCKED_DAYS.get().stream().anyMatch(name -> name.equalsIgnoreCase(day.name()));
    }

    private static boolean isDay(String name) {
        try {
            DayOfWeek.valueOf(name.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isBossBarColor(String name) {
        try {
            BossEvent.BossBarColor.valueOf(name.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
