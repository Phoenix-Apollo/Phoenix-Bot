package Botcode.StarCitizen;

import java.util.Map;

/**
 * Computes mining viability estimates from rock, ship, laser, and consumable inputs.
 *
 * Values in this module are currently heuristic placeholders that can be replaced
 * with live balancing data as the mining dataset matures.
 */
public class MiningService {

    // ---------------------------------------------------------
    // STATIC MAPS (CONSUMABLES, SHIP BONUSES, ROCK TYPES)
    // ---------------------------------------------------------

    public static final Map<String, Consumable> CONSUMABLES = Map.of(
            "surge", new Consumable(0.50, 0.0, 0.0),
            "brandt", new Consumable(0.0, -0.20, 0.0),
            "stampede", new Consumable(0.0, 0.0, 0.30),
            "none", new Consumable(0.0, 0.0, 0.0)
    );

    public static final Map<String, ShipMiningProfile> SHIP_MINING = Map.of(
            "Prospector", new ShipMiningProfile(0.0, 0.0),
            "Mole", new ShipMiningProfile(0.10, -0.10),
            "Orion", new ShipMiningProfile(0.25, -0.20)
    );

    public static final Map<String, RockProfile> ROCK_TYPES = Map.of(
            "Quantanium", new RockProfile(0.85, 1.20),
            "Beryl", new RockProfile(0.20, 0.80),
            "Laranite", new RockProfile(0.60, 1.10)
    );

    // ---------------------------------------------------------
    // LASER PROFILES (HOFSTEDE ONLY)
    // ---------------------------------------------------------

    public static final Map<String, LaserProfile> LASERS = Map.of(
            "Hofstede", new LaserProfile(6000, 1.0, 0.05)
    );

    // ---------------------------------------------------------
    // DATA CLASSES
    // ---------------------------------------------------------

    public record Consumable(double powerBonus,
                             double instabilityBonus,
                             double chargeRateBonus) {}

    public record ShipMiningProfile(double powerBonus,
                                    double stabilityBonus) {}

    public record RockProfile(double instabilityMultiplier,
                              double resistanceMultiplier) {}

    public record LaserProfile(double basePower,
                               double chargeRate,
                               double fluctuation) {}

    // ---------------------------------------------------------
    // MINING RESULT (FINAL FIELDS)
    // ---------------------------------------------------------

    public record MiningResult(
            double rawResistance,
            double rawInstability,
            double rawMass,
            double shipBonus,
            double consumableBonus,
            double multiLaserBonus,
            double effectivePower,
            double requiredPower,
            double chargeRate,
            double fluctuation,
            double breakChance,
            boolean viable
    ) {}

    // ---------------------------------------------------------
    // CALCULATION METHODS (YOUR ORIGINALS)
    // ---------------------------------------------------------

    /**
     * Computes effective laser power after ship, consumable, and multi-laser modifiers.
     */
    public static double calculateEffectivePower(double basePower,
                                                 double shipBonus,
                                                 double consumableBonus,
                                                 double multiLaserBonus) {
        return basePower * (1 + shipBonus + consumableBonus + multiLaserBonus);
    }

    /**
     * Computes resistance threshold required to start and maintain fracture progress.
     */
    public static double calculateRequiredPower(double resistance, double instability) {
        return resistance * (1.0 + (instability * 0.25));
    }

    /**
     * Estimates net charging speed for the rock given current power and mass.
     */
    public static double calculateChargeRate(double effectivePower,
                                             double resistance,
                                             double rockMass) {
        double netPower = effectivePower - resistance;
        return netPower / (rockMass * 0.75);
    }

    /**
     * Converts instability into a simplified fluctuation estimate.
     */
    public static double calculateFluctuation(double instability) {
        return instability * 0.85;
    }

    /**
     * Estimates fracture failure probability from instability and operator mistakes.
     */
    public static double calculateBreakChance(double instability,
                                              double overcharge,
                                              double timeInRed) {
        return (instability * 0.4) + (overcharge * 0.35) + (timeInRed * 0.25);
    }

    /**
     * Returns whether the current setup can realistically crack the selected rock.
     */
    public static boolean isRockViable(double requiredPower,
                                       double effectivePower,
                                       double instability) {
        if (effectivePower < requiredPower) return false;
        return instability < 0.75;
    }

    // ---------------------------------------------------------
    // MAIN ENTRY POINT: analyzeRock()
    // ---------------------------------------------------------

    /**
     * Runs the full mining analysis pipeline and returns a structured result.
     */
    public static MiningResult analyzeRock(
            String rockName,
            String shipName,
            String laserName,
            String consumableName,
            int operators
    ) {

        // Fallback profiles keep the command resilient to unknown user inputs.
        RockProfile rock = ROCK_TYPES.getOrDefault(rockName, new RockProfile(0.50, 1.00));
        ShipMiningProfile ship = SHIP_MINING.getOrDefault(shipName, new ShipMiningProfile(0.0, 0.0));
        Consumable consumable = CONSUMABLES.getOrDefault(consumableName, new Consumable(0.0, 0.0, 0.0));
        LaserProfile laser = LASERS.getOrDefault(laserName, LASERS.get("Hofstede"));

        double rawResistance = rock.resistanceMultiplier();
        double rawInstability = rock.instabilityMultiplier();
        double rawMass = 5000; // placeholder

        double shipBonus = ship.powerBonus();
        double consumableBonus = consumable.powerBonus();
        double multiLaserBonus = (operators - 1) * 0.10;

        double effectivePower = calculateEffectivePower(
                laser.basePower(),
                shipBonus,
                consumableBonus,
                multiLaserBonus
        );

        double requiredPower = calculateRequiredPower(rawResistance, rawInstability);

        double chargeRate = calculateChargeRate(effectivePower, rawResistance, rawMass);

        double fluctuation = calculateFluctuation(rawInstability);

        double breakChance = calculateBreakChance(rawInstability, 0.0, 0.0);

        boolean viable = isRockViable(requiredPower, effectivePower, rawInstability);

        return new MiningResult(
                rawResistance,
                rawInstability,
                rawMass,
                shipBonus,
                consumableBonus,
                multiLaserBonus,
                effectivePower,
                requiredPower,
                chargeRate,
                fluctuation,
                breakChance,
                viable
        );
    }

    // ---------------------------------------------------------
    // FORMATTER (BRIEF / FULL / SPECIFIC)
    // ---------------------------------------------------------

    public static class MiningResultFormatter {

        // Quick summary intended for the default slash-command response.
        /**
         * Formats a short summary for standard command replies.
         */
        public static String brief(MiningResult r) {
            return String.format(
                    "**Mining Summary**\n" +
                            "• Viable: %s\n" +
                            "• Effective Power: %.2f\n" +
                            "• Required Power: %.2f\n" +
                            "• Instability: %.2f\n" +
                            "• Break Chance: %.2f%%",
                    r.viable() ? "Yes" : "No",
                    r.effectivePower(),
                    r.requiredPower(),
                    r.rawInstability(),
                    r.breakChance() * 100
            );
        }

        /**
         * Formats a full detailed breakdown of all computed values.
         */
        public static String full(MiningResult r) {
            return String.format(
                    "**Mining Analysis (Full)**\n\n" +
                            "**Rock Stats**\n" +
                            "• Resistance: %.2f\n" +
                            "• Instability: %.2f\n" +
                            "• Mass: %.2f\n\n" +
                            "**Modifiers**\n" +
                            "• Ship Bonus: %.2f\n" +
                            "• Consumable Bonus: %.2f\n" +
                            "• Multi-Laser Bonus: %.2f\n\n" +
                            "**Computed Values**\n" +
                            "• Effective Power: %.2f\n" +
                            "• Required Power: %.2f\n" +
                            "• Charge Rate: %.4f\n" +
                            "• Fluctuation: %.4f\n" +
                            "• Break Chance: %.2f%%\n\n" +
                            "**Viable:** %s",
                    r.rawResistance(),
                    r.rawInstability(),
                    r.rawMass(),
                    r.shipBonus(),
                    r.consumableBonus(),
                    r.multiLaserBonus(),
                    r.effectivePower(),
                    r.requiredPower(),
                    r.chargeRate(),
                    r.fluctuation(),
                    r.breakChance() * 100,
                    r.viable() ? "Yes" : "No"
            );
        }

        /**
         * Formats one requested field for targeted lookups.
         */
        public static String specific(MiningResult r, String field) {
            field = field.toLowerCase();

            return switch (field) {
                case "resistance", "rawresistance" -> "Resistance: " + r.rawResistance();
                case "instability", "rawinstability" -> "Instability: " + r.rawInstability();
                case "mass", "rawmass" -> "Mass: " + r.rawMass();
                case "shipbonus" -> "Ship Bonus: " + r.shipBonus();
                case "consumablebonus" -> "Consumable Bonus: " + r.consumableBonus();
                case "multilaserbonus" -> "Multi-Laser Bonus: " + r.multiLaserBonus();
                case "effectivepower" -> "Effective Power: " + r.effectivePower();
                case "requiredpower" -> "Required Power: " + r.requiredPower();
                case "chargerate" -> "Charge Rate: " + r.chargeRate();
                case "fluctuation" -> "Fluctuation: " + r.fluctuation();
                case "breakchance" -> String.format("Break Chance: %.2f%%", r.breakChance() * 100);
                case "viable" -> "Viable: " + (r.viable() ? "Yes" : "No");
                default -> "Unknown field: " + field;
            };
        }
    }
}
