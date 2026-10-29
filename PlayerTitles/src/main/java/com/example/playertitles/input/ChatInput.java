package com.example.playertitles.input;

import com.example.playertitles.PlayerTitlesPlugin;
import com.example.playertitles.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatInput implements Listener {

    private static final long TIMEOUT_MILLIS = 60_000L;

    private record Pending(Consumer<String> callback, long expiresAt) {
    }

    private final PlayerTitlesPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInput(PlayerTitlesPlugin plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, String prompt, Consumer<String> callback) {
        pending.put(player.getUniqueId(), new Pending(callback, System.currentTimeMillis() + TIMEOUT_MILLIS));
        player.sendMessage(Text.parse(prompt));
    }

    public void cancel(Player player) {
        pending.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Pending request = pending.get(uuid);
        if (request == null) {
            return;
        }
        pending.remove(uuid);
        if (System.currentTimeMillis() > request.expiresAt()) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                request.callback().accept(text);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("处理聊天输入时出错: " + exception.getMessage());
            }
        });
    }
}
