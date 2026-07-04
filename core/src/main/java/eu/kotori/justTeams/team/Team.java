package eu.kotori.justTeams.core.team;
import eu.kotori.justTeams.api.team.*;
import eu.kotori.justTeams.api.team.*;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.api.data.ClanCustomDataCodec;
import eu.kotori.justTeams.core.util.TextUtil;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class Team implements InventoryHolder, ITeam {
   private final int id;
   private volatile String name;
   private volatile String tag;
   private volatile String description;
   private volatile UUID ownerUuid;
   private volatile Location homeLocation;
   private volatile String homeServer;
   private volatile Timestamp creationDate;
   private final AtomicBoolean pvpEnabled;
   private final AtomicBoolean isPublic;
   private final AtomicBoolean glowEnabled;
   private final AtomicReference<Double> balance;
   private final AtomicInteger kills;
   private final AtomicInteger deaths;
   private final AtomicInteger warpCount;
   private final AtomicBoolean joinFeeEnabled;
   private final AtomicReference<Double> joinFeeAmount;
   private final AtomicInteger tier;
   private final AtomicLong points;
   private final List<TeamPlayer> members;
   private volatile Inventory enderChest;
   private final List<UUID> joinRequests;
   private final AtomicBoolean enderChestLock;
   private final List<UUID> enderChestViewers;
   private volatile SortType currentSortType;
   private final AtomicBoolean acceptRequests;
   private final List<Integer> allies;
   private final List<Integer> sentAllyRequests;
   private final List<Integer> receivedAllyRequests;
   private volatile ChatColor color;
   private volatile String gradientStart;
   private volatile String gradientEnd;

   public Team(int id, String name, String tag, UUID ownerUuid, boolean defaultPvpStatus, boolean defaultPublicStatus, boolean defaultGlowStatus) {
      this(id, name, tag, ownerUuid, defaultPvpStatus, defaultPublicStatus, defaultGlowStatus, new Timestamp(System.currentTimeMillis()));
   }

   public Team(int id, String name, String tag, UUID ownerUuid, boolean defaultPvpStatus, boolean defaultPublicStatus, boolean defaultGlowStatus, Timestamp creationDate) {
      this.pvpEnabled = new AtomicBoolean(false);
      this.isPublic = new AtomicBoolean(false);
      this.glowEnabled = new AtomicBoolean(true);
      this.balance = new AtomicReference((double)0.0F);
      this.kills = new AtomicInteger(0);
      this.deaths = new AtomicInteger(0);
      this.warpCount = new AtomicInteger(0);
      this.joinFeeEnabled = new AtomicBoolean(false);
      this.joinFeeAmount = new AtomicReference((double)0.0F);
      this.tier = new AtomicInteger(1);
      this.points = new AtomicLong(0L);
      this.enderChestLock = new AtomicBoolean(false);
      this.enderChestViewers = new CopyOnWriteArrayList();
      this.currentSortType = Team.SortType.JOIN_DATE;
      this.acceptRequests = new AtomicBoolean(true);
      this.allies = new CopyOnWriteArrayList();
      this.sentAllyRequests = new CopyOnWriteArrayList();
      this.receivedAllyRequests = new CopyOnWriteArrayList();
      this.id = id;
      this.name = name;
      this.tag = TextUtil.sanitizeMiniMessage(tag);
      this.ownerUuid = ownerUuid;
      this.pvpEnabled.set(defaultPvpStatus);
      this.isPublic.set(defaultPublicStatus);
      this.glowEnabled.set(defaultGlowStatus);
      this.creationDate = creationDate;
      this.members = new CopyOnWriteArrayList();
      this.joinRequests = new CopyOnWriteArrayList();
   }

   public int getId() {
      return this.id;
   }

   public String getName() {
      return this.name;
   }

   public String getTag() {
      return this.tag != null ? this.tag : "";
   }

   public String getDescription() {
      return this.description != null ? this.description : "A new Team!";
   }

   public UUID getOwnerUuid() {
      return this.ownerUuid;
   }

   public Location getHomeLocation() {
      return this.homeLocation;
   }

   public String getHomeServer() {
      return this.homeServer;
   }

   public Timestamp getCreationDate() {
      return this.creationDate;
   }

   public void setCreationDate(Timestamp creationDate) {
      this.creationDate = creationDate;
   }

   public boolean isPvpEnabled() {
      return this.pvpEnabled.get();
   }

   public boolean isPublic() {
      return this.isPublic.get();
   }

   public double getBalance() {
      return (Double)this.balance.get();
   }

   public void setBalance(double balance) {
      this.balance.set(balance);
   }

   public void addBalance(double amount) {
      this.balance.updateAndGet((current) -> current + amount);
   }

   public void removeBalance(double amount) {
      this.balance.updateAndGet((current) -> current - amount);
   }

   public int getKills() {
      return this.kills.get();
   }

   public void setKills(int kills) {
      this.kills.set(kills);
   }

   public void incrementKills() {
      this.kills.incrementAndGet();
   }

   public int getDeaths() {
      return this.deaths.get();
   }

   public void setDeaths(int deaths) {
      this.deaths.set(deaths);
   }

   public void incrementDeaths() {
      this.deaths.incrementAndGet();
   }

   public long getPoints() {
      return this.points.get();
   }

   public void setPoints(long value) {
      this.points.set(Math.max(0L, value));
   }

   public long addPoints(long amount) {
      return amount <= 0L ? this.points.get() : this.points.updateAndGet((p) -> p + amount);
   }

   public long removePoints(long amount) {
      return amount <= 0L ? this.points.get() : this.points.updateAndGet((p) -> Math.max(0L, p - amount));
   }

   public int getWarpCount() {
      return this.warpCount.get();
   }

   public void setWarpCount(int count) {
      this.warpCount.set(count);
   }

   public List<TeamPlayer> getMembers() {
      return this.members;
   }

   public Inventory getEnderChest() {
      return this.enderChest;
   }

   public void setEnderChest(Inventory enderChest) {
      this.enderChest = enderChest;
   }

   public List<UUID> getJoinRequests() {
      return this.joinRequests;
   }

   public boolean isEnderChestLocked() {
      return this.enderChestLock.get();
   }

   public boolean tryLockEnderChest() {
      return this.enderChestLock.compareAndSet(false, true);
   }

   public void unlockEnderChest() {
      this.enderChestLock.set(false);
   }

   public List<UUID> getEnderChestViewers() {
      return this.enderChestViewers;
   }

   public void addEnderChestViewer(UUID playerUuid) {
      if (!this.enderChestViewers.contains(playerUuid)) {
         this.enderChestViewers.add(playerUuid);
      }

   }

   public void removeEnderChestViewer(UUID playerUuid) {
      this.enderChestViewers.remove(playerUuid);
   }

   public boolean hasEnderChestViewers() {
      return !this.enderChestViewers.isEmpty();
   }

   public SortType getCurrentSortType() {
      return this.currentSortType;
   }

   public void setSortType(SortType sortType) {
      this.currentSortType = sortType;
   }

   public void cycleSortType() {
      SortType currentSort = this.getCurrentSortType();
      SortType var10000;
      switch (currentSort.ordinal()) {
         case 0 -> var10000 = Team.SortType.ALPHABETICAL;
         case 1 -> var10000 = Team.SortType.ONLINE_STATUS;
         case 2 -> var10000 = Team.SortType.JOIN_DATE;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      SortType newSort = var10000;
      this.setSortType(newSort);
   }

   public void addJoinRequest(UUID playerUuid) {
      if (!this.joinRequests.contains(playerUuid)) {
         this.joinRequests.add(playerUuid);
      }

   }

   public void removeJoinRequest(UUID playerUuid) {
      this.joinRequests.remove(playerUuid);
   }

   public List<TeamPlayer> getCoOwners() {
      return (List)this.members.stream().filter((m) -> m.getRole() == TeamRole.CO_OWNER).collect(Collectors.toList());
   }

   public List<TeamPlayer> getSortedMembers(SortType sortType) {
      return (List)this.members.stream().sorted(sortType.getComparator()).collect(Collectors.toList());
   }

   public void addMember(TeamPlayer player) {
      if (player != null && player.getPlayerUuid() != null) {
         this.members.removeIf((member) -> player.getPlayerUuid().equals(member.getPlayerUuid()));
         this.members.add(player);
      }
   }

   public void removeMember(UUID playerUuid) {
      this.members.removeIf((member) -> member.getPlayerUuid().equals(playerUuid));
   }

   public boolean isMember(UUID playerUuid) {
      return this.members.stream().anyMatch((member) -> member.getPlayerUuid().equals(playerUuid));
   }

   public boolean isOwner(UUID playerUuid) {
      return this.ownerUuid.equals(playerUuid);
   }

   public boolean hasElevatedPermissions(UUID playerUuid) {
      TeamPlayer member = this.getMember(playerUuid);
      if (member == null) {
         return false;
      } else {
         return member.getRole() == TeamRole.OWNER || member.getRole() == TeamRole.CO_OWNER;
      }
   }

   public TeamPlayer getMember(UUID playerUuid) {
      return (TeamPlayer)this.members.stream().filter((m) -> m.getPlayerUuid().equals(playerUuid)).findFirst().orElse(null);
   }

   public void broadcast(String messageKey, TagResolver... resolvers) {
      this.members.forEach((member) -> {
         if (member.isOnline()) {
            JustTeams.getInstance().getMessageManager().sendMessage(member.getBukkitPlayer(), messageKey, resolvers);
         }

      });
   }

   public void setName(String name) {
      this.name = name;
   }

   public void setTag(String tag) {
      this.tag = TextUtil.sanitizeMiniMessage(tag);
   }

   public String getColoredName() {
      return this.name;
   }

   public String getColoredTag() {
      return this.tag != null ? this.tag : "";
   }

   public String getPlainName() {
      return this.stripColorCodes(this.name);
   }

   public String getPlainTag() {
      return this.stripColorCodes(this.tag != null ? this.tag : "");
   }

   private String stripColorCodes(String text) {
      return text == null ? "" : text.replaceAll("(?i)<\\/?[a-z][a-z0-9_:#]*(?::[^>]*)?>", "").replaceAll("(?i)<\\/?#[0-9A-F]{6}>", "").replaceAll("(?i)&#[0-9A-F]{6}", "").replaceAll("(?i)#[0-9A-F]{6}", "").replaceAll("(?i)&[0-9A-FK-OR]", "").replaceAll("§[0-9a-fk-or]", "");
   }

   public void setDescription(String description) {
      this.description = TextUtil.sanitizeMiniMessage(description);
   }

   public void setOwnerUuid(UUID ownerUuid) {
      this.ownerUuid = ownerUuid;
   }

   public void setHomeLocation(Location homeLocation) {
      this.homeLocation = homeLocation;
   }

   public void setHomeServer(String homeServer) {
      this.homeServer = homeServer;
   }

   public void setPvpEnabled(boolean pvpEnabled) {
      this.pvpEnabled.set(pvpEnabled);
   }

   public void setPublic(boolean isPublic) {
      this.isPublic.set(isPublic);
   }

   public boolean isGlowEnabled() {
      return this.glowEnabled.get();
   }

   public void setGlowEnabled(boolean glowEnabled) {
      this.glowEnabled.set(glowEnabled);
   }

   public void setColor(ChatColor color) {
      this.color = color;
      this.gradientStart = null;
      this.gradientEnd = null;
   }

   public ChatColor getColor() {
      return this.color;
   }

   public void setGradientColors(String startHex, String endHex) {
      this.gradientStart = startHex;
      this.gradientEnd = endHex;
      this.color = null;
   }

   public String getGradientStart() {
      return this.gradientStart;
   }

   public String getGradientEnd() {
      return this.gradientEnd;
   }

   public boolean hasGradient() {
      return this.gradientStart != null && this.gradientEnd != null;
   }

   public Inventory getInventory() {
      return this.enderChest;
   }

   public boolean setCustomData(String key, String value) {
      return JustTeams.getInstance().getStorageManager().getStorage().setTeamCustomData(this.id, key, value);
   }

   public Optional<String> getCustomData(String key) {
      return JustTeams.getInstance().getStorageManager().getStorage().getTeamCustomData(this.id, key);
   }

   public boolean removeCustomData(String key) {
      return JustTeams.getInstance().getStorageManager().getStorage().removeTeamCustomData(this.id, key);
   }

   public Map<String, String> getAllCustomData() {
      return JustTeams.getInstance().getStorageManager().getStorage().getAllTeamCustomData(this.id);
   }

   public boolean hasCustomData(String key) {
      return JustTeams.getInstance().getStorageManager().getStorage().hasTeamCustomData(this.id, key);
   }

   public int clearAllCustomData() {
      return JustTeams.getInstance().getStorageManager().getStorage().clearAllTeamCustomData(this.id);
   }

   public <T> boolean setCustomData(String key, T value) {
      try {
         String serialized = JustTeams.getInstance().getCustomDataManager().serialize(value);
         return this.setCustomData(key, serialized);
      } catch (IllegalArgumentException e) {
         JustTeams.getInstance().getLogger().warning("Could not set custom data for key " + key + ": " + e.getMessage());
         return false;
      }
   }

   public <T> Optional<T> getCustomData(String key, Class<T> type) {
      Optional<String> data = this.getCustomData(key);
      if (data.isEmpty()) {
         return Optional.empty();
      } else {
         ClanCustomDataCodec<T> codec = JustTeams.getInstance().getCustomDataManager().<T>getCodec(type);
         if (codec == null) {
            Logger var10000 = JustTeams.getInstance().getLogger();
            String var10001 = type.getName();
            var10000.warning("No codec found for type " + var10001 + " when retrieving custom data key " + key);
            return Optional.empty();
         } else {
            try {
               return Optional.ofNullable(codec.deserialize((String)data.get()));
            } catch (Exception e) {
               JustTeams.getInstance().getLogger().warning("Error deserializing custom data for key " + key + ": " + e.getMessage());
               return Optional.empty();
            }
         }
      }
   }

   public int getTier() {
      return this.tier.get();
   }

   public void setTier(int tier) {
      if (tier < 1) {
         tier = 1;
      }

      this.tier.set(tier);
   }

   public boolean isJoinFeeEnabled() {
      return this.joinFeeEnabled.get();
   }

   public void setJoinFeeEnabled(boolean enabled) {
      this.joinFeeEnabled.set(enabled);
   }

   public double getJoinFeeAmount() {
      return (Double)this.joinFeeAmount.get();
   }

   public void setJoinFeeAmount(double amount) {
      this.joinFeeAmount.set(amount);
   }

   public boolean acceptsRequests() {
      return this.acceptRequests.get();
   }

   public void setAcceptRequests(boolean accept) {
      this.acceptRequests.set(accept);
   }

   public List<Integer> getAllies() {
      return this.allies;
   }

   public void addAlly(int teamId) {
      if (!this.allies.contains(teamId)) {
         this.allies.add(teamId);
      }

   }

   public void removeAlly(int teamId) {
      this.allies.remove(teamId);
   }

   public boolean isAlly(int teamId) {
      return this.allies.contains(teamId);
   }

   public List<Integer> getSentAllyRequests() {
      return this.sentAllyRequests;
   }

   public void addSentAllyRequest(int teamId) {
      if (!this.sentAllyRequests.contains(teamId)) {
         this.sentAllyRequests.add(teamId);
      }

   }

   public void removeSentAllyRequest(int teamId) {
      this.sentAllyRequests.remove(teamId);
   }

   public List<Integer> getReceivedAllyRequests() {
      return this.receivedAllyRequests;
   }

   public void addReceivedAllyRequest(int teamId) {
      if (!this.receivedAllyRequests.contains(teamId)) {
         this.receivedAllyRequests.add(teamId);
      }

   }

   public void removeReceivedAllyRequest(int teamId) {
      this.receivedAllyRequests.remove(teamId);
   }

   public static enum SortType {
      JOIN_DATE(Comparator.comparing(TeamPlayer::getJoinDate)),
      ALPHABETICAL(Comparator.comparing((p) -> {
         String name = Bukkit.getOfflinePlayer(p.getPlayerUuid()).getName();
         return name != null ? name.toLowerCase() : "";
      })),
      ONLINE_STATUS(Comparator.comparing(TeamPlayer::isOnline).reversed());

      private final Comparator<TeamPlayer> comparator;

      private SortType(Comparator<TeamPlayer> comparator) {
         this.comparator = comparator;
      }

      public Comparator<TeamPlayer> getComparator() {
         return this.comparator;
      }

      
      private static SortType[] $values() {
         return new SortType[]{JOIN_DATE, ALPHABETICAL, ONLINE_STATUS};
      }
   }

   @Override
   public int getMemberCount() {
       return this.members.size();
   }

   @Override
   public int getMaxMembers() {
       return JustTeams.getInstance().getTeamUpgradeManager().getMaxMembers(this.getTier());
   }
}