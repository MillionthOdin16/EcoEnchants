package com.willfp.ecoenchants.enchantments.ecoenchants.normal;

import com.willfp.ecoenchants.enchantments.EcoEnchant;
import com.willfp.ecoenchants.enchantments.EcoEnchants;
import com.willfp.ecoenchants.enchantments.meta.EnchantmentType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.NotNull;

public class Stab extends EcoEnchant {
    public Stab() {
        super(
                "stab", EnchantmentType.NORMAL
        );
    }

    // START OF LISTENERS


    @Override
    public void onMeleeAttack(@NotNull LivingEntity attacker, @NotNull LivingEntity victim, int level, @NotNull EntityDamageByEntityEvent event) {
        double baseDamage = this.getConfig().getDouble(EcoEnchants.CONFIG_LOCATION + "damage-base");
        double perLevelDamage = this.getConfig().getDouble(EcoEnchants.CONFIG_LOCATION + "damage-per-level");
        double damage = baseDamage + (level * perLevelDamage);

        event.setDamage(event.getDamage() + damage);
    }
}