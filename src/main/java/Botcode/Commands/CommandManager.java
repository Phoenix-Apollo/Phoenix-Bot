package Botcode.Commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;

import com.fasterxml.jackson.databind.JsonNode;

import Botcode.StarCitizen.StarCitizenDataService;
import Botcode.StarCitizen.MiningService;

import java.util.List;

/**
 * Handles slash-command execution and autocomplete suggestions.
 *
 * Current commands are focused on Star Citizen commodity and mining tools.
 */
public class CommandManager extends ListenerAdapter {

    /**
     * Executes slash command handlers for commodity lookup and mining analysis.
     */
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // Handle commodity lookups from the normalized Star Citizen dataset.
        if (event.getName().equals("commodity")) {

            // Read command input and pull the commodities dataset from cache.
            String name = event.getOption("name").getAsString();
            JsonNode commodities = StarCitizenDataService.get("commodities");

            if (commodities == null || commodities.isEmpty()) {
                event.reply("Commodity data not loaded.").queue();
                return;
            }

            JsonNode entry = commodities.get(name);

            if (entry == null) {
                event.reply("Commodity not found: " + name).queue();
                return;
            }

            String bestBuy = entry.path("best_buy").asText("Unknown");
            String bestSell = entry.path("best_sell").asText("Unknown");
            double profit = entry.path("profit_per_scu").asDouble(0);

            // Build display lists from the JSON arrays used by the reply template.
            StringBuilder buyList = new StringBuilder();
            for (JsonNode b : entry.withArray("buy")) {
                buyList.append("• ")
                        .append(b.path("location").asText())
                        .append(" — ")
                        .append(b.path("price").asDouble())
                        .append("\n");
            }

            StringBuilder sellList = new StringBuilder();
            for (JsonNode s : entry.withArray("sell")) {
                sellList.append("• ")
                        .append(s.path("location").asText())
                        .append(" — ")
                        .append(s.path("price").asDouble())
                        .append("\n");
            }

            // Format a Discord-friendly markdown response.
            String response = "**Commodity: " + name + "**\n\n" +
                    "**Best Buy:** " + bestBuy + "\n" +
                    "**Best Sell:** " + bestSell + "\n" +
                    "**Profit per SCU:** " + profit + "\n\n" +
                    "**Buy Locations:**\n" + buyList + "\n" +
                    "**Sell Locations:**\n" + sellList;

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

            MiningService.MiningResult result = MiningService.analyzeRock(
                    rock,
                    ship,
                    laser,
                    consumable,
                    operators
            );

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
    }

    /**
     * Provides context-aware autocomplete values for mine command options.
     */
    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {

        // Only mining command options currently expose dynamic autocomplete.
        if (!event.getName().equals("mine")) return;

        String focused = event.getFocusedOption().getName();

        // Return the option list that corresponds to the currently focused argument.
        switch (focused) {

            // Suggest valid keys from each mining dataset map.
            case "rock" -> event.replyChoices(
                    MiningService.ROCK_TYPES.keySet().stream()
                            .map(name -> new Choice(name, name))
                            .toList()
            ).queue();

            case "ship" -> event.replyChoices(
                    MiningService.SHIP_MINING.keySet().stream()
                            .map(name -> new Choice(name, name))
                            .toList()
            ).queue();

            case "laser" -> event.replyChoices(
                    MiningService.LASERS.keySet().stream()
                            .map(name -> new Choice(name, name))
                            .toList()
            ).queue();

            case "consumable" -> event.replyChoices(
                    MiningService.CONSUMABLES.keySet().stream()
                            .map(name -> new Choice(name, name))
                            .toList()
            ).queue();

            // Output format and field lists are static enums.
            case "format" -> event.replyChoices(
                    List.of("brief", "full", "specific").stream()
                            .map(name -> new Choice(name, name))
                            .toList()
            ).queue();

            case "field" -> event.replyChoices(
                    List.of(
                            "rawResistance", "rawInstability", "rawMass",
                            "shipBonus", "consumableBonus", "multiLaserBonus",
                            "effectivePower", "requiredPower", "chargeRate",
                            "fluctuation", "breakChance", "viable"
                    ).stream().map(name -> new Choice(name, name)).toList()             ).queue();
        }
    }
}
