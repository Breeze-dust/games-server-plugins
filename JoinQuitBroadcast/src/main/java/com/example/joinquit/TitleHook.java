package com.example.joinquit;

import com.example.playertitles.api.TitleProvider;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class TitleHook {

    private final TitleProvider provider;

    TitleHook() {
        this.provider = Bukkit.getServicesManager().load(TitleProvider.class);
    }

    Component titleFor(Player player) {
        return provider == null ? null : provider.getTitleComponent(player.getUniqueId());
    }
}
