package com.elmakers.mine.bukkit.integration;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.LibsDisguises;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MiscDisguise;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

public class LibsDisguiseManager {
    private final Plugin disguisePlugin;
    private final Plugin owningPlugin;

    public LibsDisguiseManager(Plugin owningPlugin, Plugin disguisePlugin) {
        this.disguisePlugin = disguisePlugin;
        this.owningPlugin = owningPlugin;
    }

    public boolean initialize() {
        return (disguisePlugin != null && disguisePlugin instanceof LibsDisguises);
    }

    public boolean isDisguised(Entity entity) {
        return DisguiseAPI.isDisguised(entity);
    }
    
    public boolean disguise(Entity entity, ConfigurationSection configuration) {
        if (configuration == null) {
            DisguiseAPI.undisguiseToAll(entity);
            return true;
        }
        String disguiseName = configuration.getString("type");
        if (disguiseName == null || disguiseName.isEmpty()) {
            return false;
        }
        try {
            DisguiseType disguiseType = DisguiseType.valueOf(disguiseName.toUpperCase());
            Disguise disguise = null;
            switch (disguiseType) {
                case PLAYER:
                    PlayerDisguise playerDisguise = new PlayerDisguise(configuration.getString("name"));
                    String skin = configuration.getString("skin");
                    if (skin != null) {
                        playerDisguise.setSkin(skin);
                    }
                    disguise = playerDisguise;
                    break;
                case FALLING_BLOCK:
                case DROPPED_ITEM:
                    Material material = Material.valueOf(configuration.getString("material").toUpperCase());
                    MiscDisguise itemDisguise = new MiscDisguise(disguiseType, material.getId(), configuration.getInt("data"));
                    disguise = itemDisguise;
                    break;
                case SPLASH_POTION:
                case PAINTING:
                    MiscDisguise paintingDisguise = new MiscDisguise(disguiseType, configuration.getInt("data"));
                    disguise = paintingDisguise;
                    break;
                case ARROW:
                case TIPPED_ARROW:
                case SPECTRAL_ARROW:
                case FIREBALL:
                case SMALL_FIREBALL:
                case DRAGON_FIREBALL:
                case WITHER_SKULL:
                case FISHING_HOOK:
                    MiscDisguise miscDisguise = new MiscDisguise(disguiseType);
                    disguise = miscDisguise;
                    break;
                default:
                    boolean isBaby = configuration.getBoolean("baby", false);
                    disguise = new MobDisguise(disguiseType, isBaby);
            }
            DisguiseAPI.disguiseEntity(entity, disguise);
        } catch (Exception ex) {
            owningPlugin.getLogger().log(Level.WARNING, "Error creating disguise", ex);
            return false;
        }
        return true;
    }
}
