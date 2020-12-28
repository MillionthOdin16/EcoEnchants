package com.willfp.ecoenchants.enchantments.ecoenchants.normal;

import com.willfp.eco.util.NumberUtils;
import com.willfp.eco.util.events.armorequip.ArmorEquipEvent;
import com.willfp.eco.util.interfaces.EcoRunnable;
import com.willfp.ecoenchants.enchantments.EcoEnchant;
import com.willfp.ecoenchants.enchantments.EcoEnchants;
import com.willfp.ecoenchants.enchantments.meta.EnchantmentType;
import com.willfp.ecoenchants.enchantments.util.EnchantChecks;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class Forcefield extends EcoEnchant implements EcoRunnable {
    public Forcefield() {
        super(
                "forcefield", EnchantmentType.NORMAL
        );
    }

    private final HashMap<Player, Integer> players = new HashMap<>();

    @EventHandler
    public void onArmorEquip(@NotNull final ArmorEquipEvent event) {
        refresh();
    }

    @EventHandler
    public void onPlayerJoin(@NotNull final PlayerJoinEvent event) {
        refresh();
    }

    @EventHandler
    public void onPlayerLeave(@NotNull final PlayerQuitEvent event) {
        refresh();
    }

    private void refresh() {
        players.clear();
        this.getPlugin().getServer().getOnlinePlayers().forEach(player -> {
            int level = EnchantChecks.getArmorPoints(player, this, 0);
            if (level > 0) {
                players.put(player, level);
            }
        });
    }

    @Override
    public void run() {
        players.forEach((player, level) -> {
            if (this.getDisabledWorlds().contains(player.getWorld())) {
                return;
            }
            double initialDistance = EcoEnchants.FORCEFIELD.getConfig().getDouble(EcoEnchants.CONFIG_LOCATION + "initial-distance");
            double bonus = EcoEnchants.FORCEFIELD.getConfig().getDouble(EcoEnchants.CONFIG_LOCATION + "bonus-per-level");
            double distance = initialDistance + (level * bonus);
            double damagePerPoint = EcoEnchants.FORCEFIELD.getConfig().getDouble(EcoEnchants.CONFIG_LOCATION + "damage-per-level");
            final double damage = damagePerPoint * level;

            for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), distance, 2.0d, distance)) {
                if (!(e instanceof Monster)) {
                    continue;
                }

                ((Monster) e).damage(damage);

                if (NumberUtils.randFloat(0, 1) < 0.2) {
                    EnchantChecks.getArmorPoints(player, EcoEnchants.FORCEFIELD, 1);
                }
            }
        });
    }

    @Override
    public long getTime() {
        return this.getConfig().getInt(EcoEnchants.CONFIG_LOCATION + "repeat-ticks");
    }
}