package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class ConfigUpdater {
   private static final List<String> CONFIG_FILES = List.of("config.yml", "messages.yml", "commands.yml", "placeholders.yml");
   public static final Map<String, Set<String>> USER_CUSTOMIZABLE_KEYS = new HashMap<String, Set<String>>() {
      {
         this.put("config.yml", Set.of("storage.mysql.host", "storage.mysql.port", "storage.mysql.database", "storage.mysql.username", "storage.mysql.password", "storage.mysql.useSSL", "server-identifier", "main_color", "accent_color", "currency_format", "max_team_size", "permission_based_team_size", "max_teams_per_player", "team_creation.min_tag_length", "team_creation.max_tag_length", "team_creation.min_name_length", "team_creation.max_name_length", "debug.enabled", "webhook.url", "webhook.enabled"));
         this.put("messages.yml", Set.of("prefix", "team_chat_format", "help_header"));
         this.put("gui.yml", Set.of("no-team-gui.title", "team-gui.title", "admin-gui.title", "no-team-gui.items.create-team", "no-team-gui.items.leaderboards"));
         this.put("placeholders.yml", Set.of("colors.primary", "colors.secondary", "colors.accent", "colors.success", "colors.error", "colors.warning", "colors.info", "team_display.format", "team_display.team_icon", "team_display.team_color", "team_display.show_icon", "team_display.show_tag", "team_display.show_name", "team_display.no_team", "team_display.tag_prefix", "team_display.tag_suffix", "team_display.tag_color"));
         this.put("commands.yml", Set.of());
      }
   };
   public static final Map<String, Pattern> VALUE_VALIDATORS = new HashMap<String, Pattern>() {
      {
         this.put("storage.mysql.port", Pattern.compile("^\\d{1,5}$"));
         this.put("max_team_size", Pattern.compile("^\\d+$"));
         this.put("max_teams_per_player", Pattern.compile("^\\d+$"));
         this.put("team_creation.min_tag_length", Pattern.compile("^\\d+$"));
         this.put("team_creation.max_tag_length", Pattern.compile("^\\d+$"));
         this.put("colors.primary", Pattern.compile("^#[0-9A-Fa-f]{6}$"));
         this.put("colors.secondary", Pattern.compile("^#[0-9A-Fa-f]{6}$"));
         this.put("colors.accent", Pattern.compile("^#[0-9A-Fa-f]{6}$"));
      }
   };

   public static void updateAllConfigs(JustTeams plugin) {
      plugin.getLogger().info("Starting automatic configuration update process...");
      performConfigHealthCheck(plugin);
      int successCount = 0;
      int failCount = 0;

      for(String fileName : CONFIG_FILES) {
         try {
            File configFile = new File(plugin.getDataFolder(), fileName);
            if (configFile.exists()) {
               try {
                  YamlConfiguration.loadConfiguration(configFile);
               } catch (Exception var9) {
                  plugin.getLogger().warning("YAML syntax error detected in " + fileName + ", attempting repair...");
                  if (IntelligentConfigHelper.performYamlAutoRepair(configFile)) {
                     plugin.getLogger().info("Successfully auto-repaired " + fileName + " using intelligent repair");
                  } else if (repairYamlFile(configFile)) {
                     plugin.getLogger().info("Successfully repaired " + fileName + " using fallback method");
                  } else {
                     plugin.getLogger().severe("Failed to repair " + fileName + ", will recreate from defaults");
                  }
               }
            }

            boolean needsUpdate = needsUpdate(plugin, fileName);
            if (needsUpdate) {
               plugin.getLogger().info(fileName + " needs update, processing...");
               boolean updated = updateConfig(plugin, fileName);
               if (updated) {
                  ++successCount;
                  plugin.getLogger().info("Successfully updated " + fileName);
               } else {
                  plugin.getLogger().warning("Failed to update " + fileName + " despite needing update");
                  ++failCount;
               }
            } else {
               plugin.getLogger().fine(fileName + " is already up to date");
            }
         } catch (Exception e) {
            ++failCount;
            plugin.getLogger().log(Level.SEVERE, "Failed to update " + fileName + ": " + e.getMessage(), e);

            try {
               plugin.getLogger().warning("Creating backup and force update for " + fileName);
               createBackupAndForceUpdate(plugin, fileName);
               ++successCount;
               plugin.getLogger().info("Successfully recovered " + fileName + " with force update");
            } catch (Exception recoveryException) {
               plugin.getLogger().log(Level.SEVERE, "Failed to recover " + fileName + ": " + recoveryException.getMessage(), recoveryException);
            }
         }
      }

      plugin.getLogger().info("Configuration update process completed! Success: " + successCount + ", Failed: " + failCount);
   }

   private static boolean updateConfig(JustTeams plugin, String fileName) throws IOException {
      File configFile = new File(plugin.getDataFolder(), fileName);
      if (!configFile.exists()) {
         plugin.saveResource(fileName, false);
         plugin.getLogger().info("Created " + fileName + " from default template.");
         return true;
      } else {
         FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
         InputStream defaultConfigStream = plugin.getResource(fileName);

         boolean var13;
         label78: {
            int defaultVersion;
            label79: {
               try {
                  if (defaultConfigStream == null) {
                     plugin.getLogger().warning("Could not find default " + fileName + " in plugin resources!");
                     var13 = false;
                     break label78;
                  }

                  FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream));
                  String versionKey = getVersionKey(fileName);
                  boolean versionMismatch = false;
                  if (versionKey != null && defaultConfig.contains(versionKey)) {
                     int currentVersion = currentConfig.getInt(versionKey, 0);
                     defaultVersion = defaultConfig.getInt(versionKey);
                     if (currentVersion != defaultVersion) {
                        versionMismatch = true;
                        plugin.getLogger().info("Version mismatch detected for " + fileName + ": current=" + currentVersion + ", default=" + defaultVersion);
                     }
                  }

                  boolean updated = performComprehensiveUpdate(currentConfig, defaultConfig, fileName);
                  if (!updated && !versionMismatch) {
                     plugin.getLogger().fine(fileName + " is already up to date.");
                     defaultVersion = 0;
                     break label79;
                  }

                  if (versionMismatch) {
                     File backupFolder = new File(plugin.getDataFolder(), "backups");
                     if (!backupFolder.exists()) {
                        backupFolder.mkdirs();
                     }

                     File backupFile = new File(backupFolder, fileName + ".backup." + System.currentTimeMillis());
                     Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                     plugin.getLogger().info("Created backup before version update: " + backupFile.getName());
                  }

                  currentConfig.save(configFile);
                  plugin.getLogger().info(fileName + " has been automatically updated with new configuration options.");
                  defaultVersion = 1;
               } catch (Throwable var12) {
                  if (defaultConfigStream != null) {
                     try {
                        defaultConfigStream.close();
                     } catch (Throwable var11) {
                        var12.addSuppressed(var11);
                     }
                  }

                  throw var12;
               }

               if (defaultConfigStream != null) {
                  defaultConfigStream.close();
               }

               return defaultVersion != 0;
            }

            if (defaultConfigStream != null) {
               defaultConfigStream.close();
            }

            return defaultVersion != 0;
         }

         if (defaultConfigStream != null) {
            defaultConfigStream.close();
         }

         return var13;
      }
   }

   private static boolean performComprehensiveUpdate(FileConfiguration currentConfig, FileConfiguration defaultConfig, String fileName) {
      boolean updated = false;

      try {
         updated |= addMissingKeys(currentConfig, defaultConfig, "");
         updated |= updateVersionNumbers(currentConfig, defaultConfig, fileName);
         updated |= removeObsoleteKeys(currentConfig, defaultConfig, "");
         updated |= validateConfiguration(currentConfig, defaultConfig, fileName);
         return updated;
      } catch (Exception e) {
         throw new RuntimeException("Failed to perform comprehensive update for " + fileName + ": " + e.getMessage(), e);
      }
   }

   private static boolean validateConfiguration(FileConfiguration currentConfig, FileConfiguration defaultConfig, String fileName) {
      boolean updated = false;
      String versionKey = getVersionKey(fileName);
      if (versionKey != null && !currentConfig.contains(versionKey)) {
         currentConfig.set(versionKey, defaultConfig.getInt(versionKey, 1));
         updated = true;
      }

      if (fileName.equals("config.yml")) {
         updated |= validateConfigFile(currentConfig, defaultConfig);
      } else if (fileName.equals("messages.yml")) {
         updated |= validateMessagesFile(currentConfig, defaultConfig);
      } else if (fileName.equals("gui.yml")) {
         updated |= validateGuiFile(currentConfig, defaultConfig);
      } else if (fileName.equals("commands.yml")) {
         updated |= validateCommandsFile(currentConfig, defaultConfig);
      } else if (fileName.equals("placeholders.yml")) {
         updated |= validatePlaceholdersFile(currentConfig, defaultConfig);
      }

      return updated;
   }

   private static boolean validateConfigFile(FileConfiguration currentConfig, FileConfiguration defaultConfig) {
      boolean updated = false;
      if (!currentConfig.contains("config-version")) {
         currentConfig.set("config-version", defaultConfig.getInt("config-version", 19));
         updated = true;
      }

      if (!currentConfig.contains("storage.type")) {
         currentConfig.set("storage.type", "h2");
         updated = true;
      }

      if (!currentConfig.contains("team_chat")) {
         currentConfig.set("team_chat.character_enabled", true);
         currentConfig.set("team_chat.character", "#");
         currentConfig.set("team_chat.require_space", false);
         updated = true;
      }

      if (!currentConfig.contains("features")) {
         currentConfig.set("features.team_creation", true);
         currentConfig.set("features.team_disband", true);
         currentConfig.set("features.team_invites", true);
         currentConfig.set("features.team_home", true);
         currentConfig.set("features.team_home_set", true);
         currentConfig.set("features.team_home_teleport", true);
         currentConfig.set("features.team_warps", true);
         currentConfig.set("features.team_warp_set", true);
         currentConfig.set("features.team_warp_delete", true);
         currentConfig.set("features.team_warp_teleport", true);
         currentConfig.set("features.team_pvp", true);
         currentConfig.set("features.team_bank", true);
         currentConfig.set("features.team_enderchest", true);
         currentConfig.set("features.team_chat", true);
         currentConfig.set("features.member_leave", true);
         currentConfig.set("features.member_kick", true);
         currentConfig.set("features.member_promote", true);
         currentConfig.set("features.member_demote", true);
         currentConfig.set("features.join_requests", true);
         updated = true;
      }

      if (!currentConfig.contains("team_pvp.toggle_cooldown")) {
         currentConfig.set("team_pvp.toggle_cooldown", 300);
         updated = true;
      }

      if (!currentConfig.contains("settings.creation.tag_optional")) {
         currentConfig.set("settings.creation.tag_optional", false);
         updated = true;
      }

      if (!currentConfig.contains("settings.creation.allow_spaces_in_name")) {
         currentConfig.set("settings.creation.allow_spaces_in_name", true);
         updated = true;
      }

      if (!currentConfig.contains("team_pvp.disable_fly_on_combat")) {
         currentConfig.set("team_pvp.disable_fly_on_combat", false);
         updated = true;
      }

      String[] settingsMigrations = new String[]{"creation.require_color", "creation.default_color", "creation.use_gui_color_selection", "enable_cross_server_sync", "sync_optimization.heartbeat_interval", "sync_optimization.cross_server_sync_interval", "sync_optimization.critical_sync_interval", "performance.enable_metrics", "performance.log_slow_queries"};

      for(String subKey : settingsMigrations) {
         String wrongPath = "team_pvp." + subKey;
         String correctPath = "settings." + subKey;
         if (currentConfig.contains(wrongPath) && !currentConfig.contains(correctPath)) {
            currentConfig.set(correctPath, currentConfig.get(wrongPath));
            currentConfig.set(wrongPath, null);
            updated = true;
         }
      }

      return updated;
   }

   private static boolean validateMessagesFile(FileConfiguration currentConfig, FileConfiguration defaultConfig) {
      boolean updated = false;
      if (!currentConfig.contains("messages-version")) {
         currentConfig.set("messages-version", defaultConfig.getInt("messages-version", 5));
         updated = true;
      }

      if (!currentConfig.contains("prefix")) {
         currentConfig.set("prefix", defaultConfig.getString("prefix"));
         updated = true;
      }

      if (!currentConfig.contains("feature_disabled")) {
         currentConfig.set("feature_disabled", "<red>This feature is disabled on this server.</red>");
         updated = true;
      }

      if (!currentConfig.contains("status_enabled")) {
         currentConfig.set("status_enabled", "<green>ENABLED");
         updated = true;
      }

      if (!currentConfig.contains("status_disabled")) {
         currentConfig.set("status_disabled", "<red>DISABLED");
         updated = true;
      }

      if (!currentConfig.contains("team_glow_enabled")) {
         currentConfig.set("team_glow_enabled", "<green>Team glow has been enabled.</green>");
         updated = true;
      }

      if (!currentConfig.contains("team_glow_disabled")) {
         currentConfig.set("team_glow_disabled", "<red>Team glow has been disabled.</red>");
         updated = true;
      }

      if (!currentConfig.contains("team_tag_taken")) {
         currentConfig.set("team_tag_taken", "<red>That tag is already in use by another team. Tags must be unique.</red>");
         updated = true;
      }

      if (!currentConfig.contains("usage_admin_warps")) {
         currentConfig.set("usage_admin_warps", "<gray>Usage: /team admin warps <teamName></gray>");
         updated = true;
      }

      String[][] featureNames = new String[][]{{"team_creation", "Team Creation"}, {"sethome", "Set Home"}, {"home", "Home Teleport"}, {"setwarp", "Set Warp"}, {"warp", "Warp Teleport"}, {"rename", "Team Rename"}, {"settag", "Tag Change"}, {"setdescription", "Description Change"}, {"setcolor", "Color Change"}, {"invite", "Invite"}, {"bank_deposit", "Bank Deposit"}, {"bank_withdraw", "Bank Withdraw"}, {"enderchest", "Ender Chest"}, {"ally", "Ally Request"}};

      for(String[] entry : featureNames) {
         String path = "feature_names." + entry[0];
         if (!currentConfig.contains(path)) {
            currentConfig.set(path, entry[1]);
            updated = true;
         }
      }

      return updated;
   }

   private static boolean validateGuiFile(FileConfiguration currentConfig, FileConfiguration defaultConfig) {
      boolean updated = false;
      if (!currentConfig.contains("gui-version")) {
         currentConfig.set("gui-version", defaultConfig.getInt("gui-version", 10));
         updated = true;
      }

      if (!currentConfig.contains("team-gui.items.pvp-toggle-locked")) {
         currentConfig.set("team-gui.items.pvp-toggle-locked.material", "BARRIER");
         currentConfig.set("team-gui.items.pvp-toggle-locked.slot", 12);
         currentConfig.set("team-gui.items.pvp-toggle-locked.name", "<red>PvP Toggle (Disabled)</red>");
         currentConfig.set("team-gui.items.pvp-toggle-locked.lore", List.of("<gray>This feature has been disabled", "<gray>by the server administrator."));
         updated = true;
      }

      if (!currentConfig.contains("team-gui.items.warps-locked")) {
         currentConfig.set("team-gui.items.warps-locked.material", "BARRIER");
         currentConfig.set("team-gui.items.warps-locked.slot", 14);
         currentConfig.set("team-gui.items.warps-locked.name", "<red>Team Warps (Disabled)</red>");
         currentConfig.set("team-gui.items.warps-locked.lore", List.of("<gray>This feature has been disabled", "<gray>by the server administrator."));
         updated = true;
      }

      return updated;
   }

   private static boolean validateCommandsFile(FileConfiguration currentConfig, FileConfiguration defaultConfig) {
      boolean updated = false;
      if (!currentConfig.contains("commands-version")) {
         currentConfig.set("commands-version", defaultConfig.getInt("commands-version", 4));
         updated = true;
      }

      if (!currentConfig.contains("primary-command")) {
         currentConfig.set("primary-command", "team");
         updated = true;
      }

      if (!currentConfig.contains("command-aliases")) {
         currentConfig.set("command-aliases.guild.enabled", true);
         currentConfig.set("command-aliases.clan.enabled", true);
         currentConfig.set("command-aliases.party.enabled", false);
         currentConfig.set("command-aliases.guildmsg.enabled", true);
         currentConfig.set("command-aliases.clanmsg.enabled", true);
         currentConfig.set("command-aliases.partymsg.enabled", false);
         updated = true;
      }

      return updated;
   }

   private static boolean validatePlaceholdersFile(FileConfiguration currentConfig, FileConfiguration defaultConfig) {
      boolean updated = false;
      if (!currentConfig.contains("placeholders-version")) {
         currentConfig.set("placeholders-version", defaultConfig.getInt("placeholders-version", 3));
         updated = true;
      }

      String[] requiredSections = new String[]{"colors", "roles", "status", "sort", "date_time", "numbers", "gui", "indicators", "admin"};

      for(String section : requiredSections) {
         if (!currentConfig.contains(section)) {
            currentConfig.set(section, defaultConfig.getConfigurationSection(section));
            updated = true;
         }
      }

      return updated;
   }

   public static void migrateToPlaceholderSystem(JustTeams plugin) {
      plugin.getLogger().info("Starting migration to placeholder system...");

      try {
         migrateGuiToPlaceholders(plugin);
         updateExistingConfigurations(plugin);
         plugin.getLogger().info("Placeholder system migration completed successfully!");
      } catch (Exception e) {
         plugin.getLogger().severe("Failed to migrate to placeholder system: " + e.getMessage());
         plugin.getLogger().severe("Failed to update config: " + e.getMessage());
      }

   }

   private static void migrateGuiToPlaceholders(JustTeams plugin) {
      try {
         File guiFile = new File(plugin.getDataFolder(), "gui.yml");
         if (!guiFile.exists()) {
            plugin.getLogger().info("gui.yml not found, skipping GUI migration");
            return;
         }

         FileConfiguration guiConfig = YamlConfiguration.loadConfiguration(guiFile);
         boolean updated = false;
         updated |= migrateTeamGuiElements(guiConfig);
         updated |= migrateAdminGuiElements(guiConfig);
         updated |= migrateOtherGuiElements(guiConfig);
         if (updated) {
            guiConfig.save(guiFile);
            plugin.getLogger().info("GUI configuration migrated to use placeholder system");
         }
      } catch (Exception e) {
         plugin.getLogger().warning("Failed to migrate GUI to placeholders: " + e.getMessage());
      }

   }

   private static boolean migrateTeamGuiElements(FileConfiguration guiConfig) {
      boolean updated = false;
      ConfigurationSection teamGui = guiConfig.getConfigurationSection("team-gui");
      if (teamGui != null) {
         ConfigurationSection items = teamGui.getConfigurationSection("items");
         if (items != null) {
            ConfigurationSection sortItem = items.getConfigurationSection("sort");
            if (sortItem != null) {
               String currentName = sortItem.getString("name", "");
               if (currentName.contains("Sort by") && !currentName.contains("<placeholder:")) {
                  sortItem.set("name", "<placeholder:sort.sort_button.name>");
                  updated = true;
               }
            }
         }
      }

      return updated;
   }

   private static boolean migrateAdminGuiElements(FileConfiguration guiConfig) {
      boolean updated = false;
      if (!guiConfig.contains("admin-team-list-gui")) {
         guiConfig.set("admin-team-list-gui.title", "All Teams - Page %page%");
         guiConfig.set("admin-team-list-gui.size", 54);
         updated = true;
      }

      if (!guiConfig.contains("admin-team-manage-gui")) {
         guiConfig.set("admin-team-manage-gui.title", "Manage: %team%");
         guiConfig.set("admin-team-manage-gui.size", 27);
         updated = true;
      }

      return updated;
   }

   private static boolean migrateOtherGuiElements(FileConfiguration guiConfig) {
      boolean updated = false;
      String[] guiTypes = new String[]{"join-requests-gui", "warps-gui", "blacklist-gui", "leaderboard-view-gui", "leaderboard-category-gui"};

      for(String guiType : guiTypes) {
         if (!guiConfig.contains(guiType)) {
            guiConfig.set(guiType + ".title", "Default Title");
            guiConfig.set(guiType + ".size", 54);
            updated = true;
         }
      }

      return updated;
   }

   private static void updateExistingConfigurations(JustTeams plugin) {
      try {
         File configFile = new File(plugin.getDataFolder(), "config.yml");
         if (configFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            boolean updated = false;
            if (!config.contains("team_chat")) {
               config.set("team_chat.character_enabled", true);
               config.set("team_chat.character", "#");
               config.set("team_chat.require_space", false);
               updated = true;
            }

            if (updated) {
               config.save(configFile);
               plugin.getLogger().info("Updated config.yml with new team chat settings");
            }
         }
      } catch (Exception e) {
         plugin.getLogger().warning("Failed to update existing configurations: " + e.getMessage());
      }

   }

   private static void ensurePlaceholdersFile(JustTeams plugin) {
      try {
         File placeholdersFile = new File(plugin.getDataFolder(), "placeholders.yml");
         if (!placeholdersFile.exists()) {
            plugin.saveResource("placeholders.yml", false);
            plugin.getLogger().info("Created placeholders.yml from template");
         } else {
            FileConfiguration placeholders = YamlConfiguration.loadConfiguration(placeholdersFile);
            boolean updated = false;
            if (!placeholders.contains("placeholders-version")) {
               placeholders.set("placeholders-version", 4);
               updated = true;
            }

            if (updated) {
               placeholders.save(placeholdersFile);
               plugin.getLogger().info("Updated placeholders.yml with missing sections");
            }
         }
      } catch (Exception e) {
         plugin.getLogger().warning("Failed to ensure placeholders file: " + e.getMessage());
      }

   }

   private static boolean addMissingKeys(FileConfiguration currentConfig, FileConfiguration defaultConfig, String path) {
      boolean updated = false;

      for(String key : defaultConfig.getKeys(true)) {
         String fullPath = path.isEmpty() ? key : path + "." + key;
         if (!currentConfig.contains(key)) {
            if (!shouldSkipGuiItem(fullPath)) {
               Object defaultValue = defaultConfig.get(key);
               currentConfig.set(key, defaultValue);
               updated = true;
            }
         } else if (defaultConfig.isConfigurationSection(key) && currentConfig.isConfigurationSection(key)) {
            updated |= addMissingKeys(currentConfig.getConfigurationSection(key), defaultConfig.getConfigurationSection(key), fullPath);
         }
      }

      return updated;
   }

   private static boolean shouldSkipGuiItem(String fullPath) {
      String[] userRemovableGuiItems = new String[]{"no-team-gui.items.create-team", "team-gui.items.create-team"};

      for(String removableItem : userRemovableGuiItems) {
         if (fullPath.startsWith(removableItem)) {
            return true;
         }
      }

      return false;
   }

   private static boolean addMissingKeys(ConfigurationSection currentConfig, ConfigurationSection defaultConfig, String path) {
      boolean updated = false;

      for(String key : defaultConfig.getKeys(true)) {
         String fullPath = path.isEmpty() ? key : path + "." + key;
         if (!currentConfig.contains(key)) {
            if (!shouldSkipGuiItem(fullPath)) {
               Object defaultValue = defaultConfig.get(key);
               currentConfig.set(key, defaultValue);
               updated = true;
            }
         } else if (defaultConfig.isConfigurationSection(key) && currentConfig.isConfigurationSection(key)) {
            updated |= addMissingKeys(currentConfig.getConfigurationSection(key), defaultConfig.getConfigurationSection(key), fullPath);
         }
      }

      return updated;
   }

   private static boolean updateVersionNumbers(FileConfiguration currentConfig, FileConfiguration defaultConfig, String fileName) {
      boolean updated = false;
      String versionKey = getVersionKey(fileName);
      if (versionKey != null && defaultConfig.contains(versionKey)) {
         int currentVersion = currentConfig.getInt(versionKey, 0);
         int defaultVersion = defaultConfig.getInt(versionKey);
         if (currentVersion != defaultVersion) {
            currentConfig.set(versionKey, defaultVersion);
            updated = true;
         }
      }

      return updated;
   }

   public static String getVersionKey(String fileName) {
      String var10000;
      switch (fileName) {
         case "config.yml" -> var10000 = "config-version";
         case "gui.yml" -> var10000 = "gui-version";
         case "messages.yml" -> var10000 = "messages-version";
         case "commands.yml" -> var10000 = "commands-version";
         case "placeholders.yml" -> var10000 = "placeholders-version";
         default -> var10000 = null;
      }

      return var10000;
   }

   private static boolean removeObsoleteKeys(FileConfiguration currentConfig, FileConfiguration defaultConfig, String path) {
      boolean updated = false;
      Set<String> currentKeys = currentConfig.getKeys(true);
      Set<String> defaultKeys = defaultConfig.getKeys(true);

      for(String key : currentKeys) {
         if (!defaultKeys.contains(key) && !isUserCustomizedValue(currentConfig, key)) {
            currentConfig.set(key, null);
            updated = true;
         }
      }

      return updated;
   }

   private static boolean isUserCustomizedValue(FileConfiguration config, String key) {
      String lowerKey = key.toLowerCase();
      return lowerKey.contains("server-identifier") || lowerKey.contains("username") || lowerKey.contains("password") || lowerKey.contains("host") || lowerKey.contains("database") || lowerKey.contains("custom") || lowerKey.contains("user") || lowerKey.contains("personal");
   }

   public static void forceUpdateConfig(JustTeams plugin, String fileName) {
      try {
         File configFile = new File(plugin.getDataFolder(), fileName);
         File backupFolder = new File(plugin.getDataFolder(), "backups");
         if (!backupFolder.exists()) {
            backupFolder.mkdirs();
         }

         if (configFile.exists()) {
            File backupFile = new File(backupFolder, fileName + ".backup." + System.currentTimeMillis());
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Created backup: " + backupFile.getName());
            configFile.delete();
            plugin.getLogger().info("Deleted existing " + fileName + " for forced update.");
         }

         updateConfig(plugin, fileName);
         cleanupOldBackups(plugin, backupFolder, fileName);
      } catch (Exception e) {
         plugin.getLogger().log(Level.SEVERE, "Failed to force update " + fileName + ": " + e.getMessage(), e);
      }

   }

   private static void createBackupAndForceUpdate(JustTeams plugin, String fileName) throws IOException {
      File configFile = new File(plugin.getDataFolder(), fileName);
      File backupFolder = new File(plugin.getDataFolder(), "backups");
      if (!backupFolder.exists()) {
         backupFolder.mkdirs();
      }

      File backupFile = new File(backupFolder, fileName + ".backup." + System.currentTimeMillis());
      if (configFile.exists()) {
         Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
         plugin.getLogger().info("Created backup: " + backupFile.getName());
         configFile.delete();
         plugin.getLogger().info("Deleted corrupted " + fileName + " for recovery.");
      }

      plugin.saveResource(fileName, false);
      plugin.getLogger().info("Recovered " + fileName + " from template.");
      cleanupOldBackups(plugin, backupFolder, fileName);
   }

   public static boolean needsUpdate(JustTeams plugin, String fileName) {
      try {
         File configFile = new File(plugin.getDataFolder(), fileName);
         if (!configFile.exists()) {
            return true;
         }

         FileConfiguration currentConfig;
         try {
            currentConfig = YamlConfiguration.loadConfiguration(configFile);
         } catch (Exception var12) {
            plugin.getLogger().warning("YAML syntax error in " + fileName + ", restoring from default...");
            try {
               plugin.saveResource(fileName, true);
               plugin.getLogger().info("Restored " + fileName + " from bundled default");
               currentConfig = YamlConfiguration.loadConfiguration(configFile);
            } catch (Exception restoreEx) {
               plugin.getLogger().severe("Could not restore " + fileName + ": " + restoreEx.getMessage());
               return true;
            }
         }

         try (InputStream defaultConfigStream = plugin.getResource(fileName)) {
            if (defaultConfigStream == null) {
               return false;
            }

            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8));
            for (String key : defaultConfig.getKeys(true)) {
               if (!currentConfig.contains(key)) {
                  plugin.getLogger().info("Missing key detected in " + fileName + ": " + key);
                  return true;
               }
            }

            String versionKey = getVersionKey(fileName);
            if (versionKey != null && defaultConfig.contains(versionKey)) {
               int currentVersion = currentConfig.getInt(versionKey, 0);
               int defaultVersion = defaultConfig.getInt(versionKey);
               if (currentVersion != defaultVersion) {
                  plugin.getLogger().info("Version mismatch in " + fileName + ": current=" + currentVersion + ", default=" + defaultVersion);
                  return true;
               }
            }
         }
         return false;
      } catch (Exception e) {
         plugin.getLogger().log(Level.WARNING, "Error checking if " + fileName + " needs update: " + e.getMessage());
         return false;
      }
   }

   public static List<String> getConfigsNeedingUpdate(JustTeams plugin) {
      List<String> needsUpdate = new ArrayList();

      for(String fileName : CONFIG_FILES) {
         if (needsUpdate(plugin, fileName)) {
            needsUpdate.add(fileName);
         }
      }

      return needsUpdate;
   }

   public static void testConfigurationSystem(JustTeams plugin) {
      plugin.getLogger().info("Testing configuration system...");

      for(String fileName : CONFIG_FILES) {
         try {
            boolean needsUpdate = needsUpdate(plugin, fileName);
            plugin.getLogger().info("Config " + fileName + " needs update: " + needsUpdate);
            File configFile = new File(plugin.getDataFolder(), fileName);
            if (configFile.exists()) {
               FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
               String versionKey = getVersionKey(fileName);
               if (versionKey != null && config.contains(versionKey)) {
                  int version = config.getInt(versionKey);
                  plugin.getLogger().info("Config " + fileName + " current version: " + version);
               }
            }
         } catch (Exception e) {
            plugin.getLogger().warning("Error testing " + fileName + ": " + e.getMessage());
         }
      }

      plugin.getLogger().info("Configuration system test completed!");
   }

   public static void forceUpdateAllConfigs(JustTeams plugin) {
      plugin.getLogger().info("=== FORCE UPDATING ALL CONFIGURATION FILES ===");
      int successCount = 0;
      int failCount = 0;

      for(String fileName : CONFIG_FILES) {
         try {
            plugin.getLogger().info("Force updating " + fileName + "...");
            boolean updated = updateConfig(plugin, fileName);
            if (updated) {
               ++successCount;
               plugin.getLogger().info("Successfully force updated " + fileName);
            } else {
               plugin.getLogger().warning("Failed to force update " + fileName);
               ++failCount;
            }
         } catch (Exception e) {
            ++failCount;
            plugin.getLogger().log(Level.SEVERE, "Failed to force update " + fileName + ": " + e.getMessage(), e);
         }
      }

      plugin.getLogger().info("Force update completed! Success: " + successCount + ", Failed: " + failCount);
   }

   public static boolean isConfigurationSystemHealthy(JustTeams plugin) {
      try {
         for(String fileName : CONFIG_FILES) {
            File configFile = new File(plugin.getDataFolder(), fileName);
            if (!configFile.exists()) {
               plugin.getLogger().warning("Missing configuration file: " + fileName);
               return false;
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String versionKey = getVersionKey(fileName);
            if (versionKey != null && !config.contains(versionKey)) {
               plugin.getLogger().warning("Configuration file " + fileName + " missing version key: " + versionKey);
               return false;
            }
         }

         return true;
      } catch (Exception e) {
         plugin.getLogger().severe("Configuration system health check failed: " + e.getMessage());
         return false;
      }
   }

   private static void cleanupOldBackups(JustTeams plugin, File backupFolder, String fileName) {
      try {
         if (!backupFolder.exists()) {
            return;
         }

         File[] backupFiles = backupFolder.listFiles((dir, name) -> name.startsWith(fileName + ".backup.") && name.endsWith(".yml"));
         if (backupFiles == null || backupFiles.length <= 5) {
            return;
         }

         Arrays.sort(backupFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

         for(int i = 5; i < backupFiles.length; ++i) {
            if (backupFiles[i].delete()) {
               plugin.getLogger().info("Cleaned up old backup: " + backupFiles[i].getName());
            }
         }
      } catch (Exception e) {
         plugin.getLogger().warning("Error cleaning up old backups for " + fileName + ": " + e.getMessage());
      }

   }

   public static void cleanupAllOldBackups(JustTeams plugin) {
      try {
         File backupFolder = new File(plugin.getDataFolder(), "backups");
         if (!backupFolder.exists()) {
            return;
         }

         plugin.getLogger().info("Cleaning up old backup files...");

         for(String fileName : CONFIG_FILES) {
            cleanupOldBackups(plugin, backupFolder, fileName);
         }

         long sevenDaysAgo = System.currentTimeMillis() - 604800000L;
         File[] allBackupFiles = backupFolder.listFiles((dir, name) -> {
            if (!name.contains(".backup.")) {
               return false;
            } else {
               try {
                  String timestampStr = name.substring(name.lastIndexOf(".backup.") + 8);
                  long timestamp = Long.parseLong(timestampStr);
                  return timestamp < sevenDaysAgo;
               } catch (NumberFormatException var7) {
                  return false;
               }
            }
         });
         if (allBackupFiles != null) {
            for(File oldBackup : allBackupFiles) {
               if (oldBackup.delete()) {
                  plugin.getLogger().info("Cleaned up old backup: " + oldBackup.getName());
               }
            }
         }

         plugin.getLogger().info("Backup cleanup completed!");
      } catch (Exception e) {
         plugin.getLogger().warning("Error during backup cleanup: " + e.getMessage());
      }

   }

   public static int getBackupCount(JustTeams plugin, String fileName) {
      try {
         File backupFolder = new File(plugin.getDataFolder(), "backups");
         if (!backupFolder.exists()) {
            return 0;
         } else {
            File[] backupFiles = backupFolder.listFiles((dir, name) -> name.startsWith(fileName + ".backup.") && name.endsWith(".yml"));
            return backupFiles != null ? backupFiles.length : 0;
         }
      } catch (Exception e) {
         plugin.getLogger().warning("Error counting backups for " + fileName + ": " + e.getMessage());
         return 0;
      }
   }

   private static boolean repairYamlFile(File configFile) {
      try {
         String content = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
         content = content.replaceAll("\"<gradient:\\s*$", "\"<gradient:#4C9DDE:#4C96D2>JustTeams</gradient>\"");
         content = content.replaceAll("\"<gradient:\\s*\\n", "\"<gradient:#4C9DDE:#4C96D2>JustTeams</gradient>\"\\n");
         content = content.replaceAll("team_chat_password_warning: \"<red>Warning: Please do not shar\\s*$", "team_chat_password_warning: \"<red>Warning: Please do not share your team password with anyone!</red>\"");
         content = content.replaceAll("online-name-format: \"<gradient:\\s*$", "online-name-format: \"<gradient:#4C9DDE:#4C96D2><player></gradient>\"");
         content = content.replaceAll("offline-name-format: \"<gray><status_indicator><role_ic\\s*$", "offline-name-format: \"<gray><status_indicator><role_icon> <player></gray>\"");
         content = content.replaceAll(": \"[^\"]*$", ": \"Fixed incomplete string\"");
         Files.write(configFile.toPath(), content.getBytes(StandardCharsets.UTF_8), new OpenOption[0]);

         try {
            YamlConfiguration.loadConfiguration(configFile);
            return true;
         } catch (Exception var3) {
            return false;
         }
      } catch (Exception var4) {
         return false;
      }
   }

   public static void createConfigBackup(JustTeams plugin, String fileName) {
      try {
         File configFile = new File(plugin.getDataFolder(), fileName);
         File backupFolder = new File(plugin.getDataFolder(), "backups");
         if (!backupFolder.exists()) {
            backupFolder.mkdirs();
         }

         if (configFile.exists()) {
            File backupFile = new File(backupFolder, fileName + ".backup." + System.currentTimeMillis());
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Created manual backup: " + backupFile.getName());
            cleanupOldBackups(plugin, backupFolder, fileName);
         } else {
            plugin.getLogger().warning("Cannot backup " + fileName + " - file does not exist");
         }
      } catch (Exception e) {
         plugin.getLogger().severe("Failed to create backup for " + fileName + ": " + e.getMessage());
      }

   }

   public static void performIntelligentUpdate(JustTeams plugin) {
      plugin.getLogger().info("Starting intelligent configuration update system...");
      LocalDateTime updateTime = LocalDateTime.now();
      String timestamp = updateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
      IntelligentConfigHelper.createUpdateSnapshot(plugin, timestamp);
      restoreCreateTeamSection(plugin);
      int successCount = 0;
      int failCount = 0;

      for(String fileName : CONFIG_FILES) {
         try {
            if (IntelligentConfigHelper.performIntelligentFileUpdate(plugin, fileName, timestamp)) {
               ++successCount;
               plugin.getLogger().info("Successfully performed intelligent update on " + fileName);
            } else {
               plugin.getLogger().info(fileName + " was already up to date");
            }
         } catch (Exception e) {
            ++failCount;
            plugin.getLogger().log(Level.SEVERE, "Failed intelligent update for " + fileName + ": " + e.getMessage(), e);

            try {
               IntelligentConfigHelper.performEmergencyRecovery(plugin, fileName);
               ++successCount;
            } catch (Exception recoveryError) {
               plugin.getLogger().log(Level.SEVERE, "Emergency recovery failed for " + fileName, recoveryError);
            }
         }
      }

      IntelligentConfigHelper.generateUpdateReport(plugin, successCount, failCount, timestamp);
      plugin.getLogger().info("Intelligent update system completed! Success: " + successCount + ", Failed: " + failCount);
   }

   public static void performConfigHealthCheck(JustTeams plugin) {
      plugin.getLogger().info("Performing configuration health check...");
      boolean allHealthy = true;
      List<String> issues = new ArrayList();

      for(String fileName : CONFIG_FILES) {
         File configFile = new File(plugin.getDataFolder(), fileName);
         if (!configFile.exists()) {
            issues.add(fileName + ": File missing");
            allHealthy = false;
         } else {
            try {
               FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
               String versionKey = getVersionKey(fileName);
               if (versionKey != null && !config.contains(versionKey)) {
                  issues.add(fileName + ": Missing version key");
                  allHealthy = false;
               }

               if (IntelligentConfigHelper.hasCorruptedValues(config, fileName)) {
                  issues.add(fileName + ": Contains corrupted values");
                  allHealthy = false;
               }
            } catch (Exception e) {
               issues.add(fileName + ": YAML syntax error - " + e.getMessage());
               allHealthy = false;
            }
         }
      }

      if (allHealthy) {
         plugin.getLogger().info("✓ All configuration files are healthy");
      } else {
         plugin.getLogger().warning("✗ Configuration health check found issues:");

         for(String issue : issues) {
            plugin.getLogger().warning("  - " + issue);
         }

         plugin.getLogger().info("Running intelligent auto-repair...");
         performIntelligentUpdate(plugin);
      }

   }

   private static void restoreCreateTeamSection(JustTeams plugin) {
      try {
         File guiFile = new File(plugin.getDataFolder(), "gui.yml");
         if (!guiFile.exists()) {
            return;
         }

         FileConfiguration guiConfig = YamlConfiguration.loadConfiguration(guiFile);
         ConfigurationSection noTeamGui = guiConfig.getConfigurationSection("no-team-gui");
         if (noTeamGui == null) {
            plugin.getLogger().info("no-team-gui section missing, restoring...");
            return;
         }

         ConfigurationSection items = noTeamGui.getConfigurationSection("items");
         if (items == null) {
            plugin.getLogger().info("no-team-gui.items section missing, restoring...");
            return;
         }

         if (!items.contains("create-team")) {
            plugin.getLogger().info("create-team item missing, restoring...");
            items.set("create-team.slot", 12);
            items.set("create-team.material", "WRITABLE_BOOK");
            items.set("create-team.name", "<gradient:#4C9DDE:#4C96D2><bold>ᴄʀᴇᴀᴛᴇ ᴀ ᴛᴇᴀᴍ</bold></gradient>");
            items.set("create-team.lore", List.of("<gray>Start your own team and invite your friends!</gray>", "", "<yellow>Click to begin the creation process.</yellow>"));
            guiConfig.save(guiFile);
            plugin.getLogger().info("Successfully restored create-team section");
         }

         if (!items.contains("leaderboards")) {
            plugin.getLogger().info("leaderboards item missing, restoring...");
            items.set("leaderboards.slot", 14);
            items.set("leaderboards.material", "EMERALD");
            items.set("leaderboards.name", "<gradient:#4C9DDE:#4C96D2><bold>ᴠɪᴇᴡ ʟᴇᴀᴅᴇʀʙᴏᴀʀᴅs</bold></gradient>");
            items.set("leaderboards.lore", List.of("<gray>See the top teams on the server.</gray>", "", "<yellow>Click to view leaderboards.</yellow>"));
            guiConfig.save(guiFile);
            plugin.getLogger().info("Successfully restored leaderboards section");
         }
      } catch (Exception e) {
         plugin.getLogger().warning("Failed to restore create-team section: " + e.getMessage());
      }

   }
}
