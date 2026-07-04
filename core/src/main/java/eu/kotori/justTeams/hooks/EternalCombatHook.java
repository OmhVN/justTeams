package eu.kotori.justTeams.hooks;

import eu.kotori.justTeams.JustTeams;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class EternalCombatHook {
   private final JustTeams plugin;
   private Object eternalCombatApi;
   private Object combatManager;
   private Method isInCombatMethod;
   private Method getRemainingTimeMethod;
   private boolean enabled;
   private volatile boolean initialized;

   public EternalCombatHook(JustTeams plugin) {
      this.plugin = plugin;
      this.enabled = false;
      this.initialized = false;
   }

   public CompletableFuture<Boolean> initialize() {
      return CompletableFuture.supplyAsync(() -> {
         try {
            Plugin eternalCombat = this.plugin.getServer().getPluginManager().getPlugin("EternalCombat");
            if (eternalCombat != null && eternalCombat.isEnabled()) {
               Class<?> apiClass = Class.forName("com.eternalcode.combat.api.EternalCombatApi");
               Method getInstanceMethod = apiClass.getMethod("getInstance");
               this.eternalCombatApi = getInstanceMethod.invoke(null);
               if (this.eternalCombatApi == null) {
                  this.plugin.getLogger().warning("EternalCombat API instance is null. API may not be initialized yet.");
                  return false;
               } else {
                  Method getCombatManagerMethod = apiClass.getMethod("getCombatManager");
                  this.combatManager = getCombatManagerMethod.invoke(this.eternalCombatApi);
                  if (this.combatManager == null) {
                     this.plugin.getLogger().warning("EternalCombat CombatManager is null.");
                     return false;
                  } else {
                     Class<?> combatManagerClass = this.combatManager.getClass();
                     this.isInCombatMethod = combatManagerClass.getMethod("isInCombat", UUID.class);
                     this.getRemainingTimeMethod = combatManagerClass.getMethod("getRemainingTime", UUID.class);
                     this.enabled = true;
                     this.initialized = true;
                     this.plugin.getLogger().info("✓ EternalCombat integration enabled! Version: " + eternalCombat.getDescription().getVersion());
                     return true;
                  }
               }
            } else {
               this.plugin.getLogger().info("EternalCombat not found or disabled. Combat tag integration disabled.");
               return false;
            }
         } catch (ClassNotFoundException var6) {
            this.plugin.getLogger().info("EternalCombat API classes not found. Integration disabled. \ud83d\ude14");
            this.plugin.getLogger().info("This is normal if EternalCombat is not installed.");
            return false;
         } catch (NoSuchMethodException e) {
            this.plugin.getLogger().warning("EternalCombat API method not found. Version mismatch?");
            this.plugin.getLogger().warning("Method: " + e.getMessage());
            return false;
         } catch (Exception e) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = e.getClass().getSimpleName();
            var10000.warning("Failed to initialize EternalCombat hook: " + var10001 + ": " + e.getMessage());
            this.enabled = false;
            this.initialized = false;
            return false;
         }
      });
   }

   public boolean isInCombat(Player player) {
      if (this.isEnabled() && player != null) {
         try {
            Object result = this.isInCombatMethod.invoke(this.combatManager, player.getUniqueId());
            return result instanceof Boolean && (Boolean)result;
         } catch (Exception e) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = player.getName();
            var10000.warning("Error checking combat status for " + var10001 + ": " + e.getMessage());
            return false;
         }
      } else {
         return false;
      }
   }

   public long getRemainingCombatTime(Player player) {
      if (this.isEnabled() && player != null) {
         try {
            if (!this.isInCombat(player)) {
               return 0L;
            } else {
               Object result = this.getRemainingTimeMethod.invoke(this.combatManager, player.getUniqueId());
               if (result instanceof Long) {
                  long remainingMillis = (Long)result;
                  return (remainingMillis + 999L) / 1000L;
               } else {
                  return 0L;
               }
            }
         } catch (Exception e) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = player.getName();
            var10000.warning("Error getting combat time for " + var10001 + ": " + e.getMessage());
            return 0L;
         }
      } else {
         return 0L;
      }
   }

   public boolean isEnabled() {
      return this.enabled && this.initialized;
   }

   public void disable() {
      this.enabled = false;
      this.initialized = false;
      this.eternalCombatApi = null;
      this.combatManager = null;
      this.isInCombatMethod = null;
      this.getRemainingTimeMethod = null;
      this.plugin.getLogger().info("EternalCombat hook disabled.");
   }
}
