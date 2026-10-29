package com.hub.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 可选的头衔插件桥接：把播报文本里的在线玩家名替换成「头衔 + 名字」。
 * <p>
 * 通过 Bukkit ServicesManager 按类名查找 {@code com.example.playertitles.api.TitleProvider}，
 * 全程反射调用，所以 MiniGameLib 不依赖头衔插件、没装时所有方法原样返回。
 * 头衔组件自带头尾括号、颜色与悬浮描述，玩家名保留播报原有的颜色。
 */
public final class Titles {

    private static final String PROVIDER_CLASS = "com.example.playertitles.api.TitleProvider";

    private record Hook(Class<?> type, Method method) {
    }

    private static volatile Hook hook;

    private Titles() {
    }

    /** 给消息中出现的在线玩家名加上头衔；没有头衔插件或没人有头衔时原样返回。 */
    public static Component decorate(Component message) {
        if (message == null) {
            return null;
        }
        Hook found = hook();
        if (found == null) {
            return message;
        }

        Map<String, UUID> ids = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            ids.put(player.getName(), player.getUniqueId());
        }
        if (ids.isEmpty()) {
            return message;
        }

        // 长名字优先，避免 Bob 抢在 Bobby 前面匹配。
        Pattern pattern = patternFor(ids.keySet());

        Map<String, Component> cache = new HashMap<>();
        boolean[] titled = {false};
        Component decorated = replace(message, pattern, name -> {
            UUID id = ids.get(name);
            return id == null ? null : titleFor(found, id);
        }, cache, titled);
        return titled[0] ? decorated : message;
    }

    /** 按已给定的「玩家名 → 头衔组件」装饰消息（便于脱离服务器验证替换逻辑）。 */
    static Component decorate(Component message, Map<String, Component> titles) {
        if (message == null || titles == null || titles.isEmpty()) {
            return message;
        }
        boolean[] titled = {false};
        Component decorated = replace(message, patternFor(titles.keySet()), titles::get, new HashMap<>(), titled);
        return titled[0] ? decorated : message;
    }

    private static Pattern patternFor(Collection<String> names) {
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder regex = new StringBuilder("(?<![A-Za-z0-9_])(");
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                regex.append('|');
            }
            regex.append(Pattern.quote(sorted.get(i)));
        }
        regex.append(")(?![A-Za-z0-9_])");
        return Pattern.compile(regex.toString());
    }

    private static Component replace(Component component, Pattern pattern,
                                     Function<String, Component> resolver,
                                     Map<String, Component> cache, boolean[] titled) {
        List<Component> children = new ArrayList<>();
        for (Component child : component.children()) {
            children.add(replace(child, pattern, resolver, cache, titled));
        }

        if (component instanceof TextComponent text) {
            String content = text.content();
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                List<Component> parts = new ArrayList<>();
                int last = 0;
                do {
                    if (matcher.start() > last) {
                        parts.add(Component.text(content.substring(last, matcher.start())));
                    }
                    String name = matcher.group();
                    Component title = cache.get(name);
                    if (!cache.containsKey(name)) {
                        title = resolver.apply(name);
                        cache.put(name, title);
                    }
                    if (title != null) {
                        titled[0] = true;
                        parts.add(title);
                        if (!endsWithSpace(title)) {
                            parts.add(Component.text(" "));
                        }
                    }
                    parts.add(Component.text(name));
                    last = matcher.end();
                } while (matcher.find());
                if (last < content.length()) {
                    parts.add(Component.text(content.substring(last)));
                }

                // 保留原组件的样式（颜色/加粗）与事件，只把正文拆成多段。
                Component base = text.content("").children(List.of());
                for (Component part : parts) {
                    base = base.append(part);
                }
                for (Component child : children) {
                    base = base.append(child);
                }
                return base;
            }
        }
        return children.isEmpty() ? component : component.children(children);
    }

    private static Component titleFor(Hook found, UUID id) {
        Object provider = Bukkit.getServicesManager().load(found.type());
        if (provider == null) {
            return null;
        }
        try {
            return (Component) found.method().invoke(provider, id);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    /** 头衔组件已经带尾随空格时不再补，避免出现两个空格。 */
    private static boolean endsWithSpace(Component component) {
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        return plain.isEmpty() || plain.endsWith(" ");
    }

    /** 缓存头衔插件的服务接口；插件尚未加载时每次重新查找。 */
    private static Hook hook() {
        Hook current = hook;
        if (current != null) {
            return current;
        }
        for (Class<?> type : Bukkit.getServicesManager().getKnownServices()) {
            if (!PROVIDER_CLASS.equals(type.getName())) {
                continue;
            }
            try {
                Hook discovered = new Hook(type, type.getMethod("getTitleComponent", UUID.class));
                hook = discovered;
                return discovered;
            } catch (NoSuchMethodException ignored) {
                // 继续找：可能是别的插件注册了同名服务类。
            }
        }
        return null;
    }
}
