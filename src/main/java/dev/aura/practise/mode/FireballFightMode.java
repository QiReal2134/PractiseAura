package dev.aura.practise.mode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.game.Game;
import dev.aura.practise.game.GameState;
import dev.aura.practise.game.Team;
import dev.aura.practise.util.Msg;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

public class FireballFightMode implements ModeHandler {

    private final Map<UUID, Long> lastThrow = new HashMap<>();
    private final ModeSettings settings = new ModeSettings()
            .setNeedsBeds(true)
            .setNeedsGuard(true)
            .setDamageEnabled(true)
            .setPvp(true)
            .setAllowBreakMap(false)
            .setAllowBreakPlaced(true)
            .setAllowPlace(true)
            .setVoidKill(true);

    @Override
    public ModeSettings settings() {
        return settings;
    }

    /** 火球模式一条命：死亡即淘汰（床仍可拆，作为削弱手段） */
    @Override
    public boolean respawnOnDeath() {
        return false;
    }

    @Override
    public String id() {
        return "fireballfight";
    }

    @Override
    public String display() {
        return "FireBallFight";
    }

    @Override
    public Material icon() {
        return Material.FIRE_CHARGE;
    }

    @Override
    public void giveDefaultKit(Game game, Player p, Team team) {
        PlayerInventory inv = p.getInventory();
        inv.setItem(0, new ItemStack(Material.IRON_SWORD));
        inv.setItem(1, new ItemStack(Material.FIRE_CHARGE, 8)); // 开局一次给足
        inv.setHelmet(new ItemStack(Material.LEATHER_HELMET));
        inv.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        inv.setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
        inv.setBoots(new ItemStack(Material.LEATHER_BOOTS));
    }

    /** 右键发射火球（带冷却、消耗、爆炸由监听器接管） */
    @Override
    public boolean onRightClick(Game game, Player p) {
        if (game.state() != GameState.RUNNING || !game.isAlive(p.getUniqueId())) return false;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != Material.FIRE_CHARGE) return false;

        PractiseAuraPlugin plugin = game.plugin();
        UUID id = p.getUniqueId();
        long cooldown = (long) (plugin.settings().fireballCooldownSeconds() * 1000);
        long now = System.currentTimeMillis();
        // 懒清理：丢掉超过 5 分钟没活动的记录，防止长期运行内存堆积
        lastThrow.values().removeIf(t -> now - t > 300_000L);
        long last = lastThrow.getOrDefault(id, 0L);
        if (now - last < cooldown) {
            double left = Math.max(0.1, (cooldown - (now - last)) / 1000.0);
            Msg.send(p, "fireball.cooldown", "seconds", String.format("%.1f", left));
            return true; // 已处理：事件取消但不发射
        }
        lastThrow.put(id, now);

        // 消耗 1 颗
        int amount = hand.getAmount();
        if (amount <= 1) {
            p.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(amount - 1);
            p.getInventory().setItemInMainHand(hand);
        }

        Vector direction = p.getLocation().getDirection().normalize();
        org.bukkit.entity.LargeFireball fireball =
                p.launchProjectile(org.bukkit.entity.LargeFireball.class, direction);
        fireball.setYield(1.6f); // 实际爆炸由 EntityExplodeEvent 监听器完全接管
        fireball.setIsIncendiary(false);
        p.playSound(p.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1f, 1.4f);
        return true;
    }

    @Override
    public void addScoreboardLines(Game game, java.util.List<String> lines) {
        // 预留：后续可显示火球冷却等
    }
}
