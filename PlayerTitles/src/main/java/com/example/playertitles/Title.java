package com.example.playertitles;

import com.example.playertitles.util.Text;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;

/**
 * 一个头衔定义。{@code platedName} / {@code platedDescription} 是镀层后的变体：
 * 玩家满足镀层条件后，同一个头衔改用这套名称与描述（名称可写 {@code {RRGGBB>RRGGBB}文字} 渐变）。
 */
public record Title(String id, String name, String color, String description, boolean visible,
                    String platedName, String platedDescription) {

    /** 铁砧输入框的硬上限是 50 个字符，称号名称沿用同一个上限。 */
    public static final int MAX_NAME_LENGTH = 50;

    public Title(String id, String name, String color, String description) {
        this(id, name, color, description, true, null, null);
    }

    public Title(String id, String name, String color, String description, boolean visible) {
        this(id, name, color, description, visible, null, null);
    }

    /** 是否配置了镀层变体；没有时镀层不会改变外观。 */
    public boolean hasPlated() {
        return platedName != null && !platedName.isBlank();
    }

    public String displayName(boolean plated) {
        return plated && hasPlated() ? platedName : name;
    }

    public String displayDescription(boolean plated) {
        if (plated && platedDescription != null && !platedDescription.isBlank()) {
            return platedDescription;
        }
        return description;
    }

    public Component nameComponent() {
        return nameComponent(false);
    }

    public Component nameComponent(boolean plated) {
        return Text.titleName(color, displayName(plated));
    }

    public Component bracketedComponent() {
        return bracketedComponent(false);
    }

    public Component bracketedComponent(boolean plated) {
        return bracketed(plated).hoverEvent(HoverEvent.showText(descriptionComponent(plated)));
    }

    public Component bracketPrefix() {
        return bracketPrefix(false);
    }

    public Component bracketPrefix(boolean plated) {
        return bracketed(plated).append(Component.text(" "))
                .hoverEvent(HoverEvent.showText(descriptionComponent(plated)));
    }

    private Component bracketed(boolean plated) {
        return bracket("[").append(nameComponent(plated)).append(bracket("]"));
    }

    private Component bracket(String text) {
        return Text.parse(color + text);
    }

    public Component descriptionComponent() {
        return descriptionComponent(false);
    }

    public Component descriptionComponent(boolean plated) {
        List<String> lines = descriptionLines(plated);
        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result = result.append(Component.newline());
            }
            result = result.append(Text.parse(lines.get(i)));
        }
        return result;
    }

    public List<Component> descriptionLore() {
        return descriptionLore(false);
    }

    public List<Component> descriptionLore(boolean plated) {
        List<Component> lore = new ArrayList<>();
        for (String line : descriptionLines(plated)) {
            lore.add(Text.parse(line));
        }
        return lore;
    }

    public List<String> descriptionLines() {
        return descriptionLines(false);
    }

    public List<String> descriptionLines(boolean plated) {
        String value = displayDescription(plated);
        if (value == null || value.isBlank()) {
            value = "&7暂无描述";
        }
        String[] parts = value.replace("\\n", "\n").split("\n", -1);
        List<String> lines = new ArrayList<>();
        for (String part : parts) {
            lines.add(part.isEmpty() ? " " : part);
        }
        return lines;
    }
}
