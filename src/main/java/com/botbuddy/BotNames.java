package com.botbuddy;

import net.minecraft.server.MinecraftServer;
import java.util.Random;
import java.util.Set;

/** Generates unique Indian / gamer style names (3-16 chars, letters, digits, underscore). */
public final class BotNames {
    private static final Random R = new Random();
    private static final String[] INDIAN = {
        "Aarav","Vihaan","Arjun","Rohan","Kabir","Ishaan","Aditya","Rahul","Vikram","Karan","Dev","Yash",
        "Harsh","Raj","Rishi","Sahil","Nikhil","Ankit","Mohit","Deepak","Priya","Ananya","Diya","Kavya",
        "Riya","Neha","Pooja","Simran","Tanvi","Meera","Sanjay","Amit","Varun","Tushar","Gaurav","Pranav",
        "Lakshya","Veer","Zoya","Aman","Ayush","Shivam","Kunal","Naman","Piyush","Rudra","Tejas","Vansh"
    };
    private static final String[] GAMER = {
        "Shadow","Ninja","Sniper","Ghost","Toxic","Savage","Noob","Pro","Legend","Dragon","Phoenix","Reaper",
        "Blaze","Venom","Hunter","Titan","Storm","Rage","Wolf","Cyber","Alpha","Killer","Joker","Rocket",
        "Thunder","King","Boss","Demon","Hacker","Viper","Op","Beast"
    };
    private static final String[] TAGS = {
        "YT","OP","Gamer","Gaming","PvP","Live","FF","Plays","xD","God","Official","Pro","Ji","Bhai"
    };

    private static String pick(String[] a) { return a[R.nextInt(a.length)]; }

    private static String make() {
        String in = pick(INDIAN), g = pick(GAMER), t = pick(TAGS);
        return switch (R.nextInt(8)) {
            case 0 -> in + "_" + R.nextInt(1000);
            case 1 -> in + g;
            case 2 -> g + "_" + in;
            case 3 -> "xX" + in + "Xx";
            case 4 -> in + t;
            case 5 -> "Its" + in + t;
            case 6 -> g + (R.nextInt(9000) + 10);
            default -> in + (R.nextInt(90) + 10) + g;
        };
    }

    public static String next(MinecraftServer server, Set<String> taken) {
        for (int i = 0; i < 200; i++) {
            String n = make();
            if (n.length() >= 3 && n.length() <= 16 && !taken.contains(n)
                    && server.getPlayerManager().getPlayer(n) == null) return n;
        }
        String n;
        do { n = pick(INDIAN) + "_" + (1000 + R.nextInt(9000)); }
        while (taken.contains(n) || server.getPlayerManager().getPlayer(n) != null);
        return n;
    }
}
