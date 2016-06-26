package com.elmakers.mine.bukkit.effect;

import com.elmakers.mine.bukkit.utility.NMSUtils;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

public class SoundEffect implements com.elmakers.mine.bukkit.api.effect.SoundEffect {
    private Sound sound;
    private String customSound;
    private float volume = 1.0f;
    private float pitch = 1.0f;
    private int range = 0;

    public SoundEffect(Sound sound) {
        this.sound = sound;
        this.customSound = null;
    }

    public SoundEffect(String key) {
        if (key != null && key.length() > 0) {
            String[] pieces = StringUtils.split(key, ',');
            String soundName = pieces[0];

            if (soundName.indexOf('.') < 0) {
                try {
                    sound = Sound.valueOf(soundName.toUpperCase());
                } catch (Exception ex) {
                    sound = null;
                    customSound = soundName;
                }
            } else {
                customSound = soundName;
            }
            if (pieces.length > 1) {
                try {
                    volume = Float.parseFloat(pieces[1]);
                } catch (Exception ex) {
                    volume = 1;
                }
            }
            if (pieces.length > 2) {
                try {
                    pitch = Float.parseFloat(pieces[2]);
                } catch (Exception ex) {
                    pitch = 1;
                }
            }
        }
    }

    @Override
    public String getCustomSound() {
        return customSound;
    }

    @Override
    public Sound getSound() {
        return sound;
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public float getPitch() {
        return pitch;
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public String toString() {
        if (sound == null) return "";
        return sound.name() + "," + volume + "," + pitch;
    }

    @Override
    public int hashCode() {
        return (sound == null ? 0 : sound.hashCode())
                + 31 * (Float.floatToIntBits(pitch)
                        + 31 * Float.floatToIntBits(volume));
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SoundEffect)) return false;

        SoundEffect otherEffect = (SoundEffect)other;
        return sound != otherEffect.sound || pitch != otherEffect.pitch || volume != otherEffect.volume;
    }

    public int getRange() {
        return range;
    }

    public void play(Plugin plugin, Location sourceLocation) {
        if (sourceLocation == null || plugin == null) return;

        if (customSound != null) {
            try {
                int range = this.range;
                if (range <= 0) {
                    range = (int)(volume > 1.0 ? (16.0 * volume) : 16.0);
                }
                int rangeSquared = range * range;
                Collection<? extends Player> players = plugin.getServer().getOnlinePlayers();
                for (Player player : players) {
                    Location location = player.getLocation();
                    if (location.getWorld().equals(sourceLocation.getWorld()) && location.distanceSquared(sourceLocation) <= rangeSquared) {
                        // player.playSound(sourceLocation, customSound, volume, pitch);
                        NMSUtils.playCustomSound(player, sourceLocation, customSound, volume, pitch);
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to play custom sound: " + customSound);
            }
        }

        if (sound != null) {
            try {
                sourceLocation.getWorld().playSound(sourceLocation, sound, volume, pitch);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to play sound: " + sound);
            }
        }
    }

    public void play(Plugin plugin, Entity entity) {
        if (entity == null || plugin == null) return;

        Location sourceLocation = entity.getLocation();
            if (customSound != null) {
                try {
                    if (range > 0) {
                        int rangeSquared = range * range;
                        Collection<? extends Player> players = plugin.getServer().getOnlinePlayers();
                        for (Player player : players) {
                            Location location = player.getLocation();
                            if (location.getWorld().equals(sourceLocation.getWorld()) && location.distanceSquared(sourceLocation) <= rangeSquared) {
                                // player.playSound(sourceLocation, customSound, volume, pitch);
                                NMSUtils.playCustomSound(player, sourceLocation, customSound, volume, pitch);
                            }
                        }
                    } else if (entity instanceof Player) {
                        Player player = (Player)entity;
                        // player.playSound(sourceLocation, customSound, volume, pitch);
                        NMSUtils.playCustomSound(player, sourceLocation, customSound, volume, pitch);

                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to play custom sound: " + customSound);
                }
            }
    
            if (sound != null) {
                try {
                    if (entity instanceof Player) {
                        Player player = (Player)entity;
                        player.playSound(sourceLocation, sound, volume, pitch);
                    } else {
                        sourceLocation.getWorld().playSound(sourceLocation, sound, volume, pitch);
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to play sound: " + sound);
                }
            }
    }
}
