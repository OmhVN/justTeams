package eu.kotori.justTeams.core.util;
import eu.kotori.justTeams.api.team.*;
import eu.kotori.justTeams.api.team.*;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.team.TeamPlayer;
import eu.kotori.justTeams.api.team.TeamRole;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PAPIExpansion extends PlaceholderExpansion {
   private static final long LEADERBOARD_REFRESH_INTERVAL_MS = 60000L;
   private final JustTeams plugin;
   private final DecimalFormat kdrFormat = new DecimalFormat("#.##");
   private volatile List<Team> cachedTopKills = Collections.emptyList();
   private volatile List<Team> cachedTopBalance = Collections.emptyList();
   private volatile List<Team> cachedTopMembers = Collections.emptyList();
   private volatile List<Team> cachedTopPoints = Collections.emptyList();
   private volatile long lastCacheUpdate = 0L;
   private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

   public PAPIExpansion(JustTeams plugin) {
      this.plugin = plugin;
   }

   private String buildTabSortKey(Team team, OfflinePlayer player) {
      String playerName = player.getName() != null ? player.getName().toLowerCase() : "zzzz";
      if (team == null) {
         return "9999999999_3_" + playerName;
      } else {
         String paddedTeamId = String.format("%010d", team.getId());
         int roleSort = 3;
         if (player.getUniqueId() != null) {
            TeamPlayer member = team.getMember(player.getUniqueId());
            if (member != null) {
               switch (member.getRole()) {
                  case OWNER -> roleSort = 0;
                  case CO_OWNER -> roleSort = 1;
                  case MEMBER -> roleSort = 2;
                  default -> roleSort = 3;
               }
            }
         }

         return paddedTeamId + "_" + roleSort + "_" + playerName;
      }
   }

   private String renderTagText(String raw) {
      if (raw != null && !raw.isEmpty()) {
         boolean hasMiniMessage = raw.contains("<") && raw.contains(">");
         boolean hasLegacy = raw.indexOf(38) >= 0 || raw.indexOf(167) >= 0;
         if (!hasMiniMessage && !hasLegacy) {
            return raw;
         } else {
            Component component;
            try {
               if (hasMiniMessage) {
                  component = this.plugin.getMiniMessage().deserialize(raw);
               } else {
                  component = LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
               }
            } catch (Exception var7) {
               return raw;
            }

            try {
               return (String)this.plugin.getMiniMessage().serialize(component);
            } catch (Exception var6) {
               return LegacyComponentSerializer.legacySection().serialize(component);
            }
         }
      } else {
         return "";
      }
   }

   private String getColoredTag(Team team) {
      if (team != null && this.plugin.getConfigManager().isTeamTagEnabled()) {
         if (team.hasGradient()) {
            String startColor = team.getGradientStart();
            String endColor = team.getGradientEnd();
            return startColor != null && endColor != null ? "<gradient:" + startColor + ":" + endColor + ">" + team.getTag() + "</gradient>" : team.getTag();
         } else if (team.getColor() != null) {
            String var10000 = team.getColor().toString();
            return var10000 + team.getTag() + "§r";
         } else {
            return team.getTag();
         }
      } else {
         return "";
      }
   }

   private String getColoredName(Team team) {
      if (team == null) {
         return "";
      } else {
         String name = team.getName();
         if (team.hasGradient()) {
            String startColor = team.getGradientStart();
            String endColor = team.getGradientEnd();
            return startColor != null && endColor != null ? "<gradient:" + startColor + ":" + endColor + ">" + name + "</gradient>" : name;
         } else if (team.getColor() != null) {
            String var10000 = team.getColor().toString();
            return var10000 + name + "§r";
         } else {
            return name;
         }
      }
   }

   private void maybeRefreshLeaderboardCache() {
      if (System.currentTimeMillis() - this.lastCacheUpdate >= 60000L) {
         if (this.refreshInProgress.compareAndSet(false, true)) {
            this.plugin.getTaskRunner().runAsync(() -> {
               try {
                  this.updateLeaderboardCache();
               } finally {
                  this.refreshInProgress.set(false);
               }

            });
         }
      }
   }

   private void updateLeaderboardCache() {
      List<Team> allTeams = this.plugin.getTeamManager().getAllTeams();
      if (allTeams == null) {
         this.lastCacheUpdate = System.currentTimeMillis();
      } else {
         List<Team> byKills = new ArrayList(allTeams);
         byKills.sort((t1, t2) -> Integer.compare(t2.getKills(), t1.getKills()));
         this.cachedTopKills = topSnapshot(byKills);
         List<Team> byBalance = new ArrayList(allTeams);
         byBalance.sort((t1, t2) -> Double.compare(t2.getBalance(), t1.getBalance()));
         this.cachedTopBalance = topSnapshot(byBalance);
         List<Team> byMembers = new ArrayList(allTeams);
         byMembers.sort((t1, t2) -> Integer.compare(t2.getMembers().size(), t1.getMembers().size()));
         this.cachedTopMembers = topSnapshot(byMembers);
         List<Team> byPoints = new ArrayList(allTeams);
         byPoints.sort((t1, t2) -> Long.compare(t2.getPoints(), t1.getPoints()));
         this.cachedTopPoints = topSnapshot(byPoints);
         this.lastCacheUpdate = System.currentTimeMillis();
      }
   }

   private static List<Team> topSnapshot(List<Team> sorted) {
      int size = Math.min(sorted.size(), 10);
      return List.copyOf(sorted.subList(0, size));
   }

   public @NotNull String getIdentifier() {
      return "justteams";
   }

   public @NotNull String getAuthor() {
      return (String)this.plugin.getDescription().getAuthors().get(0);
   }

   public @NotNull String getVersion() {
      return this.plugin.getDescription().getVersion();
   }

   public boolean persist() {
      return true;
   }

   public boolean canRegister() {
      return true;
   }

   @Override
   public String onPlaceholderRequest(Player player, @NotNull String params) {
      return this.onRequest(player, params);
   }

   @Override
   public String onRequest(OfflinePlayer player, @NotNull String params) {
      if (player == null) {
         return "";
      } else {
         try {
            if (!params.startsWith("top_name_") && !params.startsWith("top_tag_") && !params.startsWith("top_colored_tag_") && !params.startsWith("top_amount_") && !params.startsWith("top_kills_amount_") && !params.startsWith("top_balance_amount_") && !params.startsWith("top_members_amount_") && !params.startsWith("top_points_amount_")) {
               if (params.startsWith("top_kills_") || params.startsWith("leaderboard_kills_")) {
                  this.maybeRefreshLeaderboardCache();
                  List<Team> cachedTopKills = this.cachedTopKills;
                  String[] parts = params.split("_");
                  if (parts.length >= 4) {
                     try {
                        label973: {
                           int pos = Integer.parseInt(parts[params.startsWith("leaderboard") ? 2 : 2]);
                           if (pos >= 1 && pos <= cachedTopKills.size()) {
                              Team topTeam = (Team)cachedTopKills.get(pos - 1);
                              String type = String.join("_", (CharSequence[])Arrays.copyOfRange(parts, 3, parts.length));
                              if (type.equalsIgnoreCase("name")) {
                                 return topTeam.getName();
                              }

                              if (type.equalsIgnoreCase("tag")) {
                                 return topTeam.getTag();
                              }

                              if (!type.equalsIgnoreCase("colored_tag") && !type.equalsIgnoreCase("gradient_tag")) {
                                 if (!type.equalsIgnoreCase("amount") && !type.equalsIgnoreCase("value")) {
                                    break label973;
                                 }

                                 return String.valueOf(topTeam.getKills());
                              }

                              return this.getColoredTag(topTeam);
                           }

                           return "---";
                        }
                     } catch (NumberFormatException var28) {
                     }
                  }
               }

               if (params.startsWith("top_points_") || params.startsWith("leaderboard_points_")) {
                  this.maybeRefreshLeaderboardCache();
                  List<Team> cachedTopPoints = this.cachedTopPoints;
                  String[] parts = params.split("_");
                  if (parts.length >= 4) {
                     try {
                        label929: {
                           int pos = Integer.parseInt(parts[2]);
                           String type = String.join("_", (CharSequence[])Arrays.copyOfRange(parts, 3, parts.length));
                           if (pos >= 1 && pos <= cachedTopPoints.size()) {
                              Team topTeam = (Team)cachedTopPoints.get(pos - 1);
                              if (type.equalsIgnoreCase("name")) {
                                 return topTeam.getName();
                              }

                              if (type.equalsIgnoreCase("tag")) {
                                 return topTeam.getTag();
                              }

                              if (!type.equalsIgnoreCase("colored_tag") && !type.equalsIgnoreCase("gradient_tag")) {
                                 if (!type.equalsIgnoreCase("amount") && !type.equalsIgnoreCase("value")) {
                                    break label929;
                                 }

                                 return String.valueOf(topTeam.getPoints());
                              }

                              return this.getColoredTag(topTeam);
                           }

                           return "---";
                        }
                     } catch (NumberFormatException var27) {
                     }
                  }
               }

               if (params.startsWith("top_balance_") || params.startsWith("top_members_") || params.startsWith("leaderboard_balance_") || params.startsWith("leaderboard_members_")) {
                  this.maybeRefreshLeaderboardCache();
                  String[] parts = params.split("_");
                  if (parts.length >= 4) {
                     try {
                        label978: {
                           int pos = Integer.parseInt(parts[2]);
                           String type = String.join("_", (CharSequence[])Arrays.copyOfRange(parts, 3, parts.length));
                           String category = parts[1];
                           List<Team> cache = category.equals("balance") ? this.cachedTopBalance : this.cachedTopMembers;
                           if (pos >= 1 && pos <= cache.size()) {
                              Team topTeam = (Team)cache.get(pos - 1);
                              if (type.equalsIgnoreCase("name")) {
                                 return topTeam.getName();
                              }

                              if (type.equalsIgnoreCase("tag")) {
                                 return topTeam.getTag();
                              }

                              if (!type.equalsIgnoreCase("colored_tag") && !type.equalsIgnoreCase("gradient_tag")) {
                                 if (category.equals("balance")) {
                                    if (!type.equalsIgnoreCase("value") && !type.equalsIgnoreCase("amount")) {
                                       break label978;
                                    }

                                    return this.kdrFormat.format(topTeam.getBalance());
                                 }

                                 if (!type.equalsIgnoreCase("value") && !type.equalsIgnoreCase("amount")) {
                                    break label978;
                                 }

                                 return String.valueOf(topTeam.getMembers().size());
                              }

                              return this.getColoredTag(topTeam);
                           }

                           return "---";
                        }
                     } catch (NumberFormatException var26) {
                     }
                  }
               }

               if (params.equalsIgnoreCase("has_team")) {
                  return this.plugin.getTeamManager().getPlayerTeamCached(player.getUniqueId()) != null ? "true" : "false";
               } else {
                  Team team = this.plugin.getTeamManager().getPlayerTeamCached(player.getUniqueId());
                  if (params.equalsIgnoreCase("display")) {
                     if (team == null) {
                        return this.plugin.getGuiConfigManager().getPlaceholder("team_display.no_team", "<gray>No Team</gray>");
                     } else {
                        String format = this.plugin.getGuiConfigManager().getPlaceholder("team_display.format", "<team_color><team_icon><team_tag></team_color>");
                        String teamIcon = this.plugin.getGuiConfigManager().getPlaceholder("team_display.team_icon", "⚔ ");
                        String teamColor = this.plugin.getGuiConfigManager().getPlaceholder("team_display.team_color", "#4C9DDE");
                        String tagPrefix = this.plugin.getGuiConfigManager().getPlaceholder("team_display.tag_prefix", "[");
                        String tagSuffix = this.plugin.getGuiConfigManager().getPlaceholder("team_display.tag_suffix", "]");
                        String tagColor = this.plugin.getGuiConfigManager().getPlaceholder("team_display.tag_color", "#FFD700");
                        boolean showIcon = this.plugin.getGuiConfigManager().getPlaceholder("team_display.show_icon", "true").equals("true");
                        boolean showTag = this.plugin.getGuiConfigManager().getPlaceholder("team_display.show_tag", "true").equals("true") && this.plugin.getConfigManager().isTeamTagEnabled();
                        boolean showName = this.plugin.getGuiConfigManager().getPlaceholder("team_display.show_name", "false").equals("true");
                        String formattedTag = "";
                        if (showTag) {
                           String rawTag = tagPrefix + team.getTag() + tagSuffix;
                           formattedTag = "<" + tagColor + ">" + rawTag + "</" + tagColor + ">";
                        }

                        String adminCheck = format.replace("<team_name>", team.getName());
                        adminCheck = adminCheck.replace("<team_tag>", formattedTag);
                        adminCheck = adminCheck.replace("<team_color>", "<" + teamColor + ">");
                        adminCheck = adminCheck.replace("</team_color>", "</" + teamColor + ">");
                        adminCheck = adminCheck.replace("<team_icon>", showIcon ? teamIcon : "");
                        adminCheck = adminCheck.replace("%team_name%", team.getName());
                        adminCheck = adminCheck.replace("%team_tag%", formattedTag);
                        adminCheck = adminCheck.replace("%team_icon%", showIcon ? teamIcon : "");
                        return adminCheck;
                     }
                  } else if (!params.equalsIgnoreCase("tab_sort") && !params.equalsIgnoreCase("sort_key")) {
                     if (params.equalsIgnoreCase("tag_silent")) {
                        return team != null && this.plugin.getConfigManager().isTeamTagEnabled() ? this.renderTagText(team.getColoredTag()) : "";
                     } else if (params.equalsIgnoreCase("tag_formatted")) {
                        if (team != null && this.plugin.getConfigManager().isTeamTagEnabled()) {
                           String prefix = this.plugin.getGuiConfigManager().getPlaceholder("team_display.tag_prefix", "[");
                           String suffix = this.plugin.getGuiConfigManager().getPlaceholder("team_display.tag_suffix", "]");
                           String inner = prefix + team.getTag() + suffix;
                           if (team.hasGradient() && team.getGradientStart() != null && team.getGradientEnd() != null) {
                              String var84 = team.getGradientStart();
                              return "<gradient:" + var84 + ":" + team.getGradientEnd() + ">" + inner + "</gradient>";
                           } else if (team.getColor() != null) {
                              String var83 = team.getColor().toString();
                              return var83 + inner + "§r";
                           } else {
                              return inner;
                           }
                        } else {
                           return "";
                        }
                     } else if (params.equalsIgnoreCase("tag_raw")) {
                        return team != null && this.plugin.getConfigManager().isTeamTagEnabled() ? team.getTag() : "";
                     } else if (!params.equalsIgnoreCase("color_tag") && !params.equalsIgnoreCase("colored_tag") && !params.equalsIgnoreCase("gradient_tag")) {
                        if (team == null) {
                           return this.plugin.getMessageManager().getRawMessage("no_team_placeholder");
                        } else {
                           switch (params.toLowerCase()) {
                              case "name":
                              case "team":
                              case "team_name":
                                 return team.getName();
                              case "tag":
                              case "team_tag":
                                 return this.plugin.getConfigManager().isTeamTagEnabled() ? this.renderTagText(team.getTag()) : "";
                              case "color_name":
                              case "colored_name":
                              case "gradient_name":
                                 return this.getColoredName(team);
                              case "team_color":
                                 ChatColor teamColor = team.getColor();
                                 return teamColor != null ? teamColor.name() : "WHITE";
                              case "team_color_code":
                              case "color_code":
                                 ChatColor colorCode = team.getColor();
                                 return colorCode != null ? colorCode.toString() : ChatColor.WHITE.toString();
                              case "description":
                              case "team_description":
                                 return team.getDescription();
                              case "owner":
                              case "team_owner":
                              case "leader":
                                 return this.plugin.getServer().getOfflinePlayer(team.getOwnerUuid()).getName();
                              case "team_id":
                                 return String.valueOf(team.getId());
                              case "member_count":
                              case "team_members":
                                 return String.valueOf(team.getMembers().size());
                              case "max_members":
                              case "team_max_members":
                                 Player owner = this.plugin.getServer().getPlayer(team.getOwnerUuid());
                                 int maxSize = owner != null ? this.plugin.getConfigManager().getMaxTeamSize(owner) : this.plugin.getConfigManager().getMaxTeamSize();
                                 return String.valueOf(maxSize);
                              case "members_online":
                              case "online":
                              case "online_members":
                                 if (this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
                                    return String.valueOf(team.getMembers().stream().filter((p) -> this.plugin.getTeamManager().isPlayerOnlineAnywhere(p.getPlayerUuid())).count());
                                 }

                                 return String.valueOf(team.getMembers().stream().filter((p) -> p.isOnline()).count());
                              case "offline_members":
                                 if (this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
                                    return String.valueOf(team.getMembers().stream().filter((p) -> !this.plugin.getTeamManager().isPlayerOnlineAnywhere(p.getPlayerUuid())).count());
                                 }

                                 return String.valueOf(team.getMembers().stream().filter((p) -> !p.isOnline()).count());
                              case "role":
                                 TeamPlayer member = team.getMember(player.getUniqueId());
                                 return member != null ? member.getRole().name() : "Unknown";
                              case "role_level":
                                 TeamPlayer memberForLevel = team.getMember(player.getUniqueId());
                                 if (memberForLevel == null) {
                                    return "0";
                                 } else {
                                    switch (memberForLevel.getRole()) {
                                       case OWNER -> {
                                          return "4";
                                       }
                                       case CO_OWNER -> {
                                          return "3";
                                       }
                                       case MEMBER -> {
                                          return "1";
                                       }
                                       default -> {
                                          return "0";
                                       }
                                    }
                                 }
                              case "role_icon":
                                 TeamPlayer memberIcon = team.getMember(player.getUniqueId());
                                 return memberIcon != null ? this.plugin.getGuiConfigManager().getRoleIcon(memberIcon.getRole().name()) : "";
                              case "role_color":
                                 TeamPlayer memberColor = team.getMember(player.getUniqueId());
                                 return memberColor != null ? this.plugin.getGuiConfigManager().getRoleColor(memberColor.getRole().name()) : "#FFFFFF";
                              case "is_owner":
                                 return team.getOwnerUuid().equals(player.getUniqueId()) ? "true" : "false";
                              case "is_admin":
                                 TeamPlayer adminCheck = team.getMember(player.getUniqueId());
                                 if (adminCheck == null) {
                                    return "false";
                                 }

                                 return adminCheck.getRole() != TeamRole.OWNER && adminCheck.getRole() != TeamRole.CO_OWNER ? "false" : "true";
                              case "is_manager":
                                 TeamPlayer managerCheck = team.getMember(player.getUniqueId());
                                 return managerCheck != null && managerCheck.getRole() == TeamRole.MANAGER ? "true" : "false";
                              case "is_co_owner":
                                 TeamPlayer memberRole = team.getMember(player.getUniqueId());
                                 return memberRole != null && memberRole.getRole().name().equals("CO_OWNER") ? "true" : "false";
                              case "is_member":
                                 return team.getMember(player.getUniqueId()) != null ? "true" : "false";
                              case "kills":
                              case "team_kills":
                                 return String.valueOf(team.getKills());
                              case "deaths":
                              case "team_deaths":
                                 return String.valueOf(team.getDeaths());
                              case "kd":
                              case "kdr":
                              case "team_kdr":
                                 if (team.getDeaths() == 0) {
                                    return String.valueOf(team.getKills());
                                 }

                                 return this.kdrFormat.format((double)team.getKills() / (double)team.getDeaths());
                              case "balance":
                              case "team_balance":
                              case "bank_balance":
                                 DecimalFormat formatter = new DecimalFormat(this.plugin.getConfigManager().getCurrencyFormat());
                                 return formatter.format(team.getBalance());
                              case "bank_formatted":
                                 DecimalFormat formatterWithSymbol = new DecimalFormat(this.plugin.getConfigManager().getCurrencyFormat());
                                 String var82 = formatterWithSymbol.format(team.getBalance());
                                 return "$" + var82;
                              case "bank_balance_raw":
                                 return String.valueOf(team.getBalance());
                              case "team_public":
                                 return team.isPublic() ? "Public" : "Private";
                              case "team_pvp":
                              case "pvp_status":
                                 return team.isPvpEnabled() ? "Enabled" : "Disabled";
                              case "pvp_enabled":
                                 return team.isPvpEnabled() ? "true" : "false";
                              case "pvp_toggle_cooldown":
                                 long remaining = this.plugin.getTeamManager().getPvpToggleCooldownRemaining(team.getId());
                                 return remaining > 0L ? remaining + "s" : "Ready";
                              case "is_public":
                                 return team.isPublic() ? "true" : "false";
                              case "has_home":
                                 return team.getHomeLocation() != null ? "true" : "false";
                              case "home_location":
                                 if (team.getHomeLocation() == null) {
                                    return "No home set";
                                 }

                                 Location loc = team.getHomeLocation();
                                 String var81 = loc.getWorld().getName();
                                 return var81 + ", " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
                              case "warp_count":
                                 return String.valueOf(team.getWarpCount());
                              case "max_warps":
                                 return String.valueOf(this.plugin.getConfigManager().getMaxWarpsPerTeam());
                              case "points":
                              case "team_points":
                                 return String.valueOf(team.getPoints());
                              case "team_size":
                              case "size":
                                 return String.valueOf(team.getMembers().size());
                              case "team_capacity":
                              case "team_max_size":
                              case "max_size":
                                 Player teamOwner = this.plugin.getServer().getPlayer(team.getOwnerUuid());
                                 int teamMaxSize = teamOwner != null ? this.plugin.getConfigManager().getMaxTeamSize(teamOwner) : this.plugin.getConfigManager().getMaxTeamSize();
                                 return String.valueOf(teamMaxSize);
                              case "team_full":
                                 Player fullCheckOwner = this.plugin.getServer().getPlayer(team.getOwnerUuid());
                                 int fullCheckMaxSize = fullCheckOwner != null ? this.plugin.getConfigManager().getMaxTeamSize(fullCheckOwner) : this.plugin.getConfigManager().getMaxTeamSize();
                                 return team.getMembers().size() >= fullCheckMaxSize ? "true" : "false";
                              case "plain_name":
                                 return team.getPlainName();
                              case "plain_tag":
                                 return this.plugin.getConfigManager().isTeamTagEnabled() ? team.getPlainTag() : "";
                              case "join_date":
                                 TeamPlayer memberInfo = team.getMember(player.getUniqueId());
                                 if (memberInfo != null) {
                                    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
                                    return memberInfo.getJoinDate().atZone(ZoneId.systemDefault()).format(dateFormatter);
                                 }

                                 return "Unknown";
                              case "created_at":
                                 if (team.getCreationDate() != null) {
                                    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
                                    return team.getCreationDate().toInstant().atZone(ZoneId.systemDefault()).format(dateFormatter);
                                 }

                                 return "Unknown";
                              case "platform":
                                 return this.plugin.getBedrockSupport().getPlatformDisplayName(this.plugin.getServer().getPlayer(player.getUniqueId()));
                              case "status_indicator":
                                 return this.plugin.getGuiConfigManager().getStatusIcon(this.plugin.getServer().getPlayer(player.getUniqueId()) != null);
                              default:
                                 return null;
                           }
                        }
                     } else {
                        return this.getColoredTag(team);
                     }
                  } else {
                     return this.buildTabSortKey(team, player);
                  }
               }
            } else {
               this.maybeRefreshLeaderboardCache();
               List<Team> cache;
               switch (this.plugin.getConfig().getString("settings.top_default_metric", "kills").toLowerCase()) {
                  case "balance" -> cache = this.cachedTopBalance;
                  case "members" -> cache = this.cachedTopMembers;
                  case "points" -> cache = this.cachedTopPoints;
                  default -> cache = this.cachedTopKills;
               }

               String[] parts = params.split("_");
               String field = parts[1];
               int posIndex = 2;
               if (field.equals("colored")) {
                  field = "colored_tag";
                  posIndex = 3;
               }

               if (parts.length > posIndex + 1 && parts[posIndex].equals("amount")) {
                  switch (parts[1]) {
                     case "kills" -> cache = this.cachedTopKills;
                     case "balance" -> cache = this.cachedTopBalance;
                     case "members" -> cache = this.cachedTopMembers;
                     case "points" -> cache = this.cachedTopPoints;
                  }

                  field = "amount_" + parts[1];
                  posIndex = 2;
               }

               try {
                  int pos = Integer.parseInt(parts[posIndex]);
                  if (pos >= 1 && pos <= cache.size()) {
                     Team t = (Team)cache.get(pos - 1);
                     String var10000;
                     switch (field) {
                        case "name":
                           var10000 = t.getName();
                           break;
                        case "tag":
                           var10000 = t.getTag();
                           break;
                        case "colored_tag":
                           var10000 = this.getColoredTag(t);
                           break;
                        case "amount":
                        case "amount_kills":
                           var10000 = String.valueOf(t.getKills());
                           break;
                        case "amount_balance":
                           var10000 = (new DecimalFormat(this.plugin.getConfigManager().getCurrencyFormat())).format(t.getBalance());
                           break;
                        case "amount_members":
                           var10000 = String.valueOf(t.getMembers().size());
                           break;
                        case "amount_points":
                           var10000 = String.valueOf(t.getPoints());
                           break;
                        default:
                           var10000 = "---";
                     }

                     return var10000;
                  } else {
                     return "---";
                  }
               } catch (NumberFormatException var25) {
                  return "---";
               }
            }
         } catch (Exception e) {
            this.plugin.getLogger().warning("Error processing PlaceholderAPI request: " + params + " for player: " + player.getName() + " - " + e.getMessage());
            return "";
         }
      }
   }
}