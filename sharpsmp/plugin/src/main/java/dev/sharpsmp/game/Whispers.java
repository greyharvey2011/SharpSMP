package dev.sharpsmp.game;

import dev.sharpsmp.SharpPlugin;
import dev.sharpsmp.data.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Whispers {
    private final SharpPlugin plugin;
    private final Map<UUID, UUID> replies = new ConcurrentHashMap<>();

    public Whispers(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean send(Player from, Player to, String message) {
        if (from.equals(to)) {
            from.sendMessage(plugin.text().msg("pay-self"));
            return false;
        }
        PlayerProfile target = plugin.profiles().get(to);
        if (!target.setting("messages")) {
            from.sendMessage(plugin.text().msg("msg-closed", plugin.text().vars("player", to.getName())));
            return false;
        }
        String body = message.length() > 256 ? message.substring(0, 256) : message;
        Component text = Component.text(body, NamedTextColor.GRAY);
        from.sendMessage(Component.text("to " + to.getName() + ": ", NamedTextColor.DARK_GRAY).append(text));
        to.sendMessage(Component.text("from " + from.getName() + ": ", NamedTextColor.DARK_GRAY).append(text));
        replies.put(to.getUniqueId(), from.getUniqueId());
        replies.put(from.getUniqueId(), to.getUniqueId());
        return true;
    }

    public Player last(Player player) {
        UUID id = replies.get(player.getUniqueId());
        if (id == null) {
            return null;
        }
        return plugin.getServer().getPlayer(id);
    }
}
