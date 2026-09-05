package dev.aura.practise.mode;

import dev.aura.practise.game.Game;
import dev.aura.practise.game.Team;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class BedFightMode implements ModeHandler {

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

    @Override
    public String id() {
        return "bedfight";
    }

    @Override
    public String display() {
        return "BedFight";
    }

    @Override
    public Material icon() {
        return Material.RED_BED;
    }

    @Override
    public void giveDefaultKit(Game game, Player p, Team team) {
        PlayerInventory inv = p.getInventory();
        inv.setItem(0, new ItemStack(Material.IRON_SWORD));
        inv.setItem(1, new ItemStack(Material.BOW));
        inv.setItem(2, new ItemStack(Material.ARROW, 16));
        inv.setItem(3, new ItemStack(Material.GOLDEN_APPLE, 3));
        inv.setItem(4, new ItemStack(Material.COOKED_BEEF, 8));
        inv.setHelmet(new ItemStack(Material.IRON_HELMET));
        inv.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        inv.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        inv.setBoots(new ItemStack(Material.IRON_BOOTS));
    }
}
