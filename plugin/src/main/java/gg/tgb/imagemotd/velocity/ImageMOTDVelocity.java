package gg.tgb.imagemotd.velocity;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.Component;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class ImageMOTDVelocity {
    private static final int MAX_MOTD_JSON_CHARS = 32000;
    private static final int HEADS_PER_ROW = 33;
    private static final int ROW_COUNT = 2;
    private static final Gson GSON = new Gson();
    private static final GsonComponentSerializer COMPONENT_SERIALIZER = GsonComponentSerializer.gson();

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    @Inject
    public ImageMOTDVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    public void onEnable() {
        // Load config
        Path configFile = dataDirectory.resolve("config.yml");
        if (!Files.exists(configFile)) {
            createDefaultConfig(configFile);
        }

        String imageFile = loadConfigValue(configFile, "image-motd.image");
        if (imageFile == null || imageFile.isBlank()) {
            logger.severe("Missing config value: image-motd.image");
            return;
        }

        Path imageFilePath = dataDirectory.resolve("images").resolve(imageFile);
        List<String> textureSources = loadTextureSources(imageFilePath);

        if (textureSources.isEmpty()) {
            logger.severe("No valid texture sources found");
            return;
        }

        List<String> unsignedTextureValues = textureSources.stream()
                .map(this::toUnsignedTextureValue)
                .toList();

        Component motd = buildUnsignedHeadMotd(unsignedTextureValues);
        
        // Register ping listener to set MOTD
        server.getEventManager().register(this, new PingListener(motd));
        
        logger.info("ImageMOTD Velocity enabled - MOTD set with " + unsignedTextureValues.size() + " textures");
    }

    private void createDefaultConfig(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());
            String defaultConfig = """
                image-motd:
                  image: "banner.png"
                """;
            Files.writeString(configFile, defaultConfig, StandardCharsets.UTF_8);
            logger.info("Created default config at " + configFile);
        } catch (IOException e) {
            logger.severe("Failed to create default config: " + e.getMessage());
        }
    }

    private String loadConfigValue(Path configFile, String key) {
        try {
            String content = Files.readString(configFile, StandardCharsets.UTF_8);
            // Simple YAML parsing for our needs
            for (String line : content.lines().toList()) {
                line = line.trim();
                if (line.startsWith(key + ":")) {
                    String value = line.substring(line.indexOf(':') + 1).trim();
                    return value.replace("\"", "").replace("'", "");
                }
            }
        } catch (IOException e) {
            logger.severe("Failed to read config: " + e.getMessage());
        }
        return null;
    }

    private List<String> loadTextureSources(Path imageFilePath) {
        if (!Files.exists(imageFilePath)) {
            logger.severe("Texture source file not found: " + imageFilePath);
            return List.of();
        }

        try {
            return Files.readAllLines(imageFilePath, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        } catch (IOException ex) {
            logger.severe("Failed to read texture source file: " + ex.getMessage());
            return List.of();
        }
    }

    private String toUnsignedTextureValue(String textureSource) {
        JsonObject json = new JsonObject();
        JsonObject textures = new JsonObject();
        JsonObject skin = new JsonObject();
        skin.addProperty("url", textureSource);
        textures.add("SKIN", skin);
        json.add("textures", textures);
        return Base64.getEncoder().encodeToString(GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }

    private Component buildUnsignedHeadMotd(List<String> unsignedTextureValues) {
        if (unsignedTextureValues.isEmpty()) {
            return Component.text("ImageMOTD: No textures loaded");
        }

        Component motd = Component.empty();
        int addedHeads = 0;

        for (int row = 0; row < ROW_COUNT; row++) {
            if (row > 0) {
                Component withNewline = motd.append(Component.newline());
                if (COMPONENT_SERIALIZER.serialize(withNewline).length() > MAX_MOTD_JSON_CHARS) {
                    logger.warning("Hit head limit at " + (row + 1) + " rows.");
                    break;
                }
                motd = withNewline;
            }

            for (int col = 0; col < HEADS_PER_ROW; col++) {
                String textureValue = unsignedTextureValues.get(addedHeads % unsignedTextureValues.size());
                
                Component head = Component.text(" ")
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("")))
                        .append(Component.text(" ")); // Placeholder for head rendering

                // For Velocity, we use player head components in the MOTD
                // This is a simplified approach - real implementation would use PlayerHeadObjectContents
                Component candidate = motd.append(Component.text("█")); // Block character as placeholder
                
                if (COMPONENT_SERIALIZER.serialize(candidate).length() > MAX_MOTD_JSON_CHARS) {
                    logger.warning("Hit head limit at " + addedHeads + " heads.");
                    break;
                }

                motd = candidate;
                addedHeads++;
            }
        }

        int finalLength = COMPONENT_SERIALIZER.serialize(motd).length();
        logger.info("MOTD built: heads=" + addedHeads + ", JSON length=" + finalLength);

        return motd;
    }

    private static class PingListener implements com.velocitypowered.api.event.Listener {
        private final Component motd;

        PingListener(Component motd) {
            this.motd = motd;
        }

        @Subscribe
        public void onProxyPing(ProxyPingEvent event) {
            ServerPing ping = event.getPing();
            ServerPing newPing = ping.asBuilder()
                    .description(motd)
                    .build();
            event.setPing(newPing);
        }
    }
}
