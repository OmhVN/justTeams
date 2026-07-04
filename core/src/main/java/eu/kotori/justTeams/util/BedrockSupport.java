package eu.kotori.justTeams.core.util;

import eu.kotori.justTeams.JustTeams;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class BedrockSupport {
   private final JustTeams plugin;
   private final Map<UUID, Boolean> bedrockPlayerCache = new ConcurrentHashMap();
   private boolean floodgateAvailable = false;

   public BedrockSupport(JustTeams plugin) {
      this.plugin = plugin;
      this.checkFloodgateAvailability();
   }

   private void checkFloodgateAvailability() {
      try {
         if (this.plugin.getServer().getPluginManager().getPlugin("floodgate") != null) {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            this.floodgateAvailable = true;
            this.plugin.getLogger().info("Floodgate detected! Bedrock support enabled.");
         } else {
            this.floodgateAvailable = false;
            this.plugin.getLogger().info("Floodgate not found. Bedrock support disabled.");
         }
      } catch (Exception e) {
         this.floodgateAvailable = false;
         this.plugin.getLogger().warning("Error checking Floodgate availability: " + e.getMessage());
      }

   }

   public boolean isBedrockPlayer(Player player) {
      if (!this.floodgateAvailable) {
         return false;
      } else {
         Boolean cached = (Boolean)this.bedrockPlayerCache.get(player.getUniqueId());
         if (cached != null) {
            return cached;
         } else {
            try {
               Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
               Object floodgateApi = floodgateApiClass.getMethod("getInstance").invoke(null);
               boolean isBedrock = (Boolean)floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(floodgateApi, player.getUniqueId());
               this.bedrockPlayerCache.put(player.getUniqueId(), isBedrock);
               return isBedrock;
            } catch (Exception e) {
               this.plugin.getLogger().warning("Error checking if player is Bedrock: " + e.getMessage());
               return false;
            }
         }
      }
   }

   public boolean isBedrockPlayer(UUID uuid) {
      if (!this.floodgateAvailable) {
         return false;
      } else {
         Boolean cached = (Boolean)this.bedrockPlayerCache.get(uuid);
         if (cached != null) {
            return cached;
         } else {
            try {
               Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
               Object floodgateApi = floodgateApiClass.getMethod("getInstance").invoke(null);
               boolean isBedrock = (Boolean)floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(floodgateApi, uuid);
               this.bedrockPlayerCache.put(uuid, isBedrock);
               return isBedrock;
            } catch (Exception e) {
               this.plugin.getLogger().warning("Error checking if UUID is Bedrock: " + e.getMessage());
               return false;
            }
         }
      }
   }

   public String getBedrockGamertag(Player player) {
      if (this.floodgateAvailable && this.isBedrockPlayer(player)) {
         try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object floodgateApi = floodgateApiClass.getMethod("getInstance").invoke(null);
            Object floodgatePlayer = floodgateApiClass.getMethod("getPlayer", UUID.class).invoke(floodgateApi, player.getUniqueId());
            if (floodgatePlayer != null) {
               return (String)floodgatePlayer.getClass().getMethod("getUsername").invoke(floodgatePlayer);
            }
         } catch (Exception e) {
            this.plugin.getLogger().warning("Error getting Bedrock gamertag: " + e.getMessage());
         }

         return null;
      } else {
         return null;
      }
   }

   public String getBedrockGamertag(UUID uuid) {
      if (this.floodgateAvailable && this.isBedrockPlayer(uuid)) {
         try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object floodgateApi = floodgateApiClass.getMethod("getInstance").invoke(null);
            Object floodgatePlayer = floodgateApiClass.getMethod("getPlayer", UUID.class).invoke(floodgateApi, uuid);
            if (floodgatePlayer != null) {
               return (String)floodgatePlayer.getClass().getMethod("getUsername").invoke(floodgatePlayer);
            }
         } catch (Exception e) {
            this.plugin.getLogger().warning("Error getting Bedrock gamertag by UUID: " + e.getMessage());
         }

         return null;
      } else {
         return null;
      }
   }

   public UUID getJavaEditionUuid(Player player) {
      if (this.floodgateAvailable && this.isBedrockPlayer(player)) {
         try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object floodgateApi = floodgateApiClass.getMethod("getInstance").invoke(null);
            Object floodgatePlayer = floodgateApiClass.getMethod("getPlayer", UUID.class).invoke(floodgateApi, player.getUniqueId());
            if (floodgatePlayer != null) {
               return (UUID)floodgatePlayer.getClass().getMethod("getJavaUniqueId").invoke(floodgatePlayer);
            }
         } catch (Exception e) {
            this.plugin.getLogger().warning("Error getting Java edition UUID: " + e.getMessage());
         }

         return player.getUniqueId();
      } else {
         return player.getUniqueId();
      }
   }

   public UUID getJavaEditionUuid(UUID uuid) {
      if (this.floodgateAvailable && this.isBedrockPlayer(uuid)) {
         try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object floodgateApi = floodgateApiClass.getMethod("getInstance").invoke(null);
            Object floodgatePlayer = floodgateApiClass.getMethod("getPlayer", UUID.class).invoke(floodgateApi, uuid);
            if (floodgatePlayer != null) {
               return (UUID)floodgatePlayer.getClass().getMethod("getJavaUniqueId").invoke(floodgatePlayer);
            }
         } catch (Exception e) {
            this.plugin.getLogger().warning("Error getting Java edition UUID by UUID: " + e.getMessage());
         }

         return uuid;
      } else {
         return uuid;
      }
   }

   public boolean isFloodgateAvailable() {
      return this.floodgateAvailable;
   }

   public void clearPlayerCache(UUID uuid) {
      this.bedrockPlayerCache.remove(uuid);
   }

   public void clearAllCache() {
      this.bedrockPlayerCache.clear();
   }

   public String getPlatformDisplayName(Player player) {
      if (this.isBedrockPlayer(player)) {
         String gamertag = this.getBedrockGamertag(player);
         if (gamertag != null && !gamertag.equals(player.getName())) {
            String var10000 = player.getName();
            return var10000 + " <gray>(<#00D4FF>Bedrock</#00D4FF>: " + gamertag + "<gray>)";
         } else {
            return player.getName() + " <gray>(<#00D4FF>Bedrock</#00D4FF>)";
         }
      } else {
         return player.getName() + " <gray>(<#00FF00>Java</#00FF00>)";
      }
   }

   public String getPlatformIndicator(Player player) {
      return this.isBedrockPlayer(player) ? "<#00D4FF>BE</#00D4FF>" : "<#00FF00>JE</#00FF00>";
   }

   public boolean isValidPlayerName(String name) {
      return name != null && !name.isEmpty() ? name.matches("^[a-zA-Z0-9_.]+$") : false;
   }

   public String normalizePlayerName(String name) {
      if (name != null && !name.isEmpty()) {
         return name.startsWith(".") ? name.substring(1) : name;
      } else {
         return name;
      }
   }

   public boolean supportsCustomForms(Player player) {
      if (!this.isBedrockPlayer(player)) {
         return false;
      } else {
         try {
            Class.forName("org.geysermc.cumulus.Forms");
            return true;
         } catch (ClassNotFoundException var3) {
            return false;
         }
      }
   }

   public boolean canUseInventoryGUI(Player player) {
      return true;
   }

   public Material getBedrockFallbackMaterial(Material original) {
      if (original != Material.PLAYER_HEAD && original != Material.PLAYER_WALL_HEAD) {
         switch (original) {
            case KNOWLEDGE_BOOK -> {
               return Material.BOOK;
            }
            case DEBUG_STICK -> {
               return Material.STICK;
            }
            case BARRIER -> {
               return original;
            }
            default -> {
               return original;
            }
         }
      } else {
         return Material.DIAMOND;
      }
   }

   public boolean isMaterialProblematic(Material material) {
      return material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD || material == Material.KNOWLEDGE_BOOK || material == Material.DEBUG_STICK;
   }

   public String getDisplayNameWithPlatform(Player player) {
      if (!this.plugin.getConfig().getBoolean("bedrock_support.show_platform_indicators", true)) {
         return player.getName();
      } else {
         return this.isBedrockPlayer(player) ? player.getName() + " §b[BE]§r" : player.getName() + " §a[JE]§r";
      }
   }

   public String getOptimizedInventoryTitle(String title, Player player) {
      if (!this.isBedrockPlayer(player)) {
         return title;
      } else {
         return title.length() > 32 ? title.substring(0, 29) + "..." : title;
      }
   }

   public boolean supportsJavaFeature(Player player, String featureName) {
      if (!this.isBedrockPlayer(player)) {
         return true;
      } else {
         switch (featureName.toLowerCase()) {
            case "offhand":
            case "shield":
            case "spectator":
            case "debug":
               return false;
            case "inventory":
            case "chat":
            case "commands":
               return true;
            default:
               return true;
         }
      }
   }
}
