package dev.aura.practise.game;

import java.util.EnumMap;
import java.util.Map;

import dev.aura.practise.util.LocUtil;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 一组对局点位：两队出生点 + 两张床。
 * 一张地图（Arena）可配置多组点位实现同图多场并发（MMC 式复制位）。
 */
public class ArenaPosition {

    private final Map<Team, Location> spawns = new EnumMap<>(Team.class);
    private final Map<Team, Location> bedHeads = new EnumMap<>(Team.class);
    private final Map<Team, BlockFace> bedFacings = new EnumMap<>(Team.class);

    public Location spawn(Team team) {
        Location loc = spawns.get(team);
        return loc == null ? null : loc.clone();
    }

    public double spawnY(Team team) {
        Location loc = spawns.get(team);
        return loc == null ? Double.NaN : loc.getY();
    }

    public Location bedHead(Team team) {
        Location loc = bedHeads.get(team);
        return loc == null ? null : loc.clone();
    }

    public BlockFace bedFacing(Team team) {
        return bedFacings.get(team);
    }

    public void setSpawn(Team team, Location loc) {
        spawns.put(team, loc.getBlock().getLocation());
    }

    public void setBed(Team team, Location headLoc, BlockFace facing) {
        bedHeads.put(team, headLoc.getBlock().getLocation());
        bedFacings.put(team, facing);
    }

    public boolean hasSpawn(Team team) {
        return spawns.containsKey(team) && spawns.get(team) != null;
    }

    public boolean hasBed(Team team) {
        return bedHeads.containsKey(team) && bedHeads.get(team) != null
                && bedFacings.containsKey(team) && bedFacings.get(team) != null;
    }

    public boolean isComplete(boolean needsBeds) {
        return hasSpawn(Team.RED) && hasSpawn(Team.BLUE)
                && (!needsBeds || (hasBed(Team.RED) && hasBed(Team.BLUE)));
    }

    public String missingHint(boolean needsBeds) {
        StringBuilder sb = new StringBuilder();
        if (!hasSpawn(Team.RED)) sb.append("红队出生点 ");
        if (!hasSpawn(Team.BLUE)) sb.append("蓝队出生点 ");
        if (needsBeds) {
            if (!hasBed(Team.RED)) sb.append("红队的床 ");
            if (!hasBed(Team.BLUE)) sb.append("蓝队的床 ");
        }
        return sb.toString().trim();
    }

    public void save(ConfigurationSection sec) {
        LocUtil.write(sec, "red-spawn", spawns.get(Team.RED));
        LocUtil.write(sec, "blue-spawn", spawns.get(Team.BLUE));
        LocUtil.write(sec, "red-bed", bedHeads.get(Team.RED));
        LocUtil.write(sec, "blue-bed", bedHeads.get(Team.BLUE));
        sec.set("red-bed-facing", bedFacings.get(Team.RED) == null ? null : bedFacings.get(Team.RED).name());
        sec.set("blue-bed-facing", bedFacings.get(Team.BLUE) == null ? null : bedFacings.get(Team.BLUE).name());
    }

    public static ArenaPosition load(ConfigurationSection sec) {
        ArenaPosition pos = new ArenaPosition();
        pos.spawns.put(Team.RED, LocUtil.read(sec, "red-spawn"));
        pos.spawns.put(Team.BLUE, LocUtil.read(sec, "blue-spawn"));
        pos.bedHeads.put(Team.RED, LocUtil.read(sec, "red-bed"));
        pos.bedHeads.put(Team.BLUE, LocUtil.read(sec, "blue-bed"));
        pos.bedFacings.put(Team.RED, parseFace(sec.getString("red-bed-facing")));
        pos.bedFacings.put(Team.BLUE, parseFace(sec.getString("blue-bed-facing")));
        return pos;
    }

    private static BlockFace parseFace(String s) {
        if (s == null) return null;
        try {
            return BlockFace.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
