package com.willfp.ecoenchants.enchantments;


import com.willfp.eco.util.StringUtils;
import com.willfp.eco.util.config.Configs;
import com.willfp.eco.util.interfaces.Registerable;
import com.willfp.eco.util.optional.Prerequisite;
import com.willfp.eco.util.plugin.AbstractEcoPlugin;
import com.willfp.ecoenchants.config.EcoEnchantsConfigs;
import com.willfp.ecoenchants.config.configs.EnchantmentConfig;
import com.willfp.ecoenchants.enchantments.meta.EnchantmentRarity;
import com.willfp.ecoenchants.enchantments.meta.EnchantmentTarget;
import com.willfp.ecoenchants.enchantments.meta.EnchantmentType;
import com.willfp.ecoenchants.enchantments.util.EnchantmentUtils;
import com.willfp.ecoenchants.enchantments.util.Watcher;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings({"unchecked", "deprecation"})
public abstract class EcoEnchant extends Enchantment implements Listener, Registerable, Watcher {
    private final AbstractEcoPlugin plugin = AbstractEcoPlugin.getInstance();

    private String name;
    private String description;
    private final String permissionName;
    private final EnchantmentType type;

    private final EnchantmentConfig config;

    private boolean grindstoneable;
    private boolean canGetFromTable;
    private boolean canGetFromVillager;
    private boolean canGetFromLoot;
    private int maxLvl;
    private Set<Enchantment> conflicts;
    private EnchantmentRarity rarity;
    private final Set<EnchantmentTarget> target = new HashSet<>();
    private final Set<Material> targetMaterials = new HashSet<>();
    private final Set<String> disabledWorldNames = new HashSet<>();
    private final List<World> disabledWorlds = new ArrayList<>();

    private boolean enabled;

    /**
     * Create a new EcoEnchant
     *
     * @param key           The key name of the enchantment
     * @param type          The type of the enchantment
     * @param prerequisites Optional {@link Prerequisite}s that must be met
     */
    protected EcoEnchant(@NotNull final String key,
                         @NotNull final EnchantmentType type,
                         @NotNull final Prerequisite... prerequisites) {
        super(NamespacedKey.minecraft(key));

        this.type = type;
        this.permissionName = key.replace("_", "");
        EcoEnchantsConfigs.addEnchantmentConfig(new EnchantmentConfig(this.permissionName, this.getClass(), this.type));
        this.config = EcoEnchantsConfigs.getEnchantmentConfig(this.permissionName);

        if (Bukkit.getPluginManager().getPermission("ecoenchants.fromtable." + permissionName) == null) {
            Permission permission = new Permission(
                    "ecoenchants.fromtable." + permissionName,
                    "Allows getting " + permissionName + " from an Enchanting Table",
                    PermissionDefault.TRUE
            );
            permission.addParent(Objects.requireNonNull(Bukkit.getPluginManager().getPermission("ecoenchants.fromtable.*")), true);
            Bukkit.getPluginManager().addPermission(permission);
        }

        //WorldguardManager.registerFlag(this.getPermissionName() + "-enabled", true);

        if (type.getRequiredToExtend() != null && !type.getRequiredToExtend().isInstance(this)) {
            return;
        }

        if (!Prerequisite.areMet(prerequisites)) {
            return;
        }

        this.update();
        EcoEnchants.addNewEcoEnchant(this);
    }

    /**
     * Update the enchantment based off config values
     * This can be overriden but may lead to unexpected behavior
     */
    public void update() {
        config.loadFromLang();
        rarity = config.getRarity();
        conflicts = config.getEnchantments(EcoEnchants.GENERAL_LOCATION + "conflicts");
        grindstoneable = config.getBool(EcoEnchants.GENERAL_LOCATION + "grindstoneable");
        canGetFromTable = config.getBool(EcoEnchants.OBTAINING_LOCATION + "table");
        canGetFromVillager = config.getBool(EcoEnchants.OBTAINING_LOCATION + "villager");
        canGetFromLoot = config.getBool(EcoEnchants.OBTAINING_LOCATION + "loot");
        maxLvl = config.getInt(EcoEnchants.GENERAL_LOCATION + "maximum-level", 1);
        name = StringUtils.translate(config.getString("name"));
        description = StringUtils.translate(config.getString("description"));
        disabledWorldNames.clear();
        disabledWorldNames.addAll(config.getStrings(EcoEnchants.GENERAL_LOCATION + "disabled-in-worlds"));
        disabledWorlds.clear();
        List<String> worldNames = Bukkit.getWorlds().stream().map(World::getName).map(String::toLowerCase).collect(Collectors.toList());
        List<String> disabledExistingWorldNames = disabledWorldNames.stream().filter(s -> worldNames.contains(s.toLowerCase())).collect(Collectors.toList());
        disabledWorlds.addAll(Bukkit.getWorlds().stream().filter(world -> disabledExistingWorldNames.contains(world.getName().toLowerCase())).collect(Collectors.toList()));
        target.clear();
        targetMaterials.clear();
        target.addAll(config.getTargets());
        target.forEach(enchantmentTarget -> targetMaterials.addAll(enchantmentTarget.getMaterials()));
        enabled = config.getBool("enabled", true);
        EnchantmentUtils.registerPlaceholders(this);

        postUpdate();
        this.register();
    }

    protected void postUpdate() {
        // Unused as some enchantments may have postUpdate tasks, however most won't.
    }

    @ApiStatus.Internal
    protected AbstractEcoPlugin getPlugin() {
        return this.plugin;
    }

    /**
     * Register the enchantment with spigot
     * Only used internally
     */
    @Override
    public void register() {
        try {
            Field byIdField = Enchantment.class.getDeclaredField("byKey");
            Field byNameField = Enchantment.class.getDeclaredField("byName");
            byIdField.setAccessible(true);
            byNameField.setAccessible(true);
            Map<NamespacedKey, Enchantment> byKey = (Map<NamespacedKey, Enchantment>) byIdField.get(null);
            Map<String, Enchantment> byName = (Map<String, Enchantment>) byNameField.get(null);
            byKey.remove(this.getKey());
            byName.remove(this.getName());

            Map<String, Enchantment> byNameClone = new HashMap<>(byName);
            for (Map.Entry<String, Enchantment> entry : byNameClone.entrySet()) {
                if (entry.getValue().getKey().equals(this.getKey())) {
                    byName.remove(entry.getKey());
                }
            }

            Field f = Enchantment.class.getDeclaredField("acceptingNew");
            f.setAccessible(true);
            f.set(null, true);
            f.setAccessible(false);

            Enchantment.registerEnchantment(this);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }
    }

    /**
     * Get if enchantment can be removed in grindstone
     *
     * @return Whether the enchantment can be removed
     */
    public boolean isGrindstoneable() {
        return grindstoneable;
    }

    /**
     * Get {@link EnchantmentType} of enchantment
     *
     * @return The {@link EnchantmentType}
     */
    public EnchantmentType getType() {
        return this.type;
    }

    /**
     * Get a set of all conflicts
     *
     * @return Conflicts
     */
    public Set<Enchantment> getConflicts() {
        return this.conflicts;
    }

    /**
     * Get if enchantment is enabled
     *
     * @return If enabled
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * Get permission name of enchantment
     *
     * @return The permission name
     */
    public String getPermissionName() {
        return permissionName;
    }

    /**
     * Get description of enchantment
     *
     * @return The description
     */
    public List<String> getDescription() {
        return Arrays.asList(WordUtils.wrap(description, Configs.CONFIG.getInt("lore.describe.wrap"), "\n", false).split("\\r?\\n"));
    }

    /**
     * Get if enchantment can be obtained from an enchanting table
     *
     * @return If can be obtained
     */
    public boolean canGetFromTable() {
        return canGetFromTable;
    }

    /**
     * Get if enchantment can be obtained from a villager
     *
     * @return If can be obtained
     */
    public boolean canGetFromVillager() {
        return canGetFromVillager;
    }

    /**
     * Get if enchantment can be obtained from chest loot
     *
     * @return If can be obtained
     */
    public boolean canGetFromLoot() {
        return canGetFromLoot;
    }

    /**
     * Get {@link EnchantmentRarity} of enchantment
     *
     * @return The enchantment rarity
     */
    public EnchantmentRarity getRarity() {
        return rarity;
    }

    /**
     * If enchantment conflicts with any enchantment in set
     *
     * @param enchantments The set to test against
     *
     * @return If there are any conflicts
     */
    public boolean conflictsWithAny(@NotNull final Set<? extends Enchantment> enchantments) {
        return conflicts.stream().anyMatch(enchantments::contains);
    }

    /**
     * Get enchantment cast to {@link Enchantment}
     *
     * @return The enchantment
     */
    public Enchantment getEnchantment() {
        return this;
    }

    /**
     * Get the target of enchantment
     *
     * @return Set of enchantable items
     */
    public Set<Material> getTarget() {
        return targetMaterials;
    }

    /**
     * Get raw target of enchantment
     *
     * @return {@link com.willfp.ecoenchants.enchantments.meta.EnchantmentTarget}
     */
    public Set<com.willfp.ecoenchants.enchantments.meta.EnchantmentTarget> getRawTargets() {
        return target;
    }

    /**
     * Get {@link EnchantmentConfig} of enchantment
     *
     * @return The config
     */
    public EnchantmentConfig getConfig() {
        return config;
    }

    /**
     * Get worlds that the enchantment is disabled in
     *
     * @return A list of all disabled worlds
     */
    public List<World> getDisabledWorlds() {
        return disabledWorlds;
    }

    /**
     * Get world names that the enchantment is disabled in
     *
     * @return A list of all disabled world names
     */
    public Set<String> getDisabledWorldNames() {
        return disabledWorldNames;
    }

    /**
     * Get display name of enchantment.
     * Not deprecated, unlike {@link Enchantment#getName()}
     *
     * @return The display name
     */
    @Override
    public @NotNull String getName() {
        return name;
    }

    /**
     * Get max level of enchantment
     *
     * @return The max level
     */
    @Override
    public int getMaxLevel() {
        return maxLvl;
    }

    /**
     * @return 1
     */
    @Override
    public int getStartLevel() {
        return 1;
    }

    /**
     * Do not use this method.
     * Only here for compatibility with {@link Enchantment}
     *
     * @return Returns {@link EnchantmentTarget#ALL}. Do not use.
     *
     * @deprecated {@link EnchantmentTarget} is not supported due to its lack of flexibility. Use {@link EcoEnchant#getTarget()} instead.
     */
    @Override
    @Deprecated
    public @NotNull org.bukkit.enchantments.EnchantmentTarget getItemTarget() {
        return org.bukkit.enchantments.EnchantmentTarget.ALL;
    }

    /**
     * Treasure enchantments do not exist in EcoEnchants
     * @see EnchantmentType#SPECIAL
     *
     * @return false
     *
     * @deprecated Treasure enchantments do not exist. Use {@link EcoEnchant#getType()} instead.
     */
    @Override
    @Deprecated
    public boolean isTreasure() {
        return false;
    }

    /**
     * While this method works, it is not recommended to use it.
     * @see EnchantmentType#CURSE
     *
     * @return Returns if enchantment is cursed.
     *
     * @deprecated Use {@link EcoEnchant#getType()} instead.
     */
    @Override
    @Deprecated
    public boolean isCursed() {
        return this.type.equals(EnchantmentType.CURSE);
    }

    /**
     * Get if enchantment conflicts with specified enchantment
     *
     * @param enchantment The enchantment to test against
     *
     * @return If conflicts
     */
    @Override
    public boolean conflictsWith(@NotNull final Enchantment enchantment) {
        return conflicts.contains(enchantment);
    }

    /**
     * If enchantment can be applied to item
     *
     * @param itemStack The {@link ItemStack} to test against
     *
     * @return If can be applied
     */
    @Override
    public boolean canEnchantItem(@NotNull final ItemStack itemStack) {
        return targetMaterials.contains(itemStack.getType()) || itemStack.getType().equals(Material.BOOK) || itemStack.getType().equals(Material.ENCHANTED_BOOK);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EcoEnchant)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        EcoEnchant enchant = (EcoEnchant) o;
        return enchant.getKey().equals(((EcoEnchant) o).getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getKey());
    }
}