package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.util.ItemBuilder;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class TeamCreationColorGUI implements InventoryHolder {
   private final JustTeams plugin;
   private final Player viewer;
   private final String teamName;
   private final String teamTag;
   private final Inventory inventory;

   public TeamCreationColorGUI(JustTeams plugin, Player viewer, String teamName, String teamTag) {
      this.plugin = plugin;
      this.viewer = viewer;
      this.teamName = teamName;
      this.teamTag = teamTag;
      this.inventory = Bukkit.createInventory(this, 54, plugin.getMiniMessage().deserialize("<gold><bold>Choose Team Color"));
      this.setupInventory();
   }

   private void setupInventory() {
      this.addColorOption(10, ChatColor.RED, "RED", Material.RED_WOOL);
      this.addColorOption(11, ChatColor.DARK_RED, "DARK_RED", Material.RED_TERRACOTTA);
      this.addColorOption(12, ChatColor.GOLD, "GOLD", Material.ORANGE_WOOL);
      this.addColorOption(13, ChatColor.YELLOW, "YELLOW", Material.YELLOW_WOOL);
      this.addColorOption(14, ChatColor.GREEN, "GREEN", Material.LIME_WOOL);
      this.addColorOption(15, ChatColor.DARK_GREEN, "DARK_GREEN", Material.GREEN_WOOL);
      this.addColorOption(16, ChatColor.AQUA, "AQUA", Material.CYAN_WOOL);
      this.addColorOption(19, ChatColor.DARK_AQUA, "DARK_AQUA", Material.LIGHT_BLUE_WOOL);
      this.addColorOption(20, ChatColor.BLUE, "BLUE", Material.BLUE_WOOL);
      this.addColorOption(21, ChatColor.DARK_BLUE, "DARK_BLUE", Material.BLUE_TERRACOTTA);
      this.addColorOption(22, ChatColor.LIGHT_PURPLE, "LIGHT_PURPLE", Material.MAGENTA_WOOL);
      this.addColorOption(23, ChatColor.DARK_PURPLE, "DARK_PURPLE", Material.PURPLE_WOOL);
      this.addColorOption(24, ChatColor.WHITE, "WHITE", Material.WHITE_WOOL);
      this.addColorOption(25, ChatColor.GRAY, "GRAY", Material.LIGHT_GRAY_WOOL);
      this.addColorOption(28, ChatColor.DARK_GRAY, "DARK_GRAY", Material.GRAY_WOOL);
      this.addColorOption(29, ChatColor.BLACK, "BLACK", Material.BLACK_WOOL);
      ItemStack cancelItem = (new ItemBuilder(Material.BARRIER)).withName("<red><bold>Cancel").withLore("<gray>Click to cancel team creation").withAction("cancel-creation").build();
      this.inventory.setItem(49, cancelItem);
      ItemStack filler = (new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)).withName(" ").build();

      for(int i = 0; i < 54; ++i) {
         if (this.inventory.getItem(i) == null) {
            this.inventory.setItem(i, filler);
         }
      }

   }

   private void addColorOption(int slot, ChatColor color, String colorName, Material material) {
      String miniMessageColor = this.chatColorToMiniMessage(colorName);
      List<String> lore = new ArrayList();
      lore.add("<gray>Team Name: <white>" + this.teamName);
      if (this.teamTag != null && !this.teamTag.isEmpty()) {
         lore.add("<gray>Team Tag: <white>" + this.teamTag);
      }

      lore.add("");
      lore.add("<gray>Preview: " + miniMessageColor + this.teamName);
      lore.add("");
      lore.add("<yellow>Click to select this color!");
      ItemStack item = (new ItemBuilder(material)).withName(miniMessageColor + "<bold>" + this.formatColorName(colorName)).withLore(lore).withAction("color:" + colorName).build();
      this.inventory.setItem(slot, item);
   }

   private String chatColorToMiniMessage(String colorName) {
      String var10000;
      switch (colorName) {
         case "RED" -> var10000 = "<red>";
         case "DARK_RED" -> var10000 = "<dark_red>";
         case "GOLD" -> var10000 = "<gold>";
         case "YELLOW" -> var10000 = "<yellow>";
         case "GREEN" -> var10000 = "<green>";
         case "DARK_GREEN" -> var10000 = "<dark_green>";
         case "AQUA" -> var10000 = "<aqua>";
         case "DARK_AQUA" -> var10000 = "<dark_aqua>";
         case "BLUE" -> var10000 = "<blue>";
         case "DARK_BLUE" -> var10000 = "<dark_blue>";
         case "LIGHT_PURPLE" -> var10000 = "<light_purple>";
         case "DARK_PURPLE" -> var10000 = "<dark_purple>";
         case "WHITE" -> var10000 = "<white>";
         case "GRAY" -> var10000 = "<gray>";
         case "DARK_GRAY" -> var10000 = "<dark_gray>";
         case "BLACK" -> var10000 = "<black>";
         default -> var10000 = "<white>";
      }

      return var10000;
   }

   private String formatColorName(String colorName) {
      return colorName.replace("_", " ");
   }

   public void open() {
      this.viewer.openInventory(this.inventory);
   }

   public @NotNull Inventory getInventory() {
      return this.inventory;
   }

   public String getTeamName() {
      return this.teamName;
   }

   public String getTeamTag() {
      return this.teamTag;
   }
}
