package Botcode.Utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;

/**
 * Builds the help embed displayed by both {@code @bot help} and {@code /help}.
 *
 * <p>All commands are documented here in one place — update this file whenever a new command is
 * added to the bot.
 */
public class HelpBuilder {

  /**
   * Phoenix orange — matches the server brand.
   */
  private static final Color EMBED_COLOR = new Color(0xFF6B00);

  /**
   * Builds and returns the full help embed.
   *
   * @param isAdmin {@code true} if the requesting user is a server admin — shows the admin-only
   *                section when {@code true}.
   */
  public static MessageEmbed build(boolean isAdmin) {
    EmbedBuilder embed =
        new EmbedBuilder()
            .setTitle("🛸  Phoenix Bot — Command Reference")
            .setColor(EMBED_COLOR)
            .setFooter("Phoenix Operations Division");

    // ----------------------------------------------------------------
    // Slash Commands — Star Citizen Tools
    // ----------------------------------------------------------------
    embed.addField(
        "📊  Star Citizen Tools  *(slash commands)*",
        "`/help`\n"
            + "› Show this command reference.\n\n"
            + "`/panel`\n"
            + "› Post the interactive operations console with button-driven tools.\n\n"
            + "`/commodity <name>`\n"
            + "› Look up buy/sell prices and best locations for a commodity.\n\n"
            + "`/trade <mode> <cargo> [commodity] [count]`\n"
            + "› Find profitable trade routes.\n"
            + "  • `mode` — `top` or `commodity`\n"
            + "  • `cargo` — your ship's cargo capacity in SCU\n"
            + "  • `commodity` — required when mode is `commodity`\n"
            + "  • `count` — number of routes to return (default: 5)\n\n"
            + "`/mine <rock> <ship> <laser> <consumable> <operators> <format> [field]`\n"
            + "› Analyse a rock's viability for mining.\n"
            + "  • `format` — `brief`, `full`, or `specific`\n"
            + "  • `field` — stat name when using `specific` format\n\n"
            + "`/ship <name>`\n"
            + "› Get full specifications for a ship.\n\n"
            + "`/weapon <name>`\n"
            + "› Get DPS and stats for a weapon.\n\n"
            + "`/reportmissing <dataset> <item> [notes]`\n"
            + "› Log missing data and run a live refresh check immediately.\n"
            + "  • Supported dataset aliases: `commodity` `trade` `mining` `ship` `weapon`\n"
            + "    `missile_rack` `component` `refinery` `salvage` `location`\n"
            + "  • Unresolved items are written to `data/manual_update_queue.md` for manual updates.",
        false);

    // ----------------------------------------------------------------
    // @mention Commands — General
    // ----------------------------------------------------------------
    embed.addField(
        "💬  Chat Commands  *(@ mention the bot)*",
        "`@bot help`\n"
            + "› Show this command reference.\n\n"
            + "`@bot phrases <intent>`\n"
            + "› List community-taught phrases for an intent.\n"
            + "  Valid intents: `greeting` `farewell` `how_are_you` `question`\n"
            + "  `complaint` `praise` `hype` `confusion`\n\n"
            + "`@bot learnme <intent>: <phrase>` / `@bot forgetme <intent>: <phrase>`\n"
            + "`@bot learnme <intent> = <phrase>` / `@bot forgetme <intent> = <phrase>`\n"
            + "› Teach or remove *your personal* reply style for an intent.\n\n"
            + "`@bot <term> = <meaning>`\n"
            + "› Teach alias mapping for natural requests (example: `@bot qt = quantum drive`).\n\n"
            + "`@bot myphrases <intent>`\n"
            + "› View your personal learned phrases for that intent.\n\n"
            + "`@bot memory me` / `@bot forget me`\n"
            + "› View or erase your stored conversation memory profile.\n\n"
            + "`@bot missing <dataset>: <item> [| notes]`\n"
            + "› Report missing data; bot logs it and checks live sources right away.\n\n"
            + "`@bot reportmissing <dataset>: <item> [| notes]`\n"
            + "› Alias for `@bot missing`.",
        false);

    // ----------------------------------------------------------------
    // Reaction Voting — everyone
    // ----------------------------------------------------------------
    embed.addField(
        "⭐  Phrase Rating  *(react to any bot message)*",
        "👍 or ✅ — upvote the response *(phrase gains score)*\n"
            + "👎 or ❌ — downvote the response *(phrase loses score; pruned at −2)*",
        false);

    // ----------------------------------------------------------------
    // Admin-only section — only shown to admins
    // ----------------------------------------------------------------
    if (isAdmin) {
      embed.addField(
          "🔧  Admin Commands  *(@ mention the bot · requires Manage Server)*",
          "`@bot allowchannel`\n"
              + "› Allow the bot to respond freely in the current channel (no @mention needed).\n"
              + "  *Note: Bot responds freely in Phoenix Industries category by default.*\n\n"
              + "`@bot denychannel`\n"
              + "› Restrict the bot to @mention-only in the current channel.\n\n"
              + "`@bot channels`\n"
              + "› List all channels where the bot responds freely (including category channels).\n\n"
              + "`@bot addguichannel`\n"
              + "› Enable the interactive GUI panel display in the current channel.\n\n"
              + "`@bot removeguichannel`\n"
              + "› Disable the interactive GUI panel display in the current channel.\n\n"
              + "`@bot guichannels`\n"
              + "› List all channels where the GUI panel is displayed.\n\n"
              + "`@bot learn <intent>: <phrase>` or `@bot learn <intent> = <phrase>`\n"
              + "› Teach the bot a new reply phrase for a given intent.\n"
              + "  Example: `@bot learn greeting: What's up, pilot!`\n\n"
              + "`@bot forget <intent>: <phrase>` or `@bot forget <intent> = <phrase>`\n"
              + "› Remove a previously taught phrase.\n"
              + "  Example: `@bot forget greeting: What's up, pilot!`\n\n"
              + "`@bot learningstats`\n"
              + "› Show autonomous-learning diagnostics (phrase totals, active counts, pending feedback).\n\n"
              + "`@bot source <dataset>: <https-url>`\n"
              + "› Submit a new candidate source URL for dataset review (safe-validated, pending approval).\n\n"
              + "`@bot approvesource <dataset>`\n"
              + "› Approve the latest pending source for a dataset.\n\n"
              + "`@bot sources`\n"
              + "› List approved and pending source overrides.",
          false);
    } else {
      embed.addField("🔧  Admin Commands", "*Hidden — available to server admins only.*", false);
    }

    return embed.build();
  }
}
