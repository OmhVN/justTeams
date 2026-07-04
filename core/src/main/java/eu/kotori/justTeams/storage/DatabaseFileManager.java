package eu.kotori.justTeams.core.storage;

import eu.kotori.justTeams.JustTeams;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.logging.Level;

public class DatabaseFileManager {
   private final JustTeams plugin;

   public DatabaseFileManager(JustTeams plugin) {
      this.plugin = plugin;
   }

   public boolean migrateOldDatabaseFiles() {
      try {
         File dataFolder = new File(this.plugin.getDataFolder(), "data");
         if (!dataFolder.exists()) {
            dataFolder.mkdirs();
            return true;
         } else {
            File oldDbFile = new File(dataFolder, "teams.mv.db");
            File newDbFile = new File(dataFolder, "justteams.mv.db");
            File oldTraceFile = new File(dataFolder, "teams.trace.db");
            File newTraceFile = new File(dataFolder, "justteams.trace.db");
            boolean migrated = false;
            if (oldDbFile.exists() && !newDbFile.exists()) {
               this.plugin.getLogger().info("Migrating database file from teams.mv.db to justteams.mv.db...");
               Files.copy(oldDbFile.toPath(), newDbFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
               this.plugin.getLogger().info("Database file migration completed successfully.");
               migrated = true;
            }

            if (oldTraceFile.exists() && !newTraceFile.exists()) {
               this.plugin.getLogger().info("Migrating trace file from teams.trace.db to justteams.trace.db...");
               Files.copy(oldTraceFile.toPath(), newTraceFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
               this.plugin.getLogger().info("Trace file migration completed successfully.");
            }

            if (migrated) {
               this.plugin.getLogger().info("Cleaning up old database files...");
               if (oldDbFile.exists()) {
                  oldDbFile.delete();
               }

               if (oldTraceFile.exists()) {
                  oldTraceFile.delete();
               }

               this.plugin.getLogger().info("Old database files cleaned up successfully.");
            }

            return true;
         }
      } catch (IOException e) {
         this.plugin.getLogger().log(Level.SEVERE, "Failed to migrate database files: " + e.getMessage(), e);
         return false;
      }
   }

   public boolean backupDatabase() {
      try {
         File dataFolder = new File(this.plugin.getDataFolder(), "data");
         File backupFolder = new File(this.plugin.getDataFolder(), "backups");
         File dbFile = new File(dataFolder, "justteams.mv.db");
         if (!backupFolder.exists()) {
            backupFolder.mkdirs();
         }

         if (dbFile.exists()) {
            File backupFile = new File(backupFolder, "justteams_backup_" + System.currentTimeMillis() + ".mv.db");
            Files.copy(dbFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            this.plugin.getLogger().info("Database backup created: " + backupFile.getName());
            this.cleanupOldDatabaseBackups();
            return true;
         } else {
            return false;
         }
      } catch (IOException e) {
         this.plugin.getLogger().log(Level.WARNING, "Failed to create database backup: " + e.getMessage(), e);
         return false;
      }
   }

   private void cleanupOldDatabaseBackups() {
      try {
         File backupFolder = new File(this.plugin.getDataFolder(), "backups");
         if (!backupFolder.exists()) {
            return;
         }

         File[] backupFiles = backupFolder.listFiles((dir, name) -> name.startsWith("justteams_backup_") && name.endsWith(".mv.db"));
         if (backupFiles == null || backupFiles.length <= 5) {
            return;
         }

         Arrays.sort(backupFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

         for(int i = 5; i < backupFiles.length; ++i) {
            if (backupFiles[i].delete()) {
               this.plugin.getLogger().info("Cleaned up old database backup: " + backupFiles[i].getName());
            }
         }
      } catch (Exception e) {
         this.plugin.getLogger().warning("Error cleaning up old database backups: " + e.getMessage());
      }

   }

   public boolean validateDatabaseFiles() {
      try {
         File dataFolder = new File(this.plugin.getDataFolder(), "data");
         File dbFile = new File(dataFolder, "justteams.mv.db");
         if (!dbFile.exists()) {
            this.plugin.getLogger().warning("Database file not found: justteams.mv.db");
            return false;
         } else if (dbFile.length() == 0L) {
            this.plugin.getLogger().warning("Database file is empty: justteams.mv.db");
            return false;
         } else {
            return true;
         }
      } catch (Exception e) {
         this.plugin.getLogger().log(Level.WARNING, "Database file validation failed: " + e.getMessage(), e);
         return false;
      }
   }
}
