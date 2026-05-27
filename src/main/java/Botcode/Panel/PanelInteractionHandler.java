package Botcode.Panel;

import com.fasterxml.jackson.databind.JsonNode;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import Botcode.StarCitizen.MiningService;
import Botcode.StarCitizen.RefineryService;
import Botcode.StarCitizen.SalvageService;
import Botcode.StarCitizen.StarCitizenUpdateManager;
import Botcode.StarCitizen.TradeService;
import Botcode.StarCitizen.StarCitizenDataService;
import Botcode.Utils.HelpBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all button clicks and modal submissions that belong to the panel module.
 *
 * <p>Only processes events whose IDs start with {@code panel:} or {@code panel_modal:} — all other
 * interactions pass through untouched.
 *
 * <p>All responses are <b>ephemeral</b> (visible only to the user who clicked).
 */
public class PanelInteractionHandler extends ListenerAdapter {

  /**
   * Per-user temporary mining configuration state for step-wise select flow.
   */
  private static final Map<Long, MiningDraft> miningDrafts = new ConcurrentHashMap<>();

  private static class MiningDraft {

    String rock;
    String ship;
    String laser;
    String consumable;
    int operators;
    int maxHeads;
    int modulesPerHead;
    int activeHeads;
    int headConfigCursor;
    String[] headLasers = new String[3];
    String[] headConsumables = new String[3];
    int[] headModuleSlots = new int[]{1, 1, 1};
  }

  private static final Map<Long, TradeDraft> tradeDrafts = new ConcurrentHashMap<>();

  private static class TradeDraft {

    String mode;
    int cargo;
    String commodity;
    int count = 8;
  }

  private static final Map<Long, RefineryDraft> refineryDrafts = new ConcurrentHashMap<>();

  private static class RefineryDraft {

    String ore;
    int rawScu;
  }

  private static final Map<Long, FpsWeaponDraft> fpsWeaponDrafts = new ConcurrentHashMap<>();

  private static class FpsWeaponDraft {

    String system = "all";
    String weaponClass = "all";
  }

  private static final int DATASET_PAGE_SIZE = 25;
  private static final String DATASET_PAGE_PREFIX = "panel:dataset_page:";
  private static final Map<String, DatasetSelectSession> datasetSelectSessions =
      new ConcurrentHashMap<>();

  private static class DatasetSelectSession {

    String dataset;
    String label;
    String selectId;
    String placeholder;
    java.util.List<String> names; // current (possibly filtered) list
    java.util.List<String> allNames; // full unfiltered list
    int page;
  }

  // ------------------------------------------------------------------
  // Button interactions
  // ------------------------------------------------------------------

  @Override
  public void onButtonInteraction(ButtonInteractionEvent event) {
    String id = event.getComponentId();
    if (!id.startsWith("panel:")) {
      return;
    }

    if (id.startsWith(DATASET_PAGE_PREFIX)) {
      handleDatasetPageButton(event, id);
      return;
    }

    if (id.startsWith(PanelButtons.BTN_DATASET_FILTER_PREFIX)) {
      handleDatasetFilterButton(event, id);
      return;
    }

    if (id.startsWith(PanelButtons.BTN_DATASET_CLEAR_FILTER_PREFIX)) {
      handleDatasetClearFilterButton(event, id);
      return;
    }

    switch (id) {
      case PanelButtons.BTN_MINING -> replyWithListSelect(
          event,
          "mining rocks",
          buildMiningRockNames(),
          PanelButtons.SELECT_MINING_ROCK,
          "Mining setup: step 1/6 — choose rock type");

      case PanelButtons.BTN_COMMODITY -> replyWithDatasetSelect(
          event, "commodities", PanelButtons.SELECT_COMMODITY_PICK, "Choose a commodity");

      case PanelButtons.BTN_SHIP ->
          replyWithDatasetSelect(event, "ships", PanelButtons.SELECT_SHIP_PICK, "Choose a ship");

      case PanelButtons.BTN_WEAPON -> event
          .reply("Ship Weapons: step 1/2 — choose class")
          .addComponents(PanelButtons.shipWeaponClassMenu())
          .setEphemeral(true)
          .queue();

      case PanelButtons.BTN_COMPONENTS -> event
          .reply("Components: step 1/2 — choose category")
          .addComponents(PanelButtons.componentCategoryMenu())
          .setEphemeral(true)
          .queue();

      case PanelButtons.BTN_ARMOR ->
          replyWithDatasetSelect(event, "armor", PanelButtons.SELECT_ARMOR_PICK, "Choose armor");

      case PanelButtons.BTN_LOCATIONS -> replyWithDatasetSelect(
          event, "locations", PanelButtons.SELECT_LOCATION_PICK, "Choose a location");

      case PanelButtons.BTN_MISSIONS -> replyWithDatasetSelect(
          event, "missions", PanelButtons.SELECT_MISSION_PICK, "Choose a mission");

      case PanelButtons.BTN_FPS_WEAPONS -> event
          .reply("FPS weapons: step 1/3 — choose system")
          .addComponents(PanelButtons.fpsSystemMenu())
          .setEphemeral(true)
          .queue();

      case PanelButtons.BTN_TRADE -> event
          .reply("Trade setup: step 1/3 — choose mode")
          .addComponents(PanelButtons.tradeModeMenu())
          .setEphemeral(true)
          .queue();

      case PanelButtons.BTN_REFINERY -> event
          .replyEmbeds(
              new net.dv8tion.jda.api.EmbedBuilder()
                  .setTitle("🏭  Refinery")
                  .setColor(new java.awt.Color(0xF39C12))
                  .setDescription("What would you like to do?")
                  .build())
          .addComponents(PanelButtons.refinerySubMenu())
          .setEphemeral(true)
          .queue();

      case PanelButtons.BTN_REFINERY_ANALYZE -> replyWithRefineryOreSelect(event);

      case PanelButtons.BTN_REFINERY_STATIONS -> event
          .replyEmbeds(PanelEmbeds.refineryStations(RefineryService.listStations()))
          .setEphemeral(true)
          .queue();

      case PanelButtons.BTN_SALVAGE -> event
          .replyEmbeds(
              new net.dv8tion.jda.api.EmbedBuilder()
                  .setTitle("🔧  Salvage")
                  .setColor(new java.awt.Color(0x8E44AD))
                  .setDescription("What would you like to know?")
                  .build())
          .addComponents(PanelButtons.salvageSubMenu())
          .setEphemeral(true)
          .queue();

      case PanelButtons.BTN_SALVAGE_HOTSPOTS -> event
          .replyEmbeds(PanelEmbeds.salvageHotspots(SalvageService.listHotspots()))
          .setEphemeral(true)
          .queue();

      case PanelButtons.BTN_SALVAGE_SHIPS -> event
          .replyEmbeds(PanelEmbeds.salvageShips(SalvageService.compareShips()))
          .setEphemeral(true)
          .queue();

      case PanelButtons.BTN_SALVAGE_MATERIALS -> event
          .replyEmbeds(PanelEmbeds.salvageMaterials(SalvageService.listMaterials()))
          .setEphemeral(true)
          .queue();

      case PanelButtons.BTN_SALVAGE_TIPS -> event
          .replyEmbeds(PanelEmbeds.salvageTips(SalvageService.getTips()))
          .setEphemeral(true)
          .queue();

      case PanelButtons.BTN_REFRESH_DATA -> handleDataRefresh(event);

      case PanelButtons.BTN_DATA_STATUS -> event
          .replyEmbeds(PanelEmbeds.dataStatus(buildDatasetStatusLines()))
          .setEphemeral(true)
          .queue();

      case PanelButtons.BTN_HELP -> {
        try {
          boolean isAdmin =
              event.getMember() != null
                  && (event.getMember().isOwner()
                  || event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)
                  || event.getMember()
                      .hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR));
          net.dv8tion.jda.api.entities.MessageEmbed helpEmbed = HelpBuilder.build(isAdmin);
          if (helpEmbed != null) {
            event.replyEmbeds(helpEmbed).setEphemeral(true).queue();
          } else {
            event.reply("⚠️ Could not load help information.").setEphemeral(true).queue();
          }
        } catch (Exception e) {
          event.reply("⚠️ Error loading help: " + e.getMessage()).setEphemeral(true).queue();
        }
      }
    }
  }

  // ------------------------------------------------------------------
  // Modal submissions
  // ------------------------------------------------------------------

  @Override
  public void onModalInteraction(ModalInteractionEvent event) {
    String id = event.getModalId();
    if (!id.startsWith("panel_modal:")) {
      return;
    }

    if (id.startsWith(PanelButtons.MODAL_DATASET_FILTER_PREFIX)) {
      handleDatasetFilterModal(event, id);
      return;
    }

    switch (id) {
      case PanelButtons.MODAL_MINING -> handleMining(event);
      case PanelButtons.MODAL_COMMODITY -> handleCommodity(event);
      case PanelButtons.MODAL_SHIP -> handleShip(event);
      case PanelButtons.MODAL_WEAPON -> handleWeapon(event);
      case PanelButtons.MODAL_TRADE -> handleTrade(event);
      case PanelButtons.MODAL_REFINERY -> handleRefinery(event);
    }
  }

  // ------------------------------------------------------------------
  // Select menu interactions
  // ------------------------------------------------------------------

  @Override
  public void onStringSelectInteraction(StringSelectInteractionEvent event) {
    String id = event.getComponentId();
    if (!id.startsWith("panel_select:")) {
      return;
    }

    String value = event.getValues().isEmpty() ? "" : event.getValues().get(0);
    if (value.isBlank()) {
      event.reply("No option selected.").setEphemeral(true).queue();
      return;
    }

    clearDatasetSelectSession(event.getUser().getIdLong(), id);

    switch (id) {
      case PanelButtons.SELECT_MINING_PROFILE -> handleMiningProfileSelect(event, value);
      case PanelButtons.SELECT_MINING_ROCK -> handleMiningRockSelect(event, value);
      case PanelButtons.SELECT_MINING_SHIP -> handleMiningShipSelect(event, value);
      case PanelButtons.SELECT_MINING_HEADS -> handleMiningHeadsSelect(event, value);
      case PanelButtons.SELECT_MINING_LASER -> handleMiningLaserSelect(event, value);
      case PanelButtons.SELECT_MINING_CONSUMABLE -> handleMiningConsumableSelect(event, value);
      case PanelButtons.SELECT_MINING_MODULE_SLOTS -> handleMiningModuleSlotsSelect(event, value);
      case PanelButtons.SELECT_MINING_OPERATORS -> handleMiningOperatorsSelect(event, value);
      case PanelButtons.SELECT_MINING_FORMAT -> handleMiningFormatSelect(event, value);
      case PanelButtons.SELECT_COMMODITY_PICK -> handleCommoditySelect(event, value);
      case PanelButtons.SELECT_SHIP_PICK -> handleShipSelect(event, value);
      case PanelButtons.SELECT_WEAPON_PICK -> handleWeaponSelect(event, value);
      case PanelButtons.SELECT_COMPONENT_CATEGORY -> handleComponentCategorySelect(event, value);
      case PanelButtons.SELECT_COMPONENT_PICK -> handleComponentSelect(event, value);
      case PanelButtons.SELECT_ARMOR_PICK -> handleArmorSelect(event, value);
      case PanelButtons.SELECT_LOCATION_PICK -> handleLocationSelect(event, value);
      case PanelButtons.SELECT_MISSION_PICK -> handleMissionSelect(event, value);
      case PanelButtons.SELECT_FPS_SYSTEM -> handleFpsSystemSelect(event, value);
      case PanelButtons.SELECT_FPS_CLASS -> handleFpsClassSelect(event, value);
      case PanelButtons.SELECT_FPS_WEAPON_PICK -> handleFpsWeaponSelect(event, value);
      case PanelButtons.SELECT_WEAPON_DOMAIN -> handleWeaponDomainSelect(event, value);
      case PanelButtons.SELECT_SHIP_WEAPON_CLASS -> handleShipWeaponClassSelect(event, value);
      case PanelButtons.SELECT_TRADE_MODE -> handleTradeModeSelect(event, value);
      case PanelButtons.SELECT_TRADE_CARGO -> handleTradeCargoSelect(event, value);
      case PanelButtons.SELECT_TRADE_COMMODITY -> handleTradeCommoditySelect(event, value);
      case PanelButtons.SELECT_TRADE_COUNT -> handleTradeCountSelect(event, value);
      case PanelButtons.SELECT_REFINERY_PROFILE -> handleRefineryProfileSelect(event, value);
      case PanelButtons.SELECT_REFINERY_ORE -> handleRefineryOreSelect(event, value);
      case PanelButtons.SELECT_REFINERY_SCU -> handleRefineryScuSelect(event, value);
      case PanelButtons.SELECT_REFINERY_VIEW -> handleRefineryViewSelect(event, value);
    }
  }

  // ------------------------------------------------------------------
  // Modal result handlers
  // ------------------------------------------------------------------

  private void handleMining(ModalInteractionEvent event) {
    String rock = value(event, "rock");
    String ship = value(event, "ship");
    String laser = value(event, "laser");
    String consumable = value(event, "consumable");
    String operatorsRaw = value(event, "operators");

    int operators;
    try {
      operators = Integer.parseInt(operatorsRaw.trim());
    } catch (NumberFormatException e) {
      event
          .reply("⚠️ Number of lasers must be a whole number (e.g. `1`).")
          .setEphemeral(true)
          .queue();
      return;
    }

    // Resolve display names after fuzzy matching so the embed shows the corrected names.
    String resolvedRock = MiningService.resolveRockName(rock);
    String resolvedShip = MiningService.resolveShipName(ship);
    String resolvedLaser = MiningService.resolveLaserName(laser);
    String resolvedCons = MiningService.resolveConsumableName(consumable);

    int moduleSlots = Math.max(1, operators);

    MiningService.MiningResult result =
        MiningService.analyzeRock(
            resolvedRock, resolvedShip, resolvedLaser, resolvedCons, operators, moduleSlots);

    // Override local variables with resolved names so embed shows corrected spelling.
    rock = resolvedRock;
    ship = resolvedShip;
    laser = resolvedLaser;
    consumable = resolvedCons;

    String brief = MiningService.MiningResultFormatter.brief(result);
    String full = MiningService.MiningResultFormatter.full(result);

    event
        .replyEmbeds(
            PanelEmbeds.miningResult(
                rock, ship, laser, consumable, operators, 1, moduleSlots, brief, full))
        .setEphemeral(true)
        .queue();
  }

  private void handleCommodity(ModalInteractionEvent event) {
    String name = value(event, "commodity_name");

    JsonNode commodities = StarCitizenDataService.get("commodities");
    if (commodities == null || commodities.isEmpty()) {
      event.replyEmbeds(PanelEmbeds.dataError("commodities")).setEphemeral(true).queue();
      return;
    }

    String resolved = StarCitizenDataService.resolveDatasetKey("commodities", name);
    JsonNode entry = resolved != null ? commodities.get(resolved) : null;
    if (entry == null) {
      java.util.List<String> suggestions =
          StarCitizenDataService.suggestDatasetKeys("commodities", name, 5);
      String suffix = suggestions.isEmpty() ? "" : "\nTry: " + String.join(", ", suggestions);
      event.reply("No commodity found for **" + name + "**." + suffix).setEphemeral(true).queue();
      return;
    }

    name = resolved;

    String bestBuyDisplay = buildBestDisplay(entry, "buy", true);
    String bestSellDisplay = buildBestDisplay(entry, "sell", false);
    double profit = entry.path("profit_per_scu").asDouble(0);

    String buyList = buildLocationList(entry, "buy", true);
    String sellList = buildLocationList(entry, "sell", false);

    event
        .replyEmbeds(
            PanelEmbeds.commodityResult(
                name, bestBuyDisplay, bestSellDisplay, profit, buyList, sellList))
        .setEphemeral(true)
        .queue();
  }

  private void handleShip(ModalInteractionEvent event) {
    String name = value(event, "ship_name");
    String resolved = StarCitizenDataService.resolveDatasetKey("ships", name);
    if (resolved != null) {
      name = resolved;
    }

    JsonNode ship = StarCitizenDataService.getShip(name);
    if (ship == null || ship.isMissingNode()) {
      java.util.List<String> suggestions =
          StarCitizenDataService.suggestDatasetKeys("ships", value(event, "ship_name"), 5);
      String suffix = suggestions.isEmpty() ? "" : "\nTry: " + String.join(", ", suggestions);
      event
          .reply("No ship found for **" + value(event, "ship_name") + "**." + suffix)
          .setEphemeral(true)
          .queue();
      return;
    }

    String info = buildShipInfoDisplay(ship.path("info"));
    String stats = buildShipStatsDisplay(ship);
    String loadout = buildShipLoadout(ship);

    event
        .replyEmbeds(PanelEmbeds.shipResult(name, info, stats, loadout))
        .setEphemeral(true)
        .queue();
  }

  private void handleWeapon(ModalInteractionEvent event) {
    String name = value(event, "weapon_name");
    String resolved = StarCitizenDataService.resolveDatasetKey("weapons", name);
    if (resolved != null) {
      name = resolved;
    }

    JsonNode weapon = StarCitizenDataService.getWeapon(name);
    if (weapon == null || weapon.isMissingNode()) {
      java.util.List<String> suggestions =
          StarCitizenDataService.suggestDatasetKeys("weapons", value(event, "weapon_name"), 5);
      String suffix = suggestions.isEmpty() ? "" : "\nTry: " + String.join(", ", suggestions);
      event
          .reply("No weapon found for **" + value(event, "weapon_name") + "**." + suffix)
          .setEphemeral(true)
          .queue();
      return;
    }

    String info = buildWeaponInfoDisplay(weapon.path("info"));
    String stats = buildWeaponStatsDisplay(weapon.path("stats"));
    String fireModes = buildWeaponExtras(weapon);

    event
        .replyEmbeds(PanelEmbeds.weaponResult(name, info, stats, fireModes))
        .setEphemeral(true)
        .queue();
  }

  private void handleTrade(ModalInteractionEvent event) {
    String cargoRaw = value(event, "cargo_scu");
    String commodity = value(event, "commodity_filter");

    int cargo;
    try {
      cargo = Integer.parseInt(cargoRaw.trim());
    } catch (NumberFormatException e) {
      event
          .reply("⚠️ Cargo capacity must be a whole number (e.g. `96`).")
          .setEphemeral(true)
          .queue();
      return;
    }

    java.util.List<TradeService.TradeRoute> routes =
        TradeService.topRoutes(cargo, commodity.isBlank() ? null : commodity, 8);

    String formatted = TradeService.Formatter.format(routes, cargo);
    event.replyEmbeds(PanelEmbeds.tradeResult(formatted)).setEphemeral(true).queue();
  }

  private void handleRefinery(ModalInteractionEvent event) {
    String ore = value(event, "ore");
    String scuRaw = value(event, "raw_scu");

    int rawScu;
    try {
      rawScu = Integer.parseInt(scuRaw.trim());
    } catch (NumberFormatException e) {
      event.reply("⚠️ SCU must be a whole number (e.g. `32`).").setEphemeral(true).queue();
      return;
    }

    RefineryService.RefineJob job = RefineryService.analyze(ore, rawScu);
    String brief = RefineryService.Formatter.brief(job);
    String full = RefineryService.Formatter.full(job);

    event
        .replyEmbeds(PanelEmbeds.refineryAnalysis(ore, rawScu, brief, full))
        .setEphemeral(true)
        .queue();
  }

  // -------------------- select handlers --------------------

  private void handleMiningProfileSelect(StringSelectInteractionEvent event, String value) {
    // format: rock|ship|laser|consumable|operators
    String[] p = value.split("\\|");
    if (p.length != 5) {
      event
          .editMessage("Invalid mining profile.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    String rock = p[0];
    String ship = p[1];
    String laser = p[2];
    String consumable = p[3];
    int operators;
    try {
      operators = Integer.parseInt(p[4]);
    } catch (NumberFormatException e) {
      event
          .editMessage("Invalid mining operator count.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    int moduleSlots = Math.max(1, operators);

    MiningService.MiningResult result =
        MiningService.analyzeRock(rock, ship, laser, consumable, operators, moduleSlots);
    String brief = MiningService.MiningResultFormatter.brief(result);
    String full = MiningService.MiningResultFormatter.full(result);

    event
        .editMessageEmbeds(
            PanelEmbeds.miningResult(
                rock, ship, laser, consumable, operators, 1, moduleSlots, brief, full))
        .setContent("")
        .setComponents(java.util.Collections.emptyList())
        .queue();
  }

  private void handleMiningRockSelect(StringSelectInteractionEvent event, String value) {
    MiningDraft d =
        miningDrafts.computeIfAbsent(event.getUser().getIdLong(), k -> new MiningDraft());
    d.rock = value;
    event
        .editMessage("Mining setup: step 2/6 — choose ship")
        .setComponents(PanelButtons.miningShipMenu())
        .queue();
  }

  private void handleMiningShipSelect(StringSelectInteractionEvent event, String value) {
    MiningDraft d =
        miningDrafts.computeIfAbsent(event.getUser().getIdLong(), k -> new MiningDraft());
    d.ship = value;
    d.maxHeads = maxMiningHeadsForShip(value);
    d.operators = Math.max(1, d.maxHeads);
    d.activeHeads = Math.max(1, d.maxHeads);
    d.modulesPerHead = 1;
    d.headConfigCursor = 0;

    if (d.maxHeads > 1) {
      event
          .editMessage("Mining setup: step 3/7 — choose active heads")
          .setComponents(PanelButtons.miningHeadCountMenu(d.maxHeads))
          .queue();
      return;
    }

    promptHeadLaserSelect(event, d);
  }

  private void handleMiningHeadsSelect(StringSelectInteractionEvent event, String value) {
    MiningDraft d =
        miningDrafts.computeIfAbsent(event.getUser().getIdLong(), k -> new MiningDraft());
    try {
      int selected = Integer.parseInt(value);
      int max = Math.max(1, d.maxHeads);
      d.activeHeads = Math.max(1, Math.min(selected, max));
      d.operators = d.activeHeads;
    } catch (NumberFormatException e) {
      d.activeHeads = Math.max(1, d.maxHeads);
      d.operators = d.activeHeads;
    }
    d.headConfigCursor = 0;
    promptHeadLaserSelect(event, d);
  }

  private void handleMiningLaserSelect(StringSelectInteractionEvent event, String value) {
    MiningDraft d =
        miningDrafts.computeIfAbsent(event.getUser().getIdLong(), k -> new MiningDraft());
    int idx = Math.max(0, Math.min(2, d.headConfigCursor));
    d.headLasers[idx] = value;
    d.laser = value;
    d.headConfigCursor += 1;

    if (d.headConfigCursor < Math.max(1, d.activeHeads)) {
      promptHeadLaserSelect(event, d);
      return;
    }

    d.headConfigCursor = 0;
    promptHeadConsumableSelect(event, d);
  }

  private void handleMiningConsumableSelect(StringSelectInteractionEvent event, String value) {
    MiningDraft d =
        miningDrafts.computeIfAbsent(event.getUser().getIdLong(), k -> new MiningDraft());
    int idx = Math.max(0, Math.min(2, d.headConfigCursor));
    d.headConsumables[idx] = value;
    d.consumable = value;
    d.headConfigCursor += 1;

    if (d.headConfigCursor < Math.max(1, d.activeHeads)) {
      promptHeadConsumableSelect(event, d);
      return;
    }

    d.headConfigCursor = 0;
    event
        .editMessage("Mining setup: step 6/7 — choose modules per head")
        .setComponents(PanelButtons.miningModuleSlotsMenu())
        .queue();
  }

  private void handleMiningModuleSlotsSelect(StringSelectInteractionEvent event, String value) {
    MiningDraft d =
        miningDrafts.computeIfAbsent(event.getUser().getIdLong(), k -> new MiningDraft());
    int parsed;
    try {
      parsed = Math.max(0, Math.min(3, Integer.parseInt(value)));
    } catch (NumberFormatException e) {
      parsed = 1;
    }

    int idx = Math.max(0, Math.min(2, d.headConfigCursor));
    d.headModuleSlots[idx] = parsed;
    d.modulesPerHead = parsed;
    d.headConfigCursor += 1;

    if (d.headConfigCursor < Math.max(1, d.activeHeads)) {
      event
          .editMessage(
              "Mining setup: step 6/7 — choose modules for head "
                  + (d.headConfigCursor + 1)
                  + " of "
                  + d.activeHeads)
          .setComponents(PanelButtons.miningModuleSlotsMenu())
          .queue();
      return;
    }

    event
        .editMessage("Mining setup: step 7/7 — choose output format")
        .setComponents(PanelButtons.miningFormatMenu())
        .queue();
  }

  private void handleMiningOperatorsSelect(StringSelectInteractionEvent event, String value) {
    MiningDraft d =
        miningDrafts.computeIfAbsent(event.getUser().getIdLong(), k -> new MiningDraft());
    try {
      d.operators = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      event
          .editMessage("Invalid operator count.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }
    event
        .editMessage("Mining setup: step 6/6 — choose output format")
        .setComponents(PanelButtons.miningFormatMenu())
        .queue();
  }

  private void handleMiningFormatSelect(StringSelectInteractionEvent event, String format) {
    MiningDraft d = miningDrafts.get(event.getUser().getIdLong());
    if (d == null
        || d.rock == null
        || d.ship == null
        || d.laser == null
        || d.consumable == null
        || d.operators <= 0) {
      event
          .editMessage("Mining setup expired. Click ⛏️ Mining and run setup again.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    int activeHeads = Math.max(1, d.activeHeads > 0 ? d.activeHeads : d.operators);
    java.util.List<String> configuredLasers = new java.util.ArrayList<>();
    java.util.List<String> configuredConsumables = new java.util.ArrayList<>();
    int totalModules = 0;
    StringBuilder headLayout = new StringBuilder();
    StringBuilder consumableLayout = new StringBuilder();
    for (int i = 0; i < activeHeads; i++) {
      String headLaser =
          (d.headLasers[i] == null || d.headLasers[i].isBlank())
              ? (d.laser == null ? "Hofstede S2" : d.laser)
              : d.headLasers[i];
      configuredLasers.add(headLaser);

      String headConsumable =
          (d.headConsumables[i] == null || d.headConsumables[i].isBlank())
              ? (d.consumable == null || d.consumable.isBlank() ? "none" : d.consumable)
              : d.headConsumables[i];
      configuredConsumables.add(headConsumable);

      int headSlots = Math.max(0, Math.min(3, d.headModuleSlots[i]));
      totalModules += headSlots;
      if (headLayout.length() > 0) {
        headLayout.append("\n");
      }
      headLayout
          .append("Head ")
          .append(i + 1)
          .append(": ")
          .append(headLaser)
          .append(" | Modules: ")
          .append(headSlots);

      if (consumableLayout.length() > 0) {
        consumableLayout.append("\n");
      }
      consumableLayout.append("Head ").append(i + 1).append(": ").append(headConsumable);
    }
    totalModules = Math.max(0, Math.min(9, totalModules));

    MiningService.MiningResult result =
        MiningService.analyzeRock(
            d.rock, d.ship, configuredLasers, configuredConsumables, activeHeads, totalModules);

    String brief = MiningService.MiningResultFormatter.brief(result);
    String full = MiningService.MiningResultFormatter.full(result);
    String selectedFull = "full".equalsIgnoreCase(format) ? full : brief;

    event
        .editMessageEmbeds(
            PanelEmbeds.miningResult(
                d.rock,
                d.ship,
                headLayout.toString(),
                consumableLayout.toString(),
                activeHeads,
                (activeHeads <= 0
                    ? 0
                    : (int) Math.ceil((double) totalModules / (double) activeHeads)),
                totalModules,
                brief,
                selectedFull))
        .setContent("")
        .setComponents(java.util.Collections.emptyList())
        .queue();

    // Reset state after completing flow.
    miningDrafts.remove(event.getUser().getIdLong());
  }

  private void handleCommoditySelect(StringSelectInteractionEvent event, String name) {
    JsonNode commodities = StarCitizenDataService.get("commodities");
    if (commodities == null || commodities.isEmpty()) {
      event
          .editMessageEmbeds(PanelEmbeds.dataError("commodities"))
          .setContent("")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    JsonNode entry = commodities.get(name);
    if (entry == null) {
      event
          .editMessageEmbeds(PanelEmbeds.notFound("commodity", name))
          .setContent("")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    String bestBuyDisplay = buildBestDisplay(entry, "buy", true);
    String bestSellDisplay = buildBestDisplay(entry, "sell", false);
    double profit = entry.path("profit_per_scu").asDouble(0);

    String buyList = buildLocationList(entry, "buy", true);
    String sellList = buildLocationList(entry, "sell", false);

    event
        .editMessageEmbeds(
            PanelEmbeds.commodityResult(
                name, bestBuyDisplay, bestSellDisplay, profit, buyList, sellList))
        .setContent("")
        .setComponents(java.util.Collections.emptyList())
        .queue();
  }

  private void handleShipSelect(StringSelectInteractionEvent event, String name) {
    JsonNode ship = StarCitizenDataService.getShip(name);
    if (ship == null || ship.isMissingNode()) {
      event
          .editMessageEmbeds(PanelEmbeds.notFound("ship", name))
          .setContent("")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    String info = buildShipInfoDisplay(ship.path("info"));
    String stats = buildShipStatsDisplay(ship);
    String loadout = buildShipLoadout(ship);
    event
        .editMessageEmbeds(PanelEmbeds.shipResult(name, info, stats, loadout))
        .setContent("")
        .setComponents(java.util.Collections.emptyList())
        .queue();
  }

  private void handleWeaponSelect(StringSelectInteractionEvent event, String name) {
    JsonNode weapon = StarCitizenDataService.getWeapon(name);
    if (weapon == null || weapon.isMissingNode()) {
      event
          .editMessageEmbeds(PanelEmbeds.notFound("weapon", name))
          .setContent("")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    String info = buildWeaponInfoDisplay(weapon.path("info"));
    String stats = buildWeaponStatsDisplay(weapon.path("stats"));
    String fireModes = buildWeaponExtras(weapon);
    event
        .editMessageEmbeds(PanelEmbeds.weaponResult(name, info, stats, fireModes))
        .setContent("")
        .setComponents(java.util.Collections.emptyList())
        .queue();
  }

  private void handleComponentSelect(StringSelectInteractionEvent event, String name) {
    JsonNode components = StarCitizenDataService.get("components");
    JsonNode component = components != null ? components.path(name) : null;
    if (component == null || component.isMissingNode()) {
      event
          .editMessageEmbeds(PanelEmbeds.notFound("component", name))
          .setContent("")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    String info = buildComponentInfoDisplay(component.path("info"));
    String stats = buildComponentStatsDisplay(component.path("stats"));
    String attrs = formatNodeFiltered(component.path("attributes"));
    event
        .editMessageEmbeds(PanelEmbeds.componentResult(name, info, stats, attrs))
        .setContent("")
        .setComponents(java.util.Collections.emptyList())
        .queue();
  }

  private void handleComponentCategorySelect(StringSelectInteractionEvent event, String category) {
    if ("weapons".equalsIgnoreCase(category)) {
      replyWithDatasetSelect(
          event,
          "weapons",
          PanelButtons.SELECT_WEAPON_PICK,
          "Components: step 2/2 — choose weapon");
      return;
    }

    java.util.List<String> names = buildComponentNamesForCategory(category);
    if (names.isEmpty()) {
      event
          .editMessage("No entries found for that component category.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    String label = "all".equalsIgnoreCase(category) ? "components" : (category + " components");
    replyWithListSelect(
        event,
        label,
        names,
        PanelButtons.SELECT_COMPONENT_PICK,
        "Components: step 2/2 — choose item");
  }

  private void handleArmorSelect(StringSelectInteractionEvent event, String name) {
    JsonNode armorSet = StarCitizenDataService.get("armor");
    JsonNode armor = armorSet != null ? armorSet.path(name) : null;
    if (armor == null || armor.isMissingNode()) {
      event
          .editMessageEmbeds(PanelEmbeds.notFound("armor", name))
          .setContent("")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    event
        .editMessageEmbeds(PanelEmbeds.armorResult(name, armor))
        .setContent("")
        .setComponents(java.util.Collections.emptyList())
        .queue();
  }

  private void handleLocationSelect(StringSelectInteractionEvent event, String name) {
    JsonNode locations = StarCitizenDataService.get("locations");
    JsonNode location = locations != null ? locations.path(name) : null;
    if (location == null || location.isMissingNode()) {
      event
          .editMessageEmbeds(PanelEmbeds.notFound("location", name))
          .setContent("")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    String details = formatNodeFiltered(location);
    event
        .editMessageEmbeds(PanelEmbeds.locationResult(name, details))
        .setContent("")
        .setComponents(java.util.Collections.emptyList())
        .queue();
  }

  private void handleMissionSelect(StringSelectInteractionEvent event, String name) {
    JsonNode missions = StarCitizenDataService.get("missions");
    JsonNode mission = missions != null ? missions.path(name) : null;
    if (mission == null || mission.isMissingNode()) {
      event
          .editMessageEmbeds(PanelEmbeds.notFound("mission", name))
          .setContent("")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    String details = buildMissionDetails(name, mission);
    event
        .editMessageEmbeds(PanelEmbeds.missionResult(name, details))
        .setContent("")
        .setComponents(java.util.Collections.emptyList())
        .queue();
  }

  private void handleWeaponDomainSelect(StringSelectInteractionEvent event, String domain) {
    if ("fps".equalsIgnoreCase(domain)) {
      // Reuse the FPS flow: initialise draft and go to step 2
      FpsWeaponDraft draft =
          fpsWeaponDrafts.computeIfAbsent(event.getUser().getIdLong(), k -> new FpsWeaponDraft());
      draft.system = "all";
      draft.weaponClass = "all";
      event
          .editMessage("FPS weapons: step 2/3 — choose system")
          .setComponents(PanelButtons.fpsSystemMenu())
          .queue();
    } else {
      // Ship weapons: show class category
      event
          .editMessage("Ship weapons: step 2/3 — choose class")
          .setComponents(PanelButtons.shipWeaponClassMenu())
          .queue();
    }
  }

  private void handleShipWeaponClassSelect(StringSelectInteractionEvent event, String classValue) {
    java.util.List<String> names = buildShipWeaponNames(classValue);
    if (names.isEmpty()) {
      event
          .editMessage("No ship weapons found for that class right now.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }
    replyWithListSelect(
        event,
        "ship weapons",
        names,
        PanelButtons.SELECT_WEAPON_PICK,
        "Ship weapons: step 3/3 — choose weapon");
  }

  private java.util.List<String> buildShipWeaponNames(String classFilter) {
    JsonNode weapons = StarCitizenDataService.get("weapons");
    java.util.List<String> names = new java.util.ArrayList<>();
    if (weapons == null || !weapons.isObject()) {
      return names;
    }

    weapons
        .fields()
        .forEachRemaining(
            entry -> {
              JsonNode info = entry.getValue().path("info");
              String domain = info.path("domain").asText("").toLowerCase(java.util.Locale.ROOT);
              if (domain.contains("fps")) {
                return; // FPS has its own button
              }

              String type = info.path("type").asText("").toLowerCase(java.util.Locale.ROOT);
              String cls = info.path("class").asText("").toLowerCase(java.util.Locale.ROOT);
              String hay =
                  (entry.getKey() + " " + type + " " + cls)
                      .toLowerCase(java.util.Locale.ROOT);
              if (isShipWeaponOrdnance(hay)) {
                return; // Missiles, torpedoes, bombs, and rocket ordnance live elsewhere.
              }

              if (classFilter != null && !classFilter.isBlank() && !"all".equals(classFilter)) {
                if (!matchesShipWeaponClass(hay, classFilter)) {
                  return;
                }
              }
              names.add(entry.getKey());
            });

    names.sort(String.CASE_INSENSITIVE_ORDER);
    return names;
  }

  private boolean isShipWeaponOrdnance(String haystack) {
    return haystack.contains("missile")
        || haystack.contains("torpedo")
        || haystack.contains("bomb")
        || haystack.contains("rocket pod")
        || haystack.contains("rocket");
  }

  private boolean matchesShipWeaponClass(String haystack, String classFilter) {
    return switch (classFilter) {
      case "cannon" -> haystack.contains("cannon");
      case "repeater" -> haystack.contains("repeater");
      case "gatling" -> haystack.contains("gatling");
      case "scattergun" -> haystack.contains("scattergun");
      // "other" = everything that doesn't match any of the above keyword groups
      case "other" -> !haystack.contains("cannon")
          && !haystack.contains("repeater")
          && !haystack.contains("gatling")
          && !haystack.contains("scattergun")
          && !isShipWeaponOrdnance(haystack);
      default -> true;
    };
  }

  private void handleFpsSystemSelect(StringSelectInteractionEvent event, String systemValue) {
    FpsWeaponDraft draft =
        fpsWeaponDrafts.computeIfAbsent(event.getUser().getIdLong(), k -> new FpsWeaponDraft());
    draft.system =
        systemValue == null || systemValue.isBlank()
            ? "all"
            : systemValue.toLowerCase(java.util.Locale.ROOT);

    event
        .editMessage("FPS weapons: step 2/3 — choose class")
        .setComponents(PanelButtons.fpsClassMenu())
        .queue();
  }

  private void handleFpsClassSelect(StringSelectInteractionEvent event, String classValue) {
    FpsWeaponDraft draft =
        fpsWeaponDrafts.computeIfAbsent(event.getUser().getIdLong(), k -> new FpsWeaponDraft());
    draft.weaponClass =
        classValue == null || classValue.isBlank()
            ? "all"
            : classValue.toLowerCase(java.util.Locale.ROOT);

    java.util.List<String> fpsNames = buildFpsWeaponNames(draft.system, draft.weaponClass);
    if (fpsNames.isEmpty()) {
      event
          .editMessage("No FPS weapons found for that system filter right now.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    replyWithListSelect(
        event,
        "fps weapons",
        fpsNames,
        PanelButtons.SELECT_FPS_WEAPON_PICK,
        "FPS weapons: step 3/3 — choose weapon");
  }

  private void handleFpsWeaponSelect(StringSelectInteractionEvent event, String name) {
    FpsWeaponDraft draft = fpsWeaponDrafts.get(event.getUser().getIdLong());
    String system = draft == null ? "all" : draft.system;
    String weaponClass = draft == null ? "all" : draft.weaponClass;

    JsonNode weapon = StarCitizenDataService.getWeapon(name);
    if (weapon == null || weapon.isMissingNode()) {
      event
          .editMessageEmbeds(PanelEmbeds.notFound("fps weapon", name))
          .setContent("")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    JsonNode info = weapon.path("info");
    JsonNode stats = weapon.path("stats");
    String domain = info.path("domain").asText("").toLowerCase(java.util.Locale.ROOT);
    if (!domain.contains("fps")) {
      event
          .editMessage("Selected item is not an FPS weapon in the current dataset.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    String systemLabel;
    if (system == null || system.isBlank() || "all".equals(system)) {
      systemLabel = "All systems";
    } else {
      systemLabel =
          Character.toUpperCase(system.charAt(0))
              + system.substring(1).toLowerCase(java.util.Locale.ROOT);
    }
    String classLabel =
        "all".equals(weaponClass)
            ? "All classes"
            : Character.toUpperCase(weaponClass.charAt(0))
              + weaponClass.substring(1).toLowerCase(java.util.Locale.ROOT);
    String infoBlock =
        buildWeaponInfoDisplay(info)
            + "\n**System Filter:** "
            + systemLabel
            + "\n**Class Filter:** "
            + classLabel;
    String statsBlock = buildWeaponStatsDisplay(stats);

    java.util.List<String> buy = formatFpsLocationListForSystem(info.path("buy_locations"), system);
    java.util.List<String> sell =
        formatFpsLocationListForSystem(info.path("sell_locations"), system);

    StringBuilder extras = new StringBuilder();
    extras.append("**Fire Modes:** ");
    JsonNode fireModes = weapon.path("fire_modes");
    if (fireModes.isArray() && !fireModes.isEmpty()) {
      java.util.List<String> modes = new java.util.ArrayList<>();
      for (JsonNode fm : fireModes) {
        String m = fm.asText(fm.path("mode").asText(""));
        if (!m.isBlank()) {
          modes.add(m);
        }
      }
      extras.append(modes.isEmpty() ? fireModes.size() + " mode(s)" : String.join(", ", modes))
          .append("\n");
    } else {
      extras.append("—");
    }
    extras.append("\n");

    JsonNode attachments = weapon.path("attachments");
    if (attachments.isArray() && !attachments.isEmpty()) {
      extras.append("**Attachment Slots:**\n");
      for (JsonNode att : attachments) {
        String aName =
            att.isTextual() ? att.asText() : att.path("name").asText(att.path("type").asText(""));
        if (!aName.isBlank()) {
          extras.append("• ").append(aName).append("\n");
        }
      }
    }

    if (!buy.isEmpty()) {
      extras.append("**Buy Locations:**\n").append(formatLocationBlock(buy)).append("\n");
    }
    if (!sell.isEmpty()) {
      extras.append("**Sell Locations:**\n").append(formatLocationBlock(sell)).append("\n");
    }

    event
        .editMessageEmbeds(PanelEmbeds.weaponResult(name, infoBlock, statsBlock, extras.toString()))
        .setContent("")
        .setComponents(java.util.Collections.emptyList())
        .queue();
    fpsWeaponDrafts.remove(event.getUser().getIdLong());
  }

  private void handleTradeCargoSelect(StringSelectInteractionEvent event, String value) {
    TradeDraft d = tradeDrafts.computeIfAbsent(event.getUser().getIdLong(), k -> new TradeDraft());
    int cargo;
    try {
      cargo = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      event
          .editMessage("Invalid cargo option.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    d.cargo = cargo;

    if ("commodity".equalsIgnoreCase(d.mode)) {
      replyWithDatasetSelect(
          event,
          "commodities",
          PanelButtons.SELECT_TRADE_COMMODITY,
          "Trade setup: step 3/3 — choose commodity");
      return;
    }

    event
        .editMessage("Trade setup: step 3/3 — choose route count")
        .setComponents(PanelButtons.tradeCountMenu())
        .queue();
  }

  private void handleTradeModeSelect(StringSelectInteractionEvent event, String value) {
    TradeDraft d = tradeDrafts.computeIfAbsent(event.getUser().getIdLong(), k -> new TradeDraft());
    d.mode = value;
    event
        .editMessage("Trade setup: step 2/3 — choose cargo size")
        .setComponents(PanelButtons.tradeCargoMenu())
        .queue();
  }

  private void handleTradeCommoditySelect(StringSelectInteractionEvent event, String commodity) {
    TradeDraft d = tradeDrafts.get(event.getUser().getIdLong());
    if (d == null || d.cargo <= 0) {
      event
          .editMessage("Trade setup expired. Start again from 📦 Trade.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }
    d.commodity = commodity;

    java.util.List<TradeService.TradeRoute> routes =
        TradeService.topRoutes(d.cargo, d.commodity, 8);
    String formatted = TradeService.Formatter.format(routes, d.cargo);
    event
        .editMessageEmbeds(PanelEmbeds.tradeResult(formatted))
        .setContent("")
        .setComponents(java.util.Collections.emptyList())
        .queue();

    tradeDrafts.remove(event.getUser().getIdLong());
  }

  private void handleTradeCountSelect(StringSelectInteractionEvent event, String value) {
    TradeDraft d = tradeDrafts.get(event.getUser().getIdLong());
    if (d == null || d.cargo <= 0) {
      event
          .editMessage("Trade setup expired. Start again from 📦 Trade.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }
    try {
      d.count = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      event
          .editMessage("Invalid route count.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    java.util.List<TradeService.TradeRoute> routes = TradeService.topRoutes(d.cargo, null, d.count);
    String formatted = TradeService.Formatter.format(routes, d.cargo);
    event
        .editMessageEmbeds(PanelEmbeds.tradeResult(formatted))
        .setContent("")
        .setComponents(java.util.Collections.emptyList())
        .queue();

    tradeDrafts.remove(event.getUser().getIdLong());
  }

  private void handleRefineryProfileSelect(StringSelectInteractionEvent event, String value) {
    // format: ore|scu
    String[] p = value.split("\\|");
    if (p.length != 2) {
      event
          .editMessage("Invalid refinery profile.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    int rawScu;
    try {
      rawScu = Integer.parseInt(p[1]);
    } catch (NumberFormatException e) {
      event
          .editMessage("Invalid refinery SCU value.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    RefineryService.RefineJob job = RefineryService.analyze(p[0], rawScu);
    String brief = RefineryService.Formatter.brief(job);
    String full = RefineryService.Formatter.full(job);
    event
        .editMessageEmbeds(PanelEmbeds.refineryAnalysis(p[0], rawScu, brief, full))
        .setContent("")
        .setComponents(java.util.Collections.emptyList())
        .queue();
  }

  private void handleRefineryOreSelect(StringSelectInteractionEvent event, String value) {
    RefineryDraft d =
        refineryDrafts.computeIfAbsent(event.getUser().getIdLong(), k -> new RefineryDraft());
    d.ore = value;
    event
        .editMessage("Refinery setup: step 2/3 — choose batch size")
        .setComponents(PanelButtons.refineryScuMenu())
        .queue();
  }

  private void handleRefineryScuSelect(StringSelectInteractionEvent event, String value) {
    RefineryDraft d =
        refineryDrafts.computeIfAbsent(event.getUser().getIdLong(), k -> new RefineryDraft());
    try {
      d.rawScu = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      event
          .editMessage("Invalid refinery SCU value.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }
    event
        .editMessage("Refinery setup: step 3/3 — choose view")
        .setComponents(PanelButtons.refineryViewMenu())
        .queue();
  }

  private void handleRefineryViewSelect(StringSelectInteractionEvent event, String value) {
    RefineryDraft d = refineryDrafts.get(event.getUser().getIdLong());
    if (d == null || d.ore == null || d.rawScu <= 0) {
      event
          .editMessage("Refinery setup expired. Start again from 🏭 Refinery.")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    RefineryService.RefineJob job = RefineryService.analyze(d.ore, d.rawScu);
    String brief = RefineryService.Formatter.brief(job);
    String full = RefineryService.Formatter.full(job);
    String selected = "full".equalsIgnoreCase(value) ? full : brief;

    event
        .editMessageEmbeds(PanelEmbeds.refineryAnalysis(d.ore, d.rawScu, brief, selected))
        .setContent("")
        .setComponents(java.util.Collections.emptyList())
        .queue();
    refineryDrafts.remove(event.getUser().getIdLong());
  }

  private void replyWithDatasetSelect(
      ButtonInteractionEvent event, String dataset, String selectId, String placeholder) {
    JsonNode data = StarCitizenDataService.get(dataset);
    if (data == null || data.isEmpty() || !data.isObject()) {
      event.replyEmbeds(PanelEmbeds.dataError(dataset)).setEphemeral(true).queue();
      return;
    }

    java.util.List<String> names = new java.util.ArrayList<>();
    data.fieldNames().forEachRemaining(names::add);
    java.util.Collections.sort(names);
    if (names.isEmpty()) {
      event.replyEmbeds(PanelEmbeds.dataError(dataset)).setEphemeral(true).queue();
      return;
    }

    replyWithListSelect(event, dataset, names, selectId, placeholder);
  }

  private void replyWithDatasetSelect(
      StringSelectInteractionEvent event, String dataset, String selectId, String placeholder) {
    JsonNode data = StarCitizenDataService.get(dataset);
    if (data == null || data.isEmpty() || !data.isObject()) {
      event
          .editMessageEmbeds(PanelEmbeds.dataError(dataset))
          .setContent("")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    java.util.List<String> names = new java.util.ArrayList<>();
    data.fieldNames().forEachRemaining(names::add);
    java.util.Collections.sort(names);
    if (names.isEmpty()) {
      event
          .editMessageEmbeds(PanelEmbeds.dataError(dataset))
          .setContent("")
          .setComponents(java.util.Collections.emptyList())
          .queue();
      return;
    }

    replyWithListSelect(event, dataset, names, selectId, placeholder);
  }

  private void replyWithListSelect(
      ButtonInteractionEvent event,
      String label,
      java.util.List<String> names,
      String selectId,
      String placeholder) {
    DatasetSelectSession session =
        startSelectSession(event.getUser().getIdLong(), label, names, selectId, placeholder);
    event
        .reply("Pick an option:")
        .addComponents(buildDatasetComponents(session))
        .setEphemeral(true)
        .queue();
  }

  private void replyWithListSelect(
      StringSelectInteractionEvent event,
      String label,
      java.util.List<String> names,
      String selectId,
      String placeholder) {
    DatasetSelectSession session =
        startSelectSession(event.getUser().getIdLong(), label, names, selectId, placeholder);
    event.editMessage("Pick an option:").setComponents(buildDatasetComponents(session)).queue();
  }

  private DatasetSelectSession startSelectSession(
      long userId,
      String label,
      java.util.List<String> names,
      String selectId,
      String placeholder) {
    DatasetSelectSession session = new DatasetSelectSession();
    session.dataset = label;
    session.label = label;
    session.selectId = selectId;
    session.placeholder = placeholder;
    session.names = new java.util.ArrayList<>(names);
    session.allNames = new java.util.ArrayList<>(names);
    session.page = 0;
    datasetSelectSessions.put(datasetSessionKey(userId, selectId), session);
    return session;
  }

  private java.util.List<String> buildMiningRockNames() {
    java.util.List<String> names = new java.util.ArrayList<>(MiningService.ROCK_TYPES.keySet());
    names.sort(String.CASE_INSENSITIVE_ORDER);
    return names;
  }

  private java.util.List<String> buildMiningLaserNames() {
    java.util.List<String> names = new java.util.ArrayList<>(MiningService.LASERS.keySet());
    // Hide legacy alias from user-facing picker.
    names.removeIf(name -> "Hofstede".equalsIgnoreCase(name));
    names.sort(String.CASE_INSENSITIVE_ORDER);
    return names;
  }

  private java.util.List<String> buildMiningConsumableNames() {
    java.util.List<String> names = new java.util.ArrayList<>(MiningService.CONSUMABLES.keySet());
    names.sort(String.CASE_INSENSITIVE_ORDER);
    return names;
  }

  private java.util.List<String> buildFpsWeaponNames(String systemFilter, String classFilter) {
    JsonNode weapons = StarCitizenDataService.get("weapons");
    java.util.List<String> names = new java.util.ArrayList<>();
    if (weapons == null || !weapons.isObject()) {
      return names;
    }

    weapons
        .fields()
        .forEachRemaining(
            entry -> {
              JsonNode info = entry.getValue().path("info");
              String domain = info.path("domain").asText("").toLowerCase(java.util.Locale.ROOT);
              if (!domain.contains("fps")) {
                return;
              }

              if (systemFilter != null && !systemFilter.isBlank() && !"all".equals(systemFilter)) {
                java.util.List<String> buy =
                    formatFpsLocationListForSystem(info.path("buy_locations"), systemFilter);
                if (buy.isEmpty()) {
                  return;
                }
              }

              if (classFilter != null && !classFilter.isBlank() && !"all".equals(classFilter)) {
                String type = info.path("type").asText("").toLowerCase(java.util.Locale.ROOT);
                String cls = info.path("class").asText("").toLowerCase(java.util.Locale.ROOT);
                String hay =
                    (entry.getKey() + " " + type + " " + cls).toLowerCase(java.util.Locale.ROOT);
                if (!matchesFpsClass(hay, classFilter)) {
                  return;
                }
              }
              names.add(entry.getKey());
            });

    names.sort(String.CASE_INSENSITIVE_ORDER);
    return names;
  }

  private boolean matchesFpsClass(String haystack, String classFilter) {
    return switch (classFilter) {
      case "pistol" -> haystack.contains("pistol")
          || haystack.contains("handgun")
          || haystack.contains("sidearm");
      case "smg" -> haystack.contains("smg") || haystack.contains("submachine");
      case "rifle" -> haystack.contains("rifle")
          || haystack.contains("assault rifle")
          || haystack.contains("battle rifle");
      case "shotgun" -> haystack.contains("shotgun");
      case "sniper" -> haystack.contains("sniper") || haystack.contains("marksman");
      case "lmg" -> haystack.contains("lmg") || haystack.contains("machine gun");
      case "launcher" -> haystack.contains("launcher")
          || haystack.contains("rocket")
          || haystack.contains("grenade");
      default -> true;
    };
  }

  private String formatLocationBlock(java.util.List<String> locations) {
    if (locations == null || locations.isEmpty()) {
      return "—";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < locations.size(); i++) {
      String loc = locations.get(i);
      String line = (i == 0 ? "" : "\n") + "• " + loc;
      if (sb.length() + line.length() > 900) {
        int remaining = locations.size() - i;
        if (remaining > 0) {
          sb.append("\n• ...and ").append(remaining).append(" more");
        }
        break;
      }
      sb.append(line);
    }
    return sb.toString();
  }

  private java.util.List<String> formatFpsLocationListForSystem(
      JsonNode locationsNode, String systemFilter) {
    java.util.List<String> out = new java.util.ArrayList<>();
    if (locationsNode == null || locationsNode.isMissingNode()) {
      return out;
    }
    String target = systemFilter == null ? "all" : systemFilter.toLowerCase(java.util.Locale.ROOT);

    if (locationsNode.isArray()) {
      for (JsonNode n : locationsNode) {
        String text = n.asText("").trim();
        if (text.isBlank()) {
          continue;
        }
        if (!"all".equals(target) && !target.equals(inferSystemFromLocationText(text))) {
          continue;
        }
        if (!out.contains(text)) {
          out.add(text);
        }
      }
    } else {
      String text = locationsNode.asText("").trim();
      if (!text.isBlank()) {
        if ("all".equals(target) || target.equals(inferSystemFromLocationText(text))) {
          out.add(text);
        }
      }
    }
    return out;
  }

  private String inferSystemFromLocationText(String location) {
    String lower = location == null ? "" : location.toLowerCase(java.util.Locale.ROOT);
    if (lower.contains("pyro")
        || lower.contains("bloom")
        || lower.contains("ruin station")
        || lower.contains("checkmate")) {
      return "pyro";
    }
    if (lower.contains("nyx") || lower.contains("levski") || lower.contains("delamar")) {
      return "nyx";
    }
    return "stanton";
  }

  private java.util.List<String> buildComponentNamesForCategory(String category) {
    JsonNode components = StarCitizenDataService.get("components");
    if (components == null || !components.isObject() || components.isEmpty()) {
      return java.util.Collections.emptyList();
    }

    String target = (category == null ? "all" : category.trim().toLowerCase());
    java.util.List<String> names = new java.util.ArrayList<>();
    components
        .fields()
        .forEachRemaining(
            entry -> {
              String name = entry.getKey();
              JsonNode info = entry.getValue().path("info");
              String type = info.path("type").asText("").toLowerCase();
              String cls = info.path("class").asText("").toLowerCase();
              String hay = (name + " " + type + " " + cls).toLowerCase();

              if (matchesComponentCategory(target, hay)) {
                names.add(name);
              }
            });

    names.sort(String.CASE_INSENSITIVE_ORDER);
    return names;
  }

  private static boolean matchesComponentCategory(String category, String haystack) {
    return switch (category) {
      case "all" -> true;
      case "shields" -> haystack.contains("shield");
      case "power" -> haystack.contains("power") || haystack.contains("plant");
      case "coolers" -> haystack.contains("cooler") || haystack.contains("cooling");
      case "quantum" ->
          haystack.contains("quantum") || haystack.contains("qdrive") || haystack.contains("jump");
      case "engines" -> haystack.contains("engine") || haystack.contains("thruster");
      case "missiles" ->
          haystack.contains("missile") || haystack.contains("torpedo") || haystack.contains("bomb");
      case "utility" -> !(haystack.contains("shield")
          || haystack.contains("power")
          || haystack.contains("plant")
          || haystack.contains("cooler")
          || haystack.contains("cooling")
          || haystack.contains("quantum")
          || haystack.contains("qdrive")
          || haystack.contains("jump")
          || haystack.contains("engine")
          || haystack.contains("thruster")
          || haystack.contains("missile")
          || haystack.contains("torpedo")
          || haystack.contains("bomb")
          || haystack.contains("weapon")
          || haystack.contains("gun")
          || haystack.contains("cannon")
          || haystack.contains("laser"));
      default -> true;
    };
  }

  private void handleDatasetPageButton(ButtonInteractionEvent event, String componentId) {
    String payload = componentId.substring(DATASET_PAGE_PREFIX.length());
    String[] parts = payload.split("\\|", 2);
    if (parts.length != 2) {
      event.reply("Invalid page control.").setEphemeral(true).queue();
      return;
    }

    String action = parts[0];
    String selectId = parts[1];
    DatasetSelectSession session =
        datasetSelectSessions.get(datasetSessionKey(event.getUser().getIdLong(), selectId));
    if (session == null || session.names == null || session.names.isEmpty()) {
      event
          .reply("Selection expired. Re-open the panel option and try again.")
          .setEphemeral(true)
          .queue();
      return;
    }

    int pageCount = Math.max(1, (int) Math.ceil(session.names.size() / (double) DATASET_PAGE_SIZE));
    if ("next".equals(action) && session.page < pageCount - 1) {
      session.page++;
    }
    if ("prev".equals(action) && session.page > 0) {
      session.page--;
    }

    event.editMessage("Pick an option:").setComponents(buildDatasetComponents(session)).queue();
  }

  private static java.util.List<ActionRow> buildDatasetComponents(DatasetSelectSession session) {
    int total = session.names.size();
    int pageCount = Math.max(1, (int) Math.ceil(total / (double) DATASET_PAGE_SIZE));
    int safePage = Math.max(0, Math.min(session.page, pageCount - 1));
    int from = safePage * DATASET_PAGE_SIZE;
    int to = Math.min(from + DATASET_PAGE_SIZE, total);

    String placeholder = session.placeholder;
    if (pageCount > 1) {
      placeholder = String.format("%s (page %d/%d)", session.placeholder, safePage + 1, pageCount);
    }
    // Append filter indicator when active
    boolean filtered = session.allNames != null && session.allNames.size() != session.names.size();
    if (filtered) {
      placeholder = "🔍 " + placeholder + " [filtered]";
    }

    StringSelectMenu.Builder menu =
        StringSelectMenu.create(session.selectId)
            .setPlaceholder(placeholder)
            .setRequiredRange(1, 1);

    for (int i = from; i < to; i++) {
      String name = session.names.get(i);
      String label = compactSelectLabel(name);
      SelectOption opt = SelectOption.of(label, name);
      if (!label.equals(name)) {
        opt = opt.withDescription("Full: " + truncateForDescription(name));
      }
      menu.addOptions(opt);
    }

    java.util.List<ActionRow> rows = new java.util.ArrayList<>();
    rows.add(ActionRow.of(menu.build()));

    // Navigation + filter row (always present so Filter is always reachable)
    Button prev =
        Button.secondary(DATASET_PAGE_PREFIX + "prev|" + session.selectId, "◀ Prev")
            .withDisabled(safePage == 0 || pageCount <= 1);
    Button next =
        Button.secondary(DATASET_PAGE_PREFIX + "next|" + session.selectId, "Next ▶")
            .withDisabled(safePage >= pageCount - 1 || pageCount <= 1);
    Button filter =
        Button.primary(PanelButtons.BTN_DATASET_FILTER_PREFIX + session.selectId, "🔍 Filter");
    if (filtered) {
      Button clear =
          Button.secondary(
              PanelButtons.BTN_DATASET_CLEAR_FILTER_PREFIX + session.selectId, "✖ Clear Filter");
      rows.add(ActionRow.of(prev, next, filter, clear));
    } else {
      rows.add(ActionRow.of(prev, next, filter));
    }

    return rows;
  }

  private static String datasetSessionKey(long userId, String selectId) {
    return userId + ":" + selectId;
  }

  private static String compactSelectLabel(String raw) {
    if (raw == null) {
      return "";
    }
    String text = raw.replaceAll("\\s+", " ").trim();
    if (text.length() <= 80) {
      return text;
    }
    return text.substring(0, 77).trim() + "...";
  }

  private static String truncateForDescription(String raw) {
    if (raw == null) {
      return "";
    }
    String text = raw.replaceAll("\\s+", " ").trim();
    if (text.length() <= 95) {
      return text;
    }
    return text.substring(0, 92).trim() + "...";
  }

  private static void clearDatasetSelectSession(long userId, String selectId) {
    datasetSelectSessions.remove(datasetSessionKey(userId, selectId));
  }

  // ── Filter button: opens a modal so users can type a search term ──────────

  private void handleDatasetFilterButton(ButtonInteractionEvent event, String componentId) {
    String selectId = componentId.substring(PanelButtons.BTN_DATASET_FILTER_PREFIX.length());
    DatasetSelectSession session =
        datasetSelectSessions.get(datasetSessionKey(event.getUser().getIdLong(), selectId));
    if (session == null) {
      event
          .reply("Selection expired — reopen the panel option and try again.")
          .setEphemeral(true)
          .queue();
      return;
    }

    net.dv8tion.jda.api.interactions.components.text.TextInput input =
        net.dv8tion.jda.api.interactions.components.text.TextInput.create(
                "filter_query",
                "Search term",
                net.dv8tion.jda.api.interactions.components.text.TextInputStyle.SHORT)
            .setPlaceholder("e.g. \"quant\", \"agri\", \"arc\"")
            .setRequired(true)
            .build();

    net.dv8tion.jda.api.interactions.modals.Modal modal =
        net.dv8tion.jda.api.interactions.modals.Modal.create(
                PanelButtons.MODAL_DATASET_FILTER_PREFIX + selectId,
                "🔍 Filter: " + session.dataset)
            .addComponents(ActionRow.of(input))
            .build();

    event.replyModal(modal).queue();
  }

  private void handleDatasetClearFilterButton(ButtonInteractionEvent event, String componentId) {
    String selectId = componentId.substring(PanelButtons.BTN_DATASET_CLEAR_FILTER_PREFIX.length());
    DatasetSelectSession session =
        datasetSelectSessions.get(datasetSessionKey(event.getUser().getIdLong(), selectId));
    if (session == null || session.allNames == null) {
      event
          .reply("Selection expired — reopen the panel option and try again.")
          .setEphemeral(true)
          .queue();
      return;
    }
    session.names = new java.util.ArrayList<>(session.allNames);
    session.page = 0;
    event.editMessage("Pick an option:").setComponents(buildDatasetComponents(session)).queue();
  }

  private void handleDatasetFilterModal(ModalInteractionEvent event, String modalId) {
    String selectId = modalId.substring(PanelButtons.MODAL_DATASET_FILTER_PREFIX.length());
    DatasetSelectSession session =
        datasetSelectSessions.get(datasetSessionKey(event.getUser().getIdLong(), selectId));
    if (session == null) {
      event
          .reply("Selection expired — reopen the panel option and try again.")
          .setEphemeral(true)
          .queue();
      return;
    }

    var mapping = event.getValue("filter_query");
    String query = (mapping != null ? mapping.getAsString() : "").trim().toLowerCase();
    if (query.isBlank()) {
      event.reply("⚠️ Enter at least one character to filter.").setEphemeral(true).queue();
      return;
    }

    // Always filter the full unfiltered list so repeated filters start clean.
    java.util.List<String> source = (session.allNames != null) ? session.allNames : session.names;
    java.util.List<String> filtered = new java.util.ArrayList<>();
    for (String name : source) {
      if (name.toLowerCase().contains(query)) {
        filtered.add(name);
      }
    }

    if (filtered.isEmpty()) {
      event
          .reply("No results found for **" + query + "**. Try a shorter term.")
          .setEphemeral(true)
          .queue();
      return;
    }

    session.names = filtered;
    session.page = 0;

    String label =
        "🔍 Results for **"
            + query
            + "** ("
            + filtered.size()
            + " match"
            + (filtered.size() == 1 ? "" : "es")
            + "):";
    event.reply(label).addComponents(buildDatasetComponents(session)).setEphemeral(true).queue();
  }

  private void handleDataRefresh(ButtonInteractionEvent event) {
    boolean isAdmin =
        event.getMember() != null
            && (event.getMember().isOwner()
            || event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)
            || event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR));
    if (!isAdmin) {
      event.reply("Only server admins can refresh datasets.").setEphemeral(true).queue();
      return;
    }

    event
        .deferReply(true)
        .queue(
            hook -> {
              String[] datasets = {
                  "commodities",
                  "ships",
                  "weapons",
                  "components",
                  "mining",
                  "refinery",
                  "refinery_stations",
                  "locations",
                  "salvage"
              };

              int ok = 0;
              StringBuilder details = new StringBuilder();
              for (String dataset : datasets) {
                StarCitizenUpdateManager.UpdateResult result =
                    StarCitizenUpdateManager.updateDetailed(dataset, true);
                if (result.isSuccess()) {
                  ok++;
                }

                String marker;
                if (result.outcome == StarCitizenUpdateManager.UpdateOutcome.UPDATED) {
                  marker = "✅";
                } else if (result.outcome == StarCitizenUpdateManager.UpdateOutcome.UNCHANGED) {
                  marker = "⚠️";
                } else {
                  marker = "❌";
                }

                details.append(marker).append(" ").append(dataset);
                if (!result.message.isBlank()) {
                  details.append(" — ").append(result.message);
                }
                details.append("\n");
              }

              hook.editOriginalEmbeds(
                      PanelEmbeds.dataRefreshResult(ok, datasets.length, details.toString().trim()))
                  .queue();
            });
  }

  private String buildDatasetStatusLines() {
    String[] datasets = {
        "commodities", "ships", "weapons", "components",
        "mining", "refinery", "refinery_stations", "salvage",
        "locations", "missions", "items", "armor"
    };

    StringBuilder sb = new StringBuilder();
    for (String dataset : datasets) {
      JsonNode node = StarCitizenDataService.get(dataset);
      int count = countEntries(node, dataset);
      sb.append("• **").append(dataset).append("**: ").append(count).append(" entries\n");
    }
    return sb.toString().trim();
  }

  private int countEntries(JsonNode node, String dataset) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return 0;
    }
    if (node.isArray()) {
      return node.size();
    }
    if (node.isObject()) {
      // Some datasets are object containers with nested maps.
      if ("refinery".equals(dataset) && node.path("methods").isObject()) {
        return node.path("methods").size();
      }
      if ("salvage".equals(dataset) && node.path("hotspots").isObject()) {
        return node.path("hotspots").size();
      }
      return node.size();
    }
    return 0;
  }

  private void replyWithRefineryOreSelect(ButtonInteractionEvent event) {
    JsonNode refinery = StarCitizenDataService.get("refinery");
    JsonNode oreValues = refinery != null ? refinery.path("ore_base_values") : null;
    if (oreValues == null
        || oreValues.isMissingNode()
        || !oreValues.isObject()
        || oreValues.isEmpty()) {
      event.replyEmbeds(PanelEmbeds.dataError("refinery ore values")).setEphemeral(true).queue();
      return;
    }

    java.util.List<String> ores = new java.util.ArrayList<>();
    oreValues.fieldNames().forEachRemaining(ores::add);
    java.util.Collections.sort(ores);
    replyWithListSelect(
        event,
        "refinery ore",
        ores,
        PanelButtons.SELECT_REFINERY_ORE,
        "Refinery setup: step 1/3 — choose ore");
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  /**
   * Safely reads a modal text-input value, returning an empty string if absent.
   */
  private static String value(ModalInteractionEvent event, String inputId) {
    var mapping = event.getValue(inputId);
    return mapping != null ? mapping.getAsString().trim() : "";
  }

  private int maxMiningHeadsForShip(String shipName) {
    if (shipName == null) {
      return 1;
    }
    String lower = shipName.toLowerCase(java.util.Locale.ROOT);
    if (lower.contains("mole") || lower.contains("orion")) {
      return 3;
    }
    return 1;
  }

  private void promptHeadLaserSelect(StringSelectInteractionEvent event, MiningDraft d) {
    int current = Math.max(0, d.headConfigCursor);
    int total = Math.max(1, d.activeHeads > 0 ? d.activeHeads : d.operators);
    replyWithListSelect(
        event,
        "mining lasers",
        buildMiningLaserNames(),
        PanelButtons.SELECT_MINING_LASER,
        "Mining setup: step 4/7 — choose laser for head " + (current + 1) + " of " + total);
  }

  private void promptHeadConsumableSelect(StringSelectInteractionEvent event, MiningDraft d) {
    int current = Math.max(0, d.headConfigCursor);
    int total = Math.max(1, d.activeHeads > 0 ? d.activeHeads : d.operators);
    replyWithListSelect(
        event,
        "mining modules",
        buildMiningConsumableNames(),
        PanelButtons.SELECT_MINING_CONSUMABLE,
        "Mining setup: step 5/7 — choose consumable for head " + (current + 1) + " of " + total);
  }

  /**
   * Formats a JSON object node as markdown key–value pairs. Returns an empty string for null,
   * array, or missing nodes.
   */
  private static String formatNode(JsonNode node) {
    if (node == null || node.isMissingNode() || !node.isObject()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    node.fields()
        .forEachRemaining(
            entry ->
                sb.append("**")
                    .append(entry.getKey())
                    .append(":** ")
                    .append(entry.getValue().asText())
                    .append("\n"));
    return sb.toString().trim();
  }

  /**
   * Like {@link #formatNode} but skips fields whose numeric value is 0 (or whose text is blank /
   * "0" / "0.0") so embeds don't show pages of empty placeholders.
   */
  private static String formatNodeFiltered(JsonNode node) {
    if (node == null || node.isMissingNode() || !node.isObject()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    node.fields()
        .forEachRemaining(
            entry -> {
              JsonNode val = entry.getValue();
              String text = formatDisplayValue(val);
              if (text.isEmpty()) {
                return;
              }
              sb.append("**").append(entry.getKey()).append(":** ").append(text).append("\n");
            });
    return sb.toString().trim();
  }

  private static String formatEntryForDisplay(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return "";
    }
    if (node.isObject()) {
      return formatNodeFiltered(node);
    }
    if (node.isArray()) {
      StringBuilder sb = new StringBuilder();
      int shown = Math.min(12, node.size());
      for (int i = 0; i < shown; i++) {
        JsonNode item = node.get(i);
        String text = item == null ? "" : item.asText("").trim();
        if (!text.isBlank()) {
          sb.append("• ").append(text).append("\n");
        }
      }
      if (node.size() > shown) {
        sb.append("*...and more*");
      }
      return sb.toString().trim();
    }
    return node.asText("").trim();
  }

  private static String formatDisplayValue(JsonNode value) {
    if (value == null || value.isNull() || value.isMissingNode()) {
      return "";
    }

    if (value.isNumber()) {
      double n = value.asDouble(0);
      if (Math.abs(n) < 1e-9) {
        return "";
      }
      if (Math.abs(n - Math.rint(n)) < 1e-9) {
        return String.format("%,.0f", n);
      }
      return String.format("%,.2f", n).replaceAll("\\.?0+$", "");
    }

    String text = value.asText("").trim();
    if (text.isEmpty() || text.equals("0") || text.equals("0.0")) {
      return "";
    }
    return text;
  }

  /**
   * Formats a price double as a whole-number string with comma separation. E.g. 2958.0 → "2,958".
   */
  private static String fmtPrice(double price) {
    return String.format("%,.0f", price);
  }

  /**
   * Returns "best location (price aUEC)" string for a commodity entry's buy or sell array. For buy:
   * finds the lowest price. For sell: finds the highest price.
   */
  private static String buildBestDisplay(JsonNode entry, String arrayKey, boolean lowestIsBest) {
    JsonNode arr = entry.path(arrayKey);
    if (!arr.isArray() || arr.isEmpty()) {
      // Fall back to stored best_buy / best_sell name if present
      String stored =
          lowestIsBest ? entry.path("best_buy").asText("") : entry.path("best_sell").asText("");
      return stored.isBlank() ? "*No data*" : stored;
    }
    double bestPrice = lowestIsBest ? Double.MAX_VALUE : 0;
    String bestLoc = null;
    for (JsonNode item : arr) {
      double price = item.path("price").asDouble(0);
      String loc = item.path("location").asText("");
      if (loc.isBlank()) {
        continue;
      }
      if (lowestIsBest ? price > 0 && price < bestPrice : price > bestPrice) {
        bestPrice = price;
        bestLoc = loc;
      }
    }
    if (bestLoc == null) {
      String stored =
          lowestIsBest ? entry.path("best_buy").asText("") : entry.path("best_sell").asText("");
      return stored.isBlank() ? "*No data*" : stored;
    }
    return bestLoc + " — " + fmtPrice(bestPrice) + " aUEC";
  }

  /**
   * Builds a bullet-list of buy or sell locations sorted by price (cheapest first for buy, most
   * expensive first for sell). Truncates to fit Discord's 1 024-char embed field limit.
   */
  private static String buildLocationList(JsonNode entry, String arrayKey, boolean buyMode) {
    JsonNode arr = entry.path(arrayKey);
    if (!arr.isArray() || arr.isEmpty()) {
      return "";
    }

    // Collect and sort
    java.util.List<double[]> rows = new java.util.ArrayList<>(); // [price, index]
    java.util.List<String> locs = new java.util.ArrayList<>();
    int idx = 0;
    for (JsonNode item : arr) {
      double price = item.path("price").asDouble(0);
      String loc = item.path("location").asText("");
      if (loc.isBlank() || price <= 0) {
        idx++;
        continue;
      }
      locs.add(loc);
      rows.add(new double[]{price, idx++});
    }
    rows.sort((a, b) -> buyMode ? Double.compare(a[0], b[0]) : Double.compare(b[0], a[0]));

    StringBuilder sb = new StringBuilder();
    for (double[] row : rows) {
      String line = "• " + locs.get((int) row[1]) + " — " + fmtPrice(row[0]) + " aUEC\n";
      if (sb.length() + line.length() > 950) {
        sb.append("*...and more*");
        break;
      }
      sb.append(line);
    }
    return sb.toString().trim();
  }

  /**
   * Builds a compact loadout summary for a ship (weapons, turrets, missiles). Returns an empty
   * string when no hardpoint data is available.
   */
  private static String buildShipLoadout(JsonNode ship) {
    StringBuilder sb = new StringBuilder();

    JsonNode weapons = ship.path("weapons");
    JsonNode turrets = ship.path("turrets");
    JsonNode missiles = ship.path("missiles");
    JsonNode missileRacks = ship.path("missile_racks");

    if (weapons.isArray() && !weapons.isEmpty()) {
      sb.append("**Weapons:**\n");
      java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
      java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
      for (JsonNode weapon : weapons) {
        String wName = weapon.isTextual() ? weapon.asText("") : weapon.path("name").asText("");
        if (wName.isBlank()) {
          wName = weapon.path("item_name").asText("Unknown");
        }
        int sz = weapon.path("size").asInt(0);
        String label = wName + (sz > 0 ? " (S" + sz + ")" : "");

        double dps = weapon.path("dps").asDouble(0);
        double alpha = weapon.path("alpha_damage").asDouble(0);
        int rpm = weapon.path("rpm").asInt(0);

        if (dps <= 0 || alpha <= 0 || rpm <= 0) {
          String resolved = StarCitizenDataService.resolveDatasetKey("weapons", wName);
          JsonNode ref = resolved != null ? StarCitizenDataService.getWeapon(resolved) : null;
          JsonNode refStats = ref != null ? ref.path("stats") : null;
          if (refStats != null && refStats.isObject()) {
            if (dps <= 0) {
              dps = refStats.path("dps").asDouble(0);
            }
            if (alpha <= 0) {
              alpha = refStats.path("alpha_damage").asDouble(0);
            }
            if (rpm <= 0) {
              rpm = refStats.path("rpm").asInt(0);
            }
          }
        }

        StringBuilder lineDetails = new StringBuilder();
        if (dps > 0) {
          lineDetails.append(String.format("%.1f DPS", dps));
        }
        if (alpha > 0) {
          if (lineDetails.length() > 0) {
            lineDetails.append(" | ");
          }
          lineDetails.append(String.format("%.1f alpha", alpha));
        }
        if (rpm > 0) {
          if (lineDetails.length() > 0) {
            lineDetails.append(" | ");
          }
          lineDetails.append(rpm).append(" RPM");
        }

        counts.merge(label, 1, Integer::sum);
        if (lineDetails.length() > 0) {
          details.putIfAbsent(label, lineDetails.toString());
        }
      }
      counts.forEach(
          (k, v) -> {
            sb.append("• ").append(v > 1 ? v + "× " : "").append(k);
            String d = details.get(k);
            if (d != null && !d.isBlank()) {
              sb.append(" — ").append(d);
            }
            sb.append("\n");
          });
    }
    if (turrets.isArray() && !turrets.isEmpty()) {
      sb.append("**Turrets:**\n");
      java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
      for (JsonNode t : turrets) {
        String tName = t.isTextual() ? t.asText("") : t.path("name").asText("Unknown");
        int sz = t.path("size").asInt(0);
        String label = tName + (sz > 0 ? " (S" + sz + ")" : "");
        counts.merge(label, 1, Integer::sum);
      }
      counts.forEach(
          (k, v) -> sb.append("• ").append(v > 1 ? v + "× " : "").append(k).append("\n"));
    }
    if (missiles.isArray() && !missiles.isEmpty()) {
      sb.append("**Missiles:**\n");
      java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
      for (JsonNode m : missiles) {
        String mName =
            m.isTextual() ? m.asText("") : m.path("name").asText(m.path("type").asText("Unknown"));
        counts.merge(mName, 1, Integer::sum);
      }
      counts.forEach(
          (k, v) -> sb.append("• ").append(v > 1 ? v + "× " : "").append(k).append("\n"));
    }
    if (missileRacks.isArray() && !missileRacks.isEmpty()) {
      sb.append("**Missile Racks:**\n");
      for (JsonNode r : missileRacks) {
        String rName = r.isTextual() ? r.asText("") : r.path("name").asText("Unknown");
        int sz = r.path("size").asInt(0);
        int cap = r.path("capacity").asInt(0);
        sb.append("• ").append(rName);
        if (sz > 0) {
          sb.append(" (S").append(sz).append(")");
        }
        if (cap > 0) {
          sb.append(" — ").append(cap).append(" missiles");
        }
        sb.append("\n");
      }
    }
    // Components summary (show count of distinct types)
    JsonNode comps = ship.path("components");
    if (comps.isObject() && !comps.isEmpty()) {
      sb.append("**Component Slots:** ").append(comps.size()).append(" type(s)\n");
    }
    return sb.toString().trim();
  }

  /**
   * Builds extra info for a weapon (fire modes, ammo, attachments). Returns an empty string when no
   * extra data is available.
   */
  private static String buildWeaponExtras(JsonNode weapon) {
    StringBuilder sb = new StringBuilder();

    JsonNode fireModes = weapon.path("fire_modes");
    if (fireModes.isArray() && !fireModes.isEmpty()) {
      sb.append("**Fire Modes:** ");
      java.util.List<String> modes = new java.util.ArrayList<>();
      for (JsonNode fm : fireModes) {
        String m = fm.asText(fm.path("mode").asText(""));
        if (!m.isBlank()) {
          modes.add(m);
        }
      }
      sb.append(modes.isEmpty() ? fireModes.size() + " mode(s)" : String.join(", ", modes))
          .append("\n");
    }

    JsonNode ammo = weapon.path("ammo");
    if (ammo.isObject() && !ammo.isEmpty()) {
      String ammoText = formatNodeFiltered(ammo);
      if (!ammoText.isBlank()) {
        sb.append("**— Ammo —**\n").append(ammoText).append("\n");
      }
    }

    JsonNode attachments = weapon.path("attachments");
    if (attachments.isArray() && !attachments.isEmpty()) {
      sb.append("**Attachment Slots:**\n");
      for (JsonNode att : attachments) {
        String aName =
            att.isTextual() ? att.asText() : att.path("name").asText(att.path("type").asText(""));
        if (!aName.isBlank()) {
          sb.append("• ").append(aName).append("\n");
        }
      }
    }

    return sb.toString().trim();
  }

  // ------------------------------------------------------------------
  // Dedicated display builders — show "—" for zero/blank fields so
  // important data is always visible even when the API hasn't filled it.
  // ------------------------------------------------------------------

  /**
   * Formats a stat value + unit string; returns "—" when the value is zero.
   */
  private static String fmtStat(double val, String unit) {
    if (val <= 0) {
      return "—";
    }
    if (Math.abs(val - Math.rint(val)) < 1e-9) {
      return String.format("%,.0f", val) + unit;
    }
    return String.format("%,.2f", val).replaceAll("\\.?0+$", "") + unit;
  }

  private static String fmtSignedStat(double val, String unit) {
    if (Math.abs(val) < 1e-9) {
      return "—";
    }
    if (Math.abs(val - Math.rint(val)) < 1e-9) {
      return String.format("%,.0f", val) + unit;
    }
    return String.format("%,.2f", val).replaceAll("\\.?0+$", "") + unit;
  }

  private static String fmtCombatStat(double val) {
    if (Math.abs(val) < 1e-9) {
      return "0";
    }
    if (Math.abs(val - Math.rint(val)) < 1e-9) {
      return String.format("%,.0f", val);
    }
    return String.format("%,.2f", val).replaceAll("\\.?0+$", "");
  }

  private static String fmtSignedPercent(double val) {
    if (Math.abs(val) < 1e-9) {
      return "0%";
    }
    String body =
        Math.abs(val - Math.rint(val)) < 1e-9
            ? String.format("%,.0f", Math.abs(val))
            : String.format("%,.2f", Math.abs(val)).replaceAll("\\.?0+$", "");
    return (val > 0 ? "+" : "-") + body + "%";
  }

  private static String fmtMinMaxSigned(JsonNode s, String minKey, String maxKey, String unit) {
    double min = s.path(minKey).asDouble(0);
    double max = s.path(maxKey).asDouble(0);
    if (Math.abs(min) < 1e-9 && Math.abs(max) < 1e-9) {
      return "—";
    }
    return fmtSignedStat(min, unit) + " / " + fmtSignedStat(max, unit);
  }

  private static void appendStatLine(StringBuilder sb, String label, double value, String unit) {
    if (value > 0) {
      sb.append("**").append(label).append(":** ").append(fmtStat(value, unit)).append("\n");
    }
  }

  private static void appendIntLine(
      StringBuilder sb, String label, JsonNode s, String key, String unit) {
    int v = s.path(key).asInt(0);
    if (v > 0) {
      sb.append("**").append(label).append(":** ").append(v).append(unit).append("\n");
    }
  }

  private static void appendBoolLine(StringBuilder sb, String label, JsonNode s, String key) {
    if (s.path(key).asBoolean(false)) {
      sb.append("**").append(label).append(":** Yes\n");
    }
  }

  /**
   * Ship info section — always shows key fields with "—" for missing data.
   */
  private static String buildShipInfoDisplay(JsonNode i) {
    if (i == null || i.isMissingNode() || !i.isObject()) {
      return "*No data*";
    }
    StringBuilder sb = new StringBuilder();

    String mfg = i.path("manufacturer").asText("").trim();
    String type = i.path("type").asText("").trim();
    String career = i.path("career").asText("").trim();
    String role = i.path("role").asText("").trim();
    String size = i.path("size").asText("").trim();
    String dims = i.path("dimensions").asText("").trim();
    int crew = i.path("crew").asInt(0);
    int cargo = i.path("cargo").asInt(0);
    double price = i.path("price_auec").asDouble(0);
    String claim = i.path("claim_time").asText("").trim();
    String expedite = i.path("expedite_time").asText("").trim();
    String url = i.path("store_url").asText("").trim();

    if (!mfg.isEmpty()) {
      sb.append("**Manufacturer:** ").append(mfg).append("\n");
    }
    if (!type.isEmpty()) {
      sb.append("**Type:** ").append(type).append("\n");
    }
    if (!career.isEmpty()) {
      sb.append("**Career:** ").append(career).append("\n");
    }
    if (!role.isEmpty()) {
      sb.append("**Role:** ").append(role).append("\n");
    }
    if (!size.isEmpty()) {
      sb.append("**Size:** ").append(size).append("\n");
    }
    if (!dims.isEmpty()) {
      sb.append("**Dimensions:** ").append(dims).append("\n");
    }
    sb.append("**Crew:** ").append(crew > 0 ? crew : "—").append("\n");
    sb.append("**Cargo:** ").append(cargo > 0 ? cargo + " SCU" : "—").append("\n");
    sb.append("**Buy At:** ")
        .append(!i.path("buy_location").asText("").trim().isEmpty()
            ? i.path("buy_location").asText("").trim()
            : "—")
        .append("\n");

    JsonNode buyLocs = i.path("buy_locations");
    if (buyLocs.isArray() && !buyLocs.isEmpty()) {
      sb.append("**Buy Locations:** ");
      java.util.List<String> locs = new java.util.ArrayList<>();
      for (JsonNode loc : buyLocs) {
        String locText = loc.asText("").trim();
        if (!locText.isBlank()) {
          locs.add(locText);
        }
      }
      sb.append(locs.isEmpty() ? "—" : String.join(", ", locs)).append("\n");
    } else {
      sb.append("**Buy Locations:** —\n");
    }

    sb.append("**Sell At:** ")
        .append(!i.path("sell_location").asText("").trim().isEmpty()
            ? i.path("sell_location").asText("").trim()
            : "—")
        .append("\n");

    JsonNode sellLocs = i.path("sell_locations");
    if (sellLocs.isArray() && !sellLocs.isEmpty()) {
      sb.append("**Sell Locations:** ");
      java.util.List<String> locs = new java.util.ArrayList<>();
      for (JsonNode loc : sellLocs) {
        String locText = loc.asText("").trim();
        if (!locText.isBlank()) {
          locs.add(locText);
        }
      }
      sb.append(locs.isEmpty() ? "—" : String.join(", ", locs)).append("\n");
    } else {
      sb.append("**Sell Locations:** —\n");
    }

    sb.append("**Price:** ").append(price > 0 ? fmtPrice(price) + " aUEC" : "—").append("\n");
    if (!claim.isEmpty()) {
      sb.append("**Claim Time:** ").append(claim).append("\n");
    }
    if (!expedite.isEmpty()) {
      sb.append("**Expedite Time:** ").append(expedite).append("\n");
    }
    if (!url.isEmpty()) {
      sb.append("**Store:** [RSI page](").append(url).append(")\n");
    }

    return sb.toString().trim();
  }

  /**
   * Ship stats section — shows DPS / flight / fuel fields with "—" for zero.
   */
  private static String buildShipStatsDisplay(JsonNode ship) {
    if (ship == null || ship.isMissingNode() || !ship.isObject()) {
      return "";
    }
    JsonNode s = ship.path("stats");
    JsonNode i = ship.path("info");
    if (s == null || s.isMissingNode() || !s.isObject()) {
      return "";
    }
    double[] derived = StarCitizenDataService.deriveShipFirepowerFromLoadout(ship);
    double[] effectiveTotals = StarCitizenDataService.deriveShipEffectiveTotalsFromLoadout(ship);
    double pilotDps = Math.max(s.path("pilot_dps").asDouble(0), derived[0]);
    double turretDps = Math.max(s.path("turret_dps").asDouble(0), derived[1]);
    double missileDps = Math.max(s.path("missile_dps").asDouble(0), derived[2]);
    double pilotAlpha = Math.max(s.path("pilot_alpha").asDouble(0), derived[3]);
    double turretAlpha = Math.max(s.path("turret_alpha").asDouble(0), derived[4]);
    double missileAlpha = Math.max(s.path("missile_alpha").asDouble(0), derived[5]);
    double totalDps = Math.max(s.path("total_dps").asDouble(0), pilotDps + turretDps + missileDps);
    double totalAlpha =
        Math.max(s.path("total_alpha").asDouble(0), pilotAlpha + turretAlpha + missileAlpha);
    StringBuilder sb = new StringBuilder();
    sb.append("**SCM / Max:** ")
        .append(i.path("scm_speed").asInt(0) > 0 ? fmtPrice(i.path("scm_speed").asInt(0)) + " m/s" : "—")
        .append(" / ")
        .append(i.path("max_speed").asInt(0) > 0 ? fmtPrice(i.path("max_speed").asInt(0)) + " m/s" : "—")
        .append("\n");
    sb.append("**Shield / Hull:** ")
        .append(i.path("shield_hp").asInt(0) > 0 ? fmtPrice(i.path("shield_hp").asInt(0)) + " HP" : "—")
        .append(" / ")
        .append(i.path("hull_hp").asInt(0) > 0 ? fmtPrice(i.path("hull_hp").asInt(0)) + " HP" : "—")
        .append("\n");
    sb.append("**Pilot / Turret / Missile DPS:** ")
        .append(fmtCombatStat(pilotDps))
        .append(" / ")
        .append(fmtCombatStat(turretDps))
        .append(" / ")
        .append(fmtCombatStat(missileDps))
        .append("\n");
    sb.append("**Pilot / Turret / Missile Alpha:** ")
        .append(fmtCombatStat(pilotAlpha))
        .append(" / ")
        .append(fmtCombatStat(turretAlpha))
        .append(" / ")
        .append(fmtCombatStat(missileAlpha))
        .append("\n");
    sb.append("**Total Alpha:** ").append(fmtCombatStat(totalAlpha)).append("\n");
    sb.append("**Armor Mod (Physical / Energy):** ")
        .append(fmtSignedPercent(s.path("armor_physical_damage_modifier").asDouble(0)))
        .append(" / ")
        .append(fmtSignedPercent(s.path("armor_energy_damage_modifier").asDouble(0)))
        .append("\n");
    sb.append("**Deflection (Physical / Energy):** ")
        .append(fmtCombatStat(s.path("deflection_physical").asDouble(0)))
        .append(" / ")
        .append(fmtCombatStat(s.path("deflection_energy").asDouble(0)))
        .append("\n");
    sb.append("**Default Loadout Effective DPS (Phys / Energy):** ")
        .append(fmtCombatStat(effectiveTotals[0]))
        .append(" / ")
        .append(fmtCombatStat(effectiveTotals[1]))
        .append("\n");
    sb.append("**Default Loadout Effective Alpha (Phys / Energy):** ")
        .append(fmtCombatStat(effectiveTotals[2]))
        .append(" / ")
        .append(fmtCombatStat(effectiveTotals[3]))
        .append("\n");
    sb.append("**Pitch / Yaw / Roll:** ")
        .append(fmtCombatStat(s.path("pitch").asDouble(0)))
        .append(" / ")
        .append(fmtCombatStat(s.path("yaw").asDouble(0)))
        .append(" / ")
        .append(fmtCombatStat(s.path("roll").asDouble(0)))
        .append("\n");
    sb.append("**Hydrogen / Quantum Fuel:** ")
        .append(fmtCombatStat(s.path("hydrogen_fuel").asDouble(0)))
        .append(" / ")
        .append(fmtCombatStat(s.path("quantum_fuel").asDouble(0)))
        .append("\n");
    sb.append("**Mass:** ")
        .append(s.path("mass").asDouble(0) > 0 ? fmtPrice(s.path("mass").asDouble(0)) : "—");
    return sb.toString().trim();
  }

  /**
   * Weapon info section — always shows key fields with "—" for missing data.
   */
  private static String buildWeaponInfoDisplay(JsonNode i) {
    if (i == null || i.isMissingNode() || !i.isObject()) {
      return "*No data*";
    }
    StringBuilder sb = new StringBuilder();

    String mfg = i.path("manufacturer").asText("").trim();
    String type = i.path("type").asText("").trim();
    String cls = i.path("class").asText("").trim();
    int size = i.path("size").asInt(0);
    String dmgType = i.path("damage_type").asText("").trim();
    String hardpt = i.path("hardpoint").asText("").trim();
    String url = i.path("store_url").asText("").trim();

    if (!mfg.isEmpty()) {
      sb.append("**Manufacturer:** ").append(mfg).append("\n");
    }
    if (!type.isEmpty()) {
      sb.append("**Type:** ").append(type).append("\n");
    }
    if (!cls.isEmpty() && !cls.equalsIgnoreCase(type)) {
      sb.append("**Class:** ").append(cls).append("\n");
    }
    sb.append("**Size:** ").append(size > 0 ? "S" + size : "—").append("\n");

    // Show domain in friendly form
    if (!i.path("domain").asText("").isBlank()) {
      String rawDomain = i.path("domain").asText("").trim();
      String friendlyDomain =
          switch (rawDomain.toLowerCase(java.util.Locale.ROOT)) {
            case "fps" -> "FPS / Personal";
            case "vehicle" -> "Ship / Vehicle";
            default -> rawDomain.substring(0, 1).toUpperCase() + rawDomain.substring(1);
          };
      sb.append("**Domain:** ").append(friendlyDomain).append("\n");
    }
    if (!dmgType.isEmpty()) {
      sb.append("**Damage Type:** ").append(dmgType).append("\n");
    }
    if (!hardpt.isEmpty()) {
      sb.append("**Hardpoint:** ").append(hardpt).append("\n");
    }
    if (!url.isEmpty()) {
      sb.append("**Store:** [RSI page](").append(url).append(")\n");
    }

    // Buy locations — use full array when available, fall back to single field
    JsonNode buyLocs = i.path("buy_locations");
    if (buyLocs.isArray() && !buyLocs.isEmpty()) {
      sb.append("**Buy Locations:**\n");
      int shown = 0;
      for (JsonNode loc : buyLocs) {
        String locText = loc.asText("").trim();
        if (!locText.isBlank()) {
          sb.append("• ").append(locText).append("\n");
          if (++shown >= 8) {
            int remaining = buyLocs.size() - shown;
            if (remaining > 0) {
              sb.append("• ...and ").append(remaining).append(" more\n");
            }
            break;
          }
        }
      }
    } else {
      String buyLoc = i.path("buy_location").asText("").trim();
      sb.append("**Buy At:** ").append(!buyLoc.isEmpty() ? buyLoc : "—").append("\n");
    }

    // Sell locations
    JsonNode sellLocs = i.path("sell_locations");
    if (sellLocs.isArray() && !sellLocs.isEmpty()) {
      sb.append("**Sell Locations:**\n");
      for (JsonNode loc : sellLocs) {
        String locText = loc.asText("").trim();
        if (!locText.isBlank()) {
          sb.append("• ").append(locText).append("\n");
        }
      }
    }

    return sb.toString().trim();
  }

  /**
   * Weapon stats section — shows damage/DPS/cost/range with "—" for zero.
   */
  private static String buildWeaponStatsDisplay(JsonNode s) {
    if (s == null || s.isMissingNode() || !s.isObject()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();

    // Core combat metrics
    appendStatLine(sb, "DPS", s.path("dps").asDouble(0), "");
    appendStatLine(sb, "Burst DPS", s.path("burst_dps").asDouble(0), "");
    appendStatLine(sb, "Alpha Damage", s.path("alpha_damage").asDouble(0), "");
    appendStatLine(sb, "Alpha Min", s.path("alpha_min").asDouble(0), "");
    appendStatLine(sb, "Alpha Max", s.path("alpha_max").asDouble(0), "");
    appendIntLine(sb, "RPM", s, "rpm", "");
    appendIntLine(sb, "Range", s, "range", " m");
    appendIntLine(sb, "Speed", s, "projectile_speed", " m/s");
    appendStatLine(sb, "Health", s.path("hp").asDouble(0), "");
    appendStatLine(sb, "Quality", s.path("quality").asDouble(0), "");

    // Penetration / power / ammo
    appendStatLine(sb, "Penetration Distance", s.path("penetration_distance").asDouble(0), " m");
    appendStatLine(
        sb, "Penetration Near Radius", s.path("penetration_near_radius").asDouble(0), " m");
    appendStatLine(
        sb, "Penetration Far Radius", s.path("penetration_far_radius").asDouble(0), " m");
    appendStatLine(sb, "Power Consumption", s.path("power_consumption").asDouble(0), "");
    appendStatLine(sb, "Max Ammos", s.path("max_ammos").asDouble(0), "");
    appendStatLine(sb, "Ammos Regen", s.path("ammos_regen").asDouble(0), "");
    appendStatLine(sb, "Regen Cooldown", s.path("regen_cooldown").asDouble(0), " s");
    appendStatLine(sb, "Overheat", s.path("overheat").asDouble(0), "");
    appendStatLine(sb, "Total Dmg Dealt", s.path("total_dmg_dealt").asDouble(0), "");
    appendStatLine(sb, "Total Dealt Time", s.path("total_dealt_time").asDouble(0), " s");

    // Charge / shot behavior
    appendStatLine(sb, "Full Charge Fire Rate", s.path("full_charge_fire_rate").asDouble(0), "");
    appendStatLine(sb, "Charged Dmg Multi", s.path("charged_dmg_multi").asDouble(0), "x");
    appendStatLine(sb, "Charge Time", s.path("charge_time").asDouble(0), " s");
    appendBoolLine(sb, "Fire Only on Full Charge", s, "fire_only_on_full_charge");
    appendBoolLine(sb, "Fire Automatically on Full Charge", s, "fire_automatically_on_full_charge");
    appendStatLine(sb, "Pellets per Shot", s.path("pellets_per_shot").asDouble(0), "");
    appendStatLine(sb, "Explosion Radius", s.path("explosion_radius").asDouble(0), " m");

    // Damage type breakdown
    appendStatLine(sb, "Ammo Dmg Biochemical", s.path("ammo_dmg_biochemical").asDouble(0), "");
    appendStatLine(sb, "Ammo Dmg Distortion", s.path("ammo_dmg_distortion").asDouble(0), "");
    appendStatLine(sb, "Ammo Dmg Energy", s.path("ammo_dmg_energy").asDouble(0), "");
    appendStatLine(sb, "Ammo Dmg Physical", s.path("ammo_dmg_physical").asDouble(0), "");
    appendStatLine(sb, "Ammo Dmg Stun", s.path("ammo_dmg_stun").asDouble(0), "");
    appendStatLine(sb, "Ammo Dmg Thermal", s.path("ammo_dmg_thermal").asDouble(0), "");

    // Spread / handling
    appendStatLine(sb, "Spread First Attack", s.path("spread_first_attack").asDouble(0), "");
    appendStatLine(sb, "Spread Attack", s.path("spread_attack").asDouble(0), "");
    appendStatLine(sb, "Spread Min", s.path("spread_min").asDouble(0), "");
    appendStatLine(sb, "Spread Max", s.path("spread_max").asDouble(0), "");
    appendStatLine(sb, "Spread Decay", s.path("spread_decay").asDouble(0), "");

    // Distortion behavior
    appendStatLine(
        sb, "Distortion Shutdown Dmg", s.path("distortion_shutdown_dmg").asDouble(0), "");
    appendStatLine(sb, "Distortion Decay Delay", s.path("distortion_decay_delay").asDouble(0), "");
    appendStatLine(sb, "Distortion Decay Rate", s.path("distortion_decay_rate").asDouble(0), "");
    appendStatLine(
        sb, "Distortion Warning Ratio", s.path("distortion_warning_ratio").asDouble(0), "");

    // Economy
    double buy = s.path("cost_auec").asDouble(0);
    if (buy > 0) {
      sb.append("**Buy Price:** ").append(fmtPrice(buy)).append(" aUEC\n");
    }
    double sell = s.path("sell_price").asDouble(0);
    if (sell > 0) {
      sb.append("**Sell Price:** ").append(fmtPrice(sell)).append(" aUEC\n");
    }

    return sb.toString().trim();
  }

  /**
   * Component info section — always shows type/size/grade/class.
   */
  private static String buildComponentInfoDisplay(JsonNode i) {
    if (i == null || i.isMissingNode() || !i.isObject()) {
      return "*No data*";
    }
    StringBuilder sb = new StringBuilder();

    String mfg = i.path("manufacturer").asText("").trim();
    String type = i.path("type").asText("").trim();
    int size = i.path("size").asInt(0);
    String grade = i.path("grade").asText("").trim();
    String cls = i.path("class").asText("").trim();
    String buyLoc = i.path("buy_location").asText("").trim();
    String url = i.path("store_url").asText("").trim();

    if (!mfg.isEmpty()) {
      sb.append("**Manufacturer:** ").append(mfg).append("\n");
    }
    if (!type.isEmpty()) {
      sb.append("**Type:** ").append(type).append("\n");
    }
    sb.append("**Size:** ").append(size > 0 ? "S" + size : "—").append("\n");
    sb.append("**Grade:** ").append(!grade.isEmpty() ? grade : "—").append("\n");
    if (!cls.isEmpty()) {
      sb.append("**Class:** ").append(cls).append("\n");
    }
    sb.append("**Buy At:** ").append(!buyLoc.isEmpty() ? buyLoc : "—").append("\n");
    if (!url.isEmpty()) {
      sb.append("**Store:** [RSI page](").append(url).append(")\n");
    }
    return sb.toString().trim();
  }

  /**
   * Component stats section — shows power/cooling/shield/QT fields with "—" for zero.
   */
  private static String buildComponentStatsDisplay(JsonNode s) {
    if (s == null || s.isMissingNode() || !s.isObject()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("**Power Output:** ")
        .append(fmtStat(s.path("power_output").asDouble(0), ""))
        .append("\n");
    sb.append("**Power Draw:** ")
        .append(fmtStat(s.path("power_draw").asDouble(0), ""))
        .append("\n");
    sb.append("**Cooling Rate:** ")
        .append(fmtStat(s.path("cooling_rate").asDouble(0), ""))
        .append("\n");
    sb.append("**Shield HP:** ")
        .append(fmtStat(s.path("shield_hp").asDouble(0), " HP"))
        .append("\n");
    sb.append("**HP:** ").append(fmtStat(s.path("hp").asDouble(0), "")).append("\n");
    sb.append("**QT Speed:** ")
        .append(fmtStat(s.path("quantum_speed").asDouble(0), " km/s"))
        .append("\n");
    sb.append("**QT Range:** ")
        .append(fmtStat(s.path("quantum_range").asDouble(0), " AU"))
        .append("\n");
    sb.append("**Shield Regen Max:** ")
        .append(fmtStat(s.path("shield_regen_max").asDouble(0), ""))
        .append("\n");
    sb.append("**Shield Regen Full:** ")
        .append(fmtStat(s.path("shield_regen_time_full").asDouble(0), " s"))
        .append("\n");
    sb.append("**Shield Regen Delay (Damaged):** ")
        .append(fmtStat(s.path("shield_regen_delay_damaged").asDouble(0), " s"))
        .append("\n");
    sb.append("**Shield Regen Delay (Downed):** ")
        .append(fmtStat(s.path("shield_regen_delay_downed").asDouble(0), " s"))
        .append("\n");
    sb.append("**Physical Resistance (Min/Max):** ")
        .append(fmtMinMaxSigned(s, "physical_resistance_min", "physical_resistance_max", "%"))
        .append("\n");
    sb.append("**Energy Resistance (Min/Max):** ")
        .append(fmtMinMaxSigned(s, "energy_resistance_min", "energy_resistance_max", "%"))
        .append("\n");
    sb.append("**Distortion Resistance (Min/Max):** ")
        .append(fmtMinMaxSigned(s, "distortion_resistance_min", "distortion_resistance_max", "%"))
        .append("\n");
    sb.append("**Physical Absorption (Min/Max):** ")
        .append(fmtMinMaxSigned(s, "physical_absorption_min", "physical_absorption_max", "%"))
        .append("\n");
    sb.append("**Energy Absorption (Min/Max):** ")
        .append(fmtMinMaxSigned(s, "energy_absorption_min", "energy_absorption_max", "%"))
        .append("\n");
    sb.append("**Distortion Absorption (Min/Max):** ")
        .append(fmtMinMaxSigned(s, "distortion_absorption_min", "distortion_absorption_max", "%"))
        .append("\n");
    sb.append("**EM Max:** ").append(fmtStat(s.path("em_max").asDouble(0), "")).append("\n");
    sb.append("**Distortion Shutdown Dmg:** ")
        .append(fmtStat(s.path("distortion_shutdown_dmg").asDouble(0), ""))
        .append("\n");
    sb.append("**Distortion Decay Delay:** ")
        .append(fmtStat(s.path("distortion_decay_delay").asDouble(0), ""))
        .append("\n");
    sb.append("**Distortion Decay Rate:** ")
        .append(fmtStat(s.path("distortion_decay_rate").asDouble(0), ""))
        .append("\n");
    sb.append("**Distortion Warning Ratio:** ")
        .append(fmtStat(s.path("distortion_warning_ratio").asDouble(0), ""))
        .append("\n");
    sb.append("**Cost:** ")
        .append(
            s.path("cost_auec").asDouble(0) > 0
                ? fmtPrice(s.path("cost_auec").asDouble(0)) + " aUEC"
                : "—")
        .append("\n");
    String result = sb.toString().trim();
    return result.replaceAll("\\*\\*[^*]+\\*\\*: —\n?", "").trim().isEmpty() ? "" : result;
  }

  /**
   * Mission details with explicit issuer/location/reputation fields.
   */
  private static String buildMissionDetails(String missionName, JsonNode mission) {
    if (mission == null || mission.isMissingNode() || mission.isNull()) {
      return "*No data*";
    }

    String type = textOrDash(mission, "type");
    String tier = textOrDash(mission, "tier");
    String difficulty = textOrDash(mission, "difficulty");
    String category = textOrDash(mission, "category");
    String giver = textOrDash(mission, "giver", "issuer", "contract_from", "contractor");
    String location = textOrDash(mission, "location", "where_to_find", "region", "area");
    String shipRequired = textOrDash(mission, "ship_required");

    String repWith =
        textOrDash(mission, "reputation_with", "rep_with", "reputation_faction", "rep_faction");
    if ("—".equals(repWith)) {
      repWith = deriveRepFaction(type, giver, missionName);
    }

    String repRequired =
        textOrDash(
            mission, "reputation_required", "rep_required", "min_reputation", "minimum_reputation");
    if ("—".equals(repRequired)) {
      repRequired = deriveRepRequirement(tier, difficulty, missionName);
    }

    double minPayout = firstPositive(mission, "payout_min_auec", "reward_min");
    double maxPayout = firstPositive(mission, "payout_max_auec", "reward_max");
    String payout =
        (minPayout > 0 || maxPayout > 0)
            ? ((minPayout > 0 ? fmtPrice(minPayout) : "?")
               + " - "
               + (maxPayout > 0 ? fmtPrice(maxPayout) : "?")
               + " aUEC")
            : "—";

    String description = textOrDash(mission, "description");
    String tips = textOrDash(mission, "tips");

    StringBuilder sb = new StringBuilder();
    sb.append("**Type:** ").append(type).append("\n");
    sb.append("**Tier:** ").append(tier).append("\n");
    sb.append("**Difficulty:** ").append(difficulty).append("\n");
    sb.append("**Category:** ").append(category).append("\n");
    sb.append("**From (Issuer):** ").append(giver).append("\n");
    sb.append("**Where to Find:** ").append(location).append("\n");
    sb.append("**Reputation With:** ").append(repWith).append("\n");
    sb.append("**Rep Required:** ").append(repRequired).append("\n");
    sb.append("**Ship Required:** ").append(shipRequired).append("\n");
    sb.append("**Payout:** ").append(payout).append("\n\n");
    sb.append("**Description:** ").append(description).append("\n");
    sb.append("**Tips:** ").append(tips);
    return sb.toString().trim();
  }

  private static String deriveRepFaction(String type, String giver, String missionName) {
    String source = (type + " " + giver + " " + missionName).toLowerCase();
    if (source.contains("bounty") || source.contains("advocacy")) {
      return "Bounty Hunters Guild / local security";
    }
    if (source.contains("mercenary") || source.contains("security")) {
      return "Mercenary Guild / local security";
    }
    if (source.contains("cargo") || source.contains("delivery") || source.contains("trade")) {
      return "Delivery / Trade contracts";
    }
    if (source.contains("mining")) {
      return "Mining Guild / industrial contacts";
    }
    if (source.contains("salvage")) {
      return "Salvage Guild / salvage operators";
    }
    return "Contract issuer reputation";
  }

  private static String deriveRepRequirement(String tier, String difficulty, String missionName) {
    String level = (tier + " " + difficulty + " " + missionName).toLowerCase();
    if (level.contains("very hard") || level.contains("elite")) {
      return "High";
    }
    if (level.contains("hard")) {
      return "Medium-High";
    }
    if (level.contains("medium")) {
      return "Medium";
    }
    if (level.contains("easy") || level.contains("entry")) {
      return "Low / none";
    }
    return "Varies by issuer";
  }

  private static String textOrDash(JsonNode node, String... keys) {
    if (node == null || keys == null) {
      return "—";
    }
    for (String key : keys) {
      if (key == null || key.isBlank()) {
        continue;
      }
      String v = node.path(key).asText("").trim();
      if (!v.isBlank()) {
        return v;
      }
    }
    return "—";
  }

  private static double firstPositive(JsonNode node, String... keys) {
    if (node == null || keys == null) {
      return 0;
    }
    for (String key : keys) {
      if (key == null || key.isBlank()) {
        continue;
      }
      JsonNode value = node.path(key);
      if (value.isNumber()) {
        double n = value.asDouble(0);
        if (n > 0) {
          return n;
        }
      }
    }
    return 0;
  }
}
