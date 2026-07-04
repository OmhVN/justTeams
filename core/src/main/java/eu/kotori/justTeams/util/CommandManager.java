package eu.kotori.justTeams.core.util;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.commands.TeamCommand;
import eu.kotori.justTeams.commands.TeamMessageCommand;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public class CommandManager {
   private final JustTeams plugin;
   private File commandsFile;
   private FileConfiguration commandsConfig;

   public CommandManager(JustTeams plugin) {
      this.plugin = plugin;
      this.createCommandsConfig();
   }

   private void createCommandsConfig() {
      this.commandsFile = new File(this.plugin.getDataFolder(), "commands.yml");
      if (!this.commandsFile.exists()) {
         this.commandsFile.getParentFile().mkdirs();
         this.plugin.saveResource("commands.yml", false);
      }

      this.commandsConfig = YamlConfiguration.loadConfiguration(this.commandsFile);
   }

   public void reload() {
      this.createCommandsConfig();
   }

   public void registerCommands() {
      try {
         String primaryCommand = this.getPrimaryCommand();
         List<String> aliases = this.getCommandAliases();
         this.registerCommand(primaryCommand, aliases, new TeamCommand(this.plugin), new TeamCommand(this.plugin));
         this.registerCommand("teammsg", List.of("tm", "tmsg"), new TeamMessageCommand(this.plugin), (TabCompleter)null);
         this.plugin.getLogger().info("CommandManager: All commands registered successfully");
      } catch (Exception e) {
         this.plugin.getLogger().log(Level.SEVERE, "Failed to register commands.", e);
      }

   }

   private void registerCommand(String name, List<String> aliases, CommandExecutor executor, TabCompleter completer) {
      try {
         Field serverCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
         serverCommandMap.setAccessible(true);
         CommandMap commandMap = (CommandMap)serverCommandMap.get(Bukkit.getServer());
         Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
         constructor.setAccessible(true);
         PluginCommand pluginCommand = (PluginCommand)constructor.newInstance(name, this.plugin);
         pluginCommand.setAliases(aliases);
         pluginCommand.setExecutor(executor);
         if (completer != null) {
            pluginCommand.setTabCompleter(completer);
         }

         commandMap.register(this.plugin.getName(), pluginCommand);
      } catch (Exception e) {
         this.plugin.getLogger().log(Level.SEVERE, "Failed to dynamically register command: " + name, e);
      }

   }

   public String getPrimaryCommand() {
      return this.commandsConfig.getString("primary-command", "team");
   }

   public List<String> getCommandAliases() {
      List<String> aliases = this.commandsConfig.getStringList("aliases");
      if (!aliases.isEmpty()) {
         return aliases;
      } else {
         ConfigurationSection section = this.commandsConfig.getConfigurationSection("command-aliases");
         if (section == null) {
            return List.of();
         } else {
            List<String> result = new ArrayList();

            for(String key : section.getKeys(false)) {
               if (section.getBoolean(key + ".enabled", true) && !key.endsWith("msg")) {
                  result.add(key);
               }
            }

            return result;
         }
      }
   }

   public boolean isCommandEnabled(String commandName) {
      return this.commandsConfig.getBoolean("commands." + commandName + ".enabled", true);
   }

   public String getCommandPermission(String commandName) {
      return this.commandsConfig.getString("commands." + commandName + ".permission", "justteams." + commandName);
   }

   public String getCommandDescription(String commandName) {
      return this.commandsConfig.getString("commands." + commandName + ".description", "No description available");
   }

   public String getCommandUsage(String commandName) {
      return this.commandsConfig.getString("commands." + commandName + ".usage", "/" + commandName);
   }

   public List<String> getCommandAliases(String commandName) {
      return this.commandsConfig.getStringList("commands." + commandName + ".aliases");
   }

   public boolean isTeamCreateEnabled() {
      return this.isCommandEnabled("create");
   }

   public boolean isTeamDisbandEnabled() {
      return this.isCommandEnabled("disband");
   }

   public boolean isTeamInviteEnabled() {
      return this.isCommandEnabled("invite");
   }

   public boolean isTeamKickEnabled() {
      return this.isCommandEnabled("kick");
   }

   public boolean isTeamLeaveEnabled() {
      return this.isCommandEnabled("leave");
   }

   public boolean isTeamPromoteEnabled() {
      return this.isCommandEnabled("promote");
   }

   public boolean isTeamDemoteEnabled() {
      return this.isCommandEnabled("demote");
   }

   public boolean isTeamInfoEnabled() {
      return this.isCommandEnabled("info");
   }

   public boolean isTeamSethomeEnabled() {
      return this.isCommandEnabled("sethome");
   }

   public boolean isTeamHomeEnabled() {
      return this.isCommandEnabled("home");
   }

   public boolean isTeamSettagEnabled() {
      return this.isCommandEnabled("settag");
   }

   public boolean isTeamSetdescriptionEnabled() {
      return this.isCommandEnabled("setdescription");
   }

   public boolean isTeamTransferEnabled() {
      return this.isCommandEnabled("transfer");
   }

   public boolean isTeamPvpEnabled() {
      return this.isCommandEnabled("pvp");
   }

   public boolean isTeamBankEnabled() {
      return this.isCommandEnabled("bank");
   }

   public boolean isTeamEnderchestEnabled() {
      return this.isCommandEnabled("enderchest");
   }

   public boolean isTeamTopEnabled() {
      return this.isCommandEnabled("top");
   }

   public boolean isTeamJoinEnabled() {
      return this.isCommandEnabled("join");
   }

   public boolean isTeamUnjoinEnabled() {
      return this.isCommandEnabled("unjoin");
   }

   public boolean isTeamPublicEnabled() {
      return this.isCommandEnabled("public");
   }

   public boolean isTeamRequestsEnabled() {
      return this.isCommandEnabled("requests");
   }

   public boolean isTeamSetwarpEnabled() {
      return this.isCommandEnabled("setwarp");
   }

   public boolean isTeamDelwarpEnabled() {
      return this.isCommandEnabled("delwarp");
   }

   public boolean isTeamWarpEnabled() {
      return this.isCommandEnabled("warp");
   }

   public boolean isTeamWarpsEnabled() {
      return this.isCommandEnabled("warps");
   }

   public boolean isTeamAdminEnabled() {
      return this.isCommandEnabled("admin");
   }

   public int getCommandCooldown(String commandName) {
      return this.commandsConfig.getInt("commands." + commandName + ".cooldown", 0);
   }

   public boolean isCommandCooldownEnabled(String commandName) {
      return this.getCommandCooldown(commandName) > 0;
   }

   public int getMaxCommandUses(String commandName) {
      return this.commandsConfig.getInt("commands." + commandName + ".max_uses", -1);
   }

   public boolean isCommandUsageLimited(String commandName) {
      return this.getMaxCommandUses(commandName) > 0;
   }

   public List<String> getCommandCategories() {
      return this.commandsConfig.getStringList("categories");
   }

   public List<String> getCommandsInCategory(String category) {
      return this.commandsConfig.getStringList("categories." + category + ".commands");
   }

   public String getCategoryDescription(String category) {
      return this.commandsConfig.getString("categories." + category + ".description", "No description available");
   }

   public String getCategoryPermission(String category) {
      return this.commandsConfig.getString("categories." + category + ".permission", "justteams.category." + category);
   }

   public String getString(String path, String defaultValue) {
      return this.commandsConfig.getString(path, defaultValue);
   }

   public int getInt(String path, int defaultValue) {
      return this.commandsConfig.getInt(path, defaultValue);
   }

   public boolean getBoolean(String path, boolean defaultValue) {
      return this.commandsConfig.getBoolean(path, defaultValue);
   }

   public List<String> getStringList(String path) {
      return this.commandsConfig.getStringList(path);
   }

   public ConfigurationSection getConfigurationSection(String path) {
      return this.commandsConfig.getConfigurationSection(path);
   }

   public boolean hasCommand(String commandName) {
      return this.commandsConfig.contains("commands." + commandName);
   }

   public boolean hasCategory(String categoryName) {
      return this.commandsConfig.contains("categories." + categoryName);
   }

   public Set<String> getCommandNames() {
      Set<String> commands = new HashSet();
      ConfigurationSection commandsSection = this.commandsConfig.getConfigurationSection("commands");
      if (commandsSection != null) {
         commands.addAll(commandsSection.getKeys(false));
      }

      return commands;
   }

   public Set<String> getConfigurationKeys() {
      return this.commandsConfig.getKeys(true);
   }

   public String getCommandHelp(String commandName) {
      if (!this.hasCommand(commandName)) {
         return "Command not found: " + commandName;
      } else {
         StringBuilder help = new StringBuilder();
         help.append("§6=== ").append(commandName.toUpperCase()).append(" ===\n");
         help.append("§7Description: §f").append(this.getCommandDescription(commandName)).append("\n");
         help.append("§7Usage: §f").append(this.getCommandUsage(commandName)).append("\n");
         help.append("§7Permission: §f").append(this.getCommandPermission(commandName)).append("\n");
         if (this.isCommandCooldownEnabled(commandName)) {
            help.append("§7Cooldown: §f").append(this.getCommandCooldown(commandName)).append(" seconds\n");
         }

         if (this.isCommandUsageLimited(commandName)) {
            help.append("§7Max Uses: §f").append(this.getMaxCommandUses(commandName)).append("\n");
         }

         List<String> aliases = this.getCommandAliases(commandName);
         if (!aliases.isEmpty()) {
            help.append("§7Aliases: §f").append(String.join(", ", aliases));
         }

         return help.toString();
      }
   }
}
