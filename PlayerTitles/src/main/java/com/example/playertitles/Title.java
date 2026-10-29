package com.example.playertitles;

import com.example.playertitles.util.Text;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;

public record Title(String id, String name, String color, String description, boolean visible) {

    public Title(String id, String name, String color, String description) {
        this(id, name, color, description, true);
    }

    public Component nameComponent() {
        return Text.parse(color + name);
    }

    public Component bracketedComponent() {
        return bracketed().hoverEvent(HoverEvent.showText(descriptionComponent()));
    }

    public Component bracketPrefix() {
        return bracketed().append(Component.text(" "))
                .hoverEvent(HoverEvent.showText(descriptionComponent()));
    }

    private Component bracketed() {
        return bracket("[").append(nameComponent()).append(bracket("]"));
    }

    private Component bracket(String text) {
        return Text.parse(color + text);
    }

    public Component descriptionComponent() {
        List<String> lines = descriptionLines();
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
        List<Component> lore = new ArrayList<>();
        for (String line : descriptionLines()) {
            lore.add(Text.parse(line));
        }
        return lore;
    }

    public List<String> descriptionLines() {
        String desc = description == null || description.isBlank() ? "&7暂无描述" : description;
        String[] parts = desc.replace("\\n", "\n").split("\n", -1);
        List<String> lines = new ArrayList<>();
        for (String part : parts) {
            lines.add(part.isEmpty() ? " " : part);
        }
        return lines;
    }
}
