package dev.aura.practise.mode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import dev.aura.practise.PractiseAuraPlugin;

/** 模式注册表：新模式在这里 register 一行 */
public final class ModeRegistry {

    private static final Map<String, ModeHandler> MODES = new LinkedHashMap<>();

    static {
        register(new BedFightMode());
        register(new FireballFightMode());
    }

    private ModeRegistry() {
    }

    public static void register(ModeHandler mode) {
        MODES.put(mode.id().toLowerCase(Locale.ROOT), mode);
    }

    /** 只读视图：调用方可遍历，不可改动注册表 */
    public static Collection<ModeHandler> all() {
        return Collections.unmodifiableCollection(MODES.values());
    }

    public static ModeHandler get(String id) {
        return id == null ? null : MODES.get(id.toLowerCase(Locale.ROOT));
    }

    /** 按 id / 显示名 / 常见别名解析，失败返回 null */
    public static ModeHandler parse(String input) {
        if (input == null) return null;
        String s = input.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        for (ModeHandler mode : MODES.values()) {
            if (mode.id().toLowerCase(Locale.ROOT).equals(s)
                    || mode.display().toLowerCase(Locale.ROOT).replace(" ", "").equals(s)) {
                return mode;
            }
        }
        // 常用别名
        if (s.equals("bed") || s.equals("bedwars") || s.equals("bw") || s.equals("bf")) return get("bedfight");
        if (s.equals("fireball") || s.equals("fbf") || s.equals("ff")) return get("fireballfight");
        return null;
    }

    public static String ids() {
        return String.join("/", new ArrayList<>(MODES.keySet()));
    }

    /** 用 config.yml 的 modes.<id> 段刷新全部模式的开关（启动与 /pa mode 修改后调用） */
    public static void refresh(PractiseAuraPlugin plugin) {
        for (ModeHandler mode : MODES.values()) {
            mode.settings().refresh(plugin, mode.id());
        }
    }
}
