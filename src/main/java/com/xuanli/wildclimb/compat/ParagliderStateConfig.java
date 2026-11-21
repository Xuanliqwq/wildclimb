package com.xuanli.wildclimb.compat;

import com.xuanli.wildclimb.Wildclimb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 调整 Paraglider 的玩家状态配置（{@code config/paraglider-player-states.toml}）。
 *
 * <p>做两件事：</p>
 * <ul>
 *     <li>跑步和滞空（普通跳跃）时耐力照常恢复：这两个状态原本会因为恢复延迟或 0 delta 卡住恢复；</li>
 *     <li>本模组的攀爬状态耐力 delta 归零：攀爬耐力由本模组自己的配置统一结算，
 *         避免和 Paraglider 的状态机重复扣。</li>
 * </ul>
 *
 * <p>配置文件里的段落形如 {@code [paraglider.running]}、{@code [wildclimb.climbing]}，
 * 也就是状态 ID 的命名空间和路径拆成两段。</p>
 */
public final class ParagliderStateConfig {
    private static final String FILE_NAME = "paraglider-player-states.toml";
    /** 需要正常恢复耐力的原版状态。 */
    private static final String[] REGEN_STATES = {"paraglider:running", "paraglider:midair"};
    /** 本模组的攀爬状态：delta 归零，耐力由本模组结算；攀爬结束后保留 10 刻恢复延迟。 */
    private static final String CLIMBING_STATE = "wildclimb:climbing";
    /** 挂在墙上不动的状态：delta 归零，也不设置恢复延迟。 */
    private static final String CLIMBING_HOLD_STATE = "wildclimb:climbing_hold";
    /** 恢复速度，与 idle 一致（每刻 20 点）。 */
    private static final String REGEN_DELTA = "20.0";
    /** 攀爬状态归零后的恢复延迟（刻）。 */
    private static final String CLIMB_RECOVERY_DELAY = "10";

    private ParagliderStateConfig() {}

    /**
     * @return 是否真的改动了文件
     */
    public static boolean apply(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            Wildclimb.LOGGER.info("[wildclimb] 还没找到 {}，等 Paraglider 生成后再处理", FILE_NAME);
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
                        "[wildclimb] 已更新 {}（跑步/滞空恢复：{} 段，攀爬状态归零：{} 段）",
                        FILE_NAME, matchedRegen, matchedOwn);
            } else if (matchedRegen == 0 && matchedOwn == 0) {
                Wildclimb.LOGGER.warn("[wildclimb] {} 里没有找到预期的状态段，未做修改", FILE_NAME);
            }
            return changed;
        } catch (IOException e) {
            Wildclimb.LOGGER.warn("[wildclimb] 无法修改 {}", FILE_NAME, e);
            return false;
        }
    }

    /** 段落标题是否对应某个状态 ID。支持 {@code [ns.path]} 与带引号的完整 ID 两种写法。 */
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
        // 兼容 [ns."full:id"] 这种写法
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
