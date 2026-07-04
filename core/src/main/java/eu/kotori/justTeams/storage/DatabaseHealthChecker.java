package eu.kotori.justTeams.core.storage;

import eu.kotori.justTeams.JustTeams;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public class DatabaseHealthChecker {
   private final JustTeams plugin;
   private final DatabaseStorage databaseStorage;
   private final AtomicBoolean isHealthy = new AtomicBoolean(true);
   private final AtomicLong lastHealthCheck = new AtomicLong(0L);
   private final AtomicLong lastRepairAttempt = new AtomicLong(0L);
   private final AtomicLong consecutiveFailures = new AtomicLong(0L);
   private static final long HEALTH_CHECK_COOLDOWN = 30000L;
   private static final long REPAIR_COOLDOWN = 300000L;
   private static final long MAX_CONSECUTIVE_FAILURES = 3L;

   public DatabaseHealthChecker(JustTeams plugin, DatabaseStorage databaseStorage) {
      this.plugin = plugin;
      this.databaseStorage = databaseStorage;
   }

   public boolean performHealthCheck() {
      long currentTime = System.currentTimeMillis();
      if (currentTime - this.lastHealthCheck.get() < 30000L) {
         return this.isHealthy.get();
      } else {
         this.lastHealthCheck.set(currentTime);

         try {
            if (!this.databaseStorage.isConnected()) {
               this.plugin.getLogger().warning("Database health check failed: Not connected");
               this.isHealthy.set(false);
               this.consecutiveFailures.incrementAndGet();
               return false;
            } else {
               Connection conn = this.databaseStorage.getConnection();

               boolean var13;
               label85: {
                  try {
                     if (!conn.isValid(5)) {
                        this.plugin.getLogger().warning("Database health check failed: Connection invalid");
                        this.isHealthy.set(false);
                        this.consecutiveFailures.incrementAndGet();
                        var13 = false;
                        break label85;
                     }

                     Statement stmt = conn.createStatement();

                     try {
                        stmt.execute("SELECT 1");
                     } catch (Throwable var9) {
                        if (stmt != null) {
                           try {
                              stmt.close();
                           } catch (Throwable var8) {
                              var9.addSuppressed(var8);
                           }
                        }

                        throw var9;
                     }

                     if (stmt != null) {
                        stmt.close();
                     }
                  } catch (Throwable var10) {
                     if (conn != null) {
                        try {
                           conn.close();
                        } catch (Throwable var7) {
                           var10.addSuppressed(var7);
                        }
                     }

                     throw var10;
                  }

                  if (conn != null) {
                     conn.close();
                  }

                  this.isHealthy.set(true);
                  this.consecutiveFailures.set(0L);
                  this.plugin.getLogger().fine("Database health check passed");
                  return true;
               }

               if (conn != null) {
                  conn.close();
               }

               return var13;
            }
         } catch (SQLException e) {
            this.plugin.getLogger().log(Level.WARNING, "Database health check failed with SQL exception: " + e.getMessage(), e);
            this.isHealthy.set(false);
            this.consecutiveFailures.incrementAndGet();
            return false;
         } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Database health check failed with unexpected exception: " + e.getMessage(), e);
            this.isHealthy.set(false);
            this.consecutiveFailures.incrementAndGet();
            return false;
         }
      }
   }

   public boolean forceHealthCheck() {
      this.lastHealthCheck.set(0L);
      return this.performHealthCheck();
   }

   public boolean performEmergencyRepair() {
      long currentTime = System.currentTimeMillis();
      if (currentTime - this.lastRepairAttempt.get() < 300000L) {
         this.plugin.getLogger().info("Emergency repair skipped due to cooldown");
         return false;
      } else {
         this.lastRepairAttempt.set(currentTime);
         this.plugin.getLogger().info("Performing emergency database repair...");

         try {
            if (this.databaseStorage.isConnected()) {
               label58: {
                  this.plugin.getLogger().info("Attempting to refresh database connections...");
                  Connection conn = this.databaseStorage.getConnection();

                  boolean var4;
                  label47: {
                     try {
                        if (conn.isValid(5)) {
                           this.plugin.getLogger().info("Emergency repair successful: Database connection restored");
                           this.isHealthy.set(true);
                           this.consecutiveFailures.set(0L);
                           var4 = true;
                           break label47;
                        }
                     } catch (Throwable var7) {
                        if (conn != null) {
                           try {
                              conn.close();
                           } catch (Throwable var6) {
                              var7.addSuppressed(var6);
                           }
                        }

                        throw var7;
                     }

                     if (conn != null) {
                        conn.close();
                     }
                     break label58;
                  }

                  if (conn != null) {
                     conn.close();
                  }

                  return var4;
               }
            }

            this.plugin.getLogger().warning("Emergency repair failed: Could not restore database connection");
            return false;
         } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Emergency repair failed with exception: " + e.getMessage(), e);
            return false;
         }
      }
   }

   public boolean isHealthy() {
      return this.isHealthy.get();
   }

   public String getStatusSummary() {
      StringBuilder summary = new StringBuilder();
      summary.append("Database Health: ").append(this.isHealthy.get() ? "HEALTHY" : "UNHEALTHY");
      summary.append(", Last Check: ").append(this.lastHealthCheck.get() == 0L ? "Never" : Instant.ofEpochMilli(this.lastHealthCheck.get()).toString());
      summary.append(", Consecutive Failures: ").append(this.consecutiveFailures.get());
      summary.append(", Last Repair: ").append(this.lastRepairAttempt.get() == 0L ? "Never" : Instant.ofEpochMilli(this.lastRepairAttempt.get()).toString());
      if (this.databaseStorage.isConnected()) {
         summary.append(", Connection: ACTIVE");
      } else {
         summary.append(", Connection: INACTIVE");
      }

      return summary.toString();
   }

   public long getConsecutiveFailures() {
      return this.consecutiveFailures.get();
   }

   public boolean shouldAttemptRepair() {
      return this.consecutiveFailures.get() >= 3L;
   }

   public void resetHealthStatus() {
      this.isHealthy.set(true);
      this.consecutiveFailures.set(0L);
      this.lastHealthCheck.set(0L);
      this.lastRepairAttempt.set(0L);
   }
}
