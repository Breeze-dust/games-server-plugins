package com.liarbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 牌面模型：6 种点数 × 4 种花色，共 24 张。 */
public final class LiarBarCards {

    public static final String[] RANKS = {"A", "2", "3", "4", "5", "6"};
    public static final String[] SUITS = {"♠", "♥", "♣", "♦"};
    public static final boolean[] RED_SUIT = {false, true, false, true};

    public record Card(int rank, int suit) {

        public String label() {
            return SUITS[suit] + RANKS[rank];
        }

        /** 带颜色的显示文本（红桃/方块红色）。 */
        public String colored() {
            return (RED_SUIT[suit] ? "&c" : "&f") + SUITS[suit] + "&e" + RANKS[rank];
        }

        public String save() {
            return rank + ":" + suit;
        }

        public static Card parse(String raw) {
            if (raw == null) {
                return null;
            }
            String[] parts = raw.split(":");
            if (parts.length != 2) {
                return null;
            }
            try {
                int rank = Integer.parseInt(parts[0]);
                int suit = Integer.parseInt(parts[1]);
                if (rank < 0 || rank >= RANKS.length || suit < 0 || suit >= SUITS.length) {
                    return null;
                }
                return new Card(rank, suit);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private LiarBarCards() {
    }

    /** 一副洗好的 24 张牌。 */
    public static List<Card> shuffledDeck() {
        List<Card> deck = new ArrayList<>(RANKS.length * SUITS.length);
        for (int rank = 0; rank < RANKS.length; rank++) {
            for (int suit = 0; suit < SUITS.length; suit++) {
                deck.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(deck);
        return deck;
    }
}
