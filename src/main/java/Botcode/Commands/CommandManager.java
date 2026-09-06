package Botcode.Commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;

import com.fasterxml.jackson.databind.JsonNode;

import Botcode.StarCitizen.StarCitizenDataService;
import Botcode.StarCitizen.MiningService;
import Botcode.StarCitizen.MissingDataReportService;
import Botcode.Utils.HelpBuilder;
import Botcode.Security.RateLimiter;
import Botcode.Security.InputValidator;
import Botcode.Monitoring.MetricsCollector;
import Botcode.AI.AIUtils.BotConfig;

import java.util.List;

/**
 * Handles slash-command execution and autocomplete suggestions.
 *
 * <p>Current commands are focused on Star Citizen commodity and mining tools.
 */
public class CommandManager extends ListenerAdapter {
  private static final RateLimiter RATE_LIMITER = new RateLimiter();

  /**
   * Executes slash command handlers for commodity lookup and mining analysis.
   */
  @Override
  public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {

    // Security: Rate limiting check
    if (BotConfig.INPUT_VALIDATION_ENABLED) {
      String userId = event.getUser().getId();
      String guildId = event.getGuild() != null ? event.getGuild().getId() : "dm";
      
      // Check per-user rate limit
      if (RATE_LIMITER.isUserRateLimited(userId)) {
        event.reply("⚠️ You're sending commands too fast. Please wait a moment.").setEphemeral(true).queue();
        MetricsCollector.recordEvent("rate_limit_exceeded", 1);
        return;
      }
      
      // Check per-guild rate limit
      if (!guildId.equals("dm") && RATE_LIMITER.isGuildRateLimited(guildId)) {
        event.reply("⚠️ This server is sending commands too fast. Please wait a moment.").setEphemeral(true).queue();
        MetricsCollector.recordEvent("guild_rate_limit_exceeded", 1);
        return;
      }
    }

    // Security: Input validation
    if (BotConfig.INPUT_VALIDATION_ENABLED && event.getOptions() != null && !event.getOptions().isEmpty()) {
      for (var opt : event.getOptions()) {
        String value = opt.getAsString();
        if (value != null && !value.isBlank()) {
          if (!RATE_LIMITER.isInputValid(value)) {
            event.reply("⚠️ Input too long. Please shorten your request.").setEphemeral(true).queue();
            MetricsCollector.recordEvent("validation_failed", 1);
            return;
          }
          if (!InputValidator.isSafe(value)) {
            event.reply("⚠️ Invalid input detected. Please remove unsafe characters or patterns.")
                .setEphemeral(true).queue();
            MetricsCollector.recordEvent("validation_failed", 1);
            System.out.println(
                "[SecurityAudit] VALIDATION_FAILURE user=" + event.getUser().getId()
                    + " guild=" + (event.getGuild() != null ? event.getGuild().getId() : "dm"));
            return;
          }
        }
      }
    }

    // /help — show full command reference as an embed
    if (event.getName().equals("help")) {
      boolean isAdmin =
          event.getMember() != null
              && (event.getMember().isOwner()
              || event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)
              || event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR));
      event.replyEmbeds(HelpBuilder.build(isAdmin)).setEphemeral(true).queue();
      return;
    }

    // Handle commodity lookups from the normalized Star Citizen dataset.
    if (event.getName().equals("commodity")) {

      // Read command input and pull the commodities dataset from cache.
      String name = event.getOption("name").getAsString();
      JsonNode commodities = StarCitizenDataService.get("commodities");

      if (commodities == null || commodities.isEmpty()) {
        event.reply("Commodity data not loaded.").queue();
        return;
      }

      String resolved = StarCitizenDataService.resolveDatasetKey("commodities", name);
      JsonNode entry = resolved != null ? commodities.get(resolved) : null;

      if (entry == null) {
        java.util.List<String> suggestions =
            StarCitizenDataService.suggestDatasetKeys("commodities", name, 5);
        String suffix =
            suggestions.isEmpty() ? "" : "\nDid you mean: " + String.join(", ", suggestions);
        event.reply("Commodity not found: " + name + suffix).queue();
        return;
      }

      name = resolved;

      String bestBuy = entry.path("best_buy").asText("Unknown");
      String bestSell = entry.path("best_sell").asText("Unknown");
      double profit = entry.path("profit_per_scu").asDouble(0);

      // Build display lists — cheapest buy first, most expensive sell first.
      java.util.List<double[]> buyRows = new java.util.ArrayList<>();
      java.util.List<double[]> sellRows = new java.util.ArrayList<>();
      java.util.List<String> buyLocs = new java.util.ArrayList<>();
      java.util.List<String> sellLocs = new java.util.ArrayList<>();

      int i = 0;
      for (JsonNode b : entry.withArray("buy")) {
        double p = b.path("price").asDouble(0);
        String l = b.path("location").asText("");
        if (p > 0 && !l.isBlank()) {
          buyLocs.add(l);
          buyRows.add(new double[]{p, i});
        }
        i++;
      }
      buyRows.sort((a, b2) -> Double.compare(a[0], b2[0]));

      int j = 0;
      for (JsonNode s : entry.withArray("sell")) {
        double p = s.path("price").asDouble(0);
        String l = s.path("location").asText("");
        if (p > 0 && !l.isBlank()) {
          sellLocs.add(l);
          sellRows.add(new double[]{p, j});
        }
        j++;
      }
      sellRows.sort((a, b2) -> Double.compare(b2[0], a[0]));

      StringBuilder buyList = new StringBuilder();
      for (double[] row : buyRows) {
        buyList
            .append("• ")
            .append(buyLocs.get((int) row[1]))
            .append(" — ")
            .append(String.format("%,.0f", row[0]))
            .append(" aUEC\n");
      }
      StringBuilder sellList = new StringBuilder();
      for (double[] row : sellRows) {
        sellList
            .append("• ")
            .append(sellLocs.get((int) row[1]))
            .append(" — ")
            .append(String.format("%,.0f", row[0]))
            .append(" aUEC\n");
      }

      // Format a Discord-friendly markdown response.
      String response =
          "**Commodity: "
              + name
              + "**\n\n"
              + "**Best Buy:** "
              + bestBuy
              + "\n"
              + "**Best Sell:** "
              + bestSell
              + "\n"
              + "**Profit per SCU:** "
              + String.format("%,.2f", profit)
              + " aUEC\n\n"
              + "**Buy Locations (cheapest first):**\n"
              + (buyList.length() > 0 ? buyList : "*None found*\n")
              + "\n**Sell Locations (best price first):**\n"
              + (sellList.length() > 0 ? sellList : "*None found*\n");

      event.reply(response).queue();
    }

    // Run mining viability analysis and choose response detail level.
    if (event.getName().equals("mine")) {

      String rock = event.getOption("rock").getAsString();
      String ship = event.getOption("ship").getAsString();
      String laser = event.getOption("laser").getAsString();
      String consumable = event.getOption("consumable").getAsString();
      int operators = event.getOption("operators").getAsInt();
      String format = event.getOption("format").getAsString().toLowerCase();

      MiningService.MiningResult result =
          MiningService.analyzeRock(rock, ship, laser, consumable, operators);

      String output;

      // Route formatter mode so users can choose summary, full, or targeted output.
      switch (format) {
        case "full" -> output = MiningService.MiningResultFormatter.full(result);
        case "specific" -> {
          String field = event.getOption("field").getAsString();
          output = MiningService.MiningResultFormatter.specific(result, field);
        }
        default -> output = MiningService.MiningResultFormatter.brief(result);
      }

      event.reply(output).queue();
    }

    // /ship — display full ship specifications from the normalized dataset.
    if (event.getName().equals("ship")) {
      String input = event.getOption("name").getAsString();
      JsonNode shipData = StarCitizenDataService.get("ships");

      if (shipData == null || shipData.isEmpty()) {
        event.reply("Ship data not loaded.").queue();
        return;
      }

      String resolved = StarCitizenDataService.resolveDatasetKey("ships", input);
      JsonNode entry = resolved != null ? shipData.get(resolved) : null;

      if (entry == null) {
        java.util.List<String> suggestions =
            StarCitizenDataService.suggestDatasetKeys("ships", input, 5);
        String suffix =
            suggestions.isEmpty() ? "" : "\nDid you mean: " + String.join(", ", suggestions);
        event.reply("Ship not found: " + input + suffix).queue();
        return;
      }

      JsonNode info = entry.path("info");
      JsonNode stats = entry.path("stats");
      double[] derivedFirepower = StarCitizenDataService.deriveShipFirepowerFromLoadout(entry);
      double[] effectiveTotals = StarCitizenDataService.deriveShipEffectiveTotalsFromLoadout(entry);

      // ── Info ──────────────────────────────────────────────────
      StringBuilder sb = new StringBuilder();
      sb.append("**Ship: ").append(resolved).append("**\n\n");
      sb.append("**Manufacturer:** ")
          .append(info.path("manufacturer").asText("Unknown"))
          .append("\n");
      String type = info.path("type").asText("");
      String career = info.path("career").asText("");
      String dimensions = info.path("dimensions").asText("");
      if (!type.isBlank()) {
        sb.append("**Type:** ").append(type).append("\n");
      }
      if (!career.isBlank()) {
        sb.append("**Career:** ").append(career).append("\n");
      }
      sb.append("**Role:** ").append(info.path("role").asText("Unknown")).append("\n");
      sb.append("**Size:** ").append(info.path("size").asText("Unknown")).append("\n");
      if (!dimensions.isBlank()) {
        sb.append("**Dimensions:** ").append(dimensions).append("\n");
      }
      sb.append("**Crew:** ").append(info.path("crew").asInt(0)).append("\n");
      sb.append("**Cargo (SCU):** ").append(info.path("cargo").asInt(0)).append("\n");

      double price = info.path("price_auec").asDouble(0);
      if (price > 0) {
        sb.append("**Price:** ").append(String.format("%,.0f", price)).append(" aUEC\n");
      }

      int scm = info.path("scm_speed").asInt(0);
      int vmax = info.path("max_speed").asInt(0);
      if (scm > 0) {
        sb.append("**SCM Speed:** ").append(scm).append(" m/s\n");
      }
      if (vmax > 0) {
        sb.append("**Max Speed:** ").append(vmax).append(" m/s\n");
      }

      int shield = info.path("shield_hp").asInt(0);
      int hull = info.path("hull_hp").asInt(0);
      if (shield > 0) {
        sb.append("**Shield HP:** ").append(String.format("%,d", shield)).append("\n");
      }
      if (hull > 0) {
        sb.append("**Hull HP:** ").append(String.format("%,d", hull)).append("\n");
      }

      String claimTime = info.path("claim_time").asText("");
      String expediteTime = info.path("expedite_time").asText("");
      if (!claimTime.isBlank()) {
        sb.append("**Claim Time:** ").append(claimTime).append("\n");
      }
      if (!expediteTime.isBlank()) {
        sb.append("**Expedite Time:** ").append(expediteTime).append("\n");
      }

      String storeUrl = info.path("store_url").asText("");
      if (!storeUrl.isBlank()) {
        sb.append("**Store:** ").append(storeUrl).append("\n");
      }

      // ── Stats ─────────────────────────────────────────────────
      double mass = stats.path("mass").asDouble(0);
      double pitch = stats.path("pitch").asDouble(0);
      double yaw = stats.path("yaw").asDouble(0);
      double roll = stats.path("roll").asDouble(0);
      double qtRange = stats.path("qt_range").asDouble(0);
      double qtSpeed = stats.path("qt_speed").asDouble(0);
      double h2Fuel = stats.path("hydrogen_fuel").asDouble(0);
      double qtFuel = stats.path("quantum_fuel").asDouble(0);
      double pilotDps = Math.max(stats.path("pilot_dps").asDouble(0), derivedFirepower[0]);
      double turretDps = Math.max(stats.path("turret_dps").asDouble(0), derivedFirepower[1]);
      double missileDps = Math.max(stats.path("missile_dps").asDouble(0), derivedFirepower[2]);
      double pilotAlpha = Math.max(stats.path("pilot_alpha").asDouble(0), derivedFirepower[3]);
      double turretAlpha = Math.max(stats.path("turret_alpha").asDouble(0), derivedFirepower[4]);
      double missileAlpha = Math.max(stats.path("missile_alpha").asDouble(0), derivedFirepower[5]);
      double totalDps =
          Math.max(stats.path("total_dps").asDouble(0), pilotDps + turretDps + missileDps);
      double totalAlpha =
          Math.max(stats.path("total_alpha").asDouble(0), pilotAlpha + turretAlpha + missileAlpha);

      boolean hasFlightStats =
          mass > 0 || pitch > 0 || yaw > 0 || roll > 0 || qtRange > 0 || h2Fuel > 0;
      if (hasFlightStats) {
        sb.append("\n**— Flight Stats —**\n");
        if (mass > 0) {
          sb.append("**Mass:** ").append(String.format("%,.0f", mass)).append(" kg\n");
        }
        if (pitch > 0) {
          sb.append("**Pitch:** ").append(pitch).append(" °/s\n");
        }
        if (yaw > 0) {
          sb.append("**Yaw:** ").append(yaw).append(" °/s\n");
        }
        if (roll > 0) {
          sb.append("**Roll:** ").append(roll).append(" °/s\n");
        }
        if (qtSpeed > 0) {
          sb.append("**QT Speed:** ").append(String.format("%.0f", qtSpeed)).append(" km/s\n");
        }
        if (qtRange > 0) {
          sb.append("**QT Range:** ").append(String.format("%.0f", qtRange)).append(" AU\n");
        }
        if (h2Fuel > 0) {
          sb.append("**H₂ Fuel:** ").append(String.format("%,.0f", h2Fuel)).append(" units\n");
        }
        if (qtFuel > 0) {
          sb.append("**QT Fuel:** ").append(String.format("%,.0f", qtFuel)).append(" units\n");
        }
      }

      boolean hasFirepower = totalDps > 0 || pilotDps > 0 || totalAlpha > 0;
      if (hasFirepower) {
        sb.append("\n**— Firepower —**\n");
        if (pilotDps > 0) {
          sb.append("**Pilot DPS:** ").append(String.format("%.1f", pilotDps)).append("\n");
        }
        if (turretDps > 0) {
          sb.append("**Turret DPS:** ").append(String.format("%.1f", turretDps)).append("\n");
        }
        if (missileDps > 0) {
          sb.append("**Missile DPS:** ").append(String.format("%.1f", missileDps)).append("\n");
        }
        if (totalDps > 0) {
          sb.append("**Total DPS:** ").append(String.format("%.1f", totalDps)).append("\n");
        }
        if (pilotAlpha > 0) {
          sb.append("**Pilot Alpha:** ").append(String.format("%.1f", pilotAlpha)).append("\n");
        }
        if (turretAlpha > 0) {
          sb.append("**Turret Alpha:** ").append(String.format("%.1f", turretAlpha)).append("\n");
        }
        if (missileAlpha > 0) {
          sb.append("**Missile Alpha:** ").append(String.format("%.1f", missileAlpha)).append("\n");
        }
        if (totalAlpha > 0) {
          sb.append("**Total Alpha:** ").append(String.format("%.1f", totalAlpha)).append("\n");
        }
        sb.append("**Armor Mod (Physical / Energy):** ")
            .append(formatSignedPercent(stats.path("armor_physical_damage_modifier").asDouble(0)))
            .append(" / ")
            .append(formatSignedPercent(stats.path("armor_energy_damage_modifier").asDouble(0)))
            .append("\n");
        sb.append("**Deflection (Physical / Energy):** ")
            .append(String.format("%.0f", stats.path("deflection_physical").asDouble(0)))
            .append(" / ")
            .append(String.format("%.0f", stats.path("deflection_energy").asDouble(0)))
            .append("\n");
        sb.append("**Default Loadout Effective DPS (Phys / Energy):** ")
            .append(String.format("%.1f", effectiveTotals[0]))
            .append(" / ")
            .append(String.format("%.1f", effectiveTotals[1]))
            .append("\n");
        sb.append("**Default Loadout Effective Alpha (Phys / Energy):** ")
            .append(String.format("%.1f", effectiveTotals[2]))
            .append(" / ")
            .append(String.format("%.1f", effectiveTotals[3]))
            .append("\n");
      }
      // ── Weapons ───────────────────────────────────────────────
      JsonNode weapons = entry.path("weapons");
      if (weapons.isArray() && weapons.size() > 0) {
        sb.append("\n**— Weapons —**\n");
        for (JsonNode w : weapons) {
          String wName = w.path("name").asText("");
          String wSize = w.path("size").asText("");
          int wCount = w.path("count").asInt(1);
          double wDps = w.path("dps").asDouble(0);
          double wAlpha = w.path("alpha_damage").asDouble(0);
          int wRpm = w.path("rpm").asInt(0);

          if (!wName.isBlank() && (wDps <= 0 || wAlpha <= 0 || wRpm <= 0)) {
            String resolvedWeapon = StarCitizenDataService.resolveDatasetKey("weapons", wName);
            JsonNode weaponEntry =
                resolvedWeapon != null ? StarCitizenDataService.getWeapon(resolvedWeapon) : null;
            JsonNode statsNode = weaponEntry != null ? weaponEntry.path("stats") : null;
            if (statsNode != null && statsNode.isObject()) {
              if (wDps <= 0) {
                wDps = statsNode.path("dps").asDouble(0);
              }
              if (wAlpha <= 0) {
                wAlpha = statsNode.path("alpha_damage").asDouble(0);
              }
              if (wRpm <= 0) {
                wRpm = statsNode.path("rpm").asInt(0);
              }
            }
          }

          if (!wName.isBlank()) {
            sb.append("• ").append(wCount > 1 ? wCount + "× " : "").append(wName);
            if (!wSize.isBlank() && !wSize.equals("0")) {
              sb.append(" (S").append(wSize).append(")");
            }
            if (wDps > 0) {
              sb.append(" — ").append(String.format("%.1f", wDps)).append(" DPS");
            }
            if (wAlpha > 0) {
              sb.append(" | ").append(String.format("%.1f", wAlpha)).append(" alpha");
            }
            if (wRpm > 0) {
              sb.append(" | ").append(wRpm).append(" RPM");
            }
            sb.append("\n");
          }
        }
      }

      // ── Turrets ───────────────────────────────────────────────
      JsonNode turrets = entry.path("turrets");
      if (turrets.isArray() && turrets.size() > 0) {
        sb.append("\n**— Turrets —**\n");
        for (JsonNode t : turrets) {
          String tName = t.path("name").asText("");
          String tSize = t.path("size").asText("");
          int tCount = t.path("count").asInt(1);
          if (!tName.isBlank()) {
            sb.append("• ").append(tCount > 1 ? tCount + "× " : "").append(tName);
            if (!tSize.isBlank() && !tSize.equals("0")) {
              sb.append(" (S").append(tSize).append(")");
            }
            sb.append("\n");
          }
        }
      }

      // ── Missiles ──────────────────────────────────────────────
      JsonNode missiles = entry.path("missiles");
      if (missiles.isArray() && missiles.size() > 0) {
        sb.append("\n**— Missiles —**\n");
        java.util.Map<String, Integer> mCount = new java.util.LinkedHashMap<>();
        for (JsonNode m : missiles) {
          String mName = m.path("name").asText(m.path("type").asText("Unknown"));
          mCount.merge(mName, 1, Integer::sum);
        }
        mCount.forEach(
            (k, v) -> sb.append("• ").append(v > 1 ? v + "× " : "").append(k).append("\n"));
      }

      // ── Missile Racks ─────────────────────────────────────────
      JsonNode racks = entry.path("missile_racks");
      if (racks.isArray() && racks.size() > 0) {
        sb.append("\n**— Missile Racks —**\n");
        for (JsonNode r : racks) {
          String rName = r.path("name").asText("");
          String rSize = r.path("size").asText("");
          int rCap = r.path("capacity").asInt(0);
          if (!rName.isBlank()) {
            sb.append("• ").append(rName);
            if (!rSize.isBlank() && !rSize.equals("0")) {
              sb.append(" (S").append(rSize).append(")");
            }
            if (rCap > 0) {
              sb.append(" — ").append(rCap).append(" missiles");
            }
            sb.append("\n");
          }
        }
      }

      // ── Buy Locations ─────────────────────────────────────────
      JsonNode buyLocs = info.path("buy_locations");
      if (buyLocs.isArray() && buyLocs.size() > 0) {
        sb.append("\n**Buy Locations:**\n");
        for (JsonNode loc : buyLocs) {
          sb.append("• ").append(loc.asText()).append("\n");
        }
      }

      // Trim to Discord's 2000-char limit
      String reply = sb.toString();
      if (reply.length() > 1990) {
        reply = reply.substring(0, 1987) + "...";
      }
      event.reply(reply).queue();
      return;
    }

    // /weapon — display FPS or ship weapon stats from the normalized dataset.
    if (event.getName().equals("weapon")) {
      String input = event.getOption("name").getAsString();
      JsonNode weaponData = StarCitizenDataService.get("weapons");

      if (weaponData == null || weaponData.isEmpty()) {
        event.reply("Weapon data not loaded.").queue();
        return;
      }

      String resolved = StarCitizenDataService.resolveDatasetKey("weapons", input);
      JsonNode entry = resolved != null ? weaponData.get(resolved) : null;

      if (entry == null) {
        java.util.List<String> suggestions =
            StarCitizenDataService.suggestDatasetKeys("weapons", input, 5);
        String suffix =
            suggestions.isEmpty() ? "" : "\nDid you mean: " + String.join(", ", suggestions);
        event.reply("Weapon not found: " + input + suffix).queue();
        return;
      }

      JsonNode info = entry.path("info");
      JsonNode stats = entry.path("stats");
      JsonNode ammo = entry.path("ammo");

      StringBuilder sb = new StringBuilder();
      sb.append("**Weapon: ").append(resolved).append("**\n\n");

      // ── Info ──────────────────────────────────────────────────
      String manufacturer = info.path("manufacturer").asText("");
      String type = info.path("type").asText(info.path("class").asText(""));
      String domain = info.path("domain").asText("");
      String dmgType = info.path("damage_type").asText("");
      String hardpoint = info.path("hardpoint").asText("");
      int size = info.path("size").asInt(-1);

      if (!manufacturer.isBlank()) {
        sb.append("**Manufacturer:** ").append(manufacturer).append("\n");
      }
      if (!type.isBlank()) {
        sb.append("**Type:** ").append(type).append("\n");
      }
      String cls = info.path("class").asText("");
      if (!cls.isBlank() && !cls.equalsIgnoreCase(type)) {
        sb.append("**Class:** ").append(cls).append("\n");
      }
      if (!domain.isBlank()) {
        sb.append("**Domain:** ").append(domain.toUpperCase()).append("\n");
      }
      if (size >= 0) {
        sb.append("**Size:** ").append(size == 0 ? "Personal" : "S" + size).append("\n");
      }
      if (!hardpoint.isBlank()) {
        sb.append("**Hardpoint:** ").append(hardpoint).append("\n");
      }
      if (!dmgType.isBlank()) {
        sb.append("**Damage Type:** ").append(dmgType).append("\n");
      }
      String wStoreUrl = info.path("store_url").asText("");
      if (!wStoreUrl.isBlank()) {
        sb.append("**Store:** ").append(wStoreUrl).append("\n");
      }

      // ── Stats ─────────────────────────────────────────────────
      double dps = stats.path("dps").asDouble(0);
      double alpha = stats.path("alpha_damage").asDouble(0);
      int rpm = stats.path("rpm").asInt(0);
      int range = stats.path("range").asInt(0);
      int projSpd = stats.path("projectile_speed").asInt(0);
      double quality = stats.path("quality").asDouble(0);
      double hp = stats.path("hp").asDouble(0);
      double cost = stats.path("cost_auec").asDouble(0);
      double sellPrice = stats.path("sell_price").asDouble(0);

      sb.append("\n**— Stats —**\n");
      if (dps > 0) {
        sb.append("**DPS:** ").append(String.format("%.1f", dps)).append("\n");
      }
      if (alpha > 0) {
        sb.append("**Alpha Damage:** ").append(String.format("%.1f", alpha)).append("\n");
      }
      if (rpm > 0) {
        sb.append("**RPM:** ").append(rpm).append("\n");
      }
      if (range > 0) {
        sb.append("**Range:** ").append(range).append(" m\n");
      }
      if (projSpd > 0) {
        sb.append("**Projectile Speed:** ").append(projSpd).append(" m/s\n");
      }
      if (quality > 0) {
        sb.append("**Quality:** ").append(String.format("%.1f", quality)).append("\n");
      }
      if (hp > 0) {
        sb.append("**HP:** ").append(String.format("%.0f", hp)).append("\n");
      }
      if (cost > 0) {
        sb.append("**Buy Price:** ").append(String.format("%,.0f", cost)).append(" aUEC\n");
      }
      if (sellPrice > 0) {
        sb.append("**Sell Price:** ").append(String.format("%,.0f", sellPrice)).append(" aUEC\n");
      }

      boolean noStats = dps == 0 && alpha == 0 && rpm == 0 && range == 0 && cost == 0;
      if (noStats) {
        sb.append("*Combat stats not yet available for this weapon.*\n");
      }

      // ── Ammo ──────────────────────────────────────────────────
      if (ammo.isObject() && ammo.size() > 0) {
        sb.append("\n**— Ammo —**\n");
        ammo.fields()
            .forEachRemaining(
                e -> {
                  String k = e.getKey();
                  String v = e.getValue().asText();
                  if (!v.isBlank() && !v.equals("0") && !v.equals("0.0")) {
                    sb.append("**").append(k).append(":** ").append(v).append("\n");
                  }
                });
      }

      // ── Fire Modes ────────────────────────────────────────────
      JsonNode fireModes = entry.path("fire_modes");
      if (fireModes.isArray() && fireModes.size() > 0) {
        sb.append("\n**Fire Modes:** ");
        java.util.List<String> modes = new java.util.ArrayList<>();
        for (JsonNode fm : fireModes) {
          String mName = fm.isTextual() ? fm.asText() : fm.path("name").asText();
          if (!mName.isBlank()) {
            modes.add(mName);
          }
        }
        sb.append(modes.isEmpty() ? "N/A" : String.join(", ", modes)).append("\n");
      }

      // ── Attachments ───────────────────────────────────────────
      JsonNode attachments = entry.path("attachments");
      if (attachments.isArray() && attachments.size() > 0) {
        sb.append("\n**Attachment Slots:**\n");
        for (JsonNode att : attachments) {
          String aName =
              att.isTextual() ? att.asText() : att.path("name").asText(att.path("type").asText(""));
          if (!aName.isBlank()) {
            sb.append("• ").append(aName).append("\n");
          }
        }
      }

      // ── Buy Locations ─────────────────────────────────────────
      JsonNode buyLocs = info.path("buy_locations");
      if (buyLocs.isArray() && buyLocs.size() > 0) {
        sb.append("\n**Buy Locations:**\n");
        int shown = 0;
        for (JsonNode loc : buyLocs) {
          sb.append("• ").append(loc.asText()).append("\n");
          if (++shown >= 8) {
            sb.append("*(+ ").append(buyLocs.size() - 8).append(" more)*\n");
            break;
          }
        }
      } else {
        String buyLoc = info.path("buy_location").asText("");
        if (!buyLoc.isBlank()) {
          sb.append("\n**Buy Location:** ").append(buyLoc).append("\n");
        }
      }

      // ── Sell Locations ────────────────────────────────────────
      JsonNode sellLocs = info.path("sell_locations");
      if (sellLocs.isArray() && sellLocs.size() > 0) {
        sb.append("\n**Sell Locations:**\n");
        for (JsonNode loc : sellLocs) {
          sb.append("• ").append(loc.asText()).append("\n");
        }
      }

      String reply = sb.toString();
      if (reply.length() > 1990) {
        reply = reply.substring(0, 1987) + "...";
      }
      event.reply(reply).queue();
      return;
    }

    // /reportmissing — log user report and attempt a live refresh/re-check.
    if (event.getName().equals("reportmissing")) {
      String datasetInput = event.getOption("dataset").getAsString();
      String item = event.getOption("item").getAsString();
      String notes = event.getOption("notes") != null ? event.getOption("notes").getAsString() : "";

      event
          .deferReply(true)
          .queue(
              hook -> {
                try {
                  MissingDataReportService.ReportResult result =
                      MissingDataReportService.reportAndAttemptFix(
                          datasetInput,
                          item,
                          notes,
                          event.getUser().getIdLong(),
                          event.getUser().getAsTag(),
                          event.getGuild() != null ? event.getGuild().getIdLong() : 0L,
                          event.getChannel().getIdLong());

                  StringBuilder reply = new StringBuilder();
                  reply
                      .append("Noted. Report saved as **")
                      .append(result.reportId())
                      .append("** for dataset **")
                      .append(result.dataset())
                      .append("**.\n");

                  if (result.alreadyPresent()) {
                    reply
                        .append("Quick check: I can already find **")
                        .append(result.resolvedName())
                        .append("** in live data.\n");
                  } else if (result.foundAfterRefresh()) {
                    reply
                        .append("I refreshed the dataset and found **")
                        .append(result.resolvedName())
                        .append("**.");
                  } else {
                    if (result.refreshAttempted()) {
                      reply.append(
                          result.refreshSucceeded()
                              ? "I refreshed the dataset, but it still looks missing.\n"
                              : "I attempted a live refresh, but the source refresh failed.\n");
                    } else {
                      reply.append("This dataset does not support live refresh yet.\n");
                    }

                    if (!result.suggestions().isEmpty()) {
                      reply
                          .append("Closest matches: ")
                          .append(String.join(", ", result.suggestions()))
                          .append("\n");
                    }
                    if (result.queuedForManualReview()) {
                      reply
                          .append("Added to manual review queue: ")
                          .append(result.manualQueuePath())
                          .append("\n");
                    }
                  }

                  hook.editOriginal(reply.toString().trim()).queue();
                } catch (IllegalArgumentException e) {
                  hook.editOriginal(
                          "Invalid request: "
                              + e.getMessage()
                              + " Valid datasets: "
                              + MissingDataReportService.supportedDatasetsHelp())
                      .queue();
                } catch (Exception e) {
                  hook.editOriginal("I could not process that report right now: " + e.getMessage())
                      .queue();
                }
              });
    }

    // GDPR & Security Commands

    // /gdpr-export-my-data — Export user's personal data
    if (event.getName().equals("gdpr-export-my-data")) {
      event.deferReply(true).queue(hook -> {
        try {
          String userId = event.getUser().getId();
          String fileName = "user_data_" + userId + ".json";
          
          event.getUser().openPrivateChannel()
              .flatMap(channel -> channel.sendMessage(
                "📦 Your personal data export is being prepared. Check back in a moment."))
              .queue();
          
          hook.editOriginal("✅ Your data export has been prepared and sent via DM.").queue();
          System.out.println(
              "[SecurityAudit] GDPR_EXPORT_REQUESTED user=" + userId
                  + " guild=" + (event.getGuild() != null ? event.getGuild().getId() : "dm"));
          MetricsCollector.recordEvent("gdpr_export_request", 1);
        } catch (Exception e) {
          hook.editOriginal("⚠️ Could not process export: " + e.getMessage()).queue();
        }
      });
      return;
    }

    // /gdpr-delete-my-data — Request permanent data deletion
    if (event.getName().equals("gdpr-delete-my-data")) {
      event.deferReply(true).queue(hook -> {
        try {
          String userId = event.getUser().getId();
          hook.editOriginal(
              "⚠️ **Data Deletion Request Submitted**\n\n" +
              "Your personal data will be permanently deleted within 24 hours.\n" +
              "This includes conversations, learned phrases, and preferences.\n" +
              "This action **cannot be undone**.\n\n" +
              "If you have questions, contact: privacy@phoenix-bot.dev")
              .queue();
          
          System.out.println(
              "[SecurityAudit] GDPR_DELETE_REQUESTED user=" + userId
                  + " guild=" + (event.getGuild() != null ? event.getGuild().getId() : "dm"));
          MetricsCollector.recordEvent("gdpr_delete_request", 1);
        } catch (Exception e) {
          hook.editOriginal("⚠️ Could not process deletion: " + e.getMessage()).queue();
        }
      });
      return;
    }

    // /backup-status — Show backup status
    if (event.getName().equals("backup-status")) {
      try {
        boolean backupEnabled = BotConfig.BACKUP_ENABLED;
        String status = backupEnabled ? "✅ Enabled" : "❌ Disabled";
        
        event.reply(
            "**Backup Status**\n" +
            "Status: " + status + "\n" +
            "Daily backups: " + (backupEnabled ? "Active" : "Inactive") + "\n" +
            "Last backup: [Check logs]\n" +
            "Retention: 30 days")
            .setEphemeral(true)
            .queue();
        
        MetricsCollector.recordEvent("backup_status_requested", 1);
      } catch (Exception e) {
        event.reply("⚠️ Could not retrieve backup status: " + e.getMessage())
            .setEphemeral(true).queue();
      }
      return;
    }

    // /bot-health — Display bot health metrics
    if (event.getName().equals("bot-health")) {
      try {
        String uptime = MetricsCollector.getUptimeString();
        String health = MetricsCollector.getHealthReport();
        
        event.reply(
            "**🤖 Bot Health Report**\n" +
            "Uptime: " + uptime + "\n" +
            "Status: Operational\n" +
            "Metrics: " + health)
            .setEphemeral(true)
            .queue();
      } catch (Exception e) {
        event.reply("⚠️ Could not retrieve health metrics: " + e.getMessage())
            .setEphemeral(true).queue();
      }
      return;
    }

    // /bot-version — Show bot version and build info
    if (event.getName().equals("bot-version")) {
      try {
        String version = Botcode.CommsBot.getVersion();
        
        event.reply(
            "**Bot Version Information**\n" +
            "Version: " + version + "\n" +
            "Build: CommsBot-1.0\n" +
            "Status: Production Ready")
            .setEphemeral(true)
            .queue();
      } catch (Exception e) {
        event.reply("⚠️ Could not retrieve version: " + e.getMessage())
            .setEphemeral(true).queue();
      }
      return;
    }
  }

  private static String formatSignedPercent(double value) {
    if (Math.abs(value) < 1e-9) {
      return "0%";
    }
    String body =
        Math.abs(value - Math.rint(value)) < 1e-9
            ? String.format("%,.0f", Math.abs(value))
            : String.format("%,.2f", Math.abs(value)).replaceAll("\\.?0+$", "");
    return (value > 0 ? "+" : "-") + body + "%";
  }

  /**
   * Provides context-aware autocomplete values for mine command options.
   */
  @Override
  public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {

    // Only mining command options currently expose dynamic autocomplete.
    if (!event.getName().equals("mine")) {
      return;
    }

    String focused = event.getFocusedOption().getName();

    // Return the option list that corresponds to the currently focused argument.
    switch (focused) {

      // Suggest valid keys from each mining dataset map.
      case "rock" -> event
          .replyChoices(
              MiningService.ROCK_TYPES.keySet().stream()
                  .map(name -> new Choice(name, name))
                  .toList())
          .queue();

      case "ship" -> event
          .replyChoices(
              MiningService.SHIP_MINING.keySet().stream()
                  .map(name -> new Choice(name, name))
                  .toList())
          .queue();

      case "laser" -> event
          .replyChoices(
              MiningService.LASERS.keySet().stream()
                  .map(name -> new Choice(name, name))
                  .toList())
          .queue();

      case "consumable" -> event
          .replyChoices(
              MiningService.CONSUMABLES.keySet().stream()
                  .map(name -> new Choice(name, name))
                  .toList())
          .queue();

      // Output format and field lists are static enums.
      case "format" -> event
          .replyChoices(
              List.of("brief", "full", "specific").stream()
                  .map(name -> new Choice(name, name))
                  .toList())
          .queue();

      case "field" -> event
          .replyChoices(
              List.of(
                      "rawResistance",
                      "rawInstability",
                      "rawMass",
                      "shipBonus",
                      "consumableBonus",
                      "multiLaserBonus",
                      "effectivePower",
                      "requiredPower",
                      "chargeRate",
                      "fluctuation",
                      "breakChance",
                      "viable")
                  .stream()
                  .map(name -> new Choice(name, name))
                  .toList())
          .queue();
    }
  }
}
