package dev.aura.practise.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /world 世界管理：list / create / tp / delete
 * 创建的世界是真实 Bukkit 世界，原版命令（/tp、/setblock、/gamemode…）都能用。
 */
public class WorldCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERM = "practiseaura.admin";

    private final PractiseAuraPlugin plugin;

    public WorldCommand(PractiseAuraPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "help" : args[0].toLowerCase();
        switch (action) {
            case "list" -> list(sender);
            case "create" -> create(sender, args);
            case "tp" -> tp(sender, args);
            case "delete" -> delete(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        Msg.send(sender, "world.help-header");
        Msg.send(sender, "world.help-list");
        Msg.send(sender, "world.help-create");
        Msg.send(sender, "world.help-tp");
        Msg.send(sender, "world.help-delete");
    }

    private void list(CommandSender sender) {
        Msg.send(sender, "world.list-header");
        for (World world : Bukkit.getWorlds()) {
            Msg.send(sender, "world.list-entry",
                    "name", world.getName(),
                    "type", world.getWorldType().name().toLowerCase(),
                    "players", String.valueOf(world.getPlayers().size()));
        }
    }

    private void create(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            Msg.send(sender, "error.no-permission");
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, "world.create-usage");
            return;
        }
        String name = args[1];
        if (Bukkit.getWorld(name) != null) {
            Msg.send(sender, "world.exists", "name", name);
            return;
        }
        String type = args.length >= 3 ? args[2].toLowerCase() : "void";
        WorldCreator creator = new WorldCreator(name);
        switch (type) {
            case "void" -> {
                creator.generator(new VoidGenerator());
                creator.generateStructures(false);
            }
            case "flat" -> {
                creator.type(WorldType.FLAT);
                creator.generateStructures(false);
            }
            case "normal" -> { /* 默认生成 */ }
            default -> {
                Msg.send(sender, "world.bad-type");
                return;
            }
        }
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            Msg.send(sender, "world.create-fail");
            return;
        }
        if (type.equals("void")) {
            // 虚空世界没有地面：在出生点铺一个小平台防止掉虚空
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    world.getBlockAt(x, 99, z).setType(Material.QUARTZ_BLOCK);
                }
            }
            world.setSpawnLocation(0, 100, 0);
        }
        Msg.send(sender, "world.created", "name", name, "type", type);
        if (sender instanceof Player p) {
            p.teleport(world.getSpawnLocation());
            Msg.send(p, "world.teleported-to-you");
        }
    }

    private void tp(CommandSender sender, String[] args) {
        if (!isPlayer(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "world.tp-usage");
            return;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            Msg.send(sender, "world.missing", "name", args[1]);
            return;
        }
        Player p = (Player) sender;
        Location target = world.getSpawnLocation().clone();
        if (world.getEnvironment() == World.Environment.NETHER || target.getY() <= world.getMinHeight()) {
            target.setY(Math.max(world.getMaxHeight() - 20, target.getY()));
        }
        p.teleport(target);
        Msg.send(p, "world.teleported", "name", world.getName());
    }

    private void delete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            Msg.send(sender, "error.no-permission");
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, "world.delete-usage");
            return;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            Msg.send(sender, "world.missing", "name", args[1]);
            return;
        }
        if (Bukkit.getWorlds().indexOf(world) == 0) {
            Msg.send(sender, "world.delete-protected");
            return;
        }
        if (!world.getPlayers().isEmpty()) {
            for (Player p : world.getPlayers()) {
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        }
        Path folder = world.getWorldFolder().toPath();
        if (!Bukkit.unloadWorld(world, false)) {
            Msg.send(sender, "world.delete-fail");
            return;
        }
        try {
            Files.walk(folder)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) {
                        }
                    });
            Msg.send(sender, "world.deleted", "name", args[1]);
        } catch (IOException ex) {
            Msg.send(sender, "world.delete-folder-fail", "error", ex.getMessage());
        }
    }

    private boolean isPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            Msg.send(sender, "error.player-only");
            return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("list");
            out.add("create");
            out.add("tp");
            out.add("delete");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "tp", "delete" -> {
                    for (World world : Bukkit.getWorlds()) out.add(world.getName());
                }
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            out.add("void");
            out.add("flat");
            out.add("normal");
        }
        String low = args[args.length - 1].toLowerCase();
        List<String> filtered = new ArrayList<>();
        for (String s : out) {
            if (s.toLowerCase().startsWith(low)) filtered.add(s);
        }
        return filtered;
    }

    /** 空世界生成器：所有区块为空（无地形、无建筑、无基岩） */
    public static class VoidGenerator extends org.bukkit.generator.ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }

        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }
}
