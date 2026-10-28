package me.nos1qnal.imagemotd;

import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class q implements Plugin, Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(q.class);
    private boolean reloadRequested = false;

    @Override
    public void onEnable() {
        LOGGER.info("ImageMOTD v1.1.0 enabled.");
    }

    @Override
    public void onDisable() {
        LOGGER.info("ImageMOTD v1.1.0 disabled.");
    }

    @Override
    public void execute(CommandSource source, String[] args) {
        if (args.length == 0) {
            source.sendMessage(net.kyori.adventure.text.Component.text("Usage: /imagemotd <generate|status|reload>"));
            return;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "generate":
                handleGenerate(source);
                break;
            case "status":
                handleStatus(source);
                break;
            case "reload":
                reloadRequested = true;
                reloadConfig();
                source.sendMessage(net.kyori.adventure.text.Component.text("Configuration reloaded."));
                break;
            default:
                source.sendMessage(net.kyori.adventure.text.Component.text("Unknown subcommand. Use generate, status, reload."));
        }
    }

    @Override
    public List<String> suggest(CommandSource source, String[] currentArgs) {
        if (currentArgs.length == 1) {
            return List.of("generate", "status", "reload");
        }
        return Collections.emptyList();
    }

    private void handleGenerate(CommandSource source) {
        source.sendMessage(net.kyori.adventure.text.Component.text("Generating image MOTD..."));
    }

    private void handleStatus(CommandSource source) {
        source.sendMessage(net.kyori.adventure.text.Component.text("ImageMOTD status: enabled."));
    }

    private void reloadConfig() {
        // reload logic
    }
}
