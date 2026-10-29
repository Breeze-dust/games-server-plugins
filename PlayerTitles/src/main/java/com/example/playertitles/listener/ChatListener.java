package com.example.playertitles.listener;

import com.example.playertitles.PlayerTitlesPlugin;
import com.example.playertitles.Title;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatListener implements Listener {

    private final PlayerTitlesPlugin plugin;

    public ChatListener(PlayerTitlesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) {
            return;
        }
        ChatRenderer previous = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Title title = plugin.getTitleManager().getTitle(source.getUniqueId());
            Component displayName = Component.empty();
            if (title != null) {
                displayName = displayName.append(title.bracketPrefix());
            }
            displayName = displayName.append(sourceDisplayName);

            Component rendered = previous.render(source, displayName, message, viewer);
            if (rendered instanceof TranslatableComponent translatable
                    && translatable.key().equals("chat.type.text")) {
                String separator = plugin.getConfig().getString("chat.separator", ": ");
                return Component.empty()
                        .append(displayName)
                        .append(Component.text(separator))
                        .append(message);
            }
            return rendered;
        });
    }
}
