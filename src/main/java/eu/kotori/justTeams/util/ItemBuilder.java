package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

public class ItemBuilder {
   private final ItemStack itemStack;
   private final MiniMessage miniMessage = MiniMessage.miniMessage();

   private static NamespacedKey getActionKey() {
      NamespacedKey key = JustTeams.getActionKey();
      if (key == null) {
         throw new IllegalStateException("JustTeams plugin not initialized - actionKey is null");
      } else {
         return key;
      }
   }

   public ItemBuilder(Material material) {
      this.itemStack = new ItemStack(material);
   }

   public ItemBuilder(ItemStack itemStack) {
      this.itemStack = itemStack.clone();
   }

   public ItemBuilder withName(String name) {
      ItemMeta meta = this.itemStack.getItemMeta();
      if (meta != null) {
         Component component = this.miniMessage.deserialize(name).decoration(TextDecoration.ITALIC, false);
         meta.displayName(component);
         this.itemStack.setItemMeta(meta);
      }

      return this;
   }

   public ItemBuilder withLore(String... loreLines) {
      return this.withLore(Arrays.asList(loreLines));
   }

   public ItemBuilder withLore(List<String> loreLines) {
      ItemMeta meta = this.itemStack.getItemMeta();
      if (meta != null) {
         List<Component> lore = (List)loreLines.stream().map((line) -> this.miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false)).collect(Collectors.toList());
         meta.lore(lore);
         this.itemStack.setItemMeta(meta);
      }

      return this;
   }

   public ItemBuilder asPlayerHead(UUID playerUuid) {
      if (this.itemStack.getType() == Material.PLAYER_HEAD) {
         ItemMeta var3 = this.itemStack.getItemMeta();
         if (var3 instanceof SkullMeta) {
            SkullMeta skullMeta = (SkullMeta)var3;

            try {
               JustTeams plugin = JustTeams.getInstance();
               if (plugin != null && plugin.getBedrockSupport() != null && plugin.getBedrockSupport().isBedrockPlayer(playerUuid)) {
                  UUID javaUuid = plugin.getBedrockSupport().getJavaEditionUuid(playerUuid);
                  if (javaUuid != null && !javaUuid.equals(playerUuid)) {
                     skullMeta.setPlayerProfile(Bukkit.createProfile(javaUuid));
                  } else {
                     skullMeta.setPlayerProfile(Bukkit.createProfile(playerUuid));
                  }
               } else {
                  skullMeta.setPlayerProfile(Bukkit.createProfile(playerUuid));
               }

               this.itemStack.setItemMeta(skullMeta);
            } catch (Exception var5) {
               skullMeta.setPlayerProfile(Bukkit.createProfile(playerUuid));
               this.itemStack.setItemMeta(skullMeta);
            }
         }
      }

      return this;
   }

   public ItemBuilder asPlayerHeadBedrockCompatible(UUID playerUuid, Material bedrockFallback) {
      try {
         JustTeams plugin = JustTeams.getInstance();
         if (plugin != null && plugin.getBedrockSupport() != null && plugin.getBedrockSupport().isBedrockPlayer(playerUuid)) {
            if (bedrockFallback != null && bedrockFallback != Material.PLAYER_HEAD) {
               ItemStack fallbackItem = new ItemStack(bedrockFallback);
               return new ItemBuilder(fallbackItem);
            } else {
               return this.asPlayerHead(playerUuid);
            }
         } else {
            return this.asPlayerHead(playerUuid);
         }
      } catch (Exception var5) {
         return this.asPlayerHead(playerUuid);
      }
   }

   public ItemBuilder withGlow() {
      this.itemStack.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
      ItemMeta meta = this.itemStack.getItemMeta();
      if (meta != null) {
         meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
         this.itemStack.setItemMeta(meta);
      }

      return this;
   }

   public ItemBuilder withEnchantmentGlint() {
      return this.withGlow();
   }

   public ItemBuilder withCustomModelData(int customModelData) {
      ItemMeta meta = this.itemStack.getItemMeta();
      if (meta != null) {
         meta.setCustomModelData(customModelData);
         this.itemStack.setItemMeta(meta);
      }

      return this;
   }

   public ItemBuilder withAction(String action) {
      if (action != null && !action.isEmpty()) {
         ItemMeta meta = this.itemStack.getItemMeta();
         if (meta != null) {
            meta.getPersistentDataContainer().set(getActionKey(), PersistentDataType.STRING, action);
            this.itemStack.setItemMeta(meta);
         }

         return this;
      } else {
         return this;
      }
   }

   public ItemBuilder withData(String key, String value) {
      if (key != null && value != null) {
         ItemMeta meta = this.itemStack.getItemMeta();
         if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(JustTeams.getInstance(), key), PersistentDataType.STRING, value);
            this.itemStack.setItemMeta(meta);
         }

         return this;
      } else {
         return this;
      }
   }

   public ItemStack build() {
      return this.itemStack;
   }
}
