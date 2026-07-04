package eu.kotori.justTeams.commands;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.gui.BlacklistGUI;
import eu.kotori.justTeams.gui.InvitesGUI;
import eu.kotori.justTeams.gui.JoinRequestGUI;
import eu.kotori.justTeams.gui.LeaderboardCategoryGUI;
import eu.kotori.justTeams.gui.NoTeamGUI;
import eu.kotori.justTeams.gui.TeamGUI;
import eu.kotori.justTeams.gui.admin.AdminGUI;
import eu.kotori.justTeams.gui.sub.TeamSettingsGUI;
import eu.kotori.justTeams.core.quests.QuestGUI;
import eu.kotori.justTeams.core.quests.QuestManager;
import eu.kotori.justTeams.core.storage.DatabaseFileManager;
import eu.kotori.justTeams.core.storage.DatabaseMigrationManager;
import eu.kotori.justTeams.core.storage.DatabaseStorage;
import eu.kotori.justTeams.core.storage.IDataStorage;
import eu.kotori.justTeams.core.team.BlacklistedPlayer;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.team.TeamManager;
import eu.kotori.justTeams.core.team.TeamPlayer;
import eu.kotori.justTeams.core.util.ConfigUpdater;
import eu.kotori.justTeams.core.util.EffectsUtil;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

public class TeamCommand implements CommandExecutor, TabCompleter {
   private final JustTeams plugin;
   private final TeamManager teamManager;
   private final ConcurrentHashMap<UUID, Long> commandCooldowns = new ConcurrentHashMap();
   private final ConcurrentHashMap<UUID, Integer> commandCounts = new ConcurrentHashMap();
   private static final long COMMAND_COOLDOWN = 1000L;
   private static final int MAX_COMMANDS_PER_MINUTE = 30;
   private static final long COMMAND_RESET_INTERVAL = 60000L;

   public TeamCommand(JustTeams plugin) {
      this.plugin = plugin;
      this.teamManager = plugin.getTeamManager();
      plugin.getTaskRunner().runTimer(() -> {
         this.commandCounts.clear();
         long now = System.currentTimeMillis();
         this.commandCooldowns.values().removeIf((t) -> now - t > 1000L);
      }, 1200L, 1200L);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
         this.handleReload(sender);
         return true;
      } else if (sender instanceof Player) {
         Player player = (Player)sender;
         if (!this.checkCommandSpam(player)) {
            this.plugin.getMessageManager().sendMessage(player, "command_spam_protection");
            return true;
         } else if (args.length == 0) {
            this.handleGUI(player);
            return true;
         } else {
            switch (args[0].toLowerCase()) {
               case "create":
                  this.handleCreate(player, args);
                  break;
               case "disband":
                  this.handleDisband(player);
                  break;
               case "invite":
                  this.handleInvite(player, args);
                  break;
               case "invites":
                  this.handleInvites(player);
                  break;
               case "accept":
                  this.handleAccept(player, args);
                  break;
               case "deny":
                  this.handleDeny(player, args);
                  break;
               case "join":
                  this.handleJoin(player, args);
                  break;
               case "unjoin":
                  this.handleUnjoin(player, args);
                  break;
               case "kick":
                  this.handleKick(player, args);
                  break;
               case "leave":
                  this.handleLeave(player);
                  break;
               case "promote":
                  this.handlePromote(player, args);
                  break;
               case "demote":
                  this.handleDemote(player, args);
                  break;
               case "info":
                  this.handleInfo(player, args);
                  break;
               case "sethome":
                  this.handleSetHome(player);
                  break;
               case "delhome":
                  this.handleDelHome(player);
                  break;
               case "home":
                  this.handleHome(player);
                  break;
               case "settag":
                  this.handleSetTag(player, args);
                  break;
               case "setcolor":
                  this.handleSetColor(player, args);
                  break;
               case "setdesc":
                  this.handleSetDescription(player, args);
                  break;
               case "setjoinfee":
                  this.handleSetJoinFee(player, args);
                  break;
               case "rename":
                  this.handleRename(player, args);
                  break;
               case "transfer":
                  this.handleTransfer(player, args);
                  break;
               case "pvp":
                  this.handlePvpToggle(player);
                  break;
               case "bank":
                  this.handleBank(player, args);
                  break;
               case "enderchest":
               case "ec":
                  this.handleEnderChest(player);
                  break;
               case "public":
                  this.handlePublicToggle(player);
                  break;
               case "glow":
                  this.handleToggleGlow(player);
                  break;
               case "requests":
                  this.handleRequests(player);
                  break;
               case "setwarp":
                  this.handleSetWarp(player, args);
                  break;
               case "delwarp":
                  this.handleDelWarp(player, args);
                  break;
               case "warp":
                  this.handleWarp(player, args);
                  break;
               case "warps":
                  this.handleWarps(player);
                  break;
               case "ally":
                  this.handleAlly(player, args);
                  break;
               case "blacklist":
                  this.handleBlacklist(player, args);
                  break;
               case "unblacklist":
                  this.handleUnblacklist(player, args);
                  break;
               case "settings":
                  this.handleSettings(player);
                  break;
               case "top":
                  this.handleTop(player, args);
                  break;
               case "admin":
                  this.handleAdmin(player, args);
                  break;
               case "serveralias":
                  this.handleServerAlias(player, args);
                  break;
               case "platform":
                  this.handlePlatform(player);
                  break;
               case "quests":
               case "quest":
                  this.handleQuests(player);
                  break;
               case "points":
                  this.handlePoints(player);
                  break;
               case "help":
                  this.handleHelp(player);
                  break;
               case "chat":
                  this.handleChat(player);
                  break;
               case "chatspy":
               case "spy":
                  this.handleChatSpy(player);
                  break;
               case "debug-permissions":
                  if (!this.hasAdminPermission(player)) {
                     return false;
                  }

                  this.plugin.getTaskRunner().runAsync(() -> {
                     try {
                        Logger var10000 = this.plugin.getLogger();
                        String var10001 = this.teamManager.getPlayerTeam(player.getUniqueId()).getName();
                        var10000.info("=== DEBUG: Team " + var10001 + " Permissions ===");

                        for(TeamPlayer member : this.teamManager.getPlayerTeam(player.getUniqueId()).getMembers()) {
                           var10000 = this.plugin.getLogger();
                           var10001 = String.valueOf(member.getPlayerUuid());
                           var10000.info("Member: " + var10001 + " - Role: " + String.valueOf(member.getRole()) + " - canUseEnderChest: " + member.canUseEnderChest() + " - canWithdraw: " + member.canWithdraw() + " - canSetHome: " + member.canSetHome() + " - canUseHome: " + member.canUseHome());
                        }

                        this.plugin.getLogger().info("=== END DEBUG ===");
                        this.plugin.getTaskRunner().runOnEntity(player, () -> this.plugin.getMessageManager().sendRawMessage(player, "<green>Team permissions debug info sent to console. Check server logs."));
                     } catch (Exception e) {
                        this.plugin.getLogger().severe("Error in debug-permissions command: " + e.getMessage());
                        this.plugin.getTaskRunner().runOnEntity(player, () -> this.plugin.getMessageManager().sendRawMessage(player, "<red>Error occurred while checking permissions. Check server logs."));
                     }

                  });
                  return true;
               case "debug-placeholders":
                  if (!this.hasAdminPermission(player)) {
                     return false;
                  }

                  this.plugin.getTaskRunner().runAsync(() -> {
                     try {
                        this.plugin.getLogger().info("=== DEBUG: PlaceholderAPI Test for " + player.getName() + " ===");
                        if (this.plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
                           this.plugin.getLogger().warning("PlaceholderAPI is not installed!");
                           this.plugin.getTaskRunner().runOnEntity(player, () -> this.plugin.getMessageManager().sendRawMessage(player, "<red>PlaceholderAPI is not installed!"));
                           return;
                        }

                        String[] placeholders = new String[]{"justteams_has_team", "justteams_name", "justteams_tag", "justteams_description", "justteams_owner", "justteams_role", "justteams_member_count", "justteams_max_members", "justteams_members_online", "justteams_kills", "justteams_deaths", "justteams_kdr", "justteams_bank_balance", "justteams_is_owner", "justteams_is_co_owner", "justteams_is_member"};

                        for(String placeholder : placeholders) {
                           String result = PlaceholderAPI.setPlaceholders(player, "%" + placeholder + "%");
                           this.plugin.getLogger().info(placeholder + ": " + result);
                        }

                        this.plugin.getLogger().info("=== END PLACEHOLDER DEBUG ===");
                        this.plugin.getTaskRunner().runOnEntity(player, () -> this.plugin.getMessageManager().sendRawMessage(player, "<green>PlaceholderAPI test completed. Check server logs for results."));
                     } catch (Exception e) {
                        this.plugin.getLogger().severe("Error in debug-placeholders command: " + e.getMessage());
                        this.plugin.getTaskRunner().runOnEntity(player, () -> this.plugin.getMessageManager().sendRawMessage(player, "<red>Error occurred while testing placeholders. Check server logs."));
                     }

                  });
                  return true;
               case "debug-conflict":
                  if (!this.hasAdminPermission(player)) {
                     return false;
                  }

                  if (args.length < 2) {
                     this.plugin.getMessageManager().sendMessage(player, "usage_debug_conflict");
                     return true;
                  }

                  String targetName = args[1];
                  Player target = Bukkit.getPlayer(targetName);
                  if (target == null) {
                     this.plugin.getMessageManager().sendRawMessage(player, "<red>Player not found.");
                     return true;
                  }

                  Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                  org.bukkit.scoreboard.Team sbTeam = mainScoreboard.getEntryTeam(target.getName());
                  this.plugin.getMessageManager().sendRawMessage(player, "<gold>=== Debug Conflict: " + target.getName() + " ===");
                  if (sbTeam == null) {
                     this.plugin.getMessageManager().sendRawMessage(player, "<yellow>Vanilla Scoreboard Team: <red>NONE");
                     this.plugin.getMessageManager().sendRawMessage(player, "<gray>If they are White, it might be client-side defaults or a packet-only plugin (like TAB) sending teams without registering them in Bukkit API.");
                  } else {
                     this.plugin.getMessageManager().sendRawMessage(player, "<yellow>Vailla Scoreboard Team: <green>" + sbTeam.getName());
                     this.plugin.getMessageManager().sendRawMessage(player, "<yellow>Team Color: <green>" + sbTeam.getColor().name());
                     this.plugin.getMessageManager().sendRawMessage(player, "<yellow>Display Name: <green>" + sbTeam.getDisplayName());
                     this.plugin.getMessageManager().sendRawMessage(player, "<yellow>Prefix: <green>" + sbTeam.getPrefix());
                     this.plugin.getMessageManager().sendRawMessage(player, "<yellow>Suffix: <green>" + sbTeam.getSuffix());
                  }

                  this.plugin.getMessageManager().sendRawMessage(player, "<gold>==============================");
                  return true;
               default:
                  this.plugin.getMessageManager().sendMessage(player, "unknown_command");
                  return false;
            }

            return true;
         }
      } else {
         this.plugin.getMessageManager().sendMessage(sender, "player_only");
         return true;
      }
   }

   private boolean checkCommandSpam(Player player) {
      long currentTime = System.currentTimeMillis();
      UUID playerId = player.getUniqueId();
      Long lastCommand = (Long)this.commandCooldowns.get(playerId);
      if (lastCommand != null && currentTime - lastCommand < 1000L) {
         return false;
      } else {
         int count = (Integer)this.commandCounts.getOrDefault(playerId, 0);
         if (count >= 30) {
            return false;
         } else {
            this.commandCooldowns.put(playerId, currentTime);
            this.commandCounts.put(playerId, count + 1);
            return true;
         }
      }
   }

   private boolean checkFeatureEnabled(Player player, String feature) {
      if (!this.plugin.getConfigManager().isFeatureEnabled(feature)) {
         this.plugin.getMessageManager().sendMessage(player, "feature_disabled");
         return false;
      } else {
         return true;
      }
   }

   private boolean validateTeamNameAndTag(String name, String tag) {
      if (name == null) {
         return false;
      } else {
         String plainName = this.stripColorCodes(name);
         String plainTag = this.stripColorCodes(tag == null ? "" : tag);
         if (plainName.length() >= this.plugin.getConfigManager().getMinNameLength() && plainName.length() <= this.plugin.getConfigManager().getMaxNameLength()) {
            if (plainTag.length() >= 2 && plainTag.length() <= this.plugin.getConfigManager().getMaxTagLength()) {
               if (name.length() <= 128 && (tag == null || tag.length() <= 255)) {
                  boolean spaces = this.plugin.getConfigManager().isSpacesInNameAllowed();
                  if (!plainName.matches(spaces ? "^[a-zA-Z0-9_ ]+$" : "^[a-zA-Z0-9_]+$")) {
                     return false;
                  } else if (!plainTag.matches("^[a-zA-Z0-9]+$")) {
                     return false;
                  } else if (!plainName.matches(spaces ? "^[0-9_ ]+$" : "^[0-9_]+$") && !plainTag.matches("^[0-9]+$")) {
                     String[] sqlPatterns = new String[]{"--", ";", "/*", "*/", "xp_", "sp_", "union", "select", "insert", "update", "delete", "drop", "create"};
                     String lowerName = plainName.toLowerCase();
                     String lowerTag = plainTag.toLowerCase();

                     for(String pattern : sqlPatterns) {
                        if (lowerName.contains(pattern) || lowerTag.contains(pattern)) {
                           this.plugin.getLogger().warning("Potential SQL injection attempt detected in team name/tag: " + name + "/" + tag);
                           return false;
                        }
                     }

                     String[] inappropriate = new String[]{"admin", "mod", "staff", "owner", "server", "minecraft", "bukkit", "spigot", "console", "system", "root"};

                     for(String word : inappropriate) {
                        if (lowerName.contains(word) || lowerTag.contains(word)) {
                           return false;
                        }
                     }

                     return true;
                  } else {
                     return false;
                  }
               } else {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   private String stripColorCodes(String text) {
      if (text == null) {
         return "";
      } else {
         text = text.replaceAll("(?i)<\\/?#[0-9A-F]{6}>", "");
         text = text.replaceAll("(?i)<[^>]+>", "");
         text = text.replaceAll("(?i)&#[0-9A-F]{6}", "");
         text = text.replaceAll("(?i)#[0-9A-F]{6}", "");
         text = text.replaceAll("(?i)&[0-9A-FK-OR]", "");
         text = text.replaceAll("§[0-9a-fk-or]", "");
         return text.trim();
      }
   }

   private boolean isValidPlayerName(String name) {
      if (name != null && !name.isEmpty()) {
         int minLength = this.plugin.getConfigManager().getMinNameLength();
         int maxLength = this.plugin.getConfigManager().getMaxNameLength();
         if (name.length() >= minLength && name.length() <= maxLength) {
            if (!name.matches("^[a-zA-Z0-9_. ]+$")) {
               return false;
            } else {
               String lowerName = name.toLowerCase();
               if (!lowerName.contains("--") && !lowerName.contains(";") && !lowerName.contains("'") && !lowerName.contains("\"")) {
                  return true;
               } else {
                  this.plugin.getLogger().warning("Potential injection attempt in player name: " + name);
                  return false;
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void handleGUI(Player player) {
      Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         (new NoTeamGUI(this.plugin, player)).open();
      } else {
         (new TeamGUI(this.plugin, team, player)).open();
      }

   }

   private static Stream<String> teamRefSuggestions(Team t) {
      List<String> out = new ArrayList(2);
      String tag = t.getPlainTag();
      if (tag != null && !tag.isEmpty()) {
         out.add(tag);
      }

      String name = t.getPlainName();
      if (name != null && !name.isEmpty() && name.indexOf(32) < 0) {
         out.add(name);
      }

      return out.stream();
   }

   private void handleCreate(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_creation")) {
         boolean tagEnabled = this.plugin.getConfigManager().isTeamTagEnabled();
         boolean tagOptional = this.plugin.getConfigManager().getBoolean("settings.creation.tag_optional", false);
         String teamTag;
         String teamName;
         if (!tagEnabled) {
            if (args.length < 2) {
               this.plugin.getMessageManager().sendMessage(player, "usage_create_no_tag");
               return;
            }

            teamName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 1, args.length));
            teamTag = "";
         } else if (args.length >= 3) {
            teamTag = args[1];
            teamName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 2, args.length));
         } else {
            if (!tagOptional || args.length != 2) {
               this.plugin.getMessageManager().sendMessage(player, "usage_create");
               return;
            }

            teamName = args[1];
            String plainName = this.stripColorCodes(teamName);
            int max = this.plugin.getConfigManager().getMaxTagLength();
            teamTag = plainName.length() > max ? plainName.substring(0, max) : plainName;
         }

         teamName = teamName.replaceAll("\\s+", " ").trim();
         if (tagEnabled) {
            if (!this.validateTeamNameAndTag(teamName, teamTag)) {
               this.plugin.getMessageManager().sendMessage(player, "invalid_team_name_or_tag");
               return;
            }
         } else {
            String plainName = this.stripColorCodes(teamName);
            boolean spacesAllowed = this.plugin.getConfigManager().isSpacesInNameAllowed();
            if (!plainName.matches(spacesAllowed ? "^[a-zA-Z0-9_ ]+$" : "^[a-zA-Z0-9_]+$")) {
               this.plugin.getMessageManager().sendMessage(player, "invalid_team_name");
               return;
            }

            String lowerName = plainName.toLowerCase();
            String[] inappropriate = new String[]{"admin", "mod", "staff", "owner", "server", "minecraft", "bukkit", "spigot"};

            for(String word : inappropriate) {
               if (lowerName.contains(word)) {
                  this.plugin.getMessageManager().sendMessage(player, "invalid_team_name");
                  return;
               }
            }
         }

         if (this.teamManager.getPlayerTeam(player.getUniqueId()) != null) {
            this.plugin.getMessageManager().sendMessage(player, "already_in_team");
         } else {
            this.teamManager.createTeam(player, teamName, teamTag);
         }
      }
   }

   private void handleDisband(Player player) {
      if (this.checkFeatureEnabled(player, "team_disband")) {
         Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.plugin.getMessageManager().sendMessage(player, "player_not_in_team");
         } else if (!team.isOwner(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage(player, "must_be_owner");
         } else {
            this.teamManager.disbandTeam(player);
         }
      }
   }

   private void handleInvite(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_invites")) {
         if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_invite");
         } else {
            String targetName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 1, args.length));
            if (!this.isValidPlayerName(targetName)) {
               this.plugin.getMessageManager().sendMessage(player, "invalid_player_name");
            } else if (targetName.equalsIgnoreCase(player.getName())) {
               this.plugin.getMessageManager().sendMessage(player, "cannot_invite_yourself");
            } else {
               Player target = Bukkit.getPlayer(targetName);
               if (target != null && target.isOnline()) {
                  if (this.teamManager.getPlayerTeam(target.getUniqueId()) != null) {
                     this.plugin.getMessageManager().sendMessage(player, "player_already_in_team", Placeholder.unparsed("target", target.getName()));
                  } else {
                     this.teamManager.invitePlayer(player, target);
                  }
               } else {
                  this.plugin.getTaskRunner().runAsync(() -> {
                     this.plugin.getStorageManager().getStorage().cachePlayerName(player.getUniqueId(), player.getName());
                     Optional<UUID> targetUuidOpt = this.plugin.getStorageManager().getStorage().getPlayerUuidByName(targetName);
                     if (targetUuidOpt.isEmpty()) {
                        String normalizedName = this.plugin.getBedrockSupport().normalizePlayerName(targetName);
                        if (!normalizedName.equals(targetName)) {
                           targetUuidOpt = this.plugin.getStorageManager().getStorage().getPlayerUuidByName(normalizedName);
                        }
                     }

                     if (targetUuidOpt.isEmpty()) {
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached(targetName);
                        if (offlinePlayer == null || !offlinePlayer.hasPlayedBefore()) {
                           this.plugin.getTaskRunner().runOnEntity(player, () -> this.plugin.getMessageManager().sendMessage(player, "player_not_found", Placeholder.unparsed("target", targetName)));
                           return;
                        }

                        UUID targetUuid = offlinePlayer.getUniqueId();
                        this.plugin.getStorageManager().getStorage().cachePlayerName(targetUuid, targetName);
                        String normalizedName = this.plugin.getBedrockSupport().normalizePlayerName(targetName);
                        if (!normalizedName.equals(targetName)) {
                           this.plugin.getStorageManager().getStorage().cachePlayerName(targetUuid, normalizedName);
                        }

                        targetUuidOpt = Optional.of(targetUuid);
                     }

                     UUID targetUuid = (UUID)targetUuidOpt.get();
                     Optional<Team> existingTeam = this.plugin.getStorageManager().getStorage().findTeamByPlayer(targetUuid);
                     if (existingTeam.isPresent()) {
                        this.plugin.getTaskRunner().runOnEntity(player, () -> this.plugin.getMessageManager().sendMessage(player, "player_already_in_team", Placeholder.unparsed("target", targetName)));
                     } else {
                        this.plugin.getTaskRunner().runOnEntity(player, () -> this.teamManager.invitePlayerByUuid(player, targetUuid, targetName));
                     }
                  });
               }
            }
         }
      }
   }

   private void handleInvites(Player player) {
      if (this.checkFeatureEnabled(player, "team_invites")) {
         (new InvitesGUI(this.plugin, player)).open();
      }
   }

   private void handleAccept(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_invites")) {
         if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_accept");
         } else {
            String teamRef = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 1, args.length)).trim();
            String plainTeamRef = this.stripColorCodes(teamRef);
            if (!plainTeamRef.isEmpty() && plainTeamRef.length() <= this.plugin.getConfigManager().getMaxNameLength() && plainTeamRef.matches("^[a-zA-Z0-9_ ]+$")) {
               if (this.teamManager.getPlayerTeam(player.getUniqueId()) != null) {
                  this.plugin.getMessageManager().sendMessage(player, "already_in_team");
               } else {
                  this.teamManager.acceptInvite(player, plainTeamRef);
               }
            } else {
               this.plugin.getMessageManager().sendMessage(player, "invalid_team_name");
            }
         }
      }
   }

   private void handleDeny(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_invites")) {
         if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_deny");
         } else {
            String teamRef = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 1, args.length)).trim();
            String plainTeamRef = this.stripColorCodes(teamRef);
            if (!plainTeamRef.isEmpty() && plainTeamRef.length() <= this.plugin.getConfigManager().getMaxNameLength() && plainTeamRef.matches("^[a-zA-Z0-9_ ]+$")) {
               this.teamManager.denyInvite(player, plainTeamRef);
            } else {
               this.plugin.getMessageManager().sendMessage(player, "invalid_team_name");
            }
         }
      }
   }

   private void handleJoin(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_join_requests")) {
         if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_join");
         } else {
            String teamRef = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 1, args.length)).trim();
            String plainTeamName = this.stripColorCodes(teamRef);
            if (!plainTeamName.isEmpty() && plainTeamName.length() <= this.plugin.getConfigManager().getMaxNameLength() && plainTeamName.matches("^[a-zA-Z0-9_ ]+$")) {
               if (this.teamManager.getPlayerTeam(player.getUniqueId()) != null) {
                  this.plugin.getMessageManager().sendMessage(player, "already_in_team");
               } else {
                  this.teamManager.joinTeam(player, plainTeamName);
               }
            } else {
               this.plugin.getMessageManager().sendMessage(player, "invalid_team_name");
            }
         }
      }
   }

   private void handleKick(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "member_kick")) {
         if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_kick");
         } else {
            String targetName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 1, args.length));
            if (!this.isValidPlayerName(targetName)) {
               this.plugin.getMessageManager().sendMessage(player, "invalid_player_name");
            } else if (targetName.equalsIgnoreCase(player.getName())) {
               this.plugin.getMessageManager().sendMessage(player, "cannot_kick_yourself");
            } else {
               Player target = Bukkit.getPlayer(targetName);
               if (target == null) {
                  this.plugin.getMessageManager().sendMessage(player, "player_not_found", Placeholder.unparsed("target", targetName));
               } else {
                  Team playerTeam = this.teamManager.getPlayerTeam(player.getUniqueId());
                  Team targetTeam = this.teamManager.getPlayerTeam(target.getUniqueId());
                  if (playerTeam != null && targetTeam != null && playerTeam.getId() == targetTeam.getId()) {
                     this.teamManager.kickPlayer(player, target.getUniqueId());
                  } else {
                     this.plugin.getMessageManager().sendMessage(player, "player_not_in_same_team");
                  }
               }
            }
         }
      }
   }

   private void handleLeave(Player player) {
      if (this.checkFeatureEnabled(player, "member_leave")) {
         Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.plugin.getMessageManager().sendMessage(player, "player_not_in_team");
         } else if (team.isOwner(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage(player, "owner_cannot_leave");
         } else {
            this.teamManager.leaveTeam(player);
         }
      }
   }

   private void handlePromote(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "member_promote")) {
         if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_promote");
         } else {
            String targetName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 1, args.length));
            if (targetName.equalsIgnoreCase(player.getName())) {
               this.plugin.getMessageManager().sendMessage(player, "cannot_promote_yourself");
            } else {
               Player target = Bukkit.getPlayer(targetName);
               if (target == null) {
                  this.plugin.getMessageManager().sendMessage(player, "player_not_found", Placeholder.unparsed("target", targetName));
               } else {
                  this.teamManager.promotePlayer(player, target.getUniqueId());
               }
            }
         }
      }
   }

   private void handleDemote(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "member_demote")) {
         if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_demote");
         } else {
            String targetName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 1, args.length));
            if (targetName.equalsIgnoreCase(player.getName())) {
               this.plugin.getMessageManager().sendMessage(player, "cannot_demote_yourself");
            } else {
               Player target = Bukkit.getPlayer(targetName);
               if (target == null) {
                  this.plugin.getMessageManager().sendMessage(player, "player_not_found", Placeholder.unparsed("target", targetName));
               } else {
                  this.teamManager.demotePlayer(player, target.getUniqueId());
               }
            }
         }
      }
   }

   private void handleInfo(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_info")) {
         if (args.length > 1) {
            String teamName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 1, args.length)).trim();
            if (teamName.isEmpty() || teamName.length() > this.plugin.getConfigManager().getMaxNameLength() || !teamName.matches("^[a-zA-Z0-9_ ]+$")) {
               this.plugin.getMessageManager().sendMessage(player, "invalid_team_name");
               return;
            }

            this.plugin.getTaskRunner().runAsync(() -> {
               Team team = this.teamManager.getTeamByName(teamName);
               this.plugin.getTaskRunner().runOnEntity(player, () -> {
                  if (team == null) {
                     this.plugin.getMessageManager().sendMessage(player, "team_not_found");
                  } else {
                     this.displayTeamInfo(player, team);
                  }
               });
            });
         } else {
            Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
               this.plugin.getMessageManager().sendMessage(player, "player_not_in_team");
               return;
            }

            this.displayTeamInfo(player, team);
         }

      }
   }

   private void displayTeamInfo(Player player, Team team) {
      if (player != null && team != null) {
         String ownerName = Bukkit.getOfflinePlayer(team.getOwnerUuid()).getName();
         String safeOwnerName = ownerName != null ? ownerName : "Unknown";
         String coOwners = (String)team.getCoOwners().stream().map((co) -> Bukkit.getOfflinePlayer(co.getPlayerUuid()).getName()).filter(Objects::nonNull).collect(Collectors.joining(", "));
         this.plugin.getMessageManager().sendRawMessage(player, this.plugin.getMessageManager().getRawMessage("team_info_header"), Placeholder.unparsed("team", team.getName()));
         if (this.plugin.getConfigManager().isTeamTagEnabled()) {
            this.plugin.getMessageManager().sendRawMessage(player, this.plugin.getMessageManager().getRawMessage("team_info_tag"), Placeholder.unparsed("tag", team.getTag()));
         }

         this.plugin.getMessageManager().sendRawMessage(player, this.plugin.getMessageManager().getRawMessage("team_info_description"), Placeholder.unparsed("description", team.getDescription()));
         this.plugin.getMessageManager().sendRawMessage(player, this.plugin.getMessageManager().getRawMessage("team_info_owner"), Placeholder.unparsed("owner", safeOwnerName));
         if (!coOwners.isEmpty()) {
            this.plugin.getMessageManager().sendRawMessage(player, this.plugin.getMessageManager().getRawMessage("team_info_co_owners"), Placeholder.unparsed("co_owners", coOwners));
         }

         double kdr = team.getDeaths() == 0 ? (double)team.getKills() : (double)team.getKills() / (double)team.getDeaths();
         this.plugin.getMessageManager().sendRawMessage(player, this.plugin.getMessageManager().getRawMessage("team_info_stats"), Placeholder.unparsed("kills", String.valueOf(team.getKills())), Placeholder.unparsed("deaths", String.valueOf(team.getDeaths())), Placeholder.unparsed("kdr", String.format("%.2f", kdr)));
         Player owner = Bukkit.getPlayer(team.getOwnerUuid());
         int maxSize = owner != null ? this.plugin.getConfigManager().getMaxTeamSize(owner) : this.plugin.getConfigManager().getMaxTeamSize();
         this.plugin.getMessageManager().sendRawMessage(player, this.plugin.getMessageManager().getRawMessage("team_info_members"), Placeholder.unparsed("member_count", String.valueOf(team.getMembers().size())), Placeholder.unparsed("max_members", String.valueOf(maxSize)));

         for(TeamPlayer member : team.getMembers()) {
            String memberName = Bukkit.getOfflinePlayer(member.getPlayerUuid()).getName();
            String safeMemberName = memberName != null ? memberName : "Unknown";
            boolean isOnline;
            if (this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
               isOnline = this.plugin.getTeamManager().isPlayerOnlineAnywhere(member.getPlayerUuid());
            } else {
               isOnline = member.isOnline();
            }

            String escapedName = safeMemberName.replace("<", "\\<").replace(">", "\\>");
            String coloredName = isOnline ? "<green>" + escapedName + "</green>" : "<red>" + escapedName + "</red>";
            this.plugin.getMessageManager().sendRawMessage(player, this.plugin.getMessageManager().getRawMessage("team_info_member_list"), Placeholder.parsed("player", coloredName));
         }

         this.plugin.getMessageManager().sendRawMessage(player, this.plugin.getMessageManager().getRawMessage("team_info_footer"));
      }
   }

   private void handleSetHome(Player player) {
      if (this.checkFeatureEnabled(player, "team_home_set")) {
         if (!this.plugin.getFeatureRestrictionManager().isFeatureAllowed(player, "sethome")) {
            this.plugin.getMessageManager().sendMessage(player, "feature_disabled_in_world", Placeholder.unparsed("feature", "sethome"), Placeholder.unparsed("world", player.getWorld().getName()));
         } else {
            Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
               this.plugin.getMessageManager().sendMessage(player, "player_not_in_team");
            } else {
               TeamPlayer member = team.getMember(player.getUniqueId());
               boolean allowed = team.hasElevatedPermissions(player.getUniqueId()) || member != null && member.canSetHome();
               if (!allowed) {
                  this.plugin.getMessageManager().sendMessage(player, "no_permission");
               } else {
                  this.teamManager.setTeamHome(player);
               }
            }
         }
      }
   }

   private void handleDelHome(Player player) {
      if (this.checkFeatureEnabled(player, "team_home_set")) {
         Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.plugin.getMessageManager().sendMessage(player, "player_not_in_team");
         } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage(player, "must_be_owner_or_co_owner");
         } else {
            this.teamManager.deleteTeamHome(player);
         }
      }
   }

   private void handleHome(Player player) {
      if (this.checkFeatureEnabled(player, "team_home_teleport")) {
         if (!this.plugin.getFeatureRestrictionManager().isFeatureAllowed(player, "home")) {
            this.plugin.getMessageManager().sendMessage(player, "feature_disabled_in_world", Placeholder.unparsed("feature", "home"), Placeholder.unparsed("world", player.getWorld().getName()));
         } else {
            Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
               this.plugin.getMessageManager().sendMessage(player, "player_not_in_team");
            } else {
               this.teamManager.teleportToHome(player);
            }
         }
      }
   }

   private void handleSetTag(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_tag")) {
         if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_settag");
         } else {
            String tag = args[1];
            String plainTag = this.stripColorCodes(tag);
            if (plainTag.length() >= 2 && plainTag.length() <= this.plugin.getConfigManager().getMaxTagLength() && plainTag.matches("^[a-zA-Z0-9]+$")) {
               this.teamManager.setTeamTag(player, tag);
            } else {
               this.plugin.getMessageManager().sendMessage(player, "invalid_team_tag");
            }
         }
      }
   }

   private void handleSetDescription(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_description")) {
         if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_setdesc");
         } else {
            String description = String.join(" ", args).substring(args[0].length() + 1);
            if (description.length() > this.plugin.getConfigManager().getMaxDescriptionLength()) {
               this.plugin.getMessageManager().sendMessage(player, "description_too_long");
            } else {
               String lowerDesc = description.toLowerCase();
               String[] inappropriate = new String[]{"admin", "mod", "staff", "owner", "server", "minecraft", "bukkit", "spigot"};

               for(String word : inappropriate) {
                  if (lowerDesc.contains(word)) {
                     this.plugin.getMessageManager().sendMessage(player, "inappropriate_description");
                     return;
                  }
               }

               this.teamManager.setTeamDescription(player, description);
            }
         }
      }
   }

   private void handleSetJoinFee(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "economy")) {
         Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.plugin.getMessageManager().sendMessage(player, "not_in_team");
         } else if (!team.getOwnerUuid().equals(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage(player, "only_owner_can_do_this");
         } else if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_setjoinfee");
         } else {
            String action = args[1].toLowerCase();
            if (action.equals("enable")) {
               team.setJoinFeeEnabled(true);
               int teamId = team.getId();
               double amount = team.getJoinFeeAmount();
               this.plugin.getTaskRunner().runAsync(() -> this.plugin.getStorageManager().getStorage().updateTeamJoinFee(teamId, true, amount));
               this.plugin.getMessageManager().sendMessage(player, "join_fee_enabled", Placeholder.unparsed("amount", this.plugin.getEconomy() != null ? this.plugin.getEconomy().format(team.getJoinFeeAmount()) : String.valueOf(team.getJoinFeeAmount())));
               EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
            } else if (action.equals("disable")) {
               team.setJoinFeeEnabled(false);
               int teamId = team.getId();
               double amount = team.getJoinFeeAmount();
               this.plugin.getTaskRunner().runAsync(() -> this.plugin.getStorageManager().getStorage().updateTeamJoinFee(teamId, false, amount));
               this.plugin.getMessageManager().sendMessage(player, "join_fee_disabled");
               EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
            } else {
               try {
                  double amount = Double.parseDouble(action);
                  if (amount < (double)0.0F) {
                     this.plugin.getMessageManager().sendMessage(player, "join_fee_must_be_positive");
                     EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                     return;
                  }

                  double maxFee = this.plugin.getConfigManager().getJoinFee(player);
                  if (maxFee > (double)0.0F && amount > maxFee) {
                     this.plugin.getMessageManager().sendMessage(player, "join_fee_exceeds_permission", Placeholder.unparsed("max", this.plugin.getEconomy() != null ? this.plugin.getEconomy().format(maxFee) : String.valueOf(maxFee)));
                     EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                     return;
                  }

                  team.setJoinFeeAmount(amount);
                  int teamIdFee = team.getId();
                  boolean enabled = team.isJoinFeeEnabled();
                  this.plugin.getTaskRunner().runAsync(() -> this.plugin.getStorageManager().getStorage().updateTeamJoinFee(teamIdFee, enabled, amount));
                  this.plugin.getMessageManager().sendMessage(player, "join_fee_amount_set", Placeholder.unparsed("amount", this.plugin.getEconomy() != null ? this.plugin.getEconomy().format(amount) : String.valueOf(amount)));
                  EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
               } catch (NumberFormatException var13) {
                  this.plugin.getMessageManager().sendMessage(player, "usage_setjoinfee");
                  EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
               }

            }
         }
      }
   }

   private void handleRename(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_rename")) {
         if (!player.hasPermission("justteams.rename") && !player.hasPermission("justteams.admin")) {
            this.plugin.getMessageManager().sendMessage(player, "no_permission");
         } else if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "rename_usage");
         } else {
            Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
               this.plugin.getMessageManager().sendMessage(player, "not_in_team");
            } else if (!team.isOwner(player.getUniqueId())) {
               this.plugin.getMessageManager().sendMessage(player, "must_be_owner");
            } else {
               String newName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 1, args.length)).replaceAll("\\s+", " ").trim();
               String oldName = team.getName();
               if (newName.equalsIgnoreCase(oldName)) {
                  this.plugin.getMessageManager().sendMessage(player, "rename_same_name");
               } else {
                  String plainName = this.stripColorCodes(newName);
                  int minLength = this.plugin.getConfigManager().getMinNameLength();
                  int maxLength = this.plugin.getConfigManager().getMaxNameLength();
                  if (plainName.length() < minLength) {
                     this.plugin.getMessageManager().sendMessage(player, "name_too_short", Placeholder.unparsed("min_length", String.valueOf(minLength)));
                  } else if (plainName.length() > maxLength) {
                     this.plugin.getMessageManager().sendMessage(player, "name_too_long", Placeholder.unparsed("max_length", String.valueOf(maxLength)));
                  } else {
                     String nameRegex = this.plugin.getConfigManager().isSpacesInNameAllowed() ? "^[a-zA-Z0-9_ ]+$" : "^[a-zA-Z0-9_]+$";
                     if (!plainName.matches(nameRegex)) {
                        this.plugin.getMessageManager().sendMessage(player, "invalid_team_name");
                     } else {
                        this.plugin.getTaskRunner().runAsync(() -> {
                           if (this.teamManager.getTeamByName(newName) != null) {
                              this.plugin.getTaskRunner().runOnEntity(player, () -> this.plugin.getMessageManager().sendMessage(player, "team_name_exists", Placeholder.unparsed("team", newName)));
                           } else {
                              Optional<Timestamp> lastRename = this.plugin.getStorageManager().getStorage().getTeamRenameTimestamp(team.getId());
                              long cooldownSeconds = this.plugin.getConfig().getLong("settings.rename_cooldown", 604800L);
                              if (lastRename.isPresent() && cooldownSeconds > 0L) {
                                 long secondsSinceRename = (System.currentTimeMillis() - ((Timestamp)lastRename.get()).getTime()) / 1000L;
                                 long remaining = cooldownSeconds - secondsSinceRename;
                                 if (remaining > 0L) {
                                    String timeLeft = this.formatTime(remaining);
                                    this.plugin.getTaskRunner().runOnEntity(player, () -> this.plugin.getMessageManager().sendMessage(player, "rename_cooldown", Placeholder.unparsed("time", timeLeft)));
                                    return;
                                 }
                              }

                              double cost = this.plugin.getConfig().getDouble("feature_costs.economy.rename", (double)500.0F);
                              boolean economyEnabled = this.plugin.getConfig().getBoolean("feature_costs.economy.enabled", true);
                              if (economyEnabled && cost > (double)0.0F && this.plugin.getEconomy() != null) {
                                 double balance = this.plugin.getEconomy().getBalance(player);
                                 if (balance < cost) {
                                    this.plugin.getTaskRunner().runOnEntity(player, () -> this.plugin.getMessageManager().sendMessage(player, "rename_too_expensive", Placeholder.unparsed("cost", String.format("%.2f", cost)), Placeholder.unparsed("balance", String.format("%.2f", balance))));
                                    return;
                                 }

                                 this.plugin.getEconomy().withdrawPlayer(player, cost);
                              }

                              this.teamManager.renameTeamInCache(team, plainName);
                              this.teamManager.markTeamModified(team.getId());
                              this.plugin.getStorageManager().getStorage().setTeamName(team.getId(), plainName);
                              this.plugin.getStorageManager().getStorage().setTeamRenameTimestamp(team.getId(), new Timestamp(System.currentTimeMillis()));
                              this.plugin.getWebhookHelper().sendTeamRenameWebhook(player.getName(), oldName, newName);
                              this.plugin.getTaskRunner().runAsync(() -> {
                                 if (this.plugin.getRedisManager() != null && this.plugin.getRedisManager().isAvailable()) {
                                    this.plugin.getRedisManager().publishTeamUpdate(team.getId(), "TEAM_RENAMED", player.getUniqueId().toString(), oldName + "|" + newName);
                                 }

                                 this.plugin.getStorageManager().getStorage().addCrossServerUpdate(team.getId(), "TEAM_RENAMED", player.getUniqueId().toString(), "ALL_SERVERS");
                              });
                              this.plugin.getTaskRunner().runOnEntity(player, () -> {
                                 this.plugin.getMessageManager().sendMessage(player, "rename_success", Placeholder.unparsed("old_name", oldName), Placeholder.unparsed("new_name", newName));
                                 team.broadcast("rename_broadcast", Placeholder.unparsed("old_name", oldName), Placeholder.unparsed("new_name", newName));
                                 this.teamManager.refreshTeamGUIsForAllMembers(team);
                              });
                           }
                        });
                     }
                  }
               }
            }
         }
      }
   }

   private String formatTime(long seconds) {
      if (seconds < 60L) {
         return seconds + " second" + (seconds != 1L ? "s" : "");
      } else if (seconds < 3600L) {
         long minutes = seconds / 60L;
         return minutes + " minute" + (minutes != 1L ? "s" : "");
      } else if (seconds < 86400L) {
         long hours = seconds / 3600L;
         return hours + " hour" + (hours != 1L ? "s" : "");
      } else {
         long days = seconds / 86400L;
         return days + " day" + (days != 1L ? "s" : "");
      }
   }

   private void handleTransfer(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_transfer")) {
         if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_transfer");
         } else {
            String targetName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 1, args.length));
            if (targetName.equalsIgnoreCase(player.getName())) {
               this.plugin.getMessageManager().sendMessage(player, "cannot_transfer_to_yourself");
            } else {
               Player target = Bukkit.getPlayer(targetName);
               if (target == null) {
                  this.plugin.getMessageManager().sendMessage(player, "player_not_found", Placeholder.unparsed("target", targetName));
               } else {
                  Team playerTeam = this.teamManager.getPlayerTeam(player.getUniqueId());
                  Team targetTeam = this.teamManager.getPlayerTeam(target.getUniqueId());
                  if (playerTeam != null && targetTeam != null && playerTeam.getId() == targetTeam.getId()) {
                     this.teamManager.transferOwnership(player, target.getUniqueId());
                  } else {
                     this.plugin.getMessageManager().sendMessage(player, "player_not_in_same_team");
                  }
               }
            }
         }
      }
   }

   private void handlePvpToggle(Player player) {
      if (this.checkFeatureEnabled(player, "team_pvp_toggle")) {
         Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.plugin.getMessageManager().sendMessage(player, "player_not_in_team");
         } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage(player, "must_be_owner_or_co_owner");
         } else {
            this.teamManager.togglePvp(player);
         }
      }
   }

   private void handleBank(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_bank")) {
         if (args.length < 2) {
            Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
               this.plugin.getMessageManager().sendMessage(player, "player_not_in_team");
            } else {
               (new TeamGUI(this.plugin, team, player)).open();
            }
         } else {
            String action = args[1].toLowerCase();
            if (!action.equals("deposit") && !action.equals("withdraw")) {
               this.plugin.getMessageManager().sendMessage(player, "usage_bank");
            } else {
               if (args.length < 3) {
                  this.plugin.getMessageManager().sendMessage(player, "usage_bank");
                  return;
               }

               try {
                  double amount = Double.parseDouble(args[2]);
                  if (amount <= (double)0.0F) {
                     this.plugin.getMessageManager().sendMessage(player, "bank_invalid_amount");
                     return;
                  }

                  if (amount > (double)1.0E9F) {
                     this.plugin.getMessageManager().sendMessage(player, "bank_amount_too_large");
                     return;
                  }

                  if (action.equals("deposit")) {
                     this.teamManager.deposit(player, amount);
                  } else {
                     this.teamManager.withdraw(player, amount);
                  }
               } catch (NumberFormatException var6) {
                  this.plugin.getMessageManager().sendMessage(player, "bank_invalid_amount");
               }
            }

         }
      }
   }

   private void handleEnderChest(Player player) {
      if (this.checkFeatureEnabled(player, "team_enderchest")) {
         if (!this.plugin.getFeatureRestrictionManager().isFeatureAllowed(player, "enderchest")) {
            this.plugin.getMessageManager().sendMessage(player, "feature_disabled_in_world", Placeholder.unparsed("feature", "enderchest"), Placeholder.unparsed("world", player.getWorld().getName()));
         } else {
            Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
               this.plugin.getMessageManager().sendMessage(player, "player_not_in_team");
            } else {
               this.teamManager.openEnderChest(player);
            }
         }
      }
   }

   private void handlePublicToggle(Player player) {
      if (this.checkFeatureEnabled(player, "team_public_toggle")) {
         Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.plugin.getMessageManager().sendMessage(player, "player_not_in_team");
         } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage(player, "must_be_owner_or_co_owner");
         } else {
            this.teamManager.togglePublicStatus(player);
         }
      }
   }

   private void handleToggleGlow(Player player) {
      if (this.checkFeatureEnabled(player, "team_glow")) {
         Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.plugin.getMessageManager().sendMessage(player, "player_not_in_team");
         } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage(player, "must_be_owner_or_co_owner");
         } else {
            this.teamManager.toggleGlow(player);
         }
      }
   }

   private void handleRequests(Player player) {
      if (this.checkFeatureEnabled(player, "team_join_requests")) {
         Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.plugin.getMessageManager().sendMessage(player, "player_not_in_team");
         } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage(player, "must_be_owner_or_co_owner");
         } else {
            (new JoinRequestGUI(this.plugin, player, team)).open();
         }
      }
   }

   private void handleSetWarp(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_warp_set")) {
         if (!this.plugin.getFeatureRestrictionManager().isFeatureAllowed(player, "setwarp")) {
            this.plugin.getMessageManager().sendMessage(player, "feature_disabled_in_world", Placeholder.unparsed("feature", "setwarp"), Placeholder.unparsed("world", player.getWorld().getName()));
         } else if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_setwarp");
         } else {
            String warpName = args[1];
            if (warpName.length() >= 2 && warpName.length() <= this.plugin.getConfigManager().getMaxNameLength() && warpName.matches("^[a-zA-Z0-9_]+$")) {
               String password = args.length > 2 ? args[2] : null;
               if (password == null || password.length() >= 3 && password.length() <= 20) {
                  this.teamManager.setTeamWarp(player, warpName, password);
               } else {
                  this.plugin.getMessageManager().sendMessage(player, "invalid_warp_password");
               }
            } else {
               this.plugin.getMessageManager().sendMessage(player, "invalid_warp_name");
            }
         }
      }
   }

   private void handleDelWarp(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_warp_delete")) {
         if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_delwarp");
         } else {
            String warpName = args[1];
            if (warpName.length() >= 2 && warpName.length() <= this.plugin.getConfigManager().getMaxNameLength() && warpName.matches("^[a-zA-Z0-9_]+$")) {
               this.teamManager.deleteTeamWarp(player, warpName);
            } else {
               this.plugin.getMessageManager().sendMessage(player, "invalid_warp_name");
            }
         }
      }
   }

   private void handleWarp(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_warp_teleport")) {
         if (!this.plugin.getFeatureRestrictionManager().isFeatureAllowed(player, "warp")) {
            this.plugin.getMessageManager().sendMessage(player, "feature_disabled_in_world", Placeholder.unparsed("feature", "warp"), Placeholder.unparsed("world", player.getWorld().getName()));
         } else if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_warp");
         } else {
            String warpName = args[1];
            if (warpName.length() >= 2 && warpName.length() <= this.plugin.getConfigManager().getMaxNameLength() && warpName.matches("^[a-zA-Z0-9_]+$")) {
               String password = args.length > 2 ? args[2] : null;
               this.teamManager.teleportToTeamWarp(player, warpName, password);
            } else {
               this.plugin.getMessageManager().sendMessage(player, "invalid_warp_name");
            }
         }
      }
   }

   private void handleWarps(Player player) {
      if (this.checkFeatureEnabled(player, "team_warps")) {
         try {
            Class.forName("eu.kotori.justTeams.gui.WarpsGUI");
            this.teamManager.openWarpsGUI(player);
         } catch (ClassNotFoundException var3) {
            this.teamManager.listTeamWarps(player);
         }

      }
   }

   private void handleAlly(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_allies")) {
         if (args.length < 2) {
            try {
               Class.forName("eu.kotori.justTeams.gui.AllyGUI");
               this.teamManager.openAllyGUI(player);
            } catch (ClassNotFoundException var7) {
               this.plugin.getMessageManager().sendMessage(player, "gui_error");
            }

         } else {
            switch (args[1].toLowerCase()) {
               case "add":
               case "request":
                  if (args.length < 3) {
                     this.plugin.getMessageManager().sendMessage(player, "usage_ally_add");
                     return;
                  }

                  String targetTeamName = args[2];
                  this.teamManager.sendAllyRequest(player, targetTeamName);
                  break;
               case "remove":
               case "break":
                  if (args.length < 3) {
                     this.plugin.getMessageManager().sendMessage(player, "usage_ally_remove");
                     return;
                  }

                  String allyTeamName = args[2];
                  this.teamManager.removeAlly(player, allyTeamName);
                  break;
               case "accept":
                  if (args.length < 3) {
                     this.plugin.getMessageManager().sendMessage(player, "usage_ally_accept");
                     return;
                  }

                  String senderTeamName = args[2];
                  this.teamManager.acceptAllyRequestByName(player, senderTeamName);
                  break;
               case "deny":
                  if (args.length < 3) {
                     this.plugin.getMessageManager().sendMessage(player, "usage_ally_deny");
                     return;
                  }

                  senderTeamName = args[2];
                  this.teamManager.denyAllyRequestByName(player, senderTeamName);
                  break;
               case "toggle":
                  this.teamManager.toggleAcceptRequests(player);
                  break;
               case "list":
                  try {
                     Class.forName("eu.kotori.justTeams.gui.AllyGUI");
                     this.teamManager.openAllyGUI(player);
                  } catch (ClassNotFoundException var8) {
                     this.plugin.getMessageManager().sendMessage(player, "gui_error");
                  }
                  break;
               default:
                  this.plugin.getMessageManager().sendMessage(player, "usage_ally");
            }

         }
      }
   }

   private void handleChat(Player player) {
      if (this.checkFeatureEnabled(player, "team_chat")) {
         this.plugin.getTeamChatListener().toggleTeamChat(player);
      }
   }

   private void handleChatSpy(Player player) {
      if (!player.hasPermission("justteams.chatspy")) {
         this.plugin.getMessageManager().sendMessage(player, "no_permission");
      } else {
         this.plugin.getTeamChatListener().toggleChatSpy(player);
      }
   }

   private void handleHelp(Player player) {
      if (this.plugin.getMessageManager().hasMessage("help_menu")) {
         this.plugin.getMessageManager().sendMessageList(player, "help_menu");
      } else {
         this.plugin.getMessageManager().sendMessage(player, "help_header");
         this.sendHelpEntry(player, "gui", "gui", "Opens the team GUI.");
         if (this.plugin.getConfigManager().isTeamTagEnabled()) {
            this.sendHelpEntry(player, "create", "create <tag> <name>", "Creates a team (name can have spaces).");
         } else if (this.plugin.getMessageManager().hasMessage("help_entries.create_no_tag")) {
            this.sendHelpEntry(player, "create_no_tag", "create <name>", "Creates a team.");
         } else {
            this.sendHelpEntry(player, "create", "create <name>", "Creates a team.");
         }

         this.sendHelpEntry(player, "disband", "disband", "Disbands your team.");
         this.sendHelpEntry(player, "invite", "invite <player>", "Invites a player.");
         this.sendHelpEntry(player, "join", "join <teamName>", "Joins a public team.");
         this.sendHelpEntry(player, "unjoin", "unjoin <teamName>", "Cancels a join request to a team.");
         this.sendHelpEntry(player, "kick", "kick <player>", "Kicks a player.");
         this.sendHelpEntry(player, "leave", "leave", "Leaves your current team.");
         this.sendHelpEntry(player, "promote", "promote <player>", "Promotes a member to Co-Owner.");
         this.sendHelpEntry(player, "demote", "demote <player>", "Demotes a Co-Owner to Member.");
         this.sendHelpEntry(player, "info", "info [team]", "Shows team info.");
         this.sendHelpEntry(player, "sethome", "sethome", "Sets the team home.");
         this.sendHelpEntry(player, "delhome", "delhome", "Deletes the team home.");
         this.sendHelpEntry(player, "home", "home", "Teleports to the team home.");
         if (this.plugin.getConfigManager().isTeamTagEnabled()) {
            this.sendHelpEntry(player, "settag", "settag <tag>", "Changes the team tag.");
         }

         this.sendHelpEntry(player, "setdesc", "setdesc <description>", "Changes the team description.");
         if (this.plugin.getConfig().getBoolean("features.team_rename", true)) {
            this.sendHelpEntry(player, "rename", "rename <newName>", "Renames the team (cooldown applies).");
         }

         this.sendHelpEntry(player, "transfer", "transfer <player>", "Transfers ownership.");
         this.sendHelpEntry(player, "pvp", "pvp", "Toggles team PvP.");
         this.sendHelpEntry(player, "bank", "bank [deposit|withdraw] [amount]", "Manages the team bank.");
         this.sendHelpEntry(player, "enderchest", "enderchest", "Opens the team ender chest.");
         if (this.plugin.getQuestManager() != null && this.plugin.getQuestManager().isEnabled()) {
            this.sendHelpEntry(player, "quests", "quests", "Opens the team quests menu.");
         }

         this.sendHelpEntry(player, "public", "public", "Toggles public join status.");
         this.sendHelpEntry(player, "requests", "requests", "View join requests.");
         this.sendHelpEntry(player, "setwarp", "setwarp <name> [password]", "Sets a team warp.");
         this.sendHelpEntry(player, "delwarp", "delwarp <name>", "Deletes a team warp.");
         this.sendHelpEntry(player, "warp", "warp <name> [password]", "Teleports to a team warp.");
         this.sendHelpEntry(player, "warps", "warps", "Lists all team warps.");
         this.sendHelpEntry(player, "top", "top", "Shows team leaderboards.");
         this.sendHelpEntry(player, "blacklist", "blacklist <player> [reason]", "Blacklists a player from your team.");
         this.sendHelpEntry(player, "unblacklist", "unblacklist <player>", "Unblacklists a player from your team.");
         if (player.hasPermission("justteams.admin.disband")) {
            this.sendHelpEntry(player, "admin_disband", "admin disband <teamName>", "Admin command to disband a team.");
         }

         if (player.hasPermission("justteams.admin.home")) {
            this.sendHelpEntry(player, "admin_home", "admin home <team>", "Admin command to teleport to a team's home.");
         }

         if (player.hasPermission("justteams.admin.enderchest")) {
            this.sendHelpEntry(player, "admin_enderchest", "admin enderchest <team>", "Admin command to view a team's ender chest.");
         }

         this.sendHelpEntry(player, "admin_warps", "admin warps <team>", "Admin command to list a team's warps.");
         this.sendHelpEntry(player, "platform", "platform", "Shows your platform information (Java/Bedrock).");
         if (player.hasPermission("justteams.admin.debug")) {
            this.sendHelpEntry(player, "debug_permissions", "debug-permissions", "Debugs the current permissions of your team.");
            this.sendHelpEntry(player, "debug_placeholders", "debug-placeholders", "Tests all PlaceholderAPI placeholders for your team.");
         }
      }

   }

   private void sendHelpEntry(Player player, String key, String defaultUsage, String defaultDesc) {
      if (this.plugin.getMessageManager().hasMessage("help_entries." + key)) {
         String entry = this.plugin.getMessageManager().getRawMessage("help_entries." + key);
         String usage = defaultUsage;
         String description;
         if (entry.contains(" - ")) {
            String[] parts = entry.split(" - ", 2);
            usage = parts[0];
            description = parts[1];
         } else {
            description = entry;
         }

         this.plugin.getMessageManager().sendMessage(player, "help_format", Placeholder.unparsed("command", usage), Placeholder.unparsed("description", description));
      }
   }

   private void handleReload(CommandSender sender) {
      if (sender instanceof Player player) {
         if (!this.hasAdminPermission(player)) {
            this.plugin.getMessageManager().sendMessage(sender, "no_permission");
            return;
         }
      }

      try {
         this.plugin.getLogger().info("Reloading JustTeams configuration...");
         this.plugin.getConfigManager().reloadConfig();
         this.plugin.getMessageManager().reload();
         this.plugin.getGuiConfigManager().reload();
         this.plugin.getCommandManager().reload();
         this.plugin.getAliasManager().reload();
         this.plugin.getGuiConfigManager().testPlaceholders();
         this.plugin.getMessageManager().sendMessage(sender, "reload");
         if (sender instanceof Player) {
            this.plugin.getMessageManager().sendMessage(sender, "reload_commands_notice");
         }

         this.plugin.getLogger().info("✓ JustTeams configuration reloaded successfully!");
      } catch (Exception e) {
         this.plugin.getLogger().severe("✗ Failed to reload configuration: " + e.getMessage());
         e.printStackTrace();
         sender.sendMessage("§c✗ Failed to reload configuration. Check console for details.");
      }

   }

   private void handleBlacklist(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_blacklist")) {
         Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.plugin.getMessageManager().sendMessage(player, "not_in_team");
         } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage(player, "must_be_owner_or_co_owner");
         } else if (args.length == 1) {
            (new BlacklistGUI(this.plugin, team, player)).open();
         } else if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_blacklist");
         } else {
            String targetName = args[1];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
               this.plugin.getMessageManager().sendMessage(player, "player_not_found", Placeholder.unparsed("target", targetName));
            } else if (target.getUniqueId().equals(player.getUniqueId())) {
               this.plugin.getMessageManager().sendMessage(player, "cannot_blacklist_self");
            } else if (team.isMember(target.getUniqueId())) {
               this.plugin.getMessageManager().sendMessage(player, "cannot_blacklist_team_member");
            } else {
               this.plugin.getTaskRunner().runAsync(() -> {
                  try {
                     if (this.plugin.getStorageManager().getStorage().isPlayerBlacklisted(team.getId(), target.getUniqueId())) {
                        List<BlacklistedPlayer> blacklist = this.plugin.getStorageManager().getStorage().getTeamBlacklist(team.getId());
                        BlacklistedPlayer blacklistedPlayer = (BlacklistedPlayer)blacklist.stream().filter((bp) -> bp.getPlayerUuid().equals(target.getUniqueId())).findFirst().orElse(null);
                        String blacklisterName = blacklistedPlayer != null ? blacklistedPlayer.getBlacklistedByName() : "Unknown";
                        this.plugin.getTaskRunner().runOnEntity(player, () -> this.plugin.getMessageManager().sendMessage(player, "player_already_blacklisted", Placeholder.unparsed("target", target.getName()), Placeholder.unparsed("blacklister", blacklisterName)));
                        return;
                     }
                  } catch (Exception e) {
                     this.plugin.getLogger().warning("Could not check if player is already blacklisted: " + e.getMessage());
                  }

               });
               String reason = args.length > 2 ? String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 2, args.length)) : "No reason specified";
               this.plugin.getTaskRunner().runAsync(() -> {
                  try {
                     boolean success = this.plugin.getStorageManager().getStorage().addPlayerToBlacklist(team.getId(), target.getUniqueId(), target.getName(), reason, player.getUniqueId(), player.getName());
                     if (success) {
                        this.plugin.getTaskRunner().runOnEntity(player, () -> {
                           try {
                              this.plugin.getMessageManager().sendMessage(player, "player_blacklisted", Placeholder.unparsed("target", target.getName()));
                           } catch (Exception e) {
                              this.plugin.getLogger().severe("Error sending blacklist success message: " + e.getMessage());
                           }

                        });
                     } else {
                        this.plugin.getTaskRunner().runOnEntity(player, () -> {
                           try {
                              this.plugin.getMessageManager().sendMessage(player, "blacklist_failed");
                           } catch (Exception e) {
                              this.plugin.getLogger().severe("Error sending blacklist failed message: " + e.getMessage());
                           }

                        });
                     }
                  } catch (Exception e) {
                     this.plugin.getLogger().severe("Error adding player to blacklist: " + e.getMessage());
                     this.plugin.getTaskRunner().runOnEntity(player, () -> {
                        try {
                           this.plugin.getMessageManager().sendMessage(player, "blacklist_failed");
                        } catch (Exception e2) {
                           this.plugin.getLogger().severe("Error sending blacklist error message: " + e2.getMessage());
                        }

                     });
                  }

               });
            }
         }
      }
   }

   private void handleUnblacklist(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_blacklist")) {
         Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.plugin.getMessageManager().sendMessage(player, "not_in_team");
         } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage(player, "must_be_owner_or_co_owner");
         } else if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage(player, "usage_unblacklist");
         } else {
            String targetName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 1, args.length));
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
               this.plugin.getMessageManager().sendMessage(player, "player_not_found", Placeholder.unparsed("target", targetName));
            } else if (target.getUniqueId().equals(player.getUniqueId())) {
               this.plugin.getMessageManager().sendMessage(player, "cannot_unblacklist_self");
            } else {
               this.plugin.getTaskRunner().runAsync(() -> {
                  try {
                     if (!this.plugin.getStorageManager().getStorage().isPlayerBlacklisted(team.getId(), target.getUniqueId())) {
                        this.plugin.getTaskRunner().runOnEntity(player, () -> this.plugin.getMessageManager().sendMessage(player, "player_not_blacklisted", Placeholder.unparsed("target", target.getName())));
                        return;
                     }

                     boolean success = this.plugin.getStorageManager().getStorage().removePlayerFromBlacklist(team.getId(), target.getUniqueId());
                     if (success) {
                        this.plugin.getTaskRunner().runOnEntity(player, () -> {
                           try {
                              this.plugin.getMessageManager().sendMessage(player, "player_unblacklisted", Placeholder.unparsed("target", target.getName()));
                           } catch (Exception e) {
                              this.plugin.getLogger().severe("Error sending unblacklist success message: " + e.getMessage());
                           }

                        });
                     } else {
                        this.plugin.getTaskRunner().runOnEntity(player, () -> {
                           try {
                              this.plugin.getMessageManager().sendMessage(player, "unblacklist_failed");
                           } catch (Exception e) {
                              this.plugin.getLogger().severe("Error sending unblacklist failed message: " + e.getMessage());
                           }

                        });
                     }
                  } catch (Exception e) {
                     this.plugin.getLogger().severe("Error removing player from blacklist: " + e.getMessage());
                     this.plugin.getTaskRunner().runOnEntity(player, () -> {
                        try {
                           this.plugin.getMessageManager().sendMessage(player, "unblacklist_failed");
                        } catch (Exception e2) {
                           this.plugin.getLogger().severe("Error sending unblacklist error message: " + e2.getMessage());
                        }

                     });
                  }

               });
            }
         }
      }
   }

   private void handleSettings(Player player) {
      Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         this.plugin.getMessageManager().sendMessage(player, "player_not_in_team");
      } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
         this.plugin.getMessageManager().sendMessage(player, "settings_permission_denied");
      } else {
         (new TeamSettingsGUI(this.plugin, player, team)).open();
      }
   }

   private void handleTop(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_leaderboard")) {
         try {
            Class.forName("eu.kotori.justTeams.gui.LeaderboardCategoryGUI");
            (new LeaderboardCategoryGUI(this.plugin, player)).open();
         } catch (ClassNotFoundException var4) {
            this.plugin.getTaskRunner().runAsync(() -> {
               try {
                  Map<Integer, Team> topTeams = this.plugin.getStorageManager().getStorage().getTopTeamsByKills(10);
                  this.plugin.getTaskRunner().runOnEntity(player, () -> {
                     this.plugin.getMessageManager().sendMessage(player, "leaderboard_header");

                     for(Map.Entry<Integer, Team> entry : topTeams.entrySet()) {
                        Team team = (Team)entry.getValue();
                        this.plugin.getMessageManager().sendRawMessage(player, this.plugin.getMessageManager().getRawMessage("leaderboard_entry"), Placeholder.unparsed("rank", String.valueOf(entry.getKey())), Placeholder.unparsed("team", team.getName()), Placeholder.unparsed("score", String.valueOf(team.getKills())));
                     }

                     this.plugin.getMessageManager().sendMessage(player, "leaderboard_footer");
                  });
               } catch (Exception ex) {
                  this.plugin.getLogger().severe("Error loading top teams: " + ex.getMessage());
                  this.plugin.getTaskRunner().runOnEntity(player, () -> this.plugin.getMessageManager().sendMessage(player, "error_loading_leaderboard"));
               }

            });
         }

      }
   }

   private void handleUnjoin(Player player, String[] args) {
      if (args.length < 2) {
         this.plugin.getMessageManager().sendMessage(player, "usage_unjoin");
      } else {
         String teamName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 1, args.length)).trim();
         if (!teamName.isEmpty() && teamName.length() <= this.plugin.getConfigManager().getMaxNameLength() && teamName.matches("^[a-zA-Z0-9_ ]+$")) {
            if (this.teamManager.getPlayerTeam(player.getUniqueId()) != null) {
               this.plugin.getMessageManager().sendMessage(player, "already_in_team");
            } else {
               this.teamManager.withdrawJoinRequest(player, teamName);
            }
         } else {
            this.plugin.getMessageManager().sendMessage(player, "invalid_team_name");
         }
      }
   }

   private void handleAdmin(Player player, String[] args) {
      if (this.hasAdminPermission(player)) {
         if (args.length >= 2 && !args[1].equalsIgnoreCase("gui")) {
            switch (args[1].toLowerCase()) {
               case "disband":
                  if (args.length < 3) {
                     this.plugin.getMessageManager().sendMessage(player, "usage_admin_disband");
                     return;
                  }

                  String teamName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 2, args.length)).trim();
                  this.teamManager.adminDisbandTeam(player, teamName);
                  break;
               case "enderchest":
                  if (args.length < 3) {
                     this.plugin.getMessageManager().sendMessage(player, "usage_admin_enderchest");
                     return;
                  }

                  teamName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 2, args.length)).trim();
                  this.teamManager.adminOpenEnderChest(player, teamName);
                  break;
               case "home":
                  if (args.length < 3) {
                     this.plugin.getMessageManager().sendMessage(player, "usage_admin_home");
                     return;
                  }

                  teamName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 2, args.length)).trim();
                  this.teamManager.adminTeleportTeamHome(player, teamName);
                  break;
               case "warps":
                  if (args.length < 3) {
                     this.plugin.getMessageManager().sendMessage(player, "usage_admin_warps");
                     return;
                  }

                  this.teamManager.adminViewWarps(player, String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 2, args.length)).trim());
                  break;
               case "testmigration":
                  this.handleTestMigration(player, args);
                  break;
               case "performance":
                  this.handlePerformance(player, args);
                  break;
               case "points":
                  this.handleAdminPoints(player, args);
                  break;
               case "quest":
                  this.handleAdminQuest(player, args);
                  break;
               default:
                  player.sendMessage("§cUsage: /team admin <gui|disband|home|warps|enderchest|points|quest|testmigration|performance> [args]");
            }

         } else {
            (new AdminGUI(this.plugin, player)).open();
         }
      }
   }

   private void handleQuests(Player player) {
      if (this.plugin.getQuestManager() != null && this.plugin.getQuestManager().isEnabled()) {
         Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.plugin.getMessageManager().sendMessage(player, "player_not_in_team");
         } else {
            (new QuestGUI(this.plugin, player, team)).open();
         }
      } else {
         player.sendMessage("§cQuest system is disabled on this server.");
      }
   }

   private void handlePoints(Player player) {
      Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         this.plugin.getMessageManager().sendMessage(player, "player_not_in_team");
      } else {
         long points = this.teamManager.getTeamPoints(team.getId());
         this.plugin.getMessageManager().sendRawMessage(player, "<gold>Team <yellow>" + team.getName() + "<gold> has <yellow>" + points + "<gold> points.");
      }
   }

   private void handleAdminPoints(Player player, String[] args) {
      if (args.length < 5) {
         player.sendMessage("§cUsage: /team admin points <team> <add|remove|set> <amount>");
      } else {
         String teamName = args[2];
         String op = args[3].toLowerCase();

         long amount;
         try {
            amount = Long.parseLong(args[4]);
         } catch (NumberFormatException var8) {
            player.sendMessage("§cAmount must be a whole number.");
            return;
         }

         this.plugin.getTaskRunner().runAsync(() -> {
            Team target = this.teamManager.getTeamByName(teamName);
            this.plugin.getTaskRunner().runOnEntity(player, () -> {
               if (target == null) {
                  this.plugin.getMessageManager().sendMessage(player, "team_not_found", Placeholder.unparsed("team", teamName));
               } else {
                  boolean ok;
                  switch (op) {
                     case "add":
                        ok = this.teamManager.addTeamPoints(target.getId(), amount, player.getName());
                        break;
                     case "remove":
                        ok = this.teamManager.removeTeamPoints(target.getId(), amount, player.getName());
                        break;
                     case "set":
                        ok = this.teamManager.setTeamPoints(target.getId(), amount, player.getName());
                        break;
                     default:
                        player.sendMessage("§cUnknown operation. Use add, remove, or set.");
                        return;
                  }

                  if (!ok) {
                     player.sendMessage("§cFailed to apply points change.");
                  } else {
                     long newPoints = this.teamManager.getTeamPoints(target.getId());
                     this.plugin.getMessageManager().sendRawMessage(player, "<green>Points updated for <white><team><green>: <yellow><points>".replace("<team>", target.getName()).replace("<points>", String.valueOf(newPoints)));
                  }
               }
            });
         });
      }
   }

   private void handleAdminQuest(Player player, String[] args) {
      if (args.length < 4) {
         player.sendMessage("§cUsage: /team admin quest <team> <give|reset|complete> [quest_id]");
      } else {
         String teamName = args[2];
         String op = args[3].toLowerCase();
         this.plugin.getTaskRunner().runAsync(() -> {
            Team resolved = this.teamManager.getTeamByName(teamName);
            this.plugin.getTaskRunner().runOnEntity(player, () -> this.handleAdminQuestResolved(player, args, op, teamName, resolved));
         });
      }
   }

   private void handleAdminQuestResolved(Player player, String[] args, String op, String teamName, Team target) {
      if (target == null) {
         this.plugin.getMessageManager().sendMessage(player, "team_not_found", Placeholder.unparsed("team", teamName));
      } else {
         QuestManager qm = this.plugin.getQuestManager();
         if (qm == null) {
            player.sendMessage("§cQuest system is disabled.");
         } else {
            switch (op) {
               case "give":
                  if (args.length < 5) {
                     player.sendMessage("§cMissing quest_id.");
                     return;
                  }

                  if (qm.assignQuest(target.getId(), args[4])) {
                     player.sendMessage("§aAssigned quest §e" + args[4] + "§a to team §e" + target.getName());
                  } else {
                     player.sendMessage("§cQuest not found or already assigned.");
                  }
                  break;
               case "reset":
                  qm.resetQuests(target.getId());
                  player.sendMessage("§aReset all quest progress for §e" + target.getName());
                  break;
               case "complete":
                  if (args.length < 5) {
                     player.sendMessage("§cMissing quest_id.");
                     return;
                  }

                  if (qm.forceComplete(target.getId(), args[4])) {
                     player.sendMessage("§aMarked quest §e" + args[4] + "§a complete for §e" + target.getName());
                  } else {
                     player.sendMessage("§cQuest not active for this team.");
                  }
                  break;
               default:
                  player.sendMessage("§cUnknown op. Use give, reset, complete.");
            }

         }
      }
   }

   private void handleServerAlias(Player player, String[] args) {
      if (!this.hasAdminPermission(player)) {
         this.plugin.getMessageManager().sendMessage(player, "no_permission");
      } else if (args.length < 2) {
         this.plugin.getMessageManager().sendMessage(player, "usage_serveralias");
      } else {
         switch (args[1].toLowerCase()) {
            case "set":
               if (args.length < 4) {
                  this.plugin.getMessageManager().sendMessage(player, "usage_serveralias");
                  return;
               }

               String serverName = args[2];
               String alias = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 3, args.length));
               if (alias.length() > 64) {
                  player.sendMessage("§cServer alias too long! Maximum 64 characters.");
                  return;
               }

               this.plugin.getTaskRunner().runAsync(() -> {
                  this.plugin.getStorageManager().getStorage().setServerAlias(serverName, alias);
                  this.plugin.getTaskRunner().runOnEntity(player, () -> this.plugin.getMessageManager().sendMessage(player, "serveralias_set", Placeholder.unparsed("server", serverName), Placeholder.unparsed("alias", alias)));
               });
               break;
            case "remove":
               if (args.length < 3) {
                  this.plugin.getMessageManager().sendMessage(player, "usage_serveralias");
                  return;
               }

               serverName = args[2];
               this.plugin.getTaskRunner().runAsync(() -> {
                  this.plugin.getStorageManager().getStorage().removeServerAlias(serverName);
                  this.plugin.getTaskRunner().runOnEntity(player, () -> this.plugin.getMessageManager().sendMessage(player, "serveralias_removed", Placeholder.unparsed("server", serverName)));
               });
               break;
            case "list":
               this.plugin.getTaskRunner().runAsync(() -> {
                  Map<String, String> aliases = this.plugin.getStorageManager().getStorage().getAllServerAliases();
                  this.plugin.getTaskRunner().runOnEntity(player, () -> {
                     if (aliases.isEmpty()) {
                        player.sendMessage("§eNo server aliases configured.");
                     } else {
                        this.plugin.getMessageManager().sendMessage(player, "serveralias_list_header");

                        for(Map.Entry<String, String> entry : aliases.entrySet()) {
                           this.plugin.getMessageManager().sendMessage(player, "serveralias_list_entry", Placeholder.unparsed("server", (String)entry.getKey()), Placeholder.unparsed("alias", (String)entry.getValue()));
                        }

                     }
                  });
               });
               break;
            default:
               this.plugin.getMessageManager().sendMessage(player, "usage_serveralias");
         }

      }
   }

   private boolean hasAdminPermission(Player player) {
      return player.isOp() || player.hasPermission("*") || player.hasPermission("justteams.admin");
   }

   private void handleTestMigration(Player player, String[] args) {
      if (args.length == 2) {
         player.sendMessage("§eTesting database migration system...");

         try {
            DatabaseFileManager fileManager = new DatabaseFileManager(this.plugin);
            boolean fileMigrationResult = fileManager.migrateOldDatabaseFiles();
            player.sendMessage("§aFile migration result: " + (fileMigrationResult ? "SUCCESS" : "FAILED"));
            boolean backupResult = fileManager.backupDatabase();
            player.sendMessage("§aBackup creation result: " + (backupResult ? "SUCCESS" : "FAILED"));
            boolean validationResult = fileManager.validateDatabaseFiles();
            player.sendMessage("§aFile validation result: " + (validationResult ? "SUCCESS" : "FAILED"));
            DatabaseMigrationManager migrationManager = new DatabaseMigrationManager(this.plugin, (DatabaseStorage)this.plugin.getStorageManager().getStorage());
            boolean migrationResult = migrationManager.performMigration();
            player.sendMessage("§aSchema migration result: " + (migrationResult ? "SUCCESS" : "FAILED"));
            boolean configHealthy = ConfigUpdater.isConfigurationSystemHealthy(this.plugin);
            player.sendMessage("§aConfiguration system health: " + (configHealthy ? "HEALTHY" : "UNHEALTHY"));
            if (fileMigrationResult && migrationResult && configHealthy) {
               player.sendMessage("§aAll migration tests passed! Database and configuration should be working correctly.");
            } else {
               player.sendMessage("§cSome migration tests failed. Check the console for details.");
            }
         } catch (Exception e) {
            player.sendMessage("§cMigration test failed with exception: " + e.getMessage());
            this.plugin.getLogger().severe("Migration test failed: " + e.getMessage());
         }
      } else {
         String action = args[2].toLowerCase();

         try {
            DatabaseFileManager fileManager = new DatabaseFileManager(this.plugin);
            DatabaseMigrationManager migrationManager = new DatabaseMigrationManager(this.plugin, (DatabaseStorage)this.plugin.getStorageManager().getStorage());
            switch (action) {
               case "test":
                  player.sendMessage("§eRunning full migration test...");
                  boolean fileResult = fileManager.migrateOldDatabaseFiles();
                  boolean backupResult = fileManager.backupDatabase();
                  boolean validationResult = fileManager.validateDatabaseFiles();
                  boolean migrationResult = migrationManager.performMigration();
                  player.sendMessage("§aFile migration: " + (fileResult ? "SUCCESS" : "FAILED"));
                  player.sendMessage("§aBackup creation: " + (backupResult ? "SUCCESS" : "FAILED"));
                  player.sendMessage("§aFile validation: " + (validationResult ? "SUCCESS" : "FAILED"));
                  player.sendMessage("§aSchema migration: " + (migrationResult ? "SUCCESS" : "FAILED"));
                  break;
               case "migrate":
                  player.sendMessage("§eRunning database migration...");
                  boolean migrateResult = migrationManager.performMigration();
                  player.sendMessage("§aMigration result: " + (migrateResult ? "SUCCESS" : "FAILED"));
                  break;
               case "validate":
                  player.sendMessage("§eValidating database files...");
                  boolean validateResult = fileManager.validateDatabaseFiles();
                  player.sendMessage("§aValidation result: " + (validateResult ? "SUCCESS" : "FAILED"));
                  break;
               case "backup":
                  player.sendMessage("§eCreating database backup...");
                  boolean backupResult2 = fileManager.backupDatabase();
                  player.sendMessage("§aBackup result: " + (backupResult2 ? "SUCCESS" : "FAILED"));
                  break;
               case "config":
                  player.sendMessage("§eTesting configuration system...");
                  ConfigUpdater.testConfigurationSystem(this.plugin);
                  boolean configHealthy = ConfigUpdater.isConfigurationSystemHealthy(this.plugin);
                  player.sendMessage("§aConfiguration system health: " + (configHealthy ? "HEALTHY" : "UNHEALTHY"));
                  break;
               case "update-config":
                  player.sendMessage("§eUpdating configuration files...");
                  ConfigUpdater.updateAllConfigs(this.plugin);
                  player.sendMessage("§aConfiguration update completed! Check console for details.");
                  break;
               case "force-update-config":
                  player.sendMessage("§eForce updating all configuration files...");
                  ConfigUpdater.forceUpdateAllConfigs(this.plugin);
                  player.sendMessage("§aForce update completed! Check console for details.");
                  break;
               case "backup-config":
                  player.sendMessage("§eCreating configuration backups...");

                  for(String configFile : List.of("config.yml", "messages.yml", "gui.yml", "commands.yml")) {
                     ConfigUpdater.createConfigBackup(this.plugin, configFile);
                  }

                  player.sendMessage("§aConfiguration backups created! Check backups folder.");
                  break;
               case "cleanup-backups":
                  player.sendMessage("§eCleaning up old backup files...");
                  ConfigUpdater.cleanupAllOldBackups(this.plugin);
                  player.sendMessage("§aBackup cleanup completed! Check console for details.");
                  break;
               default:
                  player.sendMessage("§cUnknown action: " + action);
                  player.sendMessage("§7Available actions: test, migrate, validate, backup, config, update-config, force-update-config, backup-config, cleanup-backups");
            }
         } catch (Exception e) {
            player.sendMessage("§cCommand failed with exception: " + e.getMessage());
            this.plugin.getLogger().severe("TestMigrationCommand failed: " + e.getMessage());
         }
      }

   }

   private void handlePerformance(Player player, String[] args) {
      if (!player.hasPermission("justteams.admin.performance")) {
         this.plugin.getMessageManager().sendMessage(player, "no_permission");
      } else if (args.length < 3) {
         this.showPerformanceHelp(player);
      } else {
         switch (args[2].toLowerCase()) {
            case "database" -> this.showDatabaseStats(player);
            case "cache" -> this.showCacheStats(player);
            case "tasks" -> this.showTaskStats(player);
            case "optimize" -> this.optimizeDatabase(player);
            case "cleanup" -> this.cleanupCaches(player);
            default -> this.showPerformanceHelp(player);
         }

      }
   }

   private void showPerformanceHelp(Player player) {
      player.sendMessage("§6=== JustTeams Performance Commands ===");
      player.sendMessage("§e/team admin performance database §7- Show database statistics");
      player.sendMessage("§e/team admin performance cache §7- Show cache statistics");
      player.sendMessage("§e/team admin performance tasks §7- Show task statistics");
      player.sendMessage("§e/team admin performance optimize §7- Optimize database");
      player.sendMessage("§e/team admin performance cleanup §7- Cleanup caches");
   }

   private void showDatabaseStats(Player player) {
      player.sendMessage("§6=== Database Statistics ===");
      IDataStorage var3 = this.plugin.getStorageManager().getStorage();
      if (var3 instanceof DatabaseStorage dbStorage) {
         try {
            Map<String, Object> stats = dbStorage.getDatabaseStats();
            stats.forEach((key, value) -> player.sendMessage("§e" + key + ": §f" + String.valueOf(value)));
         } catch (Exception e) {
            player.sendMessage("§cError retrieving database stats: " + e.getMessage());
         }
      } else {
         player.sendMessage("§cDatabase storage not in use");
      }

   }

   private void showCacheStats(Player player) {
      player.sendMessage("§6=== Cache Statistics ===");

      try {
         if (this.plugin.getTeamManager() != null) {
            player.sendMessage("§eTeam Cache: §f" + this.plugin.getTeamManager().getTeamNameCache().size() + " teams");
            player.sendMessage("§ePlayer Cache: §f" + this.plugin.getTeamManager().getPlayerTeamCache().size() + " players");
         }

         player.sendMessage("§eGUI Update Throttle: §aActive");
         player.sendMessage("§eTask Runner: §f" + this.plugin.getTaskRunner().getActiveTaskCount() + " active tasks");
      } catch (Exception e) {
         player.sendMessage("§cError retrieving cache statistics: " + e.getMessage());
      }

   }

   private void showTaskStats(Player player) {
      player.sendMessage("§6=== Task Statistics ===");
      int var10001 = this.plugin.getTaskRunner().getActiveTaskCount();
      player.sendMessage("§eActive Tasks: §f" + var10001);
      String var2 = this.plugin.getTaskRunner().isFolia() ? "Enabled" : "Disabled";
      player.sendMessage("§eFolia Support: §f" + var2);
      player.sendMessage("§ePaper Support: §f" + (this.plugin.getTaskRunner().isPaper() ? "Enabled" : "Disabled"));
   }

   private void optimizeDatabase(Player player) {
      player.sendMessage("§eOptimizing database...");

      try {
         IDataStorage var3 = this.plugin.getStorageManager().getStorage();
         if (var3 instanceof DatabaseStorage dbStorage) {
            dbStorage.optimizeDatabase();
            player.sendMessage("§aDatabase optimization completed!");
         } else {
            player.sendMessage("§cDatabase optimization not available for current storage type");
         }
      } catch (Exception e) {
         player.sendMessage("§cDatabase optimization failed: " + e.getMessage());
      }

   }

   private void cleanupCaches(Player player) {
      player.sendMessage("§eCleaning up caches...");

      try {
         if (this.plugin.getTeamManager() != null) {
            this.plugin.getTeamManager().getTeamNameCache().clear();
            this.plugin.getTeamManager().getPlayerTeamCache().clear();
         }

         player.sendMessage("§aCache cleanup completed!");
      } catch (Exception e) {
         player.sendMessage("§cCache cleanup failed: " + e.getMessage());
      }

   }

   private void handlePlatform(Player player) {
      if (!this.plugin.getConfigManager().isBedrockSupportEnabled()) {
         this.plugin.getMessageManager().sendMessage(player, "feature_disabled");
      } else {
         boolean isBedrock = this.plugin.getBedrockSupport().isBedrockPlayer(player);
         String platform = isBedrock ? "Bedrock Edition" : "Java Edition";
         String platformColor = isBedrock ? "<#00D4FF>" : "<#00FF00>";
         this.plugin.getMessageManager().sendRawMessage(player, "<white>Your Platform: " + platformColor + platform + "</white>");
         if (isBedrock) {
            String gamertag = this.plugin.getBedrockSupport().getBedrockGamertag(player);
            if (gamertag != null && !gamertag.equals(player.getName())) {
               this.plugin.getMessageManager().sendRawMessage(player, "<gray>Xbox Gamertag: <white>" + gamertag + "</white>");
            }

            UUID javaUuid = this.plugin.getBedrockSupport().getJavaEditionUuid(player);
            if (!javaUuid.equals(player.getUniqueId())) {
               this.plugin.getMessageManager().sendRawMessage(player, "<gray>Java Edition UUID: <white>" + javaUuid.toString() + "</white>");
            }
         }

         this.plugin.getMessageManager().sendRawMessage(player, "<gray>Current UUID: <white>" + player.getUniqueId().toString() + "</white>");
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!(sender instanceof Player player)) {
         return new ArrayList();
      } else if (args.length == 1) {
         List<String> completions = new ArrayList();
         completions.add("accept");
         completions.add("create");
         completions.add("deny");
         completions.add("disband");
         completions.add("invite");
         completions.add("invites");
         completions.add("join");
         completions.add("unjoin");
         completions.add("kick");
         completions.add("leave");
         completions.add("promote");
         completions.add("demote");
         completions.add("info");
         completions.add("sethome");
         completions.add("delhome");
         completions.add("home");
         if (this.plugin.getConfigManager().isTeamTagEnabled()) {
            completions.add("settag");
         }

         completions.add("setdesc");
         if (this.plugin.getConfig().getBoolean("features.team_rename", true)) {
            completions.add("rename");
         }

         completions.add("transfer");
         completions.add("pvp");
         completions.add("bank");
         completions.add("blacklist");
         completions.add("unblacklist");
         completions.add("settings");
         completions.add("enderchest");
         if (this.plugin.getQuestManager() != null && this.plugin.getQuestManager().isEnabled()) {
            completions.add("quests");
         }

         completions.add("public");
         completions.add("requests");
         completions.add("setwarp");
         completions.add("delwarp");
         completions.add("warp");
         completions.add("warps");
         completions.add("ally");
         completions.add("top");
         completions.add("admin");
         completions.add("platform");
         completions.add("reload");
         completions.add("chat");
         completions.add("setcolor");
         completions.add("help");
         return (List)completions.stream().filter((cmd) -> cmd.toLowerCase().startsWith(args[0].toLowerCase())).collect(Collectors.toList());
      } else {
         if (args.length == 2) {
            switch (args[0].toLowerCase()) {
               case "accept":
               case "deny":
                  return (List)this.teamManager.getPendingInviteSuggestions(player.getUniqueId()).stream().filter((name) -> name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
               case "invite":
                  Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
                  if (team != null) {
                     return (List)Bukkit.getOnlinePlayers().stream().filter((target) -> !team.isMember(target.getUniqueId()) && this.teamManager.getPlayerTeam(target.getUniqueId()) == null).map(Player::getName).filter((name) -> name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                  }
                  break;
               case "kick":
               case "promote":
               case "demote":
               case "transfer":
                  team = this.teamManager.getPlayerTeam(player.getUniqueId());
                  if (team != null) {
                     return (List)team.getMembers().stream().map((member) -> Bukkit.getOfflinePlayer(member.getPlayerUuid()).getName()).filter((name) -> name != null && name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                  }
                  break;
               case "join":
                  return (List)this.teamManager.getTeamNameCache().values().stream().flatMap(TeamCommand::teamRefSuggestions).filter((name) -> name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
               case "info":
                  return (List)this.teamManager.getTeamNameCache().values().stream().flatMap(TeamCommand::teamRefSuggestions).filter((name) -> name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
               case "setwarp":
               case "delwarp":
               case "warp":
                  return new ArrayList();
               case "blacklist":
                  return new ArrayList();
               case "unblacklist":
                  return new ArrayList();
               case "ally":
                  return (List)List.of("add", "request", "remove", "break", "accept", "deny", "toggle", "list").stream().filter((cmd) -> cmd.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
               case "admin":
                  if (this.hasAdminPermission(player)) {
                     return (List)List.of("gui", "disband", "home", "warps", "enderchest", "testmigration", "performance").stream().filter((cmd) -> cmd.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                  }
                  break;
               case "serveralias":
                  if (this.hasAdminPermission(player)) {
                     return (List)List.of("set", "remove", "list").stream().filter((cmd) -> cmd.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                  }
                  break;
               case "setcolor":
                  List<String> colors = (List)Arrays.stream(ChatColor.values()).filter(ChatColor::isColor).map(Enum::name).collect(Collectors.toList());
                  colors.add("RESET");
                  return (List)colors.stream().filter((name) -> name.toUpperCase().startsWith(args[1].toUpperCase())).collect(Collectors.toList());
            }
         }

         if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("ally")) {
               String allySubCommand = args[1].toLowerCase();
               if (allySubCommand.equals("add") || allySubCommand.equals("request") || allySubCommand.equals("remove") || allySubCommand.equals("break")) {
                  return (List)this.teamManager.getTeamNameCache().values().stream().flatMap(TeamCommand::teamRefSuggestions).filter((name) -> name.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
               }
            } else if (subCommand.equals("admin") && this.hasAdminPermission(player)) {
               if (args[1].toLowerCase().equals("disband") || args[1].toLowerCase().equals("home") || args[1].toLowerCase().equals("enderchest") || args[1].toLowerCase().equals("warps")) {
                  return (List)this.teamManager.getTeamNameCache().values().stream().flatMap(TeamCommand::teamRefSuggestions).filter((name) -> name.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
               }

               if (args[1].toLowerCase().equals("testmigration")) {
                  return (List)List.of("test", "migrate", "validate", "backup", "config", "update-config", "force-update-config", "backup-config", "cleanup-backups").stream().filter((cmd) -> cmd.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
               }

               if (args[1].toLowerCase().equals("performance")) {
                  return (List)List.of("database", "cache", "tasks", "optimize", "cleanup").stream().filter((cmd) -> cmd.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
               }
            }
         }

         return new ArrayList();
      }
   }

   private void handleSetColor(Player player, String[] args) {
      if (this.checkFeatureEnabled(player, "team_settings")) {
         if (!player.hasPermission("justteams.setcolor")) {
            this.plugin.getMessageManager().sendMessage(player, "no_permission");
         } else if (args.length < 2) {
            this.plugin.getMessageManager().sendRawMessage(player, "<gray>Usage: /team setcolor <color|reset|#hex #hex></gray>");
            this.plugin.getMessageManager().sendRawMessage(player, "<gray>Examples:</gray>");
            this.plugin.getMessageManager().sendRawMessage(player, "<gray>  /team setcolor RED</gray>");
            this.plugin.getMessageManager().sendRawMessage(player, "<gray>  /team setcolor &c <dark_gray>(legacy format)</dark_gray></gray>");
            this.plugin.getMessageManager().sendRawMessage(player, "<gray>  /team setcolor #FF0000 #00FF00 <dark_gray>(gradient)</dark_gray></gray>");
         } else {
            String colorName = args[1];
            String secondColor = args.length >= 3 ? args[2] : null;
            this.teamManager.setTeamColor(player, colorName, secondColor);
         }
      }
   }
}
