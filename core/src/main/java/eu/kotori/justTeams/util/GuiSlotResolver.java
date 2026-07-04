package eu.kotori.justTeams.core.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

public final class GuiSlotResolver {
   private GuiSlotResolver() {
   }

   public static List<Integer> resolve(ConfigurationSection section, int fallbackStart, int fallbackEnd) {
      if (section != null && section.isList("slots")) {
         List<Integer> configured = section.getIntegerList("slots");
         if (configured != null && !configured.isEmpty()) {
            List<Integer> cleaned = new ArrayList(configured.size());

            for(int slot : configured) {
               if (slot >= 0) {
                  cleaned.add(slot);
               }
            }

            if (!cleaned.isEmpty()) {
               return cleaned;
            }
         }
      }

      List<Integer> fallback = new ArrayList(Math.max(0, fallbackEnd - fallbackStart));

      for(int i = fallbackStart; i < fallbackEnd; ++i) {
         fallback.add(i);
      }

      return fallback;
   }
}
