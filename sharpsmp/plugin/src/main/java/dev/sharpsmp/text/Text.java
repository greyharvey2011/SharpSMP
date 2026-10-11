package dev.sharpsmp.text;

import dev.sharpsmp.SharpPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class Text {
    private final SharpPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();

    public Text(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    public Component mini(String input, TagResolver... resolvers) {
        return mini.deserialize(input == null ? "" : input, resolvers);
    }

    public Component msg(String key, TagResolver... resolvers) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String raw = plugin.getConfig().getString("messages." + key, "<red>Missing message <white>" + key);
        return mini(prefix + raw, resolvers);
    }

    public Component banner(String key, TagResolver... resolvers) {
        String raw = plugin.getConfig().getString("messages." + key, "<red>" + key);
        return mini(raw, resolvers);
    }

    public TagResolver vars(String... pairs) {
        TagResolver.Builder builder = TagResolver.builder();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            builder.resolver(Placeholder.unparsed(pairs[i], pairs[i + 1] == null ? "" : pairs[i + 1]));
        }
        return builder.build();
    }

    public String money(long amount) {
        return Format.money(amount, plugin.getConfig().getString("currency.symbol", "$"));
    }
}
