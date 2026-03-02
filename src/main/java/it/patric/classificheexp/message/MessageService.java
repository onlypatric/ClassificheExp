package it.patric.classificheexp.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;

public final class MessageService {

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("prefix", "<gray>[<gold>ClassificheExp</gold>]</gray>"),
            Map.entry("no_permission.base", "<prefix> <red>Non hai il permesso per usare questo comando.</red>"),
            Map.entry("no_permission.add", "<prefix> <red>Non hai il permesso per usare <white>add</white>.</red>"),
            Map.entry("no_permission.remove", "<prefix> <red>Non hai il permesso per usare <white>remove</white>.</red>"),
            Map.entry("no_permission.set", "<prefix> <red>Non hai il permesso per usare <white>set</white>.</red>"),
            Map.entry("no_permission.get", "<prefix> <red>Non hai il permesso per usare <white>get</white>.</red>"),
            Map.entry("no_permission.top", "<prefix> <red>Non hai il permesso per usare <white>top</white>.</red>"),
            Map.entry("usage.root", "<prefix> <yellow>Uso:</yellow> <white>/leaderboard <add|remove|set|get|top></white>"),
            Map.entry("usage.add", "<prefix> <yellow>Uso:</yellow> <white>/leaderboard add <name> <points></white>"),
            Map.entry("usage.remove", "<prefix> <yellow>Uso:</yellow> <white>/leaderboard remove <name> <points></white>"),
            Map.entry("usage.set", "<prefix> <yellow>Uso:</yellow> <white>/leaderboard set <name> <points></white>"),
            Map.entry("usage.get", "<prefix> <yellow>Uso:</yellow> <white>/leaderboard get <name></white>"),
            Map.entry("usage.top", "<prefix> <yellow>Uso:</yellow> <white>/leaderboard top [n]</white>"),
            Map.entry("error.subcommand_invalid", "<prefix> <red>Subcommand non valido.</red>"),
            Map.entry("error.points_positive", "<prefix> <red><points> deve essere un intero <white>> 0</white>.</red>"),
            Map.entry("error.points_non_negative", "<prefix> <red><points> deve essere un intero <white>>= 0</white>.</red>"),
            Map.entry("error.integer", "<prefix> <red><field> deve essere un intero.</red>"),
            Map.entry("error.name_invalid", "<prefix> <red>Nome non valido.</red>"),
            Map.entry("error.operation_failed", "<prefix> <red>Operazione fallita, controlla i log.</red>"),
            Map.entry("status.processing", "<prefix> <yellow>Operazione in corso...</yellow>"),
            Map.entry("success.add", "<prefix> <green>Aggiunti <points> punti a <name>. Totale: <score></green>"),
            Map.entry("success.remove", "<prefix> <green>Rimossi <points> punti da <name>. Totale: <score></green>"),
            Map.entry("success.set", "<prefix> <green>Punteggio di <name> impostato a <score>.</green>"),
            Map.entry("success.get", "<prefix> <aqua>Punteggio di <name>: <score></aqua>"),
            Map.entry("top.header", "<prefix> <gold>Top <limit></gold>"),
            Map.entry("top.empty", "<prefix> <yellow>Nessun dato in classifica.</yellow>"),
            Map.entry("top.line", "<gray><rank>)</gray> <yellow><name></yellow> <white>-</white> <green><score></green>")
    );

    private final FileConfiguration config;
    private final MiniMessage miniMessage;

    public MessageService(JavaPlugin plugin) {
        this(Objects.requireNonNull(plugin, "plugin cannot be null").getConfig());
    }

    public MessageService(FileConfiguration config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        Objects.requireNonNull(sender, "sender cannot be null");
        sender.sendMessage(component(key, resolvers));
    }

    public Component component(String key, TagResolver... resolvers) {
        String template = template(key);

        TagResolver.Builder builder = TagResolver.builder();
        if (!"prefix".equals(key)) {
            builder.resolver(Placeholder.parsed("prefix", template("prefix")));
        }
        for (TagResolver resolver : resolvers) {
            builder.resolver(resolver);
        }

        return miniMessage.deserialize(template, builder.build());
    }

    private String template(String key) {
        String configured = config.getString("messages." + key);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return DEFAULTS.getOrDefault(key, "<red>Missing message key: " + key + "</red>");
    }
}
