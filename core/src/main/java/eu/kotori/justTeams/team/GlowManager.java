package eu.kotori.justTeams.core.team;
import eu.kotori.justTeams.api.team.*;
import eu.kotori.justTeams.api.team.*;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.CollisionRule;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.NameTagVisibility;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.OptionData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode;
import eu.kotori.justTeams.JustTeams;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class GlowManager implements Listener {
   private final JustTeams plugin;
   private boolean enabled;
   private boolean usePacketEvents;
   private final boolean onlyShowOwnTeam;
   private final Map<UUID, Map<UUID, ChatColor>> glowingCache = new ConcurrentHashMap();
   private final Set<UUID> colorTeamsCreated = ConcurrentHashMap.newKeySet();
   private final ConcurrentHashMap<Integer, UUID> entityIdToUuid = new ConcurrentHashMap();

   public GlowManager(JustTeams plugin) {
      this.plugin = plugin;
      this.enabled = plugin.getConfig().getBoolean("features.team_glow", true);
      this.onlyShowOwnTeam = plugin.getConfig().getBoolean("settings.glow.only_show_own_team", true);
      if (this.enabled) {
         if (plugin.getServer().getPluginManager().getPlugin("packetevents") != null) {
            this.usePacketEvents = true;
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            (new PacketEventsGlowHandler(this)).register();
            this.startRangeCheckTask();

            for(Player p : Bukkit.getOnlinePlayers()) {
               this.createColorTeams(p);
               plugin.getTaskRunner().runTaskLater(() -> this.refreshGlow(p), 20L);
            }

            plugin.getLogger().info("Team Glow enabled using PacketEvents.");
         } else {
            plugin.getLogger().warning("PacketEvents not found! Team Glow has been disabled.");
            this.enabled = false;
            this.usePacketEvents = false;
         }
      }

   }

   public void setGlow(Player target, Player receiver, ChatColor color) {
      if (this.enabled && this.usePacketEvents) {
         try {
            if (!this.colorTeamsCreated.contains(receiver.getUniqueId())) {
               this.createColorTeams(receiver);
            }

            Map<UUID, ChatColor> targets = (Map)this.glowingCache.computeIfAbsent(receiver.getUniqueId(), (k) -> new ConcurrentHashMap());
            synchronized(targets) {
               ChatColor previousColor = (ChatColor)targets.get(target.getUniqueId());
               if (previousColor == color) {
                  return;
               }

               this.sendTeamPacket(target, receiver, color);
               this.sendMetadataPacket(target, receiver, true);
               targets.put(target.getUniqueId(), color);
            }

            if (this.plugin.getConfigManager().isDebugEnabled()) {
               Logger var10000 = this.plugin.getLogger();
               String var10001 = target.getName();
               var10000.info("[GlowDebug] SENT glow packets: Target=" + var10001 + " Receiver=" + receiver.getName() + " Color=" + color.name());
            }
         } catch (Exception e) {
            if (this.plugin.getConfigManager().isDebugEnabled()) {
               this.plugin.getLogger().warning("[GlowDebug] Failed to set glow: " + e.getMessage());
               e.printStackTrace();
            }
         }

      }
   }

   public void unsetGlow(Player target, Player receiver) {
      if (this.enabled && this.usePacketEvents) {
         try {
            Map<UUID, ChatColor> targets = (Map)this.glowingCache.get(receiver.getUniqueId());
            boolean wasGlowing = false;
            if (targets != null) {
               synchronized(targets) {
                  wasGlowing = targets.remove(target.getUniqueId()) != null;
               }
            }

            if (wasGlowing) {
               this.sendMetadataPacket(target, receiver, false);
            }
         } catch (Exception var8) {
         }

      }
   }

   private void sendMetadataPacket(Player target, Player receiver, boolean glowing) {
      byte status = 0;
      if (target.getFireTicks() > 0) {
         status = (byte)(status | 1);
      }

      if (target.isSneaking()) {
         status = (byte)(status | 2);
      }

      if (target.isSprinting()) {
         status = (byte)(status | 8);
      }

      if (target.isSwimming()) {
         status = (byte)(status | 16);
      }

      if (target.isInvisible()) {
         status = (byte)(status | 32);
      }

      if (glowing) {
         status = (byte)(status | 64);
      }

      if (target.isGliding()) {
         status = (byte)(status | 128);
      }

      EntityData<Byte> entityData = new EntityData(0, EntityDataTypes.BYTE, status);
      WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(target.getEntityId(), Collections.singletonList(entityData));
      PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, packet);
   }

   private void sendTeamPacket(Player target, Player receiver, ChatColor color) {
      String teamName = "JT_" + color.name();
      if (teamName.length() > 16) {
         teamName = teamName.substring(0, 16);
      }

      WrapperPlayServerTeams packet = new WrapperPlayServerTeams(teamName, TeamMode.ADD_ENTITIES, (WrapperPlayServerTeams.ScoreBoardTeamInfo)null, Collections.singletonList(target.getName()));
      PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, packet);
   }

   private NamedTextColor getNamedTextColor(ChatColor color) {
      NamedTextColor var10000;
      switch (color) {
         case RED -> var10000 = NamedTextColor.RED;
         case DARK_RED -> var10000 = NamedTextColor.DARK_RED;
         case BLUE -> var10000 = NamedTextColor.BLUE;
         case GREEN -> var10000 = NamedTextColor.GREEN;
         case AQUA -> var10000 = NamedTextColor.AQUA;
         case GOLD -> var10000 = NamedTextColor.GOLD;
         case GRAY -> var10000 = NamedTextColor.GRAY;
         case WHITE -> var10000 = NamedTextColor.WHITE;
         case BLACK -> var10000 = NamedTextColor.BLACK;
         case YELLOW -> var10000 = NamedTextColor.YELLOW;
         case LIGHT_PURPLE -> var10000 = NamedTextColor.LIGHT_PURPLE;
         case DARK_PURPLE -> var10000 = NamedTextColor.DARK_PURPLE;
         case DARK_BLUE -> var10000 = NamedTextColor.DARK_BLUE;
         case DARK_GREEN -> var10000 = NamedTextColor.DARK_GREEN;
         case DARK_AQUA -> var10000 = NamedTextColor.DARK_AQUA;
         case DARK_GRAY -> var10000 = NamedTextColor.DARK_GRAY;
         default -> var10000 = NamedTextColor.WHITE;
      }

      return var10000;
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      if (this.enabled && this.usePacketEvents) {
         Player player = event.getPlayer();
         this.entityIdToUuid.put(player.getEntityId(), player.getUniqueId());
         this.createColorTeams(player);
         this.plugin.getTaskRunner().runAsyncTaskLater(() -> this.refreshGlow(player), 20L);
      }
   }

   private void createColorTeams(Player receiver) {
      if (!this.colorTeamsCreated.contains(receiver.getUniqueId())) {
         for(ChatColor color : ChatColor.values()) {
            if (color.isColor()) {
               String teamName = "JT_" + color.name();
               if (teamName.length() > 16) {
                  teamName = teamName.substring(0, 16);
               }

               WrapperPlayServerTeams.ScoreBoardTeamInfo info = new WrapperPlayServerTeams.ScoreBoardTeamInfo(Component.text(teamName), Component.text(color.toString()), Component.empty(), NameTagVisibility.ALWAYS, CollisionRule.ALWAYS, this.getNamedTextColor(color), OptionData.NONE);
               WrapperPlayServerTeams packet = new WrapperPlayServerTeams(teamName, TeamMode.CREATE, info, new ArrayList());
               PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, packet);
            }
         }

         this.colorTeamsCreated.add(receiver.getUniqueId());
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      this.entityIdToUuid.remove(player.getEntityId());
      this.glowingCache.remove(playerId);
      this.colorTeamsCreated.remove(playerId);

      for(Map<UUID, ChatColor> targets : this.glowingCache.values()) {
         targets.remove(playerId);
      }

   }

   UUID getPlayerUuidByEntityId(int entityId) {
      return (UUID)this.entityIdToUuid.get(entityId);
   }

   @EventHandler
   public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
      if (this.enabled && this.usePacketEvents) {
         Player player = event.getPlayer();
         this.plugin.getTaskRunner().runAsyncTaskLater(() -> {
            if (player.isOnline()) {
               this.colorTeamsCreated.remove(player.getUniqueId());
               this.glowingCache.remove(player.getUniqueId());
               this.createColorTeams(player);
               this.refreshGlow(player);
               Team team = this.plugin.getTeamManager().getPlayerTeamCached(player.getUniqueId());
               if (team != null) {
                  for(TeamPlayer member : team.getMembers()) {
                     Player other = Bukkit.getPlayer(member.getPlayerUuid());
                     if (other != null && other.isOnline() && !other.getUniqueId().equals(player.getUniqueId())) {
                        this.refreshGlow(other);
                     }
                  }

               }
            }
         }, 20L);
      }
   }

   @EventHandler
   public void onPlayerRespawn(PlayerRespawnEvent event) {
      if (this.enabled && this.usePacketEvents) {
         Player player = event.getPlayer();
         this.plugin.getTaskRunner().runAsyncTaskLater(() -> {
            if (player.isOnline()) {
               this.colorTeamsCreated.remove(player.getUniqueId());
               this.glowingCache.remove(player.getUniqueId());
               this.createColorTeams(player);
               this.refreshGlow(player);
               Team team = this.plugin.getTeamManager().getPlayerTeamCached(player.getUniqueId());
               if (team != null) {
                  for(TeamPlayer member : team.getMembers()) {
                     Player other = Bukkit.getPlayer(member.getPlayerUuid());
                     if (other != null && other.isOnline() && !other.getUniqueId().equals(player.getUniqueId())) {
                        this.refreshGlow(other);
                     }
                  }

               }
            }
         }, 10L);
      }
   }

   public void updateGlowForTeam(Team team) {
      if (this.enabled && team != null && this.usePacketEvents) {
         for(TeamPlayer member : team.getMembers()) {
            Player p = Bukkit.getPlayer(member.getPlayerUuid());
            if (p != null && p.isOnline()) {
               this.refreshGlow(p);
            }
         }

      }
   }

   public void stopGlowForPlayer(Player player, Team team) {
      if (this.enabled && this.usePacketEvents) {
         for(Player p : Bukkit.getOnlinePlayers()) {
            this.unsetGlow(player, p);
         }

      }
   }

   private ChatColor getRoleColor(TeamRole role) {
      String configKey = "settings.glow.colors." + role.name().toLowerCase();
      String colorName = this.plugin.getConfig().getString(configKey);
      if (colorName == null) {
         if (role == TeamRole.CO_OWNER) {
            return ChatColor.RED;
         } else {
            return role == TeamRole.OWNER ? ChatColor.DARK_RED : ChatColor.WHITE;
         }
      } else {
         try {
            return ChatColor.valueOf(colorName.toUpperCase());
         } catch (IllegalArgumentException var5) {
            return ChatColor.WHITE;
         }
      }
   }

   boolean isActive() {
      return this.enabled && this.usePacketEvents;
   }

   Map<UUID, Map<UUID, ChatColor>> getGlowingCache() {
      return this.glowingCache;
   }

   JustTeams getPlugin() {
      return this.plugin;
   }

   private void startRangeCheckTask() {
      int interval = this.plugin.getConfig().getInt("settings.glow.check_interval", 20);
      if (this.usePacketEvents) {
         this.plugin.getTaskRunner().runAsyncTaskTimer(() -> {
            if (this.enabled && this.usePacketEvents) {
               Map<UUID, Team> snapshot = new HashMap();

               for(Player player : Bukkit.getOnlinePlayers()) {
                  Team team = this.plugin.getTeamManager().getPlayerTeamCached(player.getUniqueId());
                  if (team != null) {
                     snapshot.put(player.getUniqueId(), team);
                  }
               }

               this.refreshGlowBatch(snapshot);
            }
         }, (long)interval, (long)interval);
      }

   }

   private void refreshGlowBatch(Map<UUID, Team> snapshot) {
      Collection<? extends Player> online = Bukkit.getOnlinePlayers();
      int range = this.plugin.getConfig().getInt("settings.glow.range", 30);
      if (this.onlyShowOwnTeam) {
         Map<Integer, List<Player>> byTeam = new HashMap();

         for(Player player : online) {
            Team team = (Team)snapshot.get(player.getUniqueId());
            if (team != null && team.isGlowEnabled()) {
               ((List)byTeam.computeIfAbsent(team.getId(), (k) -> new ArrayList())).add(player);
            }
         }

         for(List<Player> members : byTeam.values()) {
            for(Player target : members) {
               Team targetTeam = (Team)snapshot.get(target.getUniqueId());

               for(Player receiver : members) {
                  if (!receiver.getUniqueId().equals(target.getUniqueId())) {
                     this.refreshGlowForReceiver(target, receiver, targetTeam, range);
                  }
               }
            }
         }
      } else {
         for(Player target : online) {
            Team targetTeam = (Team)snapshot.get(target.getUniqueId());

            for(Player receiver : online) {
               if (!receiver.getUniqueId().equals(target.getUniqueId())) {
                  this.refreshGlowForReceiver(target, receiver, targetTeam, range);
               }
            }
         }
      }

   }

   void refreshGlowForReceiver(Player target, Player receiver, Team team) {
      this.refreshGlowForReceiver(target, receiver, team, this.plugin.getConfig().getInt("settings.glow.range", 30));
   }

   void refreshGlowForReceiver(Player target, Player receiver, Team team, int range) {
      try {
         if (team == null || !team.isGlowEnabled()) {
            this.unsetGlow(target, receiver);
            return;
         }

         if (!target.getWorld().getUID().equals(receiver.getWorld().getUID()) || target.getLocation().distanceSquared(receiver.getLocation()) > (double)(range * range)) {
            this.unsetGlow(target, receiver);
            return;
         }

         if (this.onlyShowOwnTeam) {
            Team receiverTeam = this.plugin.getTeamManager().getPlayerTeamCached(receiver.getUniqueId());
            if (receiverTeam != null && receiverTeam.getId() == team.getId()) {
               ChatColor color = team.getColor() != null ? team.getColor() : this.getRoleColor(team.getMember(target.getUniqueId()).getRole());
               this.setGlow(target, receiver, color);
            } else {
               this.unsetGlow(target, receiver);
            }
         } else {
            ChatColor color = team.getColor() != null ? team.getColor() : this.getRoleColor(team.getMember(target.getUniqueId()).getRole());
            this.setGlow(target, receiver, color);
         }
      } catch (Throwable t) {
         if (this.plugin.getConfigManager().isDebugEnabled()) {
            this.plugin.getLogger().warning("[GlowDebug] refreshGlowForReceiver skipped: " + t.getMessage());
         }
      }

   }

   public void refreshGlow(Player player) {
      if (this.enabled && this.usePacketEvents) {
         if (player.isOnline()) {
            this.plugin.getTeamManager().getPlayerTeamAsync(player.getUniqueId()).thenAccept((team) -> {
               if (player.isOnline()) {
                  if (team != null && team.isGlowEnabled()) {
                     int range = this.plugin.getConfig().getInt("settings.glow.range", 30);

                     for(Player receiver : Bukkit.getOnlinePlayers()) {
                        if (!receiver.getUniqueId().equals(player.getUniqueId())) {
                           this.refreshGlowForReceiver(player, receiver, team, range);
                        }
                     }

                  } else {
                     for(Player receiver : Bukkit.getOnlinePlayers()) {
                        if (!receiver.getUniqueId().equals(player.getUniqueId())) {
                           this.unsetGlow(player, receiver);
                        }
                     }

                  }
               }
            }).exceptionally((ex) -> {
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  Logger var10000 = this.plugin.getLogger();
                  String var10001 = player.getName();
                  var10000.warning("Error refreshing glow for " + var10001 + ": " + ex.getMessage());
                  ex.printStackTrace();
               }

               return null;
            });
         }
      }
   }
}