package dev.sharpsmp.listen;

import dev.sharpsmp.SharpPlugin;
import dev.sharpsmp.data.PlayerProfile;
import dev.sharpsmp.data.Teams;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class ChatListener implements Listener {
    private final SharpPlugin plugin;

    public ChatListener(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (plugin.prompts().take(player, plain)) {
            event.setCancelled(true);
            return;
        }
        event.viewers().removeIf(audience -> {
            if (!(audience instanceof Player viewer) || viewer.equals(player)) {
                return false;
            }
            PlayerProfile profile = plugin.profiles().get(viewer.getUniqueId());
            return profile != null && !profile.setting("public-chat");
        });
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Teams.TeamData team = plugin.teams().of(source.getUniqueId());
            Component prefix = team == null
                    ? Component.empty()
                    : Component.text(team.name, team.namedColor()).append(Component.space());
            return prefix
                    .append(Component.text(source.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                    .append(message.colorIfAbsent(NamedTextColor.GRAY));
        });
    }
}
