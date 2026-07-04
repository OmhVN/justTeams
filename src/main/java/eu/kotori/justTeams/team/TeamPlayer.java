package eu.kotori.justTeams.team;

import java.time.Instant;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class TeamPlayer {
   private final UUID playerUuid;
   private volatile TeamRole role;
   private final Instant joinDate;
   private volatile boolean canWithdraw;
   private volatile boolean canUseEnderChest;
   private volatile boolean canSetHome;
   private volatile boolean canUseHome;
   private volatile boolean canEditMembers;
   private volatile boolean canEditCoOwners;
   private volatile boolean canKickMembers;
   private volatile boolean canPromoteMembers;
   private volatile boolean canDemoteMembers;
   private volatile boolean canPromoteToCoOwner;

   public TeamPlayer(UUID playerUuid, TeamRole role, Instant joinDate, boolean canWithdraw, boolean canUseEnderChest, boolean canSetHome, boolean canUseHome) {
      this.playerUuid = playerUuid;
      this.role = role;
      this.joinDate = joinDate;
      this.canWithdraw = canWithdraw;
      this.canUseEnderChest = canUseEnderChest;
      this.canSetHome = canSetHome;
      this.canUseHome = canUseHome;
      this.setDefaultEditingPermissions();
   }

   public TeamPlayer(UUID playerUuid, TeamRole role, Instant joinDate, boolean canWithdraw, boolean canUseEnderChest, boolean canSetHome, boolean canUseHome, boolean canEditMembers, boolean canEditCoOwners, boolean canKickMembers, boolean canPromoteMembers, boolean canDemoteMembers) {
      this.playerUuid = playerUuid;
      this.role = role;
      this.joinDate = joinDate;
      this.canWithdraw = canWithdraw;
      this.canUseEnderChest = canUseEnderChest;
      this.canSetHome = canSetHome;
      this.canUseHome = canUseHome;
      this.canEditMembers = canEditMembers;
      this.canEditCoOwners = canEditCoOwners;
      this.canKickMembers = canKickMembers;
      this.canPromoteMembers = canPromoteMembers;
      this.canDemoteMembers = canDemoteMembers;
   }

   private void setDefaultEditingPermissions() {
      switch (this.role) {
         case OWNER:
            this.canEditMembers = true;
            this.canEditCoOwners = true;
            this.canKickMembers = true;
            this.canPromoteMembers = true;
            this.canDemoteMembers = true;
            break;
         case CO_OWNER:
            this.canEditMembers = true;
            this.canEditCoOwners = false;
            this.canKickMembers = true;
            this.canPromoteMembers = false;
            this.canDemoteMembers = false;
            break;
         case MANAGER:
            this.canEditMembers = true;
            this.canEditCoOwners = false;
            this.canKickMembers = true;
            this.canPromoteMembers = false;
            this.canDemoteMembers = false;
            break;
         case MEMBER:
            this.canEditMembers = false;
            this.canEditCoOwners = false;
            this.canKickMembers = false;
            this.canPromoteMembers = false;
            this.canDemoteMembers = false;
      }

   }

   public UUID getPlayerUuid() {
      return this.playerUuid;
   }

   public TeamRole getRole() {
      return this.role;
   }

   public Instant getJoinDate() {
      return this.joinDate;
   }

   public void setRole(TeamRole role) {
      this.role = role;
      this.setDefaultEditingPermissions();
   }

   public boolean canWithdraw() {
      return this.canWithdraw;
   }

   public void setCanWithdraw(boolean canWithdraw) {
      this.canWithdraw = canWithdraw;
   }

   public boolean canUseEnderChest() {
      return this.canUseEnderChest;
   }

   public void setCanUseEnderChest(boolean canUseEnderChest) {
      this.canUseEnderChest = canUseEnderChest;
   }

   public boolean canSetHome() {
      return this.canSetHome;
   }

   public void setCanSetHome(boolean canSetHome) {
      this.canSetHome = canSetHome;
   }

   public boolean canUseHome() {
      return this.canUseHome;
   }

   public void setCanUseHome(boolean canUseHome) {
      this.canUseHome = canUseHome;
   }

   public boolean canEditMembers() {
      return this.canEditMembers;
   }

   public void setCanEditMembers(boolean canEditMembers) {
      this.canEditMembers = canEditMembers;
   }

   public boolean canEditCoOwners() {
      return this.canEditCoOwners;
   }

   public void setCanEditCoOwners(boolean canEditCoOwners) {
      this.canEditCoOwners = canEditCoOwners;
   }

   public boolean canKickMembers() {
      return this.canKickMembers;
   }

   public void setCanKickMembers(boolean canKickMembers) {
      this.canKickMembers = canKickMembers;
   }

   public boolean canPromoteMembers() {
      return this.canPromoteMembers;
   }

   public void setCanPromoteMembers(boolean canPromoteMembers) {
      this.canPromoteMembers = canPromoteMembers;
   }

   public boolean canDemoteMembers() {
      return this.canDemoteMembers;
   }

   public void setCanDemoteMembers(boolean canDemoteMembers) {
      this.canDemoteMembers = canDemoteMembers;
   }

   public boolean canPromoteToCoOwner() {
      return this.canPromoteToCoOwner;
   }

   public void setCanPromoteToCoOwner(boolean canPromoteToCoOwner) {
      this.canPromoteToCoOwner = canPromoteToCoOwner;
   }

   public boolean canEditPlayer(TeamPlayer target) {
      if (target == null) {
         return false;
      } else if (this.playerUuid.equals(target.getPlayerUuid())) {
         return false;
      } else if (this.role == TeamRole.OWNER) {
         return true;
      } else {
         if (this.role == TeamRole.CO_OWNER) {
            if (target.getRole() == TeamRole.MEMBER || target.getRole() == TeamRole.MANAGER) {
               return this.canEditMembers;
            }

            if (target.getRole() == TeamRole.CO_OWNER) {
               return this.canEditCoOwners;
            }
         }
         if (this.role == TeamRole.MANAGER) {
            if (target.getRole() == TeamRole.MEMBER) {
               return this.canEditMembers;
            }
         }

         return false;
      }
   }

   public boolean canKickPlayer(TeamPlayer target) {
      if (target == null) {
         return false;
      } else if (this.playerUuid.equals(target.getPlayerUuid())) {
         return false;
      } else if (this.role == TeamRole.OWNER) {
         return true;
      } else {
         if (this.role == TeamRole.CO_OWNER) {
            if (target.getRole() == TeamRole.MEMBER || target.getRole() == TeamRole.MANAGER) {
               return this.canKickMembers;
            }
         }
         if (this.role == TeamRole.MANAGER) {
            if (target.getRole() == TeamRole.MEMBER) {
               return this.canKickMembers;
            }
         }
         return false;
      }
   }

   public boolean canPromotePlayer(TeamPlayer target) {
      if (target == null) {
         return false;
      } else if (this.playerUuid.equals(target.getPlayerUuid())) {
         return false;
      } else if (this.role == TeamRole.OWNER) {
         return target.getRole() == TeamRole.MEMBER || target.getRole() == TeamRole.MANAGER;
      } else if (this.role == TeamRole.CO_OWNER && this.canPromoteToCoOwner) {
         return target.getRole() == TeamRole.MEMBER;
      } else {
         return false;
      }
   }

   public boolean canDemotePlayer(TeamPlayer target) {
      if (target == null) {
         return false;
      } else if (this.playerUuid.equals(target.getPlayerUuid())) {
         return false;
      } else if (this.role == TeamRole.OWNER) {
         return target.getRole() == TeamRole.CO_OWNER || target.getRole() == TeamRole.MANAGER;
      } else {
         return false;
      }
   }

   public Player getBukkitPlayer() {
      return Bukkit.getPlayer(this.playerUuid);
   }

   public boolean isOnline() {
      Player player = this.getBukkitPlayer();
      return player != null && player.isOnline();
   }
}
