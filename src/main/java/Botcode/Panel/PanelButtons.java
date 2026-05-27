package Botcode.Panel;

import Botcode.StarCitizen.MiningService;

import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Central registry of all button/modal IDs and builders for the interactive panel.
 *
 * <p>All IDs use the prefix {@code panel:} (buttons) or {@code panel_modal:} (modals) so the
 * interaction handler can filter them without touching unrelated events.
 *
 * <p>To add a new button:
 *
 * <ol>
 *   <li>Add a {@code BTN_} constant here.
 *   <li>Add it to {@link #mainPanelRows()}.
 *   <li>Handle it in {@link PanelInteractionHandler#onButtonInteraction}.
 * </ol>
 */
public class PanelButtons {

  // ------------------------------------------------------------------
  // Button IDs
  // ------------------------------------------------------------------

  /**
   * Prefix for dataset-filter buttons: panel:dataset_filter|&lt;selectId&gt;
   */
  public static final String BTN_DATASET_FILTER_PREFIX = "panel:dataset_filter|";

  /**
   * Prefix for dataset filter modal IDs: panel_modal:dataset_filter|&lt;selectId&gt;
   */
  public static final String MODAL_DATASET_FILTER_PREFIX = "panel_modal:dataset_filter|";

  /**
   * Prefix for clear-filter buttons: panel:dataset_clear_filter|&lt;selectId&gt;
   */
  public static final String BTN_DATASET_CLEAR_FILTER_PREFIX = "panel:dataset_clear_filter|";

  public static final String BTN_MINING = "panel:mining";
  public static final String BTN_COMMODITY = "panel:commodity";
  public static final String BTN_SHIP = "panel:ship";
  public static final String BTN_WEAPON = "panel:weapon";
  public static final String BTN_TRADE = "panel:trade";
  public static final String BTN_REFINERY = "panel:refinery";
  public static final String BTN_SALVAGE = "panel:salvage";
  public static final String BTN_COMPONENTS = "panel:components";
  public static final String BTN_ARMOR = "panel:armor";
  public static final String BTN_LOCATIONS = "panel:locations";
  public static final String BTN_MISSIONS = "panel:missions";
  public static final String BTN_FPS_WEAPONS = "panel:fps_weapons";
  public static final String BTN_REFRESH_DATA = "panel:refresh_data";
  public static final String BTN_DATA_STATUS = "panel:data_status";
  public static final String BTN_HELP = "panel:help";

  // Refinery sub-buttons
  public static final String BTN_REFINERY_ANALYZE = "panel:refinery_analyze";
  public static final String BTN_REFINERY_STATIONS = "panel:refinery_stations";

  // Salvage sub-buttons
  public static final String BTN_SALVAGE_HOTSPOTS = "panel:salvage_hotspots";
  public static final String BTN_SALVAGE_SHIPS = "panel:salvage_ships";
  public static final String BTN_SALVAGE_MATERIALS = "panel:salvage_materials";
  public static final String BTN_SALVAGE_TIPS = "panel:salvage_tips";

  // Select IDs
  public static final String SELECT_MINING_PROFILE = "panel_select:mining_profile";
  public static final String SELECT_MINING_ROCK = "panel_select:mining_rock";
  public static final String SELECT_MINING_SHIP = "panel_select:mining_ship";
  public static final String SELECT_MINING_HEADS = "panel_select:mining_heads";
  public static final String SELECT_MINING_LASER = "panel_select:mining_laser";
  public static final String SELECT_MINING_CONSUMABLE = "panel_select:mining_consumable";
  public static final String SELECT_MINING_MODULE_SLOTS = "panel_select:mining_module_slots";
  public static final String SELECT_MINING_OPERATORS = "panel_select:mining_operators";
  public static final String SELECT_MINING_FORMAT = "panel_select:mining_format";
  public static final String SELECT_COMMODITY_PICK = "panel_select:commodity_pick";
  public static final String SELECT_SHIP_PICK = "panel_select:ship_pick";
  public static final String SELECT_WEAPON_PICK = "panel_select:weapon_pick";
  public static final String SELECT_TRADE_MODE = "panel_select:trade_mode";
  public static final String SELECT_TRADE_CARGO = "panel_select:trade_cargo";
  public static final String SELECT_TRADE_COMMODITY = "panel_select:trade_commodity";
  public static final String SELECT_TRADE_COUNT = "panel_select:trade_count";
  public static final String SELECT_REFINERY_PROFILE = "panel_select:refinery_profile";
  public static final String SELECT_REFINERY_ORE = "panel_select:refinery_ore";
  public static final String SELECT_REFINERY_SCU = "panel_select:refinery_scu";
  public static final String SELECT_REFINERY_VIEW = "panel_select:refinery_view";
  public static final String SELECT_COMPONENT_CATEGORY = "panel_select:component_category";
  public static final String SELECT_COMPONENT_PICK = "panel_select:component_pick";
  public static final String SELECT_ARMOR_PICK = "panel_select:armor_pick";
  public static final String SELECT_LOCATION_PICK = "panel_select:location_pick";
  public static final String SELECT_MISSION_PICK = "panel_select:mission_pick";
  public static final String SELECT_FPS_SYSTEM = "panel_select:fps_system";
  public static final String SELECT_FPS_CLASS = "panel_select:fps_class";
  public static final String SELECT_FPS_WEAPON_PICK = "panel_select:fps_weapon_pick";
  public static final String SELECT_WEAPON_DOMAIN = "panel_select:weapon_domain";
  public static final String SELECT_SHIP_WEAPON_CLASS = "panel_select:ship_weapon_class";

  // ------------------------------------------------------------------
  // Modal IDs
  // ------------------------------------------------------------------

  public static final String MODAL_MINING = "panel_modal:mining";
  public static final String MODAL_COMMODITY = "panel_modal:commodity";
  public static final String MODAL_SHIP = "panel_modal:ship";
  public static final String MODAL_WEAPON = "panel_modal:weapon";
  public static final String MODAL_TRADE = "panel_modal:trade";
  public static final String MODAL_REFINERY = "panel_modal:refinery";

  // ------------------------------------------------------------------
  // Panel button rows
  // ------------------------------------------------------------------

  /**
   * Returns the two ActionRows that make up the main panel button bar. Max 5 buttons per row.
   */
  public static List<ActionRow> mainPanelRows() {
    return List.of(
        ActionRow.of(
            Button.primary(BTN_MINING, "⛏️ Mining"),
            Button.primary(BTN_COMMODITY, "💰 Commodity"),
            Button.primary(BTN_SHIP, "🚀 Ship Lookup"),
            Button.primary(BTN_WEAPON, "🔫 Ship Weapons"),
            Button.primary(BTN_TRADE, "📦 Trade")),
        ActionRow.of(
            Button.primary(BTN_REFINERY, "🏭 Refinery"),
            Button.primary(BTN_SALVAGE, "🔧 Salvage"),
            Button.secondary(BTN_REFRESH_DATA, "🔄 Refresh Data"),
            Button.secondary(BTN_DATA_STATUS, "📈 Data Status"),
            Button.secondary(BTN_HELP, "❓ Help")),
        ActionRow.of(
            Button.primary(BTN_COMPONENTS, "⚙️ Components"),
            Button.primary(BTN_ARMOR, "🛡️ Armor"),
            Button.primary(BTN_LOCATIONS, "🗺️ Locations"),
            Button.primary(BTN_MISSIONS, "📜 Missions"),
            Button.primary(BTN_FPS_WEAPONS, "🎯 FPS Weapons")));
  }

  public static ActionRow weaponDomainMenu() {
    return ActionRow.of(
        StringSelectMenu.create(SELECT_WEAPON_DOMAIN)
            .setPlaceholder("Weapon Lookup: choose domain")
            .addOptions(
                SelectOption.of("🎯 FPS / Personal Weapons", "fps")
                    .withDescription("Rifles, SMGs, pistols, shotguns..."),
                SelectOption.of("🚀 Ship / Vehicle Weapons", "ship")
                    .withDescription("Laser cannons, repeaters, ballistics..."))
            .setRequiredRange(1, 1)
            .build());
  }

  public static ActionRow shipWeaponClassMenu() {
    return ActionRow.of(
        StringSelectMenu.create(SELECT_SHIP_WEAPON_CLASS)
            .setPlaceholder("Ship weapons: choose class")
            .addOptions(
                SelectOption.of("All Classes", "all").withDescription("Show all ship weapons"),
                SelectOption.of("Cannons", "cannon")
                    .withDescription("e.g. Omnisky, Deadbolt, FL series"),
                SelectOption.of("Repeaters", "repeater")
                    .withDescription("e.g. CF-117 Bulldog, Attrition, Panther"),
                SelectOption.of("Gatlings", "gatling")
                    .withDescription("e.g. AD4B, AD5B, Draugar, Breakneck"),
                SelectOption.of("Scatterguns", "scattergun")
                    .withDescription("e.g. Dominance, Havoc, APAR"),
                SelectOption.of("Other", "other")
                    .withDescription("Turrets, beams, special mounts, oddballs..."))
            .setRequiredRange(1, 1)
            .build());
  }

  public static ActionRow fpsSystemMenu() {
    return ActionRow.of(
        StringSelectMenu.create(SELECT_FPS_SYSTEM)
            .setPlaceholder("FPS weapons: choose system")
            .addOptions(
                SelectOption.of("All systems", "all"),
                SelectOption.of("Stanton", "stanton"),
                SelectOption.of("Pyro", "pyro"),
                SelectOption.of("Nyx", "nyx"))
            .setRequiredRange(1, 1)
            .build());
  }

  public static ActionRow fpsClassMenu() {
    return ActionRow.of(
        StringSelectMenu.create(SELECT_FPS_CLASS)
            .setPlaceholder("FPS weapons: choose class")
            .addOptions(
                SelectOption.of("All Classes", "all"),
                SelectOption.of("Pistols", "pistol"),
                SelectOption.of("SMGs", "smg"),
                SelectOption.of("Rifles", "rifle"),
                SelectOption.of("Shotguns", "shotgun"),
                SelectOption.of("Snipers", "sniper"),
                SelectOption.of("LMGs", "lmg"),
                SelectOption.of("Launchers", "launcher"))
            .setRequiredRange(1, 1)
            .build());
  }

  /**
   * Refinery sub-menu buttons shown after clicking 🏭 Refinery.
   */
  public static List<ActionRow> refinerySubMenu() {
    return List.of(
        ActionRow.of(
            Button.primary(BTN_REFINERY_ANALYZE, "📊 Analyse Method"),
            Button.secondary(BTN_REFINERY_STATIONS, "📍 Stations")));
  }

  /**
   * Salvage sub-menu buttons shown after clicking 🔧 Salvage.
   */
  public static List<ActionRow> salvageSubMenu() {
    return List.of(
        ActionRow.of(
            Button.primary(BTN_SALVAGE_HOTSPOTS, "📍 Hotspots"),
            Button.primary(BTN_SALVAGE_SHIPS, "🚀 Ships"),
            Button.primary(BTN_SALVAGE_MATERIALS, "🪨 Materials"),
            Button.secondary(BTN_SALVAGE_TIPS, "💡 Tips")));
  }

  /**
   * Mining presets as a select menu (no text input).
   */
  public static ActionRow miningProfileMenu() {
    StringSelectMenu menu =
        StringSelectMenu.create(SELECT_MINING_PROFILE)
            .setPlaceholder("Choose a mining setup")
            .addOptions(
                SelectOption.of(
                    "Prospector | Quantanium | Hofstede S2 | Surge | 1 laser",
                    "Quantanium|Prospector|Hofstede S2|surge|1"),
                SelectOption.of(
                    "MOLE | Quantanium | Rigler XL | Surge | 3 lasers",
                    "Quantanium|Mole|Rigler XL|surge|3"),
                SelectOption.of(
                    "MOLE | Quantanium | Helix II | Stampede | 3 lasers",
                    "Quantanium|Mole|Helix II|stampede|3"),
                SelectOption.of(
                    "Prospector | Laranite | Lancet MH2 | Brandt | 1 laser",
                    "Laranite|Prospector|Lancet MH2|brandt|1"),
                SelectOption.of(
                    "MOLE | Agricium | Arbor MH2 | None | 2 lasers",
                    "Agricium|Mole|Arbor MH2|none|2"),
                SelectOption.of(
                    "Prospector | Bexalite | Helix II | Brandt | 1 laser",
                    "Bexalite|Prospector|Helix II|brandt|1"),
                SelectOption.of(
                    "MOLE | Beryl | Hofstede S2 | Rime | 2 lasers",
                    "Beryl|Mole|Hofstede S2|rime|2"))
            .setRequiredRange(1, 1)
            .build();
    return ActionRow.of(menu);
  }

  /**
   * Step 1: rock choice.
   */
  public static ActionRow miningRockMenu() {
    List<Map.Entry<String, MiningService.RockProfile>> rocks =
        new ArrayList<>(MiningService.ROCK_TYPES.entrySet());
    rocks.sort(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));

    List<SelectOption> options = new ArrayList<>();
    for (Map.Entry<String, MiningService.RockProfile> entry : rocks) {
      String name = entry.getKey();
      String tier = rockTier(entry.getValue());
      options.add(SelectOption.of(name + " (" + tier + ")", name));
    }

    List<SelectOption> displayOptions = options.size() > 25 ? options.subList(0, 25) : options;

    return ActionRow.of(
        StringSelectMenu.create(SELECT_MINING_ROCK)
            .setPlaceholder("Select rock type")
            .addOptions(displayOptions)
            .setRequiredRange(1, 1)
            .build());
  }

  private static String rockTier(MiningService.RockProfile profile) {
    double resistance = profile.resistanceMultiplier();
    if (resistance >= 1.15) {
      return "Very high value";
    }
    if (resistance >= 1.00) {
      return "High value";
    }
    if (resistance >= 0.80) {
      return "Medium value";
    }
    return "Low value";
  }

  /**
   * Step 2: mining ship choice.
   */
  public static ActionRow miningShipMenu() {
    return ActionRow.of(
        StringSelectMenu.create(SELECT_MINING_SHIP)
            .setPlaceholder("Select mining ship")
            .addOptions(
                SelectOption.of("Prospector", "Prospector")
                    .withDescription("Solo | 32 SCU | 1x S1 laser"),
                SelectOption.of("MOLE", "Mole").withDescription("Crew | 96 SCU | 3x S2 lasers"),
                SelectOption.of("Orion", "Orion").withDescription("Large | 480 SCU | 3x S2 lasers"))
            .setRequiredRange(1, 1)
            .build());
  }

  /**
   * Step 3: mining laser choice.
   */
  public static ActionRow miningLaserMenu() {
    return ActionRow.of(
        StringSelectMenu.create(SELECT_MINING_LASER)
            .setPlaceholder("Select mining laser head")
            .addOptions(
                // Size 2 (MOLE / Orion)
                SelectOption.of("Rigler XL", "Rigler XL")
                    .withDescription("S2 | High power | High fluctuation"),
                SelectOption.of("Helix II", "Helix II")
                    .withDescription("S2 | High power | Medium fluctuation"),
                SelectOption.of("Arbor MH2", "Arbor MH2").withDescription("S2 | Balanced"),
                SelectOption.of("Hofstede S2", "Hofstede S2")
                    .withDescription("S2 | Stable balanced"),
                SelectOption.of("Lancet MH2", "Lancet MH2")
                    .withDescription("S2 | Fast charge | Low fluctuation"),
                // Size 1 (Prospector)
                SelectOption.of("Helix I", "Helix I").withDescription("S1 | High power"),
                SelectOption.of("Arbor MH1", "Arbor MH1").withDescription("S1 | Balanced"),
                SelectOption.of("Hofstede S1", "Hofstede S1").withDescription("S1 | Stable"),
                SelectOption.of("Lancet MH1", "Lancet MH1").withDescription("S1 | Fast charge"),
                SelectOption.of("Klein S", "Klein S")
                    .withDescription("S1 | Low power | Very stable"))
            .setRequiredRange(1, 1)
            .build());
  }

  /**
   * Step 3: active mining heads/lasers in use.
   */
  public static ActionRow miningHeadCountMenu(int maxHeads) {
    List<SelectOption> options = new ArrayList<>();
    for (int i = 1; i <= Math.max(1, maxHeads); i++) {
      options.add(SelectOption.of(String.valueOf(i), String.valueOf(i)));
    }
    return ActionRow.of(
        StringSelectMenu.create(SELECT_MINING_HEADS)
            .setPlaceholder("Select active mining heads")
            .addOptions(options)
            .setRequiredRange(1, 1)
            .build());
  }

  /**
   * Step 4: consumable / module choice.
   */
  public static ActionRow miningConsumableMenu() {
    return ActionRow.of(
        StringSelectMenu.create(SELECT_MINING_CONSUMABLE)
            .setPlaceholder("Select active/passive module")
            .addOptions(
                // ── Active modules ─────────────────────────────────
                SelectOption.of("Surge", "surge").withDescription("Active | +50% power burst"),
                SelectOption.of("Stampede", "stampede")
                    .withDescription("Active | +30% charge rate"),
                SelectOption.of("Brandt", "brandt").withDescription("Active | -20% instability"),
                SelectOption.of("Focus I", "focus i").withDescription("Active | +20% focus power"),
                SelectOption.of("Focus II", "focus ii")
                    .withDescription("Active | +35% focus power"),
                SelectOption.of("Focus III", "focus iii")
                    .withDescription("Active | +50% focus power"),
                SelectOption.of("Torrent I", "torrent i")
                    .withDescription("Active | +10% power | +8% charge"),
                SelectOption.of("Torrent II", "torrent ii")
                    .withDescription("Active | +15% power | +12% charge"),
                SelectOption.of("Torrent III", "torrent iii")
                    .withDescription("Active | +20% power | +15% charge"),
                SelectOption.of("XMT XL", "xmt xl")
                    .withDescription("Active | +25% power | -10% instability"),
                // ── Passive modules ────────────────────────────────
                SelectOption.of("Lifeline", "lifeline")
                    .withDescription("Passive | Extends optimal window"),
                SelectOption.of("Optimum", "optimum")
                    .withDescription("Passive | Reduces fluctuation"),
                SelectOption.of("Torrent", "torrent")
                    .withDescription("Passive | Steady power output"),
                SelectOption.of("Rime", "rime").withDescription("Passive | Stability boost"),
                SelectOption.of("Rime I", "rime i")
                    .withDescription("Passive | Stability boost tier 1"),
                SelectOption.of("Rime II", "rime ii")
                    .withDescription("Passive | Stability boost tier 2"),
                SelectOption.of("Rime III", "rime iii")
                    .withDescription("Passive | Stability boost tier 3"),
                SelectOption.of("Forel", "forel").withDescription("Passive | -15% raw instability"),
                SelectOption.of("Grelin", "grelin").withDescription("Passive | +20% charge rate"),
                SelectOption.of("Herc", "herc").withDescription("Passive | +12% effective power"),
                SelectOption.of("Impulse", "impulse")
                    .withDescription("Passive | +8% power efficiency"),
                SelectOption.of("Jutes", "jütes").withDescription("Passive | -10% resistance"),
                SelectOption.of("None", "none").withDescription("No module"))
            .setRequiredRange(1, 1)
            .build());
  }

  /**
   * Step 5: operator count.
   */
  public static ActionRow miningOperatorsMenu() {
    return ActionRow.of(
        StringSelectMenu.create(SELECT_MINING_OPERATORS)
            .setPlaceholder("Select number of operators")
            .addOptions(
                SelectOption.of("1", "1"),
                SelectOption.of("2", "2"),
                SelectOption.of("3", "3"),
                SelectOption.of("4", "4"))
            .setRequiredRange(1, 1)
            .build());
  }

  /**
   * Step 5: module slots per active head.
   */
  public static ActionRow miningModuleSlotsMenu() {
    return ActionRow.of(
        StringSelectMenu.create(SELECT_MINING_MODULE_SLOTS)
            .setPlaceholder("Select modules per head")
            .addOptions(
                SelectOption.of("0 (none)", "0"),
                SelectOption.of("1", "1"),
                SelectOption.of("2", "2"),
                SelectOption.of("3", "3"))
            .setRequiredRange(1, 1)
            .build());
  }

  /**
   * Step 6: output format.
   */
  public static ActionRow miningFormatMenu() {
    return ActionRow.of(
        StringSelectMenu.create(SELECT_MINING_FORMAT)
            .setPlaceholder("Select output format")
            .addOptions(SelectOption.of("Brief", "brief"), SelectOption.of("Full", "full"))
            .setRequiredRange(1, 1)
            .build());
  }

  /**
   * Trade presets as cargo-size selector.
   */
  public static ActionRow tradeCargoMenu() {
    StringSelectMenu menu =
        StringSelectMenu.create(SELECT_TRADE_CARGO)
            .setPlaceholder("Choose cargo size for top trade routes")
            .addOptions(
                SelectOption.of("Starter (32 SCU)", "32"),
                SelectOption.of("Small Hauler (64 SCU)", "64"),
                SelectOption.of("Medium Hauler (96 SCU)", "96"),
                SelectOption.of("Large Hauler (174 SCU)", "174"),
                SelectOption.of("Heavy Cargo (300 SCU)", "300"))
            .setRequiredRange(1, 1)
            .build();
    return ActionRow.of(menu);
  }

  /**
   * Trade mode selector (top routes vs specific commodity).
   */
  public static ActionRow tradeModeMenu() {
    return ActionRow.of(
        StringSelectMenu.create(SELECT_TRADE_MODE)
            .setPlaceholder("Choose trade mode")
            .addOptions(
                SelectOption.of("Top routes", "top"),
                SelectOption.of("Specific commodity", "commodity"))
            .setRequiredRange(1, 1)
            .build());
  }

  /**
   * Number of routes to show for top-mode trade.
   */
  public static ActionRow tradeCountMenu() {
    return ActionRow.of(
        StringSelectMenu.create(SELECT_TRADE_COUNT)
            .setPlaceholder("Choose number of routes")
            .addOptions(
                SelectOption.of("Top 3", "3"),
                SelectOption.of("Top 5", "5"),
                SelectOption.of("Top 8", "8"),
                SelectOption.of("Top 10", "10"))
            .setRequiredRange(1, 1)
            .build());
  }

  /**
   * Refinery presets as ore+batch selector.
   */
  public static ActionRow refineryProfileMenu() {
    StringSelectMenu menu =
        StringSelectMenu.create(SELECT_REFINERY_PROFILE)
            .setPlaceholder("Choose refinery batch")
            .addOptions(
                SelectOption.of("Quantainium (32 SCU)", "Quantainium|32"),
                SelectOption.of("Quantainium (64 SCU)", "Quantainium|64"),
                SelectOption.of("Laranite (32 SCU)", "Laranite|32"),
                SelectOption.of("Agricium (32 SCU)", "Agricium|32"),
                SelectOption.of("Bexalite (32 SCU)", "Bexalite|32"))
            .setRequiredRange(1, 1)
            .build();
    return ActionRow.of(menu);
  }

  /**
   * Refinery batch SCU selector.
   */
  public static ActionRow refineryScuMenu() {
    return ActionRow.of(
        StringSelectMenu.create(SELECT_REFINERY_SCU)
            .setPlaceholder("Choose raw SCU batch size")
            .addOptions(
                SelectOption.of("16 SCU", "16"),
                SelectOption.of("32 SCU", "32"),
                SelectOption.of("64 SCU", "64"),
                SelectOption.of("96 SCU", "96"))
            .setRequiredRange(1, 1)
            .build());
  }

  /**
   * Refinery output view mode selector.
   */
  public static ActionRow refineryViewMenu() {
    return ActionRow.of(
        StringSelectMenu.create(SELECT_REFINERY_VIEW)
            .setPlaceholder("Choose output view")
            .addOptions(
                SelectOption.of("Brief recommendation", "brief"),
                SelectOption.of("Full method breakdown", "full"))
            .setRequiredRange(1, 1)
            .build());
  }

  /**
   * Component category breakdown shown before component name selection.
   */
  public static ActionRow componentCategoryMenu() {
    return ActionRow.of(
        StringSelectMenu.create(SELECT_COMPONENT_CATEGORY)
            .setPlaceholder("Components: choose a category")
            .addOptions(
                SelectOption.of("All Components", "all"),
                SelectOption.of("Shields", "shields"),
                SelectOption.of("Power Plants", "power"),
                SelectOption.of("Coolers", "coolers"),
                SelectOption.of("Quantum Drives", "quantum"),
                SelectOption.of("Engines / Thrusters", "engines"),
                SelectOption.of("Weapons", "weapons"),
                SelectOption.of("Missiles", "missiles"),
                SelectOption.of("Utility / Other", "utility"))
            .setRequiredRange(1, 1)
            .build());
  }

  // ------------------------------------------------------------------
  // Modal builders
  // ------------------------------------------------------------------

  /**
   * Mining analysis modal — up to 5 text inputs (Discord maximum).
   */
  public static Modal miningModal() {
    return Modal.create(MODAL_MINING, "⛏️ Mining Analysis")
        .addComponents(
            ActionRow.of(
                TextInput.create("rock", "Rock Type", TextInputStyle.SHORT)
                    .setPlaceholder("e.g. Quantainium, Bexalite, Agricium")
                    .setRequired(true)
                    .build()),
            ActionRow.of(
                TextInput.create("ship", "Mining Ship", TextInputStyle.SHORT)
                    .setPlaceholder("e.g. Prospector, MOLE")
                    .setRequired(true)
                    .build()),
            ActionRow.of(
                TextInput.create("laser", "Mining Laser", TextInputStyle.SHORT)
                    .setPlaceholder("e.g. Helix II, Hofstede S2")
                    .setRequired(true)
                    .build()),
            ActionRow.of(
                TextInput.create("consumable", "Consumable", TextInputStyle.SHORT)
                    .setPlaceholder("e.g. Surge, Stampede, None")
                    .setRequired(true)
                    .build()),
            ActionRow.of(
                TextInput.create("operators", "Number of Lasers", TextInputStyle.SHORT)
                    .setPlaceholder("e.g. 1")
                    .setRequired(true)
                    .build()))
        .build();
  }

  /**
   * Commodity lookup modal.
   */
  public static Modal commodityModal() {
    return Modal.create(MODAL_COMMODITY, "💰 Commodity Lookup")
        .addComponents(
            ActionRow.of(
                TextInput.create("commodity_name", "Commodity Name", TextInputStyle.SHORT)
                    .setPlaceholder("e.g. Quantainium, Agricium, Laranite")
                    .setRequired(true)
                    .build()))
        .build();
  }

  /**
   * Ship lookup modal.
   */
  public static Modal shipModal() {
    return Modal.create(MODAL_SHIP, "🚀 Ship Lookup")
        .addComponents(
            ActionRow.of(
                TextInput.create("ship_name", "Ship Name", TextInputStyle.SHORT)
                    .setPlaceholder("e.g. Prospector, Carrack, Hornet F7C")
                    .setRequired(true)
                    .build()))
        .build();
  }

  /**
   * Weapon lookup modal.
   */
  public static Modal weaponModal() {
    return Modal.create(MODAL_WEAPON, "🔫 Weapon Lookup")
        .addComponents(
            ActionRow.of(
                TextInput.create("weapon_name", "Weapon Name", TextInputStyle.SHORT)
                    .setPlaceholder("e.g. CF-117 Badger, M7A Laser Cannon")
                    .setRequired(true)
                    .build()))
        .build();
  }

  /**
   * Trade route modal.
   */
  public static Modal tradeModal() {
    return Modal.create(MODAL_TRADE, "📦 Trade Routes")
        .addComponents(
            ActionRow.of(
                TextInput.create("cargo_scu", "Cargo Capacity (SCU)", TextInputStyle.SHORT)
                    .setPlaceholder("e.g. 96")
                    .setRequired(true)
                    .build()),
            ActionRow.of(
                TextInput.create(
                        "commodity_filter", "Commodity Filter (optional)", TextInputStyle.SHORT)
                    .setPlaceholder("Leave blank for top routes")
                    .setRequired(false)
                    .build()))
        .build();
  }

  /**
   * Refinery analysis modal — ore name and SCU amount.
   */
  public static Modal refineryModal() {
    return Modal.create(MODAL_REFINERY, "🏭 Refinery Analysis")
        .addComponents(
            ActionRow.of(
                TextInput.create("ore", "Ore Name", TextInputStyle.SHORT)
                    .setPlaceholder("e.g. Quantainium, Laranite, Bexalite")
                    .setRequired(true)
                    .build()),
            ActionRow.of(
                TextInput.create("raw_scu", "Raw SCU to Refine", TextInputStyle.SHORT)
                    .setPlaceholder("e.g. 32")
                    .setRequired(true)
                    .build()))
        .build();
  }
}
