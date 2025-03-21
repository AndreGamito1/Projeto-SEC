package com.depchain.utils;

/**
 * A logger that can be toggled on/off per level
 */
public class Logger {
    // ANSI color codes
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";

    // Log levels
    public static final int STUBBORN_LINKS = 0;          // Layer 0
    public static final int AUTH_LINKS = 1;              // Layer 1
    public static final int LEADER_ERRORS = 2;           // Layer 2
    public static final int CLIENT_LIBRARY = 3;          // Layer 3
    public static final int MEMBER = 4;                  // Layer 4
    public static final int CONDITIONAL_COLLECT = 5;     // Layer 5
    public static final int EPOCH_CONSENSUS = 6;         // Layer 6

    private static boolean[] enabledLevels = new boolean[]{true, true, true, true, true, true, true};

    /**
     * Initialize logger from command line arguments.
     * Expected format: --log=0,1,2 or --log=none or --log=all
     * 
     * @param args Command line arguments
     */
    public static void initFromArgs(String logArg) {
        disableAll();

        if (logArg.startsWith("--log=")) {
            String logValue = logArg.substring(6).toLowerCase();

            if (logValue.equals("all")) {
                enableAll();
            } else if (logValue.equals("none")) {
                disableAll();
            } else {
                String[] levels = logValue.split(",");
                for (String levelStr : levels) {
                    try {
                        int level = Integer.parseInt(levelStr.trim());
                        enableLevel(level);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid log level: " + levelStr);
                    }
                }
            }
        }
    }

    // Enable a specific level
    public static void enableLevel(int level) {
        if (level >= 0 && level < enabledLevels.length) {
            enabledLevels[level] = true;
        }
    }

    // Disable a specific level
    public static void disableLevel(int level) {
        if (level >= 0 && level < enabledLevels.length) {
            enabledLevels[level] = false;
        }
    }

    // Enable all levels
    public static void enableAll() {
        for (int i = 0; i < enabledLevels.length; i++) {
            enabledLevels[i] = true;
        }
    }

    // Disable all levels
    public static void disableAll() {
        for (int i = 0; i < enabledLevels.length; i++) {
            enabledLevels[i] = false;
        }
    }

    // Log a message if the specified level is enabled
    public static void log(int level, String message) {
        if (level >= 0 && level < enabledLevels.length && enabledLevels[level]) {
            String prefix = "";
            String color = RESET;
            switch (level) {
                case STUBBORN_LINKS:
                    prefix = "[STUBBORN] ";
                    color = RED;
                    break;
                case AUTH_LINKS:
                    prefix = "[AUTH] ";
                    color = GREEN;
                    break;
                case LEADER_ERRORS:
                    prefix = "[LEADER] ";
                    color = YELLOW;
                    break;
                case CLIENT_LIBRARY:
                    prefix = "[ClientLibrary] ";
                    color = PURPLE;
                    break;
                case MEMBER:
                    prefix = "[Member] ";
                    color = BLUE;
                    break;
                case CONDITIONAL_COLLECT:
                    prefix = "[ConditionalCollector] ";
                    color = CYAN;
                    break;
                case EPOCH_CONSENSUS:
                    prefix = "[EpochConsensus] ";
                    color = WHITE;
                    break;
            }
            System.out.println(color + prefix + message + RESET);
        }
    }
}