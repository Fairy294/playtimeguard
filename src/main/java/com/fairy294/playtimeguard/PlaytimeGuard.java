package com.fairy294.playtimeguard;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(PlaytimeGuard.MODID)
public final class PlaytimeGuard {
    public static final String MODID = "playtimeguard";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PlaytimeGuard(ModContainer modContainer) {
        // 仅注册服务器配置和游戏事件，不注册任何客户端或物品内容。
        modContainer.registerConfig(ModConfig.Type.SERVER, GuardConfig.SPEC);
        NeoForge.EVENT_BUS.register(new GuardEvents());
    }
}
