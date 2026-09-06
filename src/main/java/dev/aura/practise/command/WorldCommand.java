package dev.aura.practise.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    /**
     * 启动时加载 worlds.yml 里登记的自定义世界（Bukkit 重启后不会自动加载
     * 运行时 createWorld 的世界，必须由插件负责）。
     */
    public static void loadRegistered(PractiseAuraPlugin plugin) {
        java.io.File f = new java.io.File(plugin.getDataFolder(), "worlds.yml");
        if (!f.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        var sec = cfg.getConfigurationSection("worlds");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            if (Bukkit.getWorld(name) != null) continue;
            String type = sec.getString(name + ".type", "void");
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
                default -> {
                }
            }
            World loaded = Bukkit.createWorld(creator);
            if (loaded != null && type.equals("void")) {
                prepareVoidWorld(loaded); // 文件夹可能从未落盘（空区块不保存），补平台并落盘
            }
            plugin.getLogger().info("已加载世界 " + name + " (" + type + ")");
        }
    }

    /** 世界创建成功后登记到 worlds.yml，重启后自动加载 */
    private static void register(PractiseAuraPlugin plugin, String name, String type) {
        java.io.File f = new java.io.File(plugin.getDataFolder(), "worlds.yml");
        org.bukkit.configuration.file.YamlConfiguration cfg = f.exists()
                ? org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f)
                : new org.bukkit.configuration.file.YamlConfiguration();
        cfg.set("worlds." + name + ".type", type);
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(f);
        } catch (IOException ex) {
            plugin.getLogger().severe("保存 worlds.yml 失败: " + ex.getMessage());
        }
    }

    /**
     * 虚空世界落盘保障：全空区块不会被 Paper 序列化，世界文件夹可能根本不写盘，
     * 重启即丢。这里强制铺出生平台并立刻 save，确保 level.dat/region 落地。
     */
    private static void prepareVoidWorld(World world) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                world.getBlockAt(x, 99, z).setType(Material.QUARTZ_BLOCK);
            }
        }
        world.setSpawnLocation(0, 100, 0);
        world.save();
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
                    world.getName(),
                    world.getWorldType().name().toLowerCase(),
                    world.getPlayers().size());
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
        if (!name.matches("[A-Za-z0-9_\\-]{1,24}")) { // 与竞技场名同一套规则，防止路径里夹奇怪字符
            Msg.send(sender, "create.bad-name");
            return;
        }
        if (Bukkit.getWorld(name) != null) {
            Msg.send(sender, "world.exists", name);
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
        register(plugin, name, type); // 登记，重启后自动加载
        if (type.equals("void")) {
            prepareVoidWorld(world);
        }
        Msg.send(sender, "world.created", name, type);
        if (sender instanceof Player p) {
            p.teleport(world.getSpawnLocation());
            Msg.send(p, "world.teleported-to-you");
        }
    }

    private void tp(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            Msg.send(sender, "error.no-permission");
            return;
        }
        if (!isPlayer(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "world.tp-usage");
            return;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            Msg.send(sender, "world.missing", args[1]);
            return;
        }
        Player p = (Player) sender;
        Location target = world.getSpawnLocation().clone();
        if (world.getEnvironment() == World.Environment.NETHER || target.getY() <= world.getMinHeight()) {
            target.setY(Math.max(world.getMaxHeight() - 20, target.getY()));
        }
        p.teleport(target);
        Msg.send(p, "world.teleported", world.getName());
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
        // 已登记但未加载的世界也要放行：deleteWorldFiles 会清理 worlds.yml 登记和残留文件夹
        if (Bukkit.getWorld(args[1]) == null && !isRegistered(plugin, args[1])) {
            Msg.send(sender, "world.missing", args[1]);
            return;
        }
        deleteWorldFiles(plugin, sender, args[1]);
    }

    /** 卸载并真实删除世界文件夹，同时移除 worlds.yml 登记（供 /world delete 与 /pa delete 共用） */
    public static void deleteWorldFiles(PractiseAuraPlugin plugin, CommandSender sender, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            // 世界未加载：只清理 worlds.yml 登记过的自定义世界，防止按裸名误删服务器目录下的任意文件夹
            if (!isRegistered(plugin, worldName)) {
                Msg.send(sender, "world.missing", worldName);
                return;
            }
            // 文件夹可能从未落盘（虚空世界空区块不保存）或有残留，删得掉多少算多少
            String error = deleteFolder(Bukkit.getWorldContainer().toPath().resolve(worldName));
            if (error != null) {
                Msg.send(sender, "world.delete-folder-fail", error);
                return; // 保留登记，文件夹还在时可重试
            }
            unregister(plugin, worldName);
            Msg.send(sender, "world.deleted", worldName);
            return;
        }
        if (Bukkit.getWorlds().indexOf(world) == 0) {
            Msg.send(sender, "world.delete-protected");
            return;
        }
        if (plugin.games().worldInUse(world.getName())) {
            Msg.send(sender, "world.delete-in-use", world.getName());
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
        String error = deleteFolder(folder);
        if (error != null) {
            Msg.send(sender, "world.delete-folder-fail", error);
            return; // 保留登记：文件夹还在，重启会重新加载，可重试删除
        }
        unregister(plugin, worldName); // 同步移除 worlds.yml 登记，避免重启后重新加载已删除的世界
        Msg.send(sender, "world.deleted", worldName);
    }

    /** 递归删除文件夹（后序遍历、边遍边删，不物化全部路径）；返回 null 表示彻底删除（或本来就不存在），否则为首个失败原因 */
    private static String deleteFolder(Path folder) {
        if (!Files.exists(folder)) return null;
        String[] firstError = {null};
        try {
            Files.walkFileTree(folder, new java.nio.file.SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                    try {
                        Files.delete(file);
                    } catch (IOException ex) {
                        if (firstError[0] == null) firstError[0] = ex.toString();
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    try {
                        Files.delete(dir);
                    } catch (IOException ex) {
                        if (firstError[0] == null) firstError[0] = ex.toString();
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ex) {
            if (firstError[0] == null) firstError[0] = ex.toString();
        }
        return firstError[0];
    }

    /** 世界是否登记在 worlds.yml 中（即由 /world create 创建的自定义世界） */
    public static boolean isRegistered(PractiseAuraPlugin plugin, String name) {
        java.io.File f = new java.io.File(plugin.getDataFolder(), "worlds.yml");
        if (!f.exists()) return false;
        org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        return cfg.contains("worlds." + name);
    }

    /** 世界删除后从 worlds.yml 移除登记 */
    private static void unregister(PractiseAuraPlugin plugin, String name) {
        java.io.File f = new java.io.File(plugin.getDataFolder(), "worlds.yml");
        if (!f.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        if (!cfg.contains("worlds." + name)) return;
        cfg.set("worlds." + name, null);
        try {
            cfg.save(f);
        } catch (IOException ex) {
            plugin.getLogger().severe("更新 worlds.yml 失败: " + ex.getMessage());
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
