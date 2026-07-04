package eu.kotori.justTeams.gui.admin;
import eu.kotori.justTeams.api.team.*;
import eu.kotori.justTeams.api.team.*;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.team.TeamPlayer;
import eu.kotori.justTeams.api.team.TeamRole;
import eu.kotori.justTeams.core.util.ItemBuilder;
import eu.kotori.justTeams.core.util.TextUtil;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class AdminMemberEditGUI implements InventoryHolder {
   private final JustTeams plugin;
   private final Player viewer;
   private final Team team;
   private final TeamPlayer member;
   private final Inventory inventory;

   public AdminMemberEditGUI(JustTeams plugin, Player viewer, Team team, TeamPlayer member) {
      this.plugin = plugin;
      this.viewer = viewer;
      this.team = team;
      this.member = member;
      OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(member.getPlayerUuid());
      String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
      this.inventory = Bukkit.createInventory(this, 54, plugin.getMiniMessage().deserialize("<gold><bold>ᴇᴅɪᴛ ᴍᴇᴍʙᴇʀ</bold></gold> <dark_gray>» <white><player>", Placeholder.unparsed("player", playerName)));
      this.initializeItems();
   }

   private void initializeItems() {
      this.inventory.clear();
      OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(this.member.getPlayerUuid());
      String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
      boolean isOwner = this.member.getPlayerUuid().equals(this.team.getOwnerUuid());
      List<String> infoLore = new ArrayList();
      infoLore.add("<gray>Name: <white>" + playerName);
      String var10001 = this.member.getRole().toString();
      infoLore.add("<gray>Role: <white>" + var10001);
      var10001 = this.member.isOnline() ? "<green>✓" : "<red>✗";
      infoLore.add("<gray>Online: " + var10001);
      infoLore.add("");
      var10001 = TextUtil.toMiniMessage(this.plugin.getMiniMessage(), this.team.getName());
      infoLore.add("<gray>Team: <white>" + var10001);
      this.inventory.setItem(4, (new ItemBuilder(Material.PLAYER_HEAD)).withName((isOwner ? "<gold><bold>★ " : "<white>") + playerName).withLore(infoLore).asPlayerHead(this.member.getPlayerUuid()).build());
      Inventory var10000 = this.inventory;
      ItemBuilder var10002 = (new ItemBuilder(this.member.canWithdraw() ? Material.LIME_DYE : Material.GRAY_DYE)).withName(this.member.canWithdraw() ? "<green><bold>ᴡɪᴛʜᴅʀᴀᴡ</bold> <gray>(Enabled)" : "<gray><bold>ᴡɪᴛʜᴅʀᴀᴡ</bold> <gray>(Disabled)");
      String[] var10003 = new String[5];
      String var10006 = this.member.canWithdraw() ? "<green>Enabled" : "<red>Disabled";
      var10003[0] = "<gray>Status: " + var10006;
      var10003[1] = "";
      var10003[2] = "<gray>Allow member to withdraw from bank";
      var10003[3] = "";
      var10003[4] = "<yellow>Click to toggle";
      var10000.setItem(19, var10002.withLore(var10003).withAction("toggle-withdraw").build());
      var10000 = this.inventory;
      var10002 = (new ItemBuilder(this.member.canUseEnderChest() ? Material.LIME_DYE : Material.GRAY_DYE)).withName(this.member.canUseEnderChest() ? "<dark_purple><bold>ᴇɴᴅᴇʀᴄʜᴇsᴛ</bold> <gray>(Enabled)" : "<gray><bold>ᴇɴᴅᴇʀᴄʜᴇsᴛ</bold> <gray>(Disabled)");
      var10003 = new String[5];
      var10006 = this.member.canUseEnderChest() ? "<green>Enabled" : "<red>Disabled";
      var10003[0] = "<gray>Status: " + var10006;
      var10003[1] = "";
      var10003[2] = "<gray>Allow member to use team enderchest";
      var10003[3] = "";
      var10003[4] = "<yellow>Click to toggle";
      var10000.setItem(20, var10002.withLore(var10003).withAction("toggle-enderchest").build());
      var10000 = this.inventory;
      var10002 = (new ItemBuilder(this.member.canSetHome() ? Material.LIME_DYE : Material.GRAY_DYE)).withName(this.member.canSetHome() ? "<aqua><bold>sᴇᴛ ʜᴏᴍᴇ</bold> <gray>(Enabled)" : "<gray><bold>sᴇᴛ ʜᴏᴍᴇ</bold> <gray>(Disabled)");
      var10003 = new String[5];
      var10006 = this.member.canSetHome() ? "<green>Enabled" : "<red>Disabled";
      var10003[0] = "<gray>Status: " + var10006;
      var10003[1] = "";
      var10003[2] = "<gray>Allow member to set team home";
      var10003[3] = "";
      var10003[4] = "<yellow>Click to toggle";
      var10000.setItem(21, var10002.withLore(var10003).withAction("toggle-sethome").build());
      this.inventory.setItem(22, (new ItemBuilder(this.member.canUseHome() ? Material.LIME_DYE : Material.GRAY_DYE)).withName(this.member.canUseHome() ? "<light_purple><bold>ᴜsᴇ ʜᴏᴍᴇ</bold> <gray>(Enabled)" : "<gray><bold>ᴜsᴇ ʜᴏᴍᴇ</bold> <gray>(Disabled)").withLore("<gray>Status: " + (this.member.canUseHome() ? "<green>Enabled" : "<red>Disabled"), "", "<gray>Allow member to teleport to team home", "", "<yellow>Click to toggle").withAction("toggle-usehome").build());
      if (this.member.getRole() == TeamRole.CO_OWNER) {
         this.inventory.setItem(23, (new ItemBuilder(this.member.canPromoteToCoOwner() ? Material.LIME_DYE : Material.GRAY_DYE)).withName(this.member.canPromoteToCoOwner() ? "<gold><bold>ᴘʀᴏᴍᴏᴛᴇ ᴄᴏ-ᴏᴡɴᴇʀ</bold> <gray>(Enabled)" : "<gray><bold>ᴘʀᴏᴍᴏᴛᴇ ᴄᴏ-ᴏᴡɴᴇʀ</bold> <gray>(Disabled)").withLore("<gray>Status: " + (this.member.canPromoteToCoOwner() ? "<green>Enabled" : "<red>Disabled"), "", "<gray>Allow this Co-owner to promote", "<gray>Members to Co-owner", "", "<red>⚠ Owner only", "", "<yellow>Click to toggle").withAction("toggle-promote-co-owner").build());
      }

      if (!isOwner) {
         var10000 = this.inventory;
         var10002 = (new ItemBuilder(Material.EMERALD)).withName("<green><bold>ᴘʀᴏᴍᴏᴛᴇ</bold>");
         var10003 = new String[5];
         var10006 = this.member.getRole().toString();
         var10003[0] = "<gray>Current Role: <white>" + var10006;
         var10003[1] = "";
         TeamRole var21 = this.member.getRole();
         var10003[2] = "<gray>Promote to " + (var21 == TeamRole.MEMBER ? "Co-Owner" : "N/A");
         var10003[3] = "";
         var10003[4] = "<yellow>Click to promote";
         var10000.setItem(29, var10002.withLore(var10003).withAction("promote-member").build());
         if (this.member.getRole() == TeamRole.CO_OWNER) {
            this.inventory.setItem(30, (new ItemBuilder(Material.REDSTONE)).withName("<red><bold>ᴅᴇᴍᴏᴛᴇ</bold>").withLore("<gray>Current Role: <white>" + this.member.getRole().toString(), "", "<gray>Demote to Member", "", "<yellow>Click to demote").withAction("demote-member").build());
         }
      }

      if (!isOwner) {
         this.inventory.setItem(33, (new ItemBuilder(Material.TNT)).withName("<dark_red><bold>ᴋɪᴄᴋ ᴍᴇᴍʙᴇʀ</bold>").withLore("<gray>Remove <white>" + playerName + " <gray>from the team", "<red>⚠ This action cannot be undone!", "", "<yellow>Click to kick member").withAction("kick-member").build());
      }

      this.inventory.setItem(45, (new ItemBuilder(Material.ARROW)).withName("<gray><bold>◀ ʙᴀᴄᴋ</bold>").withLore("<gray>Return to team management").withAction("back-button").build());
      ItemStack fillItem = (new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)).withName(" ").build();

      for(int i = 0; i < 54; ++i) {
         if (this.inventory.getItem(i) == null) {
            this.inventory.setItem(i, fillItem);
         }
      }

   }

   public void open() {
      this.viewer.openInventory(this.inventory);
   }

   public Team getTeam() {
      return this.team;
   }

   public TeamPlayer getMember() {
      return this.member;
   }

   public @NotNull Inventory getInventory() {
      return this.inventory;
   }
}