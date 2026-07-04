package eu.kotori.justTeams.core.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public class InventoryUtil {
   public static String serializeInventory(Inventory inventory) throws IOException {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
      dataOutput.writeInt(inventory.getSize());

      for(int i = 0; i < inventory.getSize(); ++i) {
         dataOutput.writeObject(inventory.getItem(i));
      }

      dataOutput.close();
      return Base64.getMimeEncoder().encodeToString(outputStream.toByteArray());
   }

   public static String serializeContents(ItemStack[] contents) throws IOException {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
      dataOutput.writeInt(contents.length);

      for(ItemStack item : contents) {
         dataOutput.writeObject(item);
      }

      dataOutput.close();
      return Base64.getMimeEncoder().encodeToString(outputStream.toByteArray());
   }

   public static void deserializeInventory(Inventory inventory, String data) throws IOException {
      if (data != null && !data.isEmpty()) {
         ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getMimeDecoder().decode(data));
         BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
         inventory.clear();
         int size = dataInput.readInt();
         int capacity = inventory.getSize();
         List<ItemStack> overflow = new ArrayList();

         for(int i = 0; i < size; ++i) {
            try {
               ItemStack item = (ItemStack)dataInput.readObject();
               if (item != null) {
                  if (i < capacity) {
                     inventory.setItem(i, item);
                  } else {
                     overflow.add(item);
                  }
               }
            } catch (ClassNotFoundException e) {
               throw new IOException("Unable to decode class type.", e);
            }
         }

         dataInput.close();
         if (!overflow.isEmpty()) {
            Logger.getLogger("JustTeams").warning("Enderchest payload had " + size + " slots but the inventory only holds " + capacity + "; " + overflow.size() + " stored item(s) did not fit and were not loaded into the view. They remain in the saved data only if the chest is NOT re-saved at the smaller size.");
         }

      }
   }
}
