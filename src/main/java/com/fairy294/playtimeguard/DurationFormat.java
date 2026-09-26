package com.fairy294.playtimeguard;

public final class DurationFormat {
    private DurationFormat() {}

    // 向上取整，避免剩余不足一分钟时过早显示为零。
    public static String formatDuration(long millis) {
        long seconds = Math.max(0, millis + 999) / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return hours + "小时" + minutes + "分";
        }
        if (seconds >= 60) {
            return minutes + "分";
        }
        return seconds + "秒";
    }
}
