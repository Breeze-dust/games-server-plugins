package com.hubnpc;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.MojangSkinDataResult;
import net.skinsrestorer.api.property.SkinProperty;
import org.bukkit.Bukkit;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** 通过 SkinsRestorer 读取玩家皮肤，并转成 Mannequin 能用的 profile。 */
final class SkinService {

    /** 一份解析好的皮肤（textures 属性）。 */
    record SkinData(UUID uuid, String name, String value, String signature) {
    }

    private final HubNPCPlugin plugin;

    SkinService(HubNPCPlugin plugin) {
        this.plugin = plugin;
    }

    boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("SkinsRestorer");
    }

    /** 异步按玩家名读取皮肤，回调切回主线程；失败或没装皮肤插件时回调 null。 */
    void fetch(String playerName, Consumer<SkinData> callback) {
        if (!available()) {
            callback.accept(null);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            SkinData data = null;
            try {
                SkinsRestorer api = SkinsRestorerProvider.get();
                Optional<MojangSkinDataResult> result =
                        api.getSkinStorage().getPlayerSkin(playerName, false);
                if (result.isPresent()) {
                    MojangSkinDataResult found = result.get();
                    SkinProperty property = found.getSkinProperty();
                    data = new SkinData(found.getUniqueId(), playerName,
                            property.getValue(), property.getSignature());
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("读取皮肤失败（" + playerName + "）：" + t.getMessage());
            }
            SkinData result = data;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    static ResolvableProfile profile(SkinData data) {
        ResolvableProfile.Builder builder = ResolvableProfile.resolvableProfile()
                .addProperty(new ProfileProperty("textures", data.value(), data.signature()));
        if (data.uuid() != null) {
            builder.uuid(data.uuid());
        }
        if (data.name() != null) {
            builder.name(data.name());
        }
        return builder.build();
    }

    static ResolvableProfile profile(String name, String value, String signature) {
        return profile(new SkinData(null, name, value, signature));
    }
}
