package com.liarbar;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 牌库：20 张 = 三种普通牌各 6 张 + 万能牌 2 张。
 * 万能牌在手牌里是下界之星，结算时算作本回合的目标牌。
 */
public final class LiarBarCards {

    /** 普通牌的点数：0=钻石 1=绿宝石 2=金锭。 */
    public static final int RANK_DIAMOND = 0;
    public static final int RANK_EMERALD = 1;
    public static final int RANK_GOLD = 2;
    /** 万能牌。 */
    public static final int WILDCARD = 3;

    public static final int NORMAL_RANKS = 3;
    public static final int COPIES_PER_RANK = 6;
    public static final int WILDCARDS = 2;
    public static final int DECK_SIZE = NORMAL_RANKS * COPIES_PER_RANK + WILDCARDS;

    public static final Material[] MATERIALS = {
            Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.NETHER_STAR
    };
    public static final String[] NAMES = {"钻石", "绿宝石", "金锭", "万能牌"};
    public static final String[] COLORS = {"&b", "&a", "&6", "&5"};

    public record Card(int rank) {

        /** 结算时：万能牌算作目标牌。 */
        public boolean matches(int target) {
            return rank == target || rank == WILDCARD;
        }

        public String display() {
            return COLORS[rank] + NAMES[rank];
        }

        public Material material() {
            return MATERIALS[rank];
        }
    }

    private LiarBarCards() {
    }

    /** 洗好的 20 张牌。 */
    public static List<Card> shuffledDeck() {
        List<Card> deck = new ArrayList<>(DECK_SIZE);
        for (int rank = 0; rank < NORMAL_RANKS; rank++) {
            for (int copy = 0; copy < COPIES_PER_RANK; copy++) {
                deck.add(new Card(rank));
            }
        }
        for (int copy = 0; copy < WILDCARDS; copy++) {
            deck.add(new Card(WILDCARD));
        }
        Collections.shuffle(deck);
        return deck;
    }
}
