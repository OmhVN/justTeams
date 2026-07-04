package eu.kotori.justTeams.core.util;

import eu.kotori.justTeams.JustTeams;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class EffectsUtil {
   private static final JustTeams plugin = JustTeams.getInstance();

   public static void playSound(Player player, SoundType type) {
      if (player != null && plugin.getConfigManager().isSoundsEnabled()) {
         String soundName;
         switch (type) {
            case SUCCESS -> soundName = plugin.getConfigManager().getSuccessSound();
            case ERROR -> soundName = plugin.getConfigManager().getErrorSound();
            case TELEPORT -> soundName = plugin.getConfigManager().getTeleportSound();
            default -> { return; }
         }

         plugin.getTaskRunner().runOnEntity(player, () -> {
            try {
               String key = soundName.toLowerCase().replace(".", "_");
               Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(key));
               if (sound != null) {
                  player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
               } else {
                  player.playSound(player.getLocation(), soundName.toLowerCase(), 1.0F, 1.0F);
               }
            } catch (Exception e) {
               plugin.getLogger().warning("Invalid sound name in config.yml: " + soundName);
            }
         });
      }
   }

   public static void spawnParticles(Location location, Particle particle, int count) {
      if (plugin.getConfigManager().isParticlesEnabled() && location != null && location.getWorld() != null) {
         plugin.getTaskRunner().runAtLocation(location, () -> location.getWorld().spawnParticle(particle, location, count, 0.5, 0.5, 0.5, 0.0));
      }
   }

   public enum SoundType {
      SUCCESS,
      ERROR,
      TELEPORT
   }
}
