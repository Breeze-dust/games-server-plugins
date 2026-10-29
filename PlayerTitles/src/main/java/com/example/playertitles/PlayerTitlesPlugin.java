package com.example.playertitles;

import com.example.playertitles.api.TitleProvider;
import com.example.playertitles.command.TitlesCommand;
import com.example.playertitles.gui.MenuListener;
import com.example.playertitles.input.ChatInput;
import com.example.playertitles.listener.ChatListener;
import com.example.playertitles.listener.MenuItemListener;
import com.example.playertitles.listener.PlayerConnectionListener;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class PlayerTitlesPlugin extends JavaPlugin implements TitleProvider {

    private TitleManager titleManager;
    private ChatInput chatInput;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        titleManager = new TitleManager(this);
        titleManager.load();

        chatInput = new ChatInput(this);

        getServer().getServicesManager().register(TitleProvider.class, this, this, ServicePriority.Normal);

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuItemListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(chatInput, this);

        PluginCommand command = getCommand("titles");
        if (command != null) {
            TitlesCommand executor = new TitlesCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (titleManager != null) {
            titleManager.cleanupTeams();
        }
    }

    public TitleManager getTitleManager() {
        return titleManager;
    }

    public ChatInput getChatInput() {
        return chatInput;
    }

    @Override
    public @Nullable String getTitleId(UUID playerId) {
        return titleManager.getTitleId(playerId);
    }

    @Override
    public @Nullable Component getTitleComponent(UUID playerId) {
        Title title = titleManager.getTitle(playerId);
        return title == null ? null : title.bracketedComponent();
    }

    @Override
    public void refresh(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            titleManager.applyNametag(player);
        }
    }
}
