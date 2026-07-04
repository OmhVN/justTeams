package eu.kotori.justTeams.core.util;

import eu.kotori.justTeams.JustTeams;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class IntelligentConfigHelper {
   public static boolean performYamlAutoRepair(File configFile) {
      if (configFile != null && configFile.exists()) {
         try {
            String raw = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            String repaired = raw.replace("\t", "  ");
            if (raw.equals(repaired)) {
               return false;
            } else {
               File backup = new File(configFile.getParentFile(), configFile.getName() + ".broken.bak");
               Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
               Files.write(configFile.toPath(), repaired.getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
               YamlConfiguration.loadConfiguration(configFile);
               return true;
            }
         } catch (Exception var4) {
            return false;
         }
      } else {
         return false;
      }
   }

   public static void createUpdateSnapshot(JustTeams plugin, String timestamp) {
      if (plugin != null && timestamp != null) {
         File backupDir = new File(plugin.getDataFolder(), "snapshots");
         if (!backupDir.exists()) {
            backupDir.mkdirs();
         }

         File dir = plugin.getDataFolder();
         File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
         if (files != null) {
            for(File f : files) {
               try {
                  String var10003 = f.getName();
                  File dest = new File(backupDir, var10003 + "." + timestamp);
                  Files.copy(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
               } catch (IOException var10) {
               }
            }

         }
      }
   }

   public static boolean performIntelligentFileUpdate(JustTeams plugin, String fileName, String timestamp) throws IOException {
      if (plugin != null && fileName != null) {
         File configFile = new File(plugin.getDataFolder(), fileName);
         if (!configFile.exists()) {
            plugin.saveResource(fileName, false);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static void performEmergencyRecovery(JustTeams plugin, String fileName) throws IOException {
      if (plugin != null && fileName != null) {
         File configFile = new File(plugin.getDataFolder(), fileName);
         File backup = new File(plugin.getDataFolder(), fileName + ".emergency-" + System.currentTimeMillis());
         if (configFile.exists()) {
            Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            configFile.delete();
         }

         plugin.saveResource(fileName, false);
         plugin.getLogger().warning("Emergency recovery: regenerated " + fileName + " (backup at " + backup.getName() + ")");
      }
   }

   public static void generateUpdateReport(JustTeams plugin, int successCount, int failCount, String timestamp) {
      if (plugin != null) {
         plugin.getLogger().info(String.format("Config update report [%s]: %d succeeded, %d failed", timestamp, successCount, failCount));
      }
   }

   public static boolean hasCorruptedValues(FileConfiguration config, String fileName) {
      if (config == null) {
         return true;
      } else {
         Set<String> keys = config.getKeys(true);
         return keys == null || keys.isEmpty();
      }
   }
}
