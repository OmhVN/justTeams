package eu.kotori.justTeams.gui.admin;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.team.TeamPlayer;
import eu.kotori.justTeams.core.util.ItemBuilder;
import eu.kotori.justTeams.core.util.TextUtil;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class AdminTeamManageGUI implements InventoryHolder {
   private final JustTeams plugin;
   private final Player viewer;
   private final Team targetTeam;
   private final Inventory inventory;
   private final int memberPage;
   private static final int MEMBERS_PER_PAGE = 21;

   public AdminTeamManageGUI(JustTeams plugin, Player viewer, Team targetTeam) {
      this(plugin, viewer, targetTeam, 0);
   }

   public AdminTeamManageGUI(JustTeams plugin, Player viewer, Team targetTeam, int memberPage) {
      this.plugin = plugin;
      this.viewer = viewer;
      this.targetTeam = targetTeam;
      this.memberPage = memberPage;
      this.inventory = Bukkit.createInventory(this, 54, plugin.getMiniMessage().deserialize("<gold><bold>ᴍᴀɴᴀɢᴇ</bold></gold> <dark_gray>» <white>" + TextUtil.toMiniMessage(plugin.getMiniMessage(), targetTeam.getName())));
      this.initializeItems();
   }

   private void initializeItems() {
      this.inventory.clear();
      OfflinePlayer owner = Bukkit.getOfflinePlayer(this.targetTeam.getOwnerUuid());
      String ownerName = owner.getName() != null ? owner.getName() : "Unknown";
      Player ownerPlayer = Bukkit.getPlayer(this.targetTeam.getOwnerUuid());
      int maxSize = ownerPlayer != null ? this.plugin.getConfigManager().getMaxTeamSize(ownerPlayer) : this.plugin.getConfigManager().getMaxTeamSize();
      Inventory var10000 = this.inventory;
      ItemBuilder var10002 = (new ItemBuilder(Material.NAME_TAG)).withName("<yellow><bold>ᴛᴇᴀᴍ ɪɴғᴏ");
      String[] var10003 = new String[4];
      String var10006 = this.targetTeam.getName();
      var10003[0] = "<gray>Name: <white>" + var10006;
      var10006 = this.targetTeam.getTag();
      var10003[1] = "<gray>Tag: <white>" + var10006;
      var10003[2] = "<gray>Owner: <white>" + ownerName;
      int var38 = this.targetTeam.getMembers().size();
      var10003[3] = "<gray>Members: <white>" + var38 + "<gray>/<white>" + maxSize;
      var10000.setItem(0, var10002.withLore(var10003).build());
      var10000 = this.inventory;
      var10002 = (new ItemBuilder(Material.WRITABLE_BOOK)).withName("<aqua><bold>ᴅᴇsᴄʀɪᴘᴛɪᴏɴ");
      var10003 = new String[3];
      String var39 = this.targetTeam.getDescription();
      var10003[0] = "<gray>Current: <white>" + var39;
      var10003[1] = "";
      var10003[2] = "<yellow>Click to change description";
      var10000.setItem(1, var10002.withLore(var10003).withAction("edit-description").build());
      var10000 = this.inventory;
      var10002 = (new ItemBuilder(Material.PAPER)).withName("<gold><bold>ʀᴇɴᴀᴍᴇ ᴛᴇᴀᴍ");
      var10003 = new String[4];
      var39 = this.targetTeam.getName();
      var10003[0] = "<gray>Current: <white>" + var39;
      var10003[1] = "";
      var10003[2] = "<yellow>Click to rename team";
      var10003[3] = "<gray>(No cooldown for admins)";
      var10000.setItem(2, var10002.withLore(var10003).withAction("rename-team").build());
      var10000 = this.inventory;
      var10002 = (new ItemBuilder(Material.OAK_SIGN)).withName("<light_purple><bold>ᴄʜᴀɴɢᴇ ᴛᴀɢ");
      var10003 = new String[3];
      var39 = this.targetTeam.getTag();
      var10003[0] = "<gray>Current: <white>" + var39;
      var10003[1] = "";
      var10003[2] = "<yellow>Click to change tag";
      var10000.setItem(3, var10002.withLore(var10003).withAction("edit-tag").build());
      var10000 = this.inventory;
      var10002 = (new ItemBuilder(this.targetTeam.isPublic() ? Material.LIME_DYE : Material.GRAY_DYE)).withName(this.targetTeam.isPublic() ? "<green><bold>ᴘᴜʙʟɪᴄ <gray>(Click to make private)" : "<gray><bold>ᴘʀɪᴠᴀᴛᴇ <gray>(Click to make public)");
      var10003 = new String[3];
      var39 = this.targetTeam.isPublic() ? "<green>Public" : "<red>Private";
      var10003[0] = "<gray>Status: " + var39;
      var10003[1] = "";
      var10003[2] = "<yellow>Click to toggle";
      var10000.setItem(9, var10002.withLore(var10003).withAction("toggle-public").build());
      this.inventory.setItem(10, (new ItemBuilder(this.targetTeam.isPvpEnabled() ? Material.NETHERITE_SWORD : Material.WOODEN_SWORD)).withName(this.targetTeam.isPvpEnabled() ? "<red><bold>ᴘᴠᴘ ᴇɴᴀʙʟᴇᴅ <gray>(Click to disable)" : "<gray><bold>ᴘᴠᴘ ᴅɪsᴀʙʟᴇᴅ <gray>(Click to enable)").withLore("<gray>PvP: " + (this.targetTeam.isPvpEnabled() ? "<green>Enabled" : "<red>Disabled"), "", "<yellow>Click to toggle").withAction("toggle-pvp").build());
      var10000 = this.inventory;
      var10002 = (new ItemBuilder(Material.DIAMOND)).withName("<yellow><bold>ʙᴀʟᴀɴᴄᴇ");
      var10003 = new String[3];
      Object[] var10007 = new Object[]{this.targetTeam.getBalance()};
      var10003[0] = "<gray>Current: <white>" + String.format("%.2f", var10007);
      var10003[1] = "";
      var10003[2] = "<yellow>Click <gray>to set balance";
      var10000.setItem(11, var10002.withLore(var10003).withAction("edit-balance").build());
      var10000 = this.inventory;
      var10002 = (new ItemBuilder(Material.IRON_SWORD)).withName("<red><bold>sᴛᴀᴛɪsᴛɪᴄs");
      var10003 = new String[]{"<gray>Kills: <white>" + this.targetTeam.getKills(), "<gray>Deaths: <white>" + this.targetTeam.getDeaths(), null, null, null};
      var39 = this.targetTeam.getDeaths() > 0 ? String.format("%.2f", (double)this.targetTeam.getKills() / (double)this.targetTeam.getDeaths()) : "∞";
      var10003[2] = "<gray>K/D: <white>" + var39;
      var10003[3] = "";
      var10003[4] = "<yellow>Click to edit stats";
      var10000.setItem(12, var10002.withLore(var10003).withAction("edit-stats").build());
      this.inventory.setItem(13, (new ItemBuilder(Material.ENDER_CHEST)).withName("<dark_purple><bold>ᴇɴᴅᴇʀᴄʜᴇsᴛ").withLore("<gray>View team's ender chest", "", "<yellow>Click to open").withAction("view-enderchest").build());
      this.inventory.setItem(14, (new ItemBuilder(Material.COMPASS)).withName("<green><bold>ᴛᴇʟᴇᴘᴏʀᴛ ᴛᴏ ʜᴏᴍᴇ").withLore("<gray>Teleport to the team's home location", "", "<yellow>Click to teleport").withAction("teleport-home").build());
      this.inventory.setItem(15, (new ItemBuilder(Material.FILLED_MAP)).withName("<aqua><bold>ᴠɪᴇᴡ ᴡᴀʀᴘs").withLore("<gray>See this team's warps", "<gray>(name, coordinates, server)", "", "<yellow>Click to view").withAction("view-warps").build());
      this.inventory.setItem(17, (new ItemBuilder(Material.TNT)).withName("<dark_red><bold>ᴅɪsʙᴀɴᴅ ᴛᴇᴀᴍ").withLore("<gray>Permanently delete this team", "<red>⚠ This cannot be undone!", "", "<yellow>Click to disband").withAction("disband-team").build());
      ItemStack divider = (new ItemBuilder(Material.YELLOW_STAINED_GLASS_PANE)).withName("<yellow><bold>▬▬▬ ᴍᴇᴍʙᴇʀs ▬▬▬").build();

      for(int i = 18; i < 27; ++i) {
         this.inventory.setItem(i, divider);
      }

      List<TeamPlayer> members = this.targetTeam.getMembers();
      int totalPages = (int)Math.ceil((double)members.size() / (double)21.0F);
      int startIndex = this.memberPage * 21;
      int endIndex = Math.min(startIndex + 21, members.size());

      for(int i = startIndex; i < endIndex; ++i) {
         TeamPlayer member = (TeamPlayer)members.get(i);
         int slot = 27 + (i - startIndex);
         this.inventory.setItem(slot, this.createMemberItem(member));
      }

      if (this.memberPage > 0) {
         this.inventory.setItem(48, (new ItemBuilder(Material.ARROW)).withName("<yellow>◀ ᴘʀᴇᴠɪᴏᴜs").withAction("prev-members").build());
      }

      var10000 = this.inventory;
      var10002 = (new ItemBuilder(Material.PLAYER_HEAD)).withName("<aqua><bold>ᴍᴇᴍʙᴇʀs");
      var10003 = new String[]{"<gray>Total: <white>" + members.size(), null};
      int var44 = this.memberPage + 1;
      var10003[1] = "<gray>Page: <white>" + var44 + " <gray>of <white>" + Math.max(1, totalPages);
      var10000.setItem(49, var10002.withLore(var10003).build());
      if (this.memberPage < totalPages - 1) {
         this.inventory.setItem(50, (new ItemBuilder(Material.ARROW)).withName("<yellow>ɴᴇxᴛ ▶").withAction("next-members").build());
      }

      this.inventory.setItem(45, (new ItemBuilder(Material.ARROW)).withName("<gray><bold>◀ ʙᴀᴄᴋ").withLore("<gray>Return to team list").withAction("back-button").build());
      this.inventory.setItem(53, (new ItemBuilder(Material.LIME_DYE)).withName("<green><bold>ʀᴇғʀᴇsʜ").withLore("<gray>Refresh this menu").withAction("refresh").build());
      ItemStack fillItem = (new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)).withName(" ").build();

      for(int i = 45; i < 54; ++i) {
         if (this.inventory.getItem(i) == null) {
            this.inventory.setItem(i, fillItem);
         }
      }

   }

   private ItemStack createMemberItem(TeamPlayer member) {
      OfflinePlayer player = Bukkit.getOfflinePlayer(member.getPlayerUuid());
      String playerName = player.getName() != null ? player.getName() : "Unknown";
      boolean isOwner = member.getPlayerUuid().equals(this.targetTeam.getOwnerUuid());
      List<String> lore = new ArrayList();
      String var10001 = member.getRole().toString();
      lore.add("<gray>Role: <white>" + var10001);
      var10001 = member.isOnline() ? "<green>✓" : "<red>✗";
      lore.add("<gray>Online: " + var10001);
      lore.add("");
      lore.add("<gray>Permissions:");
      var10001 = member.canWithdraw() ? "<green>✓" : "<red>✗";
      lore.add("<gray>- Withdraw: " + var10001);
      var10001 = member.canUseEnderChest() ? "<green>✓" : "<red>✗";
      lore.add("<gray>- Ender Chest: " + var10001);
      var10001 = member.canSetHome() ? "<green>✓" : "<red>✗";
      lore.add("<gray>- Set Home: " + var10001);
      lore.add("<gray>- Use Home: " + (member.canUseHome() ? "<green>✓" : "<red>✗"));
      if (!isOwner) {
         lore.add("");
         lore.add("<yellow>Left-Click <gray>to edit permissions");
         lore.add("<yellow>Right-Click <gray>to kick member");
      }

      return (new ItemBuilder(Material.PLAYER_HEAD)).withName((isOwner ? "<gold><bold>★ " : "<white>") + playerName).withLore(lore).asPlayerHead(member.getPlayerUuid()).withAction("member-" + member.getPlayerUuid().toString()).build();
   }

   public void open() {
      this.viewer.openInventory(this.inventory);
   }

   public Team getTargetTeam() {
      return this.targetTeam;
   }

   public int getMemberPage() {
      return this.memberPage;
   }

   public Inventory getInventory() {
      return this.inventory;
   }
}
