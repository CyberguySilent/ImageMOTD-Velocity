package me.nos1qnal.imagemotd;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.SimpleCommand.Invocation;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * ImageMOTD for Velocity 3.5.1 / Java 21 (Minestrator compatible).
 * <p>
 * Generates a text-based MOTD from an image (each cell = one coloured character)
 * and applies it to every server-list ping via {@link ProxyPingEvent}.
 * Velocity 3.x has no {@code ProxyServer#getMotd()}/{@code #setMotd()}, so the
 * MOTD is rewritten dynamically on each ping.
 */
@Plugin(
        id = "imagemotd",
        name = "ImageMOTD",
        version = "1.1.0",
        description = "Image based MOTD generator",
        authors = {"CyberguySilent"}
)
public class q {

    private static final Path DEFAULT_DATA_FOLDER = Path.of("plugins", "ImageMOTD");
    private static final String DEFAULT_IMAGE_NAME = "images/image.png";
    private static final String DEFAULT_RESOURCE = "/264x16.png";
    private static final String CACHE_FILE_NAME = "images/cache.txt";

    private static final int DEFAULT_WIDTH = 40;
    private static final int DEFAULT_HEIGHT = 3;

    // 16 classic Minecraft chat colours (RGB)
    private static final int[][] MINECRAFT_COLOURS = {
            {0x00, 0x00, 0x00}, // black
            {0x00, 0x00, 0xAA}, // dark_blue
            {0x00, 0xAA, 0x00}, // dark_green
            {0x00, 0xAA, 0xAA}, // dark_aqua
            {0xAA, 0x00, 0x00}, // dark_red
            {0xAA, 0x00, 0xAA}, // dark_purple
            {0xFF, 0xAA, 0x00}, // gold
            {0xAA, 0xAA, 0xAA}, // gray
            {0x55, 0x55, 0x55}, // dark_gray
            {0x55, 0x55, 0xFF}, // blue
            {0x55, 0xFF, 0x55}, // green
            {0x55, 0xFF, 0xFF}, // aqua
            {0xFF, 0x55, 0x55}, // red
            {0xFF, 0x55, 0xFF}, // light_purple
            {0xFF, 0xFF, 0x55}, // yellow
            {0xFF, 0xFF, 0xFF}  // white
    };

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataFolder;

    private ImageMOTDGenerator generator;

    @Inject
    public q(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        this.dataFolder = DEFAULT_DATA_FOLDER;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            Files.createDirectories(dataFolder.resolve("images"));
        } catch (IOException e) {
            logger.warn("ImageMOTD: could not create data folder {}", dataFolder, e);
        }

        // Extract default image if no user image is present.
        Path imageFile = dataFolder.resolve(DEFAULT_IMAGE_NAME);
        if (!Files.isRegularFile(imageFile)) {
            try (InputStream in = getClass().getResourceAsStream(DEFAULT_RESOURCE)) {
                if (in != null) {
                    Files.copy(in, imageFile, StandardCopyOption.REPLACE_EXISTING);
                    logger.info("ImageMOTD: extracted default image to {}", imageFile);
                } else {
                    logger.warn("ImageMOTD: default resource {} not found", DEFAULT_RESOURCE);
                }
            } catch (IOException e) {
                logger.warn("ImageMOTD: could not extract default image", e);
            }
        }

        this.generator = new ImageMOTDGenerator(dataFolder, logger);
        generator.generate(); // initial MOTD at startup

        // Register command.
        CommandMeta meta = server.getCommandManager().metaBuilder("imagemotd")
                .plugin(this)
                .build();
        server.getCommandManager().register(meta, new ImagemotdCommand(this));

        // @Subscribe methods on the @Plugin class are auto-registered by Velocity (Guice),
        // no manual EventManager.register needed (and forbidden for the main instance).

        logger.info("ImageMOTD v1.1.0 enabled. MOTD generator initialised.");
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        if (generator != null && generator.isGenerated()) {
            event.setPing(
                    event.getPing().asBuilder()
                            .description(generator.getComponent())
                            .build()
            );
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("ImageMOTD v1.1.0 disabled.");
    }

    public static class ImagemotdCommand implements SimpleCommand {

        private final q plugin;

        public ImagemotdCommand(q plugin) {
            this.plugin = plugin;
        }

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();
            if (!source.hasPermission("imagemotd.admin")) {
                source.sendMessage(Component.text("You don't have permission to use this command."));
                return;
            }
            if (args.length == 0 || args[0].isBlank()) {
                source.sendMessage(Component.text("Usage: /imagemotd <generate|status|reload>"));
                return;
            }
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "generate" -> handleGenerate(source);
                case "status" -> handleStatus(source);
                case "reload" -> handleReload(source);
                default -> source.sendMessage(Component.text("Unknown subcommand. Use generate, status, reload."));
            }
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            String[] args = invocation.arguments();
            List<String> subs = List.of("generate", "status", "reload");
            if (args.length == 0) {
                return subs;
            }
            if (args.length == 1) {
                String typed = args[args.length - 1].toLowerCase();
                return subs.stream().filter(s -> s.startsWith(typed)).toList();
            }
            return Collections.emptyList();
        }

        private void handleGenerate(CommandSource source) {
            Objects.requireNonNull(plugin, "plugin instance not bound");
            boolean ok = plugin.generator.generate();
            if (ok) {
                source.sendMessage(Component.text("ImageMOTD: generated successfully."));
            } else {
                source.sendMessage(Component.text("ImageMOTD: generation failed, see console."));
            }
        }

        private void handleStatus(CommandSource source) {
            Objects.requireNonNull(plugin, "plugin instance not bound");
            ImageMOTDGenerator g = plugin.generator;
            StringBuilder msg = new StringBuilder("ImageMOTD status: ");
            msg.append(g.isGenerated() ? "generated" : "not generated");
            msg.append(" | image=").append(g.getImagePath());
            msg.append(" | size=").append(g.getWidth()).append("x").append(g.getHeight());
            msg.append(" | cells=").append(g.getCellCount());
            source.sendMessage(Component.text(msg.toString()));
        }

        private void handleReload(CommandSource source) {
            Objects.requireNonNull(plugin, "plugin instance not bound");
            boolean ok = plugin.generator.generate();
            if (ok) {
                source.sendMessage(Component.text("ImageMOTD: reloaded and regenerated."));
            } else {
                source.sendMessage(Component.text("ImageMOTD: reload failed, see console."));
            }
        }
    }

    /** Builds a coloured text MOTD from an image. */
    public static class ImageMOTDGenerator {

        private final Path dataFolder;
        private final Logger logger;
        private final int width;
        private final int height;

        private volatile boolean generated = false;
        private volatile String imagePath = "";
        private volatile int cellCount = 0;
        private volatile Component component = Component.text("ImageMOTD not generated.");

        public ImageMOTDGenerator(Path dataFolder, Logger logger) {
            this(dataFolder, logger, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }

        public ImageMOTDGenerator(Path dataFolder, Logger logger, int width, int height) {
            this.dataFolder = dataFolder;
            this.logger = logger;
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
        }

        /** Generate (or regenerate) the MOTD from the image. Returns true on success. */
        public boolean generate() {
            Path imageFile = dataFolder.resolve(DEFAULT_IMAGE_NAME);
            BufferedImage image;
            String source;

            if (Files.isRegularFile(imageFile)) {
                source = imageFile.toString();
                try {
                    image = ImageIO.read(imageFile.toFile());
                } catch (IOException e) {
                    logger.warn("ImageMOTD: could not read image {}", imageFile, e);
                    return false;
                }
            } else {
                source = DEFAULT_RESOURCE;
                try (InputStream in = getClass().getResourceAsStream(DEFAULT_RESOURCE)) {
                    if (in == null) {
                        logger.warn("ImageMOTD: default resource {} not found", DEFAULT_RESOURCE);
                        return false;
                    }
                    image = ImageIO.read(in);
                } catch (IOException e) {
                    logger.warn("ImageMOTD: could not read default resource", e);
                    return false;
                }
            }

            if (image == null) {
                logger.warn("ImageMOTD: unable to decode image from {}", source);
                return false;
            }

            List<List<ColoredChar>> grid = buildGrid(image);
            Component comp = buildComponent(grid);

            // Write cache file.
            Path cacheFile = dataFolder.resolve(CACHE_FILE_NAME);
            try {
                Files.createDirectories(cacheFile.getParent());
                List<String> lines = new ArrayList<>();
                lines.add("generated=true");
                lines.add("image=" + source);
                lines.add("width=" + width);
                lines.add("height=" + height);
                lines.add("cells=" + grid.size());
                lines.add("---");
                for (List<ColoredChar> row : grid) {
                    StringBuilder sb = new StringBuilder();
                    for (ColoredChar cc : row) {
                        sb.append(cc.c());
                    }
                    lines.add(sb.toString());
                }
                Files.write(cacheFile, lines);
            } catch (IOException e) {
                logger.warn("ImageMOTD: could not write cache file {}", cacheFile, e);
                // Not fatal: the MOTD is still generated in memory.
            }

            this.imagePath = source;
            this.cellCount = grid.size() * (grid.isEmpty() ? 0 : grid.get(0).size());
            this.component = comp;
            this.generated = true;

            logger.info("ImageMOTD: generated MOTD from {} ({}x{} cells, {} total)",
                    source, width, height, this.cellCount);
            return true;
        }

        public boolean isGenerated() {
            return generated;
        }

        public String getImagePath() {
            return imagePath;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getCellCount() {
            return cellCount;
        }

        public Component getComponent() {
            return component;
        }

        private List<List<ColoredChar>> buildGrid(BufferedImage image) {
            int srcW = image.getWidth();
            int srcH = image.getHeight();
            List<List<ColoredChar>> grid = new ArrayList<>(height);
            for (int y = 0; y < height; y++) {
                List<ColoredChar> row = new ArrayList<>(width);
                int sy = y * srcH / height;
                for (int x = 0; x < width; x++) {
                    int sx = x * srcW / width;
                    // Average a small neighbourhood block for smoother results.
                    int rgb = sampleAverage(image, sx, sy, srcW, srcH);
                    TextColor color = closestMinecraftColour(rgb);
                    char c = isTransparent(rgb) ? ' ' : '█';
                    row.add(new ColoredChar(c, color));
                }
                grid.add(row);
            }
            return grid;
        }

        private int sampleAverage(BufferedImage image, int cx, int cy, int srcW, int srcH) {
            int block = Math.max(1, Math.min(srcW / width, srcH / height));
            int r = 0, g = 0, b = 0, a = 0, count = 0;
            int x0 = Math.max(0, cx - block / 2);
            int x1 = Math.min(srcW - 1, cx + block / 2);
            int y0 = Math.max(0, cy - block / 2);
            int y1 = Math.min(srcH - 1, cy + block / 2);
            for (int yy = y0; yy <= y1; yy++) {
                for (int xx = x0; xx <= x1; xx++) {
                    int rgb = image.getRGB(xx, yy);
                    r += (rgb >> 16) & 0xFF;
                    g += (rgb >> 8) & 0xFF;
                    b += rgb & 0xFF;
                    a += (rgb >> 24) & 0xFF;
                    count++;
                }
            }
            if (count == 0) {
                return 0xFF000000; // opaque black fallback
            }
            return (a / count << 24) | ((r / count) << 16) | ((g / count) << 8) | (b / count);
        }

        private boolean isTransparent(int rgb) {
            return ((rgb >> 24) & 0xFF) < 128;
        }

        private TextColor closestMinecraftColour(int rgb) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            int bestIdx = 0;
            long bestDist = Long.MAX_VALUE;
            for (int i = 0; i < MINECRAFT_COLOURS.length; i++) {
                int[] c = MINECRAFT_COLOURS[i];
                long d = (long) (r - c[0]) * (r - c[0])
                        + (long) (g - c[1]) * (g - c[1])
                        + (long) (b - c[2]) * (b - c[2]);
                if (d < bestDist) {
                    bestDist = d;
                    bestIdx = i;
                }
            }
            int[] c = MINECRAFT_COLOURS[bestIdx];
            return TextColor.color(c[0], c[1], c[2]);
        }

        /** A single coloured character cell of the MOTD grid. */
        private record ColoredChar(char c, TextColor color) {
        }

        private Component buildComponent(List<List<ColoredChar>> grid) {
            Component current = Component.empty();
            for (int i = 0; i < grid.size(); i++) {
                List<ColoredChar> row = grid.get(i);
                for (ColoredChar cc : row) {
                    current = current.append(Component.text(String.valueOf(cc.c()), cc.color()));
                }
                if (i < grid.size() - 1) {
                    current = current.append(Component.newline());
                }
            }
            return current;
        }
    }
}
