package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.storage.DatabaseHealthChecker;
import eu.kotori.justTeams.storage.DatabaseStorage;

public class StartupManager {
   private final JustTeams plugin;
   private final DatabaseHealthChecker healthChecker;
   private boolean startupCompleted = false;
   private boolean startupSuccessful = false;

   public StartupManager(JustTeams plugin, DatabaseStorage databaseStorage) {
      this.plugin = plugin;
      this.healthChecker = new DatabaseHealthChecker(plugin, databaseStorage);
   }

   public boolean performStartup() {
      this.plugin.getLogger().info("Starting comprehensive startup sequence...");

      try {
         ConfigUpdater.updateAllConfigs(this.plugin);
         ConfigUpdater.cleanupAllOldBackups(this.plugin);
         boolean healthy = this.healthChecker.performHealthCheck();
         if (!healthy) {
            this.healthChecker.performEmergencyRepair();
         }

         this.startupCompleted = true;
         this.startupSuccessful = true;
         this.plugin.getLogger().info("Startup sequence completed successfully!");
         return true;
      } catch (Exception e) {
         this.plugin.getLogger().severe("Startup sequence failed: " + e.getMessage());
         this.startupCompleted = true;
         this.startupSuccessful = false;
         return false;
      }
   }

   public boolean isStartupCompleted() {
      return this.startupCompleted;
   }

   public boolean isStartupSuccessful() {
      return this.startupSuccessful;
   }

   public boolean isDatabaseHealthy() {
      return this.healthChecker.isHealthy();
   }

   public String getDatabaseStatus() {
      return this.healthChecker.getStatusSummary();
   }

   public boolean forceHealthCheck() {
      return this.healthChecker.forceHealthCheck();
   }

   public boolean performEmergencyRepair() {
      return this.healthChecker.performEmergencyRepair();
   }

   public String getStartupSummary() {
      String var10000 = this.startupSuccessful ? "SUCCESS" : "FAILED";
      return "Startup Status: " + var10000 + ", Database Health: " + (this.healthChecker.isHealthy() ? "HEALTHY" : "UNHEALTHY");
   }

   public void schedulePeriodicHealthChecks() {
      this.plugin.getLogger().info("Scheduling periodic health checks...");
      this.plugin.getTaskRunner().runAsyncTaskTimer(() -> {
         try {
            if (!this.healthChecker.performHealthCheck()) {
               this.plugin.getLogger().warning("Database health check failed, attempting emergency repair...");
               this.healthChecker.performEmergencyRepair();
            }
         } catch (Exception e) {
            this.plugin.getLogger().severe("Periodic health check failed: " + e.getMessage());
         }

      }, 18000L, 18000L);
   }

   public void schedulePeriodicPermissionSaves() {
      this.plugin.getLogger().info("Scheduling periodic permission saves...");
      this.plugin.getTaskRunner().runAsyncTaskTimer(() -> {
         try {
            if (this.plugin.getConfigManager().isDebugEnabled()) {
               this.plugin.getDebugLogger().log("Performing periodic permission save...");
            }

            this.plugin.getTeamManager().forceSaveAllTeamData();
            if (this.plugin.getConfigManager().isDebugEnabled()) {
               this.plugin.getDebugLogger().log("Periodic permission save completed successfully");
            }
         } catch (Exception e) {
            this.plugin.getLogger().severe("Periodic permission save failed: " + e.getMessage());
         }

      }, 36000L, 36000L);
   }
}
