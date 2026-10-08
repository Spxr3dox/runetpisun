package com.runetpisun.careelixir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class Card {
    public static final int COST_MIRROR = -1;

    public final String id;
    public final String name;
    public final String shortName;
    public final int cost;
    public final int abilityCost;

    private Card(String name, String shortName, int cost, int abilityCost) {
        this.id = name;
        this.name = name;
        this.shortName = shortName;
        this.cost = cost;
        this.abilityCost = abilityCost;
    }

    public boolean isChampion() { return abilityCost > 0; }
    public boolean isMirror() { return cost == COST_MIRROR; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Card && id.equals(((Card) o).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    private static final List<Card> ALL = new ArrayList<>();

    private static void c(String name, String shortName, int cost) {
        ALL.add(new Card(name, shortName, cost, 0));
    }

    private static void champ(String name, String shortName, int cost, int ability) {
        ALL.add(new Card(name, shortName, cost, ability));
    }

    static {
        c("Skeletons", "Skelly", 1);
        c("Ice Spirit", "IceSp", 1);
        c("Fire Spirit", "FireSp", 1);
        c("Electro Spirit", "ElSp", 1);
        c("Heal Spirit", "HealSp", 1);

        c("Goblins", "Gobs", 2);
        c("Spear Goblins", "SpGob", 2);
        c("Bomber", "Bomber", 2);
        c("Bats", "Bats", 2);
        c("Ice Golem", "IceGol", 2);
        c("Wall Breakers", "WallBr", 2);
        c("Suspicious Bush", "Bush", 2);
        c("Berserker", "Berser", 2);
        c("Zap", "Zap", 2);
        c("The Log", "Log", 2);
        c("Giant Snowball", "Snowb", 2);
        c("Barbarian Barrel", "BarbBl", 2);
        c("Rage", "Rage", 2);
        c("Goblin Curse", "Curse", 2);

        c("Knight", "Knight", 3);
        c("Archers", "Arch", 3);
        c("Minions", "Minion", 3);
        c("Skeleton Army", "Skarmy", 3);
        c("Ice Wizard", "IceWiz", 3);
        c("Guards", "Guards", 3);
        c("Princess", "Prncss", 3);
        c("Miner", "Miner", 3);
        c("Mega Minion", "MegaMn", 3);
        c("Dart Goblin", "Dart", 3);
        c("Goblin Gang", "GobGng", 3);
        c("Bandit", "Bandit", 3);
        c("Royal Ghost", "Ghost", 3);
        c("Skeleton Barrel", "SkBarl", 3);
        c("Fisherman", "Fisher", 3);
        c("Firecracker", "FireCr", 3);
        c("Elixir Golem", "ElxGol", 3);
        champ("Little Prince", "LPrnc", 3, 1);
        c("Cannon", "Cannon", 3);
        c("Tombstone", "Tomb", 3);
        c("Arrows", "Arrows", 3);
        c("Goblin Barrel", "GobBrl", 3);
        c("Tornado", "Tornad", 3);
        c("Clone", "Clone", 3);
        c("Earthquake", "Quake", 3);
        c("Royal Delivery", "RDeliv", 3);
        c("Void", "Void", 3);

        c("Valkyrie", "Valk", 4);
        c("Musketeer", "Musket", 4);
        c("Baby Dragon", "BabyD", 4);
        c("Mini P.E.K.K.A", "MiniPK", 4);
        c("Hog Rider", "Hog", 4);
        c("Dark Prince", "DPrnc", 4);
        c("Lumberjack", "Lumber", 4);
        c("Battle Ram", "Ram", 4);
        c("Inferno Dragon", "InfDr", 4);
        c("Electro Wizard", "EWiz", 4);
        c("Hunter", "Hunter", 4);
        c("Night Witch", "NWitch", 4);
        c("Zappies", "Zappy", 4);
        c("Flying Machine", "FlyMch", 4);
        c("Magic Archer", "MagArc", 4);
        c("Battle Healer", "Healer", 4);
        c("Skeleton Dragons", "SkDrag", 4);
        c("Mother Witch", "MWitch", 4);
        c("Phoenix", "Phoenx", 4);
        c("Goblin Demolisher", "GobDem", 4);
        c("Rune Giant", "RuneG", 4);
        champ("Golden Knight", "GKnght", 4, 1);
        champ("Skeleton King", "SkKing", 4, 2);
        champ("Mighty Miner", "MMiner", 4, 1);
        c("Tesla", "Tesla", 4);
        c("Bomb Tower", "BombT", 4);
        c("Mortar", "Mortar", 4);
        c("Furnace", "Furnce", 4);
        c("Goblin Cage", "GobCag", 4);
        c("Goblin Drill", "Drill", 4);
        c("Fireball", "Fball", 4);
        c("Freeze", "Freeze", 4);
        c("Poison", "Poison", 4);

        c("Giant", "Giant", 5);
        c("Balloon", "Loon", 5);
        c("Witch", "Witch", 5);
        c("Barbarians", "Barbs", 5);
        c("Prince", "Prince", 5);
        c("Wizard", "Wizard", 5);
        c("Minion Horde", "Horde", 5);
        c("Bowler", "Bowler", 5);
        c("Executioner", "Exe", 5);
        c("Ram Rider", "RamRdr", 5);
        c("Rascals", "Rascal", 5);
        c("Cannon Cart", "CCart", 5);
        c("Royal Hogs", "RHogs", 5);
        c("Electro Dragon", "EDrag", 5);
        c("Goblin Machine", "GobMch", 5);
        champ("Archer Queen", "AQueen", 5, 1);
        champ("Monk", "Monk", 5, 1);
        champ("Goblinstein", "Gstein", 5, 2);
        c("Inferno Tower", "InfTwr", 5);
        c("Goblin Hut", "GobHut", 5);
        c("Graveyard", "Grave", 5);

        c("Giant Skeleton", "GSkel", 6);
        c("Royal Giant", "RG", 6);
        c("Sparky", "Sparky", 6);
        c("Elite Barbarians", "eBarbs", 6);
        c("Goblin Giant", "GobGnt", 6);
        champ("Boss Bandit", "BBandt", 6, 1);
        c("X-Bow", "X-Bow", 6);
        c("Barbarian Hut", "BrbHut", 6);
        c("Elixir Collector", "Pump", 6);
        c("Lightning", "Light", 6);
        c("Rocket", "Rocket", 6);

        c("P.E.K.K.A", "PEKKA", 7);
        c("Lava Hound", "Lava", 7);
        c("Mega Knight", "MK", 7);
        c("Royal Recruits", "Recrts", 7);
        c("Electro Giant", "EGiant", 7);

        c("Golem", "Golem", 8);
        c("Three Musketeers", "3M", 9);
        c("Mirror", "Mirror", COST_MIRROR);

        Collections.sort(ALL, new Comparator<Card>() {
            @Override
            public int compare(Card a, Card b) { return a.name.compareToIgnoreCase(b.name); }
        });
    }

    public static List<Card> all() { return Collections.unmodifiableList(ALL); }

    public static Card byId(String id) {
        for (Card c : ALL) if (c.id.equals(id)) return c;
        return null;
    }

    public static List<Card> withCost(int cost) {
        List<Card> out = new ArrayList<>();
        for (Card c : ALL) {
            if (cost == 0 ? c.isMirror() : (cost >= 8 ? c.cost >= 8 : c.cost == cost))
                out.add(c);
        }
        return out;
    }
}
