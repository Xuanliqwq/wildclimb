package com.xuanli.wildclimb.compat;

import com.xuanli.wildclimb.Wildclimb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Keeps Paraglider recovery settings and Wild Climb stamina ownership in sync. */
public final class ParagliderStateConfig {
    private static final String FILE_NAME = "paraglider-player-states.toml";
    private static final String[] REGEN_STATES = {"paraglider:running", "paraglider:midair"};
    private static final String CLIMBING_STATE = "wildclimb:climbing";
    private static final String CLIMBING_HOLD_STATE = "wildclimb:climbing_hold";
    private static final String REGEN_DELTA = "20.0";
    private static final String CLIMB_RECOVERY_DELAY = "10";

    private ParagliderStateConfig() {}

    public static boolean apply(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            Wildclimb.LOGGER.info("[wildclimb] Waiting for Paraglider to generate {}", FILE_NAME);
            return false;
        }

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            boolean changed = false;
            boolean inRegen = false;
            boolean inClimbing = false;
            boolean inClimbingHold = false;
            int matchedRegen = 0;
            int matchedOwn = 0;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.trim();

                if (trimmed.startsWith("[")) {
                    inRegen = false;
                    inClimbing = false;
                    inClimbingHold = false;
                    if (matchesAny(trimmed, REGEN_STATES)) {
                        inRegen = true;
                        matchedRegen++;
                    } else if (matches(trimmed, CLIMBING_STATE)) {
                        inClimbing = true;
                        matchedOwn++;
                    } else if (matches(trimmed, CLIMBING_HOLD_STATE)) {
                        inClimbingHold = true;
                        matchedOwn++;
                    }
                    continue;
                }

                if (inRegen) {
                    if (trimmed.startsWith("staminaDelta")) {
                        changed |= replaceValue(lines, i, REGEN_DELTA);
                    } else if (trimmed.startsWith("recoveryDelay")) {
                        changed |= replaceValue(lines, i, "0");
                    }
                } else if (inClimbing) {
                    if (trimmed.startsWith("staminaDelta")) {
                        changed |= replaceValue(lines, i, "0.0");
                    } else if (trimmed.startsWith("recoveryDelay")) {
                        changed |= replaceValue(lines, i, CLIMB_RECOVERY_DELAY);
                    }
                } else if (inClimbingHold) {
                    if (trimmed.startsWith("staminaDelta")) {
                        changed |= replaceValue(lines, i, "0.0");
                    } else if (trimmed.startsWith("recoveryDelay")) {
                        changed |= replaceValue(lines, i, "0");
                    }
                }
            }

            if (changed) {
                Files.write(file, lines, StandardCharsets.UTF_8);
                Wildclimb.LOGGER.info(
                        "[wildclimb] Updated {} ({} recovery states and {} climbing states)",
                        FILE_NAME, matchedRegen, matchedOwn);
            } else if (matchedRegen == 0 && matchedOwn == 0) {
                Wildclimb.LOGGER.warn("[wildclimb] No matching movement states found in {}", FILE_NAME);
            }
            return changed;
        } catch (IOException e) {
            Wildclimb.LOGGER.warn("[wildclimb] Could not update {}", FILE_NAME, e);
            return false;
        }
    }

    private static boolean matchesAny(String header, String[] ids) {
        for (String id : ids) {
            if (matches(header, id)) return true;
        }
        return false;
    }

    private static boolean matches(String header, String id) {
        int colon = id.indexOf(':');
        String namespace = colon >= 0 ? id.substring(0, colon) : id;
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        String dotted = ("[" + namespace + "." + path + "]").replace(" ", "");
        String normalized = header.replace(" ", "");
        if (normalized.equalsIgnoreCase(dotted)) return true;
        // Also accept a quoted full ID inside the namespace table.
        return normalized.contains(id);
    }

    private static boolean replaceValue(List<String> lines, int index, String value) {
        String line = lines.get(index);
        int equals = line.indexOf('=');
        if (equals < 0) return false;
        String replaced = line.substring(0, equals + 1) + " " + value;
        if (replaced.equals(line)) return false;
        lines.set(index, replaced);
        return true;
    }
}
