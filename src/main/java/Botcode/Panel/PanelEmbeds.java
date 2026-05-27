package Botcode.Panel;

import com.fasterxml.jackson.databind.JsonNode;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;

/**
 * Builds all embeds used by the interactive panel.
 *
 * <p>Each static method returns a ready-to-send {@link MessageEmbed}. Edit this file to change how
 * results are displayed without touching any interaction or command logic.
 */
public class PanelEmbeds {

  private static final Color COLOR_PANEL = new Color(0xFF6B00); // Phoenix orange
  private static final Color COLOR_MINING = new Color(0xE8A020); // amber
  private static final Color COLOR_TRADE = new Color(0x2ECC71); // green
  private static final Color COLOR_SHIP = new Color(0x3498DB); // blue
  private static final Color COLOR_WEAPON = new Color(0xE74C3C); // red
  private static final Color COLOR_INFO = new Color(0x95A5A6); // grey
  private static final Color COLOR_SOON = new Color(0x7F8C8D); // dark grey
  private static final String FOOTER_TEXT = "Phoenix Operations Division";
  private static final int EMBED_FIELD_LIMIT = 1024;

  // ------------------------------------------------------------------
  // Main panel embed
  // ------------------------------------------------------------------

  /**
   * The persistent embed shown in the panel channel. Users click the buttons below this to interact
   * with each module.
   */
  public static MessageEmbed mainPanel() {
    return new EmbedBuilder()
        .setTitle("🛸  Phoenix Industries — Operations Console")
        .setColor(COLOR_PANEL)
        .setDescription(
            "Use the buttons below to access Star Citizen tools.\n"
                + "All results are shown only to you.\n\u200B")
        .addField("⛏️  Mining", "Analyse rock viability, laser settings & consumables.", true)
        .addField("💰  Commodity", "Buy/sell price lookup and profit per SCU.", true)
        .addField("🚀  Ship Lookup", "Full ship specs, loadout, and stats.", true)
        .addField("🔫  Weapon", "Weapon stats, DPS, fire modes & ammo.", true)
        .addField("📦  Trade", "Best trade routes for your cargo capacity.", true)
        .addField("🏭  Refinery", "Method comparison, yield calc & station list.", true)
        .addField("🔧  Salvage", "Hotspots, ship guide, materials & tips.", true)
        .addField("⚙️  Components", "Component info, stats, and attributes.", true)
        .addField("🛡️  Armor", "Armor item details and values.", true)
        .addField("🗺️  Locations", "System/body/type lookup for known POIs.", true)
        .addField("📜  Missions", "Mission data entries from current dataset.", true)
        .addField(
            "🎯  FPS Weapons",
            "FPS-specific weapons by system with cost and buy/ammo locations.",
            true)
        .setFooter(FOOTER_TEXT)
        .build();
  }

  // ------------------------------------------------------------------
  // Mining result embed
  // ------------------------------------------------------------------

  public static MessageEmbed miningResult(
      String rock,
      String ship,
      String laser,
      String consumable,
      int operators,
      int modulesPerHead,
      int totalModules,
      String brief,
      String full) {
    return new EmbedBuilder()
        .setTitle("⛏️  Mining Analysis — " + rock)
        .setColor(COLOR_MINING)
        .addField(
            "Setup",
            "**Ship:** "
                + ship
                + "\n"
                + "**Active Heads:** "
                + operators
                + "\n"
                + "**Laser:** "
                + laser
                + "\n"
                + "**Consumable:** "
                + consumable
                + "\n"
                + "**Modules/Head:** "
                + modulesPerHead
                + "\n"
                + "**Total Modules:** "
                + totalModules,
            true)
        .addField("Quick Summary", brief, false)
        .addField("Full Analysis", "```\n" + full + "\n```", false)
        .setFooter(FOOTER_TEXT)
        .build();
  }

  // ------------------------------------------------------------------
  // Commodity result embed
  // ------------------------------------------------------------------

  public static MessageEmbed commodityResult(
      String name,
      String bestBuy,
      String bestSell,
      double profit,
      String buyLocations,
      String sellLocations) {
    return new EmbedBuilder()
        .setTitle("💰  Commodity — " + name)
        .setColor(COLOR_TRADE)
        .addField("Best Buy", bestBuy, true)
        .addField("Best Sell", bestSell, true)
        .addField("Profit / SCU", String.format("%.2f aUEC", profit), true)
        .addField("Buy Locations", buyLocations.isEmpty() ? "*None found*" : buyLocations, false)
        .addField("Sell Locations", sellLocations.isEmpty() ? "*None found*" : sellLocations, false)
        .setFooter(FOOTER_TEXT)
        .build();
  }

  // ------------------------------------------------------------------
  // Ship result embed
  // ------------------------------------------------------------------

  public static MessageEmbed shipResult(String name, String info, String stats, String loadout) {
    EmbedBuilder eb = new EmbedBuilder().setTitle("🚀  Ship Specs — " + name).setColor(COLOR_SHIP);
    addChunkedField(eb, "Info", info.isEmpty() ? "*No data*" : info, false);
    addChunkedField(eb, "Stats", stats.isEmpty() ? "*No data*" : stats, false);
    if (!loadout.isEmpty()) {
      addChunkedField(eb, "Loadout / Hardpoints", loadout, false);
    }
    return eb.setFooter(FOOTER_TEXT).build();
  }

  public static MessageEmbed shipResult(
      String name, String info, String stats, String loadout, String imageUrl) {
    EmbedBuilder eb = new EmbedBuilder().setTitle("🚀  Ship Specs — " + name).setColor(COLOR_SHIP);
    addChunkedField(eb, "Info", info.isEmpty() ? "*No data*" : info, false);
    addChunkedField(eb, "Stats", stats.isEmpty() ? "*No data*" : stats, false);
    if (!loadout.isEmpty()) {
      addChunkedField(eb, "Loadout / Hardpoints", loadout, false);
    }
    if (imageUrl != null && !imageUrl.isBlank()) {
      eb.setImage(imageUrl);
    }
    return eb.setFooter(FOOTER_TEXT).build();
  }

  // ------------------------------------------------------------------
  // Weapon result embed
  // ------------------------------------------------------------------

  public static MessageEmbed weaponResult(String name, String info, String stats, String extras) {
    EmbedBuilder eb =
        new EmbedBuilder().setTitle("🔫  Weapon Stats — " + name).setColor(COLOR_WEAPON);
    addChunkedField(eb, "Info", info.isEmpty() ? "*No data*" : info, false);
    addChunkedField(
        eb,
        "Stats",
        stats.isEmpty() ? "*Combat stats not yet available in live data.*" : stats,
        false);
    if (!extras.isEmpty()) {
      addChunkedField(eb, "Additional Details", extras, false);
    }
    return eb.setFooter(FOOTER_TEXT).build();
  }

  private static java.util.List<String> splitFieldText(String text, int limit) {
    java.util.List<String> out = new java.util.ArrayList<>();
    if (text == null || text.isBlank()) {
      return out;
    }

    String[] lines = text.split("\\n");
    StringBuilder current = new StringBuilder();
    for (String line : lines) {
      String add = current.isEmpty() ? line : "\n" + line;
      if (current.length() + add.length() > limit) {
        if (!current.isEmpty()) {
          out.add(current.toString());
          current = new StringBuilder();
        }
        if (line.length() > limit) {
          int start = 0;
          while (start < line.length()) {
            int end = Math.min(start + limit, line.length());
            out.add(line.substring(start, end));
            start = end;
          }
          continue;
        }
      }
      if (current.isEmpty()) {
        current.append(line);
      } else {
        current.append("\n").append(line);
      }
    }
    if (!current.isEmpty()) {
      out.add(current.toString());
    }
    return out;
  }

  // ------------------------------------------------------------------
  // Trade result embed
  // ------------------------------------------------------------------

  public static MessageEmbed tradeResult(String routes) {
    EmbedBuilder eb =
        new EmbedBuilder()
            .setTitle("📦  Trade Routes")
            .setColor(COLOR_TRADE)
            .setFooter(FOOTER_TEXT);
    addChunkedField(eb, "Routes", routes.isEmpty() ? "*No routes found.*" : routes, false);
    return eb.build();
  }

  // ------------------------------------------------------------------
  // Additional dataset embeds
  // ------------------------------------------------------------------

  public static MessageEmbed componentResult(
      String name, String info, String stats, String attributes) {
    EmbedBuilder eb =
        new EmbedBuilder().setTitle("⚙️  Component — " + name).setColor(new Color(0x16A085));
    addChunkedField(eb, "Info", info.isEmpty() ? "*No data*" : info, true);
    addChunkedField(eb, "Stats", stats.isEmpty() ? "*No data*" : stats, true);
    if (!attributes.isEmpty()) {
      addChunkedField(eb, "Attributes", attributes, false);
    }
    return eb.setFooter(FOOTER_TEXT).build();
  }

  /**
   * Legacy plain-text armor result (kept for backwards compatibility).
   */
  public static MessageEmbed armorResult(String name, String details) {
    return new EmbedBuilder()
        .setTitle("🛡️  Armor — " + name)
        .setColor(new Color(0x9B59B6))
        .setDescription(details.isEmpty() ? "*No data*" : details)
        .setFooter(FOOTER_TEXT)
        .build();
  }

  /**
   * Rich armor result built directly from a JsonNode entry. Shows description, class/weight/pieces
   * profile, resistance percentages, buy locations, and notes as separate embed fields.
   */
  public static MessageEmbed armorResult(String name, JsonNode armor) {
    if (armor == null || armor.isMissingNode() || armor.isNull()) {
      return armorResult(name, "*No data*");
    }

    EmbedBuilder eb =
        new EmbedBuilder().setTitle("🛡️  Armor — " + name).setColor(new Color(0x9B59B6));

    // Description
    String desc = armor.path("description").asText("").trim();
    if (!desc.isEmpty()) {
      eb.setDescription(desc);
    }

    // Profile column
    StringBuilder profile = new StringBuilder();
    appendField(profile, "Class", armor.path("class").asText(""));
    appendField(profile, "Manufacturer", armor.path("manufacturer").asText(""));
    appendField(profile, "Weight Class", armor.path("weight_class").asText(""));
    appendField(profile, "Pieces", armor.path("pieces").asText(""));
    if (profile.length() > 0) {
      eb.addField("Profile", profile.toString().trim(), true);
    }

    // Resistance column
    int ballistic = armor.path("ballistic_resist_pct").asInt(0);
    int energy = armor.path("energy_resist_pct").asInt(0);
    int distortion = armor.path("distortion_resist_pct").asInt(0);
    String temp = armor.path("temp_resist").asText("").trim();
    String resistance =
        "**Ballistic:** "
            + (ballistic > 0 ? ballistic + "%" : "—")
            + "\n"
            + "**Energy:** "
            + (energy > 0 ? energy + "%" : "—")
            + "\n"
            + "**Distortion:** "
            + (distortion > 0 ? distortion + "%" : "—")
            + "\n"
            + "**Temp Resist:** "
            + (temp.isEmpty() ? "—" : temp);
    eb.addField("Resistance", resistance, true);

    // Buy locations & notes
    String buyLocations = armor.path("buy_locations").asText("").trim();
    if (!buyLocations.isEmpty()) {
      eb.addField("Buy Locations", buyLocations, false);
    }

    String notes = armor.path("notes").asText("").trim();
    if (!notes.isEmpty()) {
      eb.addField("Notes", notes, false);
    }

    return eb.setFooter(FOOTER_TEXT).build();
  }

  private static void appendField(StringBuilder sb, String label, String value) {
    if (value != null && !value.isBlank()) {
      sb.append("**").append(label).append(":** ").append(value).append("\n");
    }
  }

  public static MessageEmbed locationResult(String name, String details) {
    EmbedBuilder eb =
        new EmbedBuilder()
            .setTitle("🗺️  Location — " + name)
            .setColor(new Color(0x1ABC9C))
            .setFooter(FOOTER_TEXT);
    addChunkedField(eb, "Details", details.isEmpty() ? "*No data*" : details, false);
    return eb.build();
  }

  public static MessageEmbed missionResult(String name, String details) {
    EmbedBuilder eb =
        new EmbedBuilder()
            .setTitle("📜  Mission — " + name)
            .setColor(new Color(0xF1C40F))
            .setFooter(FOOTER_TEXT);
    addChunkedField(eb, "Details", details.isEmpty() ? "*No data*" : details, false);
    return eb.build();
  }

  // ------------------------------------------------------------------
  // Generic embeds
  // ------------------------------------------------------------------

  /**
   * Shown when a module is not yet implemented.
   */
  public static MessageEmbed comingSoon(String module) {
    return new EmbedBuilder()
        .setTitle("🚧  " + module + " — Coming Soon")
        .setColor(COLOR_SOON)
        .setDescription("This module is under development. Check back in a future update!")
        .setFooter(FOOTER_TEXT)
        .build();
  }

  // ------------------------------------------------------------------
  // Refinery embeds
  // ------------------------------------------------------------------

  public static MessageEmbed refineryAnalysis(String ore, int rawScu, String brief, String full) {
    return new EmbedBuilder()
        .setTitle("🏭  Refinery Analysis — " + ore)
        .setColor(new Color(0xF39C12))
        .addField("Input", rawScu + " SCU raw ore", true)
        .addField("Recommendation", brief, false)
        .addField("All Methods", "```\n" + full + "\n```", false)
        .setFooter(FOOTER_TEXT)
        .build();
  }

  public static MessageEmbed refineryStations(String stations) {
    EmbedBuilder eb =
        new EmbedBuilder()
            .setTitle("🏭  Refinery Stations")
            .setColor(new Color(0xF39C12))
            .setFooter(FOOTER_TEXT);
    addChunkedField(eb, "Stations", stations.isEmpty() ? "*No station data.*" : stations, false);
    return eb.build();
  }

  // ------------------------------------------------------------------
  // Salvage embeds
  // ------------------------------------------------------------------

  public static MessageEmbed salvageHotspots(String content) {
    EmbedBuilder eb =
        new EmbedBuilder()
            .setTitle("🔧  Salvage Hotspots")
            .setColor(new Color(0x8E44AD))
            .setFooter(FOOTER_TEXT);
    addChunkedField(eb, "Hotspots", content.isEmpty() ? "*No data.*" : content, false);
    return eb.build();
  }

  public static MessageEmbed salvageShips(String content) {
    EmbedBuilder eb =
        new EmbedBuilder()
            .setTitle("🔧  Salvage Ships")
            .setColor(new Color(0x8E44AD))
            .setFooter(FOOTER_TEXT);
    addChunkedField(eb, "Ships", content.isEmpty() ? "*No data.*" : content, false);
    return eb.build();
  }

  public static MessageEmbed salvageMaterials(String content) {
    EmbedBuilder eb =
        new EmbedBuilder()
            .setTitle("🔧  Salvage Materials")
            .setColor(new Color(0x8E44AD))
            .setFooter(FOOTER_TEXT);
    addChunkedField(eb, "Materials", content.isEmpty() ? "*No data.*" : content, false);
    return eb.build();
  }

  public static MessageEmbed salvageTips(String content) {
    EmbedBuilder eb =
        new EmbedBuilder()
            .setTitle("💡  Salvage Tips")
            .setColor(new Color(0x8E44AD))
            .setFooter(FOOTER_TEXT);
    addChunkedField(eb, "Tips", content.isEmpty() ? "*No tips yet.*" : content, false);
    return eb.build();
  }

  /**
   * Shown when a lookup returns no data.
   */
  public static MessageEmbed notFound(String type, String query) {
    return new EmbedBuilder()
        .setTitle("❌  Not Found")
        .setColor(COLOR_INFO)
        .setDescription(
            "No "
                + type
                + " found matching **"
                + query
                + "**.\n"
                + "Check the spelling and try again.")
        .setFooter(FOOTER_TEXT)
        .build();
  }

  /**
   * Shown when data fails to load.
   */
  public static MessageEmbed dataError(String dataset) {
    return new EmbedBuilder()
        .setTitle("⚠️  Data Unavailable")
        .setColor(COLOR_INFO)
        .setDescription(
            "The **"
                + dataset
                + "** dataset isn't loaded yet.\n"
                + "Try again in a moment or contact an admin.")
        .setFooter(FOOTER_TEXT)
        .build();
  }

  /**
   * Shown after an admin data-refresh action from the panel.
   */
  public static MessageEmbed dataRefreshResult(int ok, int total, String details) {
    EmbedBuilder eb =
        new EmbedBuilder()
            .setTitle("🔄  Data Refresh Complete")
            .setColor(ok == total ? COLOR_TRADE : COLOR_INFO)
            .setDescription("Updated **" + ok + "/" + total + "** datasets.")
            .setFooter(FOOTER_TEXT);
    addChunkedField(
        eb, "Datasets", details == null || details.isBlank() ? "*No details*" : details, false);
    return eb.build();
  }

  /**
   * Snapshot of loaded dataset sizes for quick GUI health checks.
   */
  public static MessageEmbed dataStatus(String details) {
    EmbedBuilder eb =
        new EmbedBuilder()
            .setTitle("📈  Dataset Status")
            .setColor(COLOR_PANEL)
            .setFooter(FOOTER_TEXT);
    addChunkedField(
        eb,
        "Status",
        details == null || details.isBlank() ? "*No dataset status available.*" : details,
        false);
    return eb.build();
  }

  private static void addChunkedField(
      EmbedBuilder eb, String title, String content, boolean inline) {
    java.util.List<String> chunks = splitFieldText(content, EMBED_FIELD_LIMIT);
    if (chunks.isEmpty()) {
      eb.addField(title, "*No data*", inline);
      return;
    }
    for (int i = 0; i < chunks.size(); i++) {
      String fieldTitle = i == 0 ? title : title + " (cont.)";
      eb.addField(fieldTitle, chunks.get(i), inline);
    }
  }
}
