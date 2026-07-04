package eu.kotori.justTeams.core.util;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class TextUtil {
   private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();
   private static final Set<String> SAFE_MINI_TAGS = Set.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "grey", "dark_gray", "dark_grey", "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "color", "colour", "c", "gradient", "rainbow", "bold", "b", "italic", "em", "i", "underlined", "u", "strikethrough", "st", "obfuscated", "obf", "reset", "r");
   private static final Pattern MINI_TAG_PATTERN = Pattern.compile("<\\s*/?\\s*([a-zA-Z_#][a-zA-Z0-9_#-]*)");
   private static final Pattern FULL_TAG_PATTERN = Pattern.compile("<\\s*/?\\s*([a-zA-Z_#][a-zA-Z0-9_#-]*)[^<>]*>");

   private TextUtil() {
   }

   public static boolean hasLegacyCodes(String s) {
      return s != null && (s.indexOf(38) >= 0 || s.indexOf(167) >= 0);
   }

   public static Component parse(MiniMessage miniMessage, String raw) {
      if (raw != null && !raw.isEmpty()) {
         if (hasLegacyCodes(raw)) {
            return LEGACY.deserialize(raw.replace('§', '&'));
         } else {
            try {
               return miniMessage.deserialize(raw);
            } catch (Exception var3) {
               return Component.text(raw);
            }
         }
      } else {
         return Component.empty();
      }
   }

   public static String toMiniMessage(MiniMessage miniMessage, String raw) {
      if (raw != null && !raw.isEmpty()) {
         return hasLegacyCodes(raw) ? (String)miniMessage.serialize(LEGACY.deserialize(raw.replace('§', '&'))) : raw;
      } else {
         return "";
      }
   }

   public static boolean containsUnsafeMiniMessage(String input) {
      if (input != null && input.indexOf(60) >= 0) {
         Matcher m = MINI_TAG_PATTERN.matcher(input);

         while(m.find()) {
            String tag = m.group(1).toLowerCase();
            if (!tag.startsWith("#") && !SAFE_MINI_TAGS.contains(tag)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static String sanitizeMiniMessage(String input) {
      if (input != null && input.indexOf(60) >= 0) {
         String out = input;

         for(int i = 0; i < 8; ++i) {
            String next = stripUnsafeTagsOnce(out);
            if (next.equals(out)) {
               break;
            }

            out = next;
         }

         if (containsUnsafeMiniMessage(out)) {
            out = out.replace("<", "").replace(">", "");
         }

         return out;
      } else {
         return input;
      }
   }

   private static String stripUnsafeTagsOnce(String input) {
      Matcher m = FULL_TAG_PATTERN.matcher(input);
      StringBuilder sb = new StringBuilder();

      while(m.find()) {
         String tag = m.group(1).toLowerCase();
         boolean safe = tag.startsWith("#") || SAFE_MINI_TAGS.contains(tag);
         m.appendReplacement(sb, safe ? Matcher.quoteReplacement(m.group()) : "");
      }

      m.appendTail(sb);
      return sb.toString();
   }
}
