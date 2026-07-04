package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class EffectsUtil {
   private static final JustTeams plugin = JustTeams.getInstance();

   public static void playSound(Player player, SoundType type) {
      if (player != null && plugin.getConfigManager().isSoundsEnabled()) {
         String var10000;
         switch (type.ordinal()) {
            case 0 -> var10000 = plugin.getConfigManager().getSuccessSound();
            case 1 -> var10000 = plugin.getConfigManager().getErrorSound();
            case 2 -> var10000 = plugin.getConfigManager().getTeleportSound();
            default -> throw new MatchException((String)null, (Throwable)null);
         }

         String soundName = var10000;
         plugin.getTaskRunner().runOnEntity(player, () -> {
            try {
               Sound sound = Sound.valueOf(soundName.toUpperCase());
               player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
            } catch (IllegalArgumentException var3) {
               plugin.getLogger().warning("Invalid sound name in config.yml: " + soundName);
            }

         });
      }
   }

   public static void spawnParticles(Location location, Particle particle, int count) {
      if (plugin.getConfigManager().isParticlesEnabled() && location != null && location.getWorld() != null) {
         plugin.getTaskRunner().runAtLocation(location, () -> location.getWorld().spawnParticle(particle, location, count, (double)0.5F, (double)0.5F, (double)0.5F, (double)0.0F));
      }
   }

   public static enum SoundType {
      SUCCESS,
      ERROR,
      TELEPORT;

      // $FF: synthetic method
      private static SoundType[] $values() {
         return new SoundType[]{SUCCESS, ERROR, TELEPORT};
      }
   }
}
