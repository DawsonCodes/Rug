package com.dawsoncodes.rug;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Rug extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private static final String VERSION = "0.2.11-nms-alpha12";
    private static final String COPYRIGHT = "\u00A9 2026 DawsonCodes";
    private static final char COLOR = '\u00A7';
    private static final String DISPLAY_NAME = COLOR + "6Rug Engine";
    private static final String PREFIX = COLOR + "8[" + DISPLAY_NAME + COLOR + "8] " + COLOR + "7";
    private static final String GUI_TITLE = COLOR + "6Rug Control";
    private static final String GUI_RULES_TITLE = COLOR + "6Rug Rules";
    private static final String GUI_PLAYERS_TITLE = COLOR + "6Rug Fake Players";
    private static final String GUI_DETAIL_TITLE = COLOR + "6Rug Bot Details";
    private static final String GUI_CLEANUP_TITLE = COLOR + "6Rug Cleanup";
    private static final String GUI_SKIN_TITLE = COLOR + "6Rug Skin Tools";
    private static final long SKIN_CACHE_OK_MS = 60L * 60L * 1000L;
    private static final long SKIN_CACHE_FAIL_MS = 10L * 60L * 1000L;

    private static final String HITBOX_TAG = "rug_hitbox";
    private static final String VISUAL_TAG = "rug_visual";
    private static final String REMOVING_TAG = "rug_removing";
    private static final String NAME_TAG_PREFIX = "rug_name_";
    private static final String SKIN_TAG_PREFIX = "rug_skin_";
    private static final String NMS_TAG = "rug_nms_player";

    // All player sub-actions Rug will parse and run. Aliases stay valid so old
    // muscle memory keeps working, but they are not all advertised in tab
    // completion (see PLAYER_ACTION_SUGGEST for the clean suggestion list).
    private static final List<String> PLAYER_ACTIONS = Arrays.asList(
            "spawn", "remove", "kill", "tp", "tphere", "move", "move_here", "resync", "look", "stop", "status", "skin", "hand", "equip", "refresh", "inventory", "inv"
    );
    // Clean, de-duplicated suggestions only. No deprecated/hidden aliases.
    private static final List<String> RUG_SUGGEST = Arrays.asList(
            "player", "players", "rules", "rule", "gui", "skincheck", "purge", "help", "about"
    );
    private static final List<String> PLAYER_ACTION_SUGGEST = Arrays.asList(
            "spawn", "kill", "remove", "hand", "status", "inventory", "skin"
    );
    private static final List<String> RULES = Arrays.asList(
            "playerBackend", "verboseMessages", "skinLayers", "punchKnockback", "allowDuplicateOnlineNames",
            "broadcastDeaths", "sendQuitMessage", "deathAlertSound", "summonAlertSound",
            "playerVisualEnabled", "playerHeadEnabled", "hitboxEnabled", "hitboxInvisible", "hitboxFireProof"
    );
    // Short, human-readable descriptions shown by /rug rules.
    private static final Map<String, String> RULE_DESCRIPTIONS = buildRuleDescriptions();

    private static Map<String, String> buildRuleDescriptions() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("playerBackend", "auto / nms / visual fake-player backend");
        map.put("verboseMessages", "extra spawn/backend detail in chat + console");
        map.put("skinLayers", "all / none / comma list (cape,jacket,sleeves,pants,hat)");
        map.put("punchKnockback", "how far a punched fake player slides (0 = off)");
        map.put("allowDuplicateOnlineNames", "allow risky duplicate names of real players");
        map.put("broadcastDeaths", "broadcast a fake-player death line");
        map.put("sendQuitMessage", "broadcast a fake-player leave line after death/removal");
        map.put("deathAlertSound", "sound on fake death: none/wither/guardian/dragon/hurt");
        map.put("summonAlertSound", "sound on visual-backend spawn (none by default)");
        map.put("playerVisualEnabled", "visual backend spawns a player-look armor stand");
        map.put("playerHeadEnabled", "visual backend uses a player head for the skin");
        map.put("hitboxEnabled", "visual backend spawns a hittable hidden hitbox");
        map.put("hitboxInvisible", "keep the visual-backend hitbox invisible");
        map.put("hitboxFireProof", "stop the visual-backend hitbox burning in daylight");
        return map;
    }
    // Generic, neutral default fake-player names only. No real or personal names.
    private static final String DEFAULT_NAME = "RugBot";
    private static final List<String> DEFAULT_NAME_SUGGESTIONS = Arrays.asList(
            "RugBot", "TestBot", "BuilderBot", "MinerBot", "RedstoneBot",
            "UtilityBot", "SpawnBot", "DebugBot", "AlphaBot", "BetaBot"
    );

    private final Map<String, FakeHandle> tracked = new LinkedHashMap<>();
    private final Map<String, SkinResult> skinCache = new LinkedHashMap<>();
    private final Map<String, Long> skinCacheTime = new LinkedHashMap<>();
    private final Set<String> learnedNames = new LinkedHashSet<>();
    // GUI context: which fake player a viewer's detail page is bound to, and
    // which fake player's live inventory a viewer currently has open.
    private final Map<UUID, String> guiBotName = new LinkedHashMap<>();
    private final Map<UUID, String> invEditing = new LinkedHashMap<>();

    /** Verbose/debug chat + console output. Off by default for clean spawns. */
    private boolean verbose() {
        return ruleBool("verboseMessages", false);
    }

    private void sendVerbose(CommandSender sender, String message) {
        if (sender != null && verbose()) {
            sender.sendMessage(message);
        }
    }

    private String trackKey(String rawName) {
        return sanitizeName(rawName).toLowerCase(Locale.ROOT);
    }

    private void track(FakeHandle handle) {
        tracked.put(trackKey(handle.name), handle);
    }

    private FakeHandle getTracked(String rawName) {
        return tracked.get(trackKey(rawName));
    }

    private void untrack(String rawName) {
        tracked.remove(trackKey(rawName));
    }

    private List<String> trackedNames() {
        List<String> names = new ArrayList<String>();
        for (FakeHandle handle : tracked.values()) {
            names.add(handle.name);
        }
        return names;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        hook("rug");
        Bukkit.getPluginManager().registerEvents(this, this);
        startMaintenanceLoop();
        getLogger().info("Rug " + VERSION + " enabled. " + COPYRIGHT + ". Use /rug help.");
    }

    /**
     * One reliable repeating task that keeps living NMS fake players behaving:
     * it lets them pick up nearby items and re-broadcasts their real held item
     * so equipment (e.g. a popped totem) never desyncs into a ghost hand.
     */
    private void startMaintenanceLoop() {
        try {
            getServer().getScheduler().runTaskTimer(this, new Runnable() {
                @Override
                public void run() {
                    tickFakePlayers();
                }
            }, 20L, 8L);
        } catch (Throwable throwable) {
            getLogger().warning("Could not start Rug maintenance loop: " + rootMessage(throwable));
        }
    }

    private void tickFakePlayers() {
        if (tracked.isEmpty()) {
            return;
        }
        for (FakeHandle handle : new ArrayList<FakeHandle>(tracked.values())) {
            if (handle == null || !"nms".equalsIgnoreCase(handle.backend) || !(handle.bukkitPlayer instanceof Player)) {
                continue;
            }
            Player player = (Player) handle.bukkitPlayer;
            try {
                Object dead = null;
                try { dead = invokeFlexible(player, "isDead"); } catch (Throwable ignored) {}
                if (Boolean.TRUE.equals(dead) || hasTag(player, REMOVING_TAG)) {
                    continue;
                }
                manuallyPickupNearbyItems(player);
                syncFakeEquipment(handle);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Rug disabled. Tracked fake-player names this session: " + trackedNames());
    }

    private void hook(String commandName) {
        PluginCommand cmd = getCommand(commandName);
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (!command.getName().equalsIgnoreCase("rug")) {
                sender.sendMessage(PREFIX + COLOR + "cEverything starts from /rug. Try /rug help.");
                return true;
            }
            return handleRug(sender, args);
        } catch (Throwable throwable) {
            sender.sendMessage(PREFIX + COLOR + "cCommand failed, but Rug caught it.");
            sender.sendMessage(PREFIX + COLOR + "c" + throwable.getClass().getSimpleName() + ": " + safeText(rootMessage(throwable)));
            getLogger().warning("Command failed: " + throwable.getClass().getSimpleName() + ": " + rootMessage(throwable));
            return true;
        }
    }

    private boolean handleRug(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                openMainGui((Player) sender);
            } else {
                showMainHelp(sender);
            }
            return true;
        }
        if (is(args[0], "help")) {
            showMainHelp(sender);
            return true;
        }

        if (is(args[0], "about") || is(args[0], "version")) {
            sender.sendMessage(PREFIX + DISPLAY_NAME + COLOR + "7 " + COLOR + "e" + VERSION + COLOR + "7 by " + COLOR + "eDawsonCodes" + COLOR + "7.");
            sender.sendMessage(PREFIX + "Backend: " + COLOR + "e" + ruleText("playerBackend", "auto") + COLOR + "7. NMS alpha uses a true ServerPlayer with a fake no-write connection.");
            sender.sendMessage(PREFIX + "Fallback: " + COLOR + "eBukkit visual" + COLOR + "7 if Paper/NMS blocks the alpha backend.");
            sender.sendMessage(PREFIX + "Skin layers: " + COLOR + "e" + ruleText("skinLayers", "all") + COLOR + "7. Use all/none or cape,jacket,sleeves,pants,hat.");
            return true;
        }

        if (is(args[0], "reload")) {
            reloadConfig();
            sender.sendMessage(PREFIX + "Reloaded config.yml.");
            return true;
        }

        if (is(args[0], "menu") || is(args[0], "gui")) {
            if (sender instanceof Player) {
                openMainGui((Player) sender);
            } else {
                sender.sendMessage(PREFIX + "The GUI is in-game only. Try /rug rules from console.");
            }
            return true;
        }

        if (is(args[0], "rules")) {
            showRules(sender);
            return true;
        }

        if (is(args[0], "rule")) {
            return handleRule(sender, args);
        }

        if (is(args[0], "debug")) {
            sender.sendMessage(PREFIX + "Tracked: " + COLOR + "e" + trackedNames());
            sender.sendMessage(PREFIX + "Loose Rug visuals: " + COLOR + "e" + findByTag(VISUAL_TAG, null).size() + COLOR + "7, hitboxes: " + COLOR + "e" + findByTag(HITBOX_TAG, null).size());
            sender.sendMessage(PREFIX + "No dependencies required. NMS alpha uses Paper internals reflectively, then falls back to Bukkit visuals.");
            sender.sendMessage(PREFIX + "Fake online players now: " + COLOR + "e" + countFakeOnlinePlayers());
            return true;
        }

        if (is(args[0], "skincheck")) {
            String who = args.length >= 2 ? sanitizeName(args[1]) : (sender instanceof Player ? ((Player) sender).getName() : DEFAULT_NAME);
            SkinResult result = resolveSkinProfile(who);
            sender.sendMessage(PREFIX + "Skincheck " + COLOR + "e" + who + COLOR + "7: " + result.statusLine());
            sender.sendMessage(PREFIX + "Skin layer mask now: " + COLOR + "e" + skinLayerMask() + COLOR + "7 (" + ruleText("skinLayers", "all") + ")");
            return true;
        }

        if (is(args[0], "players")) {
            return handlePlayer(sender, new String[]{"list"});
        }

        if (isPlayerNamespace(args[0])) {
            return handlePlayer(sender, tail(args));
        }

        ParsedPlayerCommand shortcut = parsePlayerCommand(args);
        if (shortcut != null) {
            return runPlayerCommand(sender, shortcut);
        }

        if (is(args[0], "list")) {
            return handlePlayer(sender, new String[]{"list"});
        }
        if (is(args[0], "removeall") || is(args[0], "killall")) {
            return handlePlayer(sender, new String[]{"removeall"});
        }
        if (is(args[0], "purge")) {
            return handlePlayer(sender, new String[]{"purge"});
        }

        sender.sendMessage(PREFIX + COLOR + "cUnknown Rug command. Try /rug help.");
        return true;
    }

    private boolean isPlayerNamespace(String arg) {
        return is(arg, "player") || is(arg, "fakeplayer") || is(arg, "fakeplayers") || is(arg, "fp") || is(arg, "bot") || is(arg, "bots");
    }

    private void showMainHelp(CommandSender sender) {
        sender.sendMessage(COLOR + "6Rug " + COLOR + "8- " + COLOR + "7" + VERSION + COLOR + "8 - " + COLOR + "7main hub");
        sender.sendMessage(COLOR + "e/rug " + COLOR + "8- " + COLOR + "7open the control GUI");
        sender.sendMessage(COLOR + "e/rug player <name> spawn [skin] " + COLOR + "8- " + COLOR + "7spawn a fake player");
        sender.sendMessage(COLOR + "e/rug player <name> ... " + COLOR + "8- " + COLOR + "7kill, remove, hand, status, inventory, skin");
        sender.sendMessage(COLOR + "e/rug players " + COLOR + "8- " + COLOR + "7list tracked fake players");
        sender.sendMessage(COLOR + "e/rug rules " + COLOR + "8| " + COLOR + "e/rug rule <name> [value] " + COLOR + "8- " + COLOR + "7view/edit rules");
        sender.sendMessage(COLOR + "e/rug skincheck <name> " + COLOR + "8- " + COLOR + "7test a skin lookup");
        sender.sendMessage(COLOR + "e/rug purge " + COLOR + "8- " + COLOR + "7force-remove stuck fake players");
        sender.sendMessage(COLOR + "8Tip: " + COLOR + "7/rug player help " + COLOR + "8shows the full fake-player command list.");
    }

    private boolean handleRule(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + COLOR + "cUsage: /rug rule <name> [value]");
            sender.sendMessage(PREFIX + "Rules: " + COLOR + "e" + String.join(", ", RULES));
            return true;
        }
        String rule = canonicalRule(args[1]);
        if (rule == null) {
            sender.sendMessage(PREFIX + COLOR + "cUnknown rule. Try /rug rules.");
            return true;
        }
        FileConfiguration cfg = getConfig();
        String path = "rules." + rule;
        if (args.length == 2) {
            sender.sendMessage(PREFIX + rule + " = " + COLOR + "e" + String.valueOf(cfg.get(path)));
            return true;
        }
        String value = join(args, 2);
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            cfg.set(path, Boolean.parseBoolean(value));
        } else {
            cfg.set(path, value);
        }
        saveConfig();
        sender.sendMessage(PREFIX + "Set " + COLOR + "e" + rule + COLOR + "7 to " + COLOR + "e" + value + COLOR + "7.");
        return true;
    }

    private void showRules(CommandSender sender) {
        FileConfiguration cfg = getConfig();
        sender.sendMessage(COLOR + "6Rug Rules " + COLOR + "8- " + COLOR + "7use " + COLOR + "e/rug rule <name> <value>");
        for (String rule : RULES) {
            String desc = RULE_DESCRIPTIONS.get(rule);
            String line = COLOR + "e" + rule + COLOR + "7 = " + COLOR + "f" + cfg.get("rules." + rule);
            if (desc != null) {
                line = line + COLOR + "8 - " + COLOR + "7" + desc;
            }
            sender.sendMessage(line);
        }
    }

    private boolean handlePlayer(CommandSender sender, String[] args) {
        if (args.length == 0 || is(args[0], "help")) {
            showPlayerHelp(sender);
            return true;
        }
        if (is(args[0], "list")) {
            sender.sendMessage(PREFIX + "Tracked fake players: " + COLOR + "e" + (tracked.isEmpty() ? "none" : String.join(", ", trackedNames())));
            return true;
        }
        if (is(args[0], "removeall") || is(args[0], "killall")) {
            return removeAll(sender);
        }
        if (is(args[0], "purge")) {
            return purgeFakePlayers(sender);
        }
        if ((is(args[0], "hand") || is(args[0], "equip") || is(args[0], "refresh")) && args.length == 1) {
            if (tracked.size() == 1) {
                return refreshFakeHand(sender, trackedNames().get(0));
            }
            sender.sendMessage(PREFIX + COLOR + "cUsage: /rug player <name> hand");
            sender.sendMessage(PREFIX + COLOR + "7You have " + COLOR + "e" + tracked.size() + COLOR + "7 tracked fake players, so I need a name.");
            return true;
        }

        // Strict syntax: the first token is always the fake player's name, the
        // second must be a known action. Anything else shows help and never
        // accidentally spawns a fake player.
        if (args.length < 2) {
            sender.sendMessage(PREFIX + COLOR + "cMissing action for " + COLOR + "e" + sanitizeName(args[0]) + COLOR + "c.");
            sender.sendMessage(PREFIX + COLOR + "7Use /rug player " + sanitizeName(args[0]) + " spawn|kill|remove|hand|status|inventory|skin");
            return true;
        }
        ParsedPlayerCommand parsed = parsePlayerCommand(args);
        if (parsed == null) {
            sender.sendMessage(PREFIX + COLOR + "cUnknown action " + COLOR + "e" + args[1] + COLOR + "c for " + COLOR + "e" + sanitizeName(args[0]) + COLOR + "c. Nothing was spawned.");
            showPlayerHelp(sender);
            return true;
        }
        return runPlayerCommand(sender, parsed);
    }

    private boolean runPlayerCommand(CommandSender sender, ParsedPlayerCommand parsed) {
        switch (parsed.action) {
            case "spawn":
                return spawn(sender, parsed.rawName, parsed.skinName);
            case "remove":
            case "kill":
                if (parsed.action.equals("kill")) {
                    killFake(sender, parsed.name);
                } else {
                    removeFake(sender, parsed.name, true, false);
                }
                return true;
            case "tp":
            case "tphere":
            case "move":
            case "move_here":
                return moveHere(sender, parsed.name, parsed.skinName);
            case "resync":
                return resyncHitbox(sender, parsed.name);
            case "look":
                return lookAtSender(sender, parsed.name);
            case "stop":
                sender.sendMessage(PREFIX + "No running action pack yet for " + COLOR + "e" + parsed.name + COLOR + "7. v0.2 will add action states.");
                return true;
            case "status":
                showStatus(sender, parsed.name);
                return true;
            case "hand":
            case "equip":
            case "refresh":
                return refreshFakeHand(sender, parsed.name);
            case "inventory":
            case "inv":
                return openFakeInventory(sender, parsed.name);
            case "skin":
                // Re-spawn in place with the requested skin so the change applies cleanly.
                return spawn(sender, parsed.rawName, parsed.skinName);
            default:
                sender.sendMessage(PREFIX + COLOR + "cUnknown action. Try /rug player help.");
                return true;
        }
    }

    private void showPlayerHelp(CommandSender sender) {
        sender.sendMessage(COLOR + "6Rug Fake Players " + COLOR + "8- " + COLOR + "7commands");
        sender.sendMessage(COLOR + "e/rug player <name> spawn [skin] " + COLOR + "8- " + COLOR + "7spawn a fake player");
        sender.sendMessage(COLOR + "e/rug player <name> kill " + COLOR + "8- " + COLOR + "7vanilla death, then full cleanup");
        sender.sendMessage(COLOR + "e/rug player <name> remove " + COLOR + "8- " + COLOR + "7quietly remove it");
        sender.sendMessage(COLOR + "e/rug player <name> hand " + COLOR + "8- " + COLOR + "7refresh held item / equipment");
        sender.sendMessage(COLOR + "e/rug player <name> status " + COLOR + "8- " + COLOR + "7backend / skin / tracking info");
        sender.sendMessage(COLOR + "e/rug player <name> inventory " + COLOR + "8- " + COLOR + "7open its inventory to edit");
        sender.sendMessage(COLOR + "e/rug player <name> skin <skinName> " + COLOR + "8- " + COLOR + "7re-spawn with a new skin");
        sender.sendMessage(COLOR + "e/rug player <name> tp " + COLOR + "8- " + COLOR + "7move it to you");
        sender.sendMessage(COLOR + "e/rug player purge " + COLOR + "8| " + COLOR + "e removeall " + COLOR + "8- " + COLOR + "7clean up stuck / all bots");
    }

    /**
     * Opens the fake player's live inventory to the command sender so items can be
     * viewed/edited. Editing the live inventory avoids item duplication. Guards
     * against dead/removed/visual-backend fakes so it never crashes.
     */
    private boolean openFakeInventory(CommandSender sender, String rawName) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "Run this in-game to open a fake player's inventory.");
            return true;
        }
        Player viewer = (Player) sender;
        String name = sanitizeName(rawName);
        FakeHandle fake = getTracked(name);
        Player target = null;
        if (fake != null && fake.bukkitPlayer instanceof Player) {
            target = (Player) fake.bukkitPlayer;
        } else {
            Player online = findOnlinePlayerIgnoreCase(name);
            if (online != null && isLikelyFakeOnlinePlayer(online)) {
                target = online;
            }
        }
        if (target == null) {
            sender.sendMessage(PREFIX + COLOR + "cNo live (NMS) fake player named " + COLOR + "e" + name + COLOR + "c to open. Visual-backend bots have no inventory.");
            return true;
        }
        try {
            boolean dead = false;
            try { Object d = invokeFlexible(target, "isDead"); dead = Boolean.TRUE.equals(d); } catch (Throwable ignored) {}
            if (dead || hasTag(target, REMOVING_TAG)) {
                sender.sendMessage(PREFIX + COLOR + "c" + name + " is dead/removing; nothing to open.");
                return true;
            }
            Inventory inv = (Inventory) invokeFlexible(target, "getInventory");
            if (inv == null) {
                sender.sendMessage(PREFIX + COLOR + "cCould not read " + name + "'s inventory.");
                return true;
            }
            viewer.openInventory(inv);
            invEditing.put(viewer.getUniqueId(), name);
            sender.sendMessage(PREFIX + "Editing " + COLOR + "e" + name + COLOR + "7's inventory. Close to apply + refresh.");
        } catch (Throwable throwable) {
            sender.sendMessage(PREFIX + COLOR + "cCould not open inventory: " + safeText(rootMessage(throwable)));
        }
        return true;
    }

    private boolean spawn(CommandSender sender, String rawName, String rawSkinName) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "Run this in-game so I can spawn it at your location.");
            return true;
        }
        String requestedName = sanitizeName(rawName);
        String requestedSkinName = sanitizeName(rawSkinName == null ? requestedName : rawSkinName);
        String skinName = resolveSkinName(requestedSkinName);
        learnName(requestedName);
        learnName(skinName);
        String name = requestedName;

        // Minecraft names cap at 16 chars. If we truncated, say so once, plainly.
        String cleanedRequest = rawName == null ? "" : rawName.replaceAll("[^A-Za-z0-9_]", "");
        if (cleanedRequest.length() > 16) {
            sender.sendMessage(PREFIX + COLOR + "eName capped at 16 chars" + COLOR + "7; spawned as " + COLOR + "e" + name + COLOR + "7.");
        }

        Player realNameConflict = findRealOnlinePlayerIgnoreCase(name);
        if (realNameConflict != null && !ruleBool("allowDuplicateOnlineNames", false)) {
            String oldName = name;
            name = uniqueBotName(realNameConflict.getName());
            skinName = resolveSkinName(realNameConflict.getName());
            learnName(name);
            learnName(skinName);
            sender.sendMessage(PREFIX + COLOR + "e" + oldName + COLOR + "7 is a real online player; spawned as " + COLOR + "e" + name + COLOR + "7 instead.");
            sendVerbose(sender, PREFIX + "Set /rug rule allowDuplicateOnlineNames true to allow risky duplicate names.");
        }

        purgeName(name, true);
        removeFake(sender, name, false, false);

        Location base = ((Player) sender).getLocation();
        Object visual = null;
        Object hitbox = null;

        // Resolve the skin once so the spawn line can honestly say whether a real
        // skin was found or the default skin is in use.
        SkinResult spawnSkin = resolveSkinProfile(skinName);
        String skinDesc = (spawnSkin != null && spawnSkin.hasTexture())
                ? "using skin " + COLOR + "e" + spawnSkin.name + COLOR + "7"
                : "using default skin";

        String backend = ruleText("playerBackend", "auto").toLowerCase(Locale.ROOT);
        if (!backend.equals("visual")) {
            NmsSpawn nms = trySpawnNmsFakePlayer(base, name, skinName);
            if (nms.success) {
                FakeHandle handle = new FakeHandle(name, skinName, uuidOf(nms.bukkitPlayer), null, System.currentTimeMillis(), "nms", nms.bukkitPlayer, nms.nmsPlayer);
                track(handle);
                syncFakeEquipment(handle);
                // One concise line by default; details live behind verboseMessages.
                sender.sendMessage(PREFIX + "Spawned " + COLOR + "e" + name + COLOR + "7 " + skinDesc + COLOR + "7.");
                sendVerbose(sender, PREFIX + "Backend=nms (" + nms.bukkitClass + "), skinLayers=" + ruleText("skinLayers", "all") + ". Use /rug player " + name + " status for details.");
                return true;
            }
            if (backend.equals("nms")) {
                sender.sendMessage(PREFIX + COLOR + "cNMS spawn failed: " + safeText(nms.message));
                sendVerbose(sender, PREFIX + COLOR + "7Set /rug rule playerBackend auto or visual to use fallback mode.");
                return true;
            }
            sendVerbose(sender, PREFIX + COLOR + "eNMS backend failed, using visual fallback: " + safeText(nms.message));
        }

        if (ruleBool("playerVisualEnabled", true)) {
            visual = spawnPlayerVisual(base, name, skinName, spawnSkin);
        }
        if (ruleBool("hitboxEnabled", true)) {
            hitbox = spawnHitbox(base, name, skinName);
        }

        track(new FakeHandle(name, skinName, uuidOf(visual), uuidOf(hitbox), System.currentTimeMillis(), "visual", null, null));
        playConfiguredSound("summonAlertSound", 1.0f, 1.0f);
        sender.sendMessage(PREFIX + "Spawned " + COLOR + "e" + name + COLOR + "7 " + skinDesc + COLOR + "7 (visual backend).");
        return true;
    }

    private boolean moveHere(CommandSender sender, String rawName, String skinOverride) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "Run this in-game so I can move it to your location.");
            return true;
        }
        String name = sanitizeName(rawName);
        FakeHandle old = getTracked(name);
        String skinName = sanitizeName(skinOverride == null ? (old == null ? name : old.skinName) : skinOverride);
        removeEntities(name, true);
        return spawn(sender, name, skinName);
    }

    private boolean resyncHitbox(CommandSender sender, String rawName) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "Run this in-game so I can rebuild the hitbox at your location.");
            return true;
        }
        String name = sanitizeName(rawName);
        FakeHandle old = getTracked(name);
        String skinName = old == null ? name : old.skinName;
        removeTagged(HITBOX_TAG, name, true);
        Object hitbox = null;
        if (ruleBool("hitboxEnabled", true)) {
            hitbox = spawnHitbox(((Player) sender).getLocation(), name, skinName);
        }
        UUID visualId = old == null ? null : old.visualId;
        track(new FakeHandle(name, skinName, visualId, uuidOf(hitbox), System.currentTimeMillis(), "visual", null, null));
        sender.sendMessage(PREFIX + "Rebuilt hitbox for " + COLOR + "e" + name + COLOR + "7.");
        return true;
    }

    private boolean lookAtSender(CommandSender sender, String rawName) {
        sender.sendMessage(PREFIX + "Look/rotation needs the packet/NMS backend. No-deps visual is static for now.");
        return true;
    }

    private void showStatus(CommandSender sender, String rawName) {
        String name = sanitizeName(rawName);
        FakeHandle fake = getTracked(name);
        int looseVisuals = findByTag(VISUAL_TAG, name).size();
        int looseHitboxes = findByTag(HITBOX_TAG, name).size();
        if (fake == null) {
            sender.sendMessage(PREFIX + COLOR + "e" + name + COLOR + "7 is not tracked this session. Loose pieces: visual=" + looseVisuals + " hitbox=" + looseHitboxes);
            return;
        }
        sender.sendMessage(PREFIX + COLOR + "e" + fake.name + COLOR + "7 skin=" + COLOR + "e" + fake.skinName + COLOR + "7 backend=" + COLOR + "e" + fake.backend + COLOR + "7 visual=" + shortId(fake.visualId) + " hitbox=" + shortId(fake.hitboxId));
        sender.sendMessage(PREFIX + "Loose pieces: visual=" + COLOR + "e" + looseVisuals + COLOR + "7 hitbox=" + COLOR + "e" + looseHitboxes);
    }

    private boolean removeAll(CommandSender sender) {
        int trackedRemoved = 0;
        for (String name : new LinkedHashSet<String>(trackedNames())) {
            removeFake(sender, name, false, false);
            trackedRemoved++;
        }
        tracked.clear();
        int visuals = removeTagged(VISUAL_TAG, null, true);
        int hitboxes = removeTagged(HITBOX_TAG, null, true);
        int fakeOnline = purgeAllFakeOnlinePlayers(true) + removeNmsEntities(null, true);
        sender.sendMessage(PREFIX + "Removed " + COLOR + "e" + trackedRemoved + COLOR + "7 tracked fake player(s). Swept loose entities: visual=" + COLOR + "e" + visuals + COLOR + "7 hitbox=" + COLOR + "e" + hitboxes + COLOR + "7 fakeOnline=" + COLOR + "e" + fakeOnline + COLOR + "7.");
        return true;
    }

    private boolean purgeFakePlayers(CommandSender sender) {
        int fakeOnline = purgeAllFakeOnlinePlayers(true) + removeNmsEntities(null, true);
        int visuals = removeTagged(VISUAL_TAG, null, true);
        int hitboxes = removeTagged(HITBOX_TAG, null, true);
        tracked.clear();
        sender.sendMessage(PREFIX + "Purged stuck Rug test players. fakeOnline=" + COLOR + "e" + fakeOnline + COLOR + "7 visual=" + COLOR + "e" + visuals + COLOR + "7 hitbox=" + COLOR + "e" + hitboxes + COLOR + "7.");
        return true;
    }


    private void killFake(CommandSender sender, String rawName) {
        String name = sanitizeName(rawName);
        FakeHandle fake = getTracked(name);
        if (fake != null && "nms".equalsIgnoreCase(fake.backend) && fake.bukkitPlayer instanceof Entity) {
            Entity entity = (Entity) fake.bukkitPlayer;
            try {
                if (sender instanceof Player) {
                    Method damage = entity.getClass().getMethod("damage", double.class, Entity.class);
                    damage.invoke(entity, 10000.0d, (Player) sender);
                } else {
                    Method damage = entity.getClass().getMethod("damage", double.class);
                    damage.invoke(entity, 10000.0d);
                }
                sender.sendMessage(PREFIX + "Killed " + COLOR + "e" + name + COLOR + "7 using the vanilla damage path.");
                return;
            } catch (Throwable ignored) {
                invokeIfExists(entity, "setHealth", new Class[]{double.class}, 0.0d);
                sender.sendMessage(PREFIX + "Killed " + COLOR + "e" + name + COLOR + "7 using fallback health=0.");
                return;
            }
        }
        removeFake(sender, name, true, true);
    }

    private void removeFake(CommandSender sender, String rawName, boolean tell, boolean deathSound) {
        String name = sanitizeName(rawName);
        FakeHandle fake = getTracked(name);
        int removed = 0;
        if (fake != null && "nms".equalsIgnoreCase(fake.backend)) {
            removed += removeNmsFake(fake, true);
        }
        removed += removeEntities(name, true);
        untrack(name);
        if (deathSound) {
            broadcastFakeDeath(name, sender instanceof Player ? ((Player) sender).getName() : null);
            playConfiguredSound("deathAlertSound", 1.0f, 1.0f);
        }
        if (tell) {
            sender.sendMessage(PREFIX + "Removed " + COLOR + "e" + name + COLOR + "7. Pieces removed=" + COLOR + "e" + removed + COLOR + "7.");
        }
    }

    private int removeEntities(String rawName, boolean markRemoving) {
        String name = sanitizeName(rawName);
        return removeTagged(HITBOX_TAG, name, markRemoving) + removeTagged(VISUAL_TAG, name, markRemoving) + removeNmsEntities(name, markRemoving);
    }

    private Object spawnPlayerVisual(Location base, String name, String skinName, SkinResult skin) {
        Location loc = cloneAndOffset(base, 0.0, 0.0, 0.0);
        Object stand = spawnEntity(loc, "ARMOR_STAND");
        tagEntity(stand, VISUAL_TAG, name, skinName);
        invokeIfExists(stand, "setCustomName", new Class[]{String.class}, name);
        invokeIfExists(stand, "setCustomNameVisible", new Class[]{boolean.class}, true);
        invokeIfExists(stand, "setGravity", new Class[]{boolean.class}, false);
        invokeIfExists(stand, "setInvulnerable", new Class[]{boolean.class}, true);
        invokeIfExists(stand, "setSilent", new Class[]{boolean.class}, true);
        invokeIfExists(stand, "setPersistent", new Class[]{boolean.class}, true);
        invokeIfExists(stand, "setVisible", new Class[]{boolean.class}, true);
        invokeIfExists(stand, "setArms", new Class[]{boolean.class}, true);
        invokeIfExists(stand, "setBasePlate", new Class[]{boolean.class}, false);
        invokeIfExists(stand, "setSmall", new Class[]{boolean.class}, false);
        invokeIfExists(stand, "setCollidable", new Class[]{boolean.class}, false);
        equipPlayerLook(stand, skinName, skin);
        return stand;
    }

    private Object spawnHitbox(Location base, String name, String skinName) {
        Location loc = cloneAndOffset(base, 0.0, 0.0, 0.0);
        Object zombie = spawnEntity(loc, "ZOMBIE");
        tagEntity(zombie, HITBOX_TAG, name, skinName);
        invokeIfExists(zombie, "setCustomName", new Class[]{String.class}, name);
        invokeIfExists(zombie, "setCustomNameVisible", new Class[]{boolean.class}, false);
        invokeIfExists(zombie, "setAI", new Class[]{boolean.class}, false);
        invokeIfExists(zombie, "setAware", new Class[]{boolean.class}, false);
        invokeIfExists(zombie, "setSilent", new Class[]{boolean.class}, true);
        invokeIfExists(zombie, "setPersistent", new Class[]{boolean.class}, true);
        invokeIfExists(zombie, "setInvulnerable", new Class[]{boolean.class}, false);
        invokeIfExists(zombie, "setRemoveWhenFarAway", new Class[]{boolean.class}, false);
        invokeIfExists(zombie, "setAdult", new Class[]{}, new Object[]{});
        if (ruleBool("hitboxInvisible", true)) {
            invokeIfExists(zombie, "setInvisible", new Class[]{boolean.class}, true);
        }
        if (ruleBool("hitboxFireProof", true)) {
            invokeIfExists(zombie, "setFireTicks", new Class[]{int.class}, 0);
        }
        return zombie;
    }

    
    private NmsSpawn trySpawnNmsFakePlayer(Location base, String name, String skinName) {
        try {
            Object craftServer = invokeStaticRequired(Bukkit.class, "getServer", new Class[]{}, new Object[]{});
            Object minecraftServer = invokeFirst(craftServer, new String[]{"getServer"}, new Class[]{}, new Object[]{});
            if (minecraftServer == null) {
                return NmsSpawn.fail("CraftServer#getServer not found");
            }
            Object world = invokeRequired(base, "getWorld", new Class[]{}, new Object[]{});
            Object nmsWorld = invokeFirst(world, new String[]{"getHandle"}, new Class[]{}, new Object[]{});
            if (nmsWorld == null) {
                return NmsSpawn.fail("CraftWorld#getHandle not found");
            }

            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Constructor<?> gameProfileCtor = gameProfileClass.getConstructor(UUID.class, String.class);
            UUID fakeUuid = UUID.nameUUIDFromBytes(("Rug:" + name).getBytes(StandardCharsets.UTF_8));
            SkinResult skin = resolveSkinProfile(skinName);
            Object gameProfile = gameProfileCtor.newInstance(fakeUuid, name);
            copySkinProfile(gameProfile, skinName, skin);

            Object clientInfo = defaultClientInformation();
            Class<?> playerClass = firstClass("net.minecraft.server.level.EntityPlayer", "net.minecraft.server.level.ServerPlayer");
            Object nmsPlayer = constructServerPlayer(playerClass, minecraftServer, nmsWorld, gameProfile, clientInfo);
            if (nmsPlayer == null) {
                return NmsSpawn.fail("No usable ServerPlayer/EntityPlayer constructor found");
            }
            moveNmsPlayer(nmsPlayer, base);
            applySkinLayerMask(nmsPlayer);

            Object bukkitPlayer = invokeFirst(nmsPlayer, new String[]{"getBukkitEntity"}, new Class[]{}, new Object[]{});
            if (bukkitPlayer instanceof Entity) {
                invokeIfExists(bukkitPlayer, "addScoreboardTag", new Class[]{String.class}, NMS_TAG);
                invokeIfExists(bukkitPlayer, "addScoreboardTag", new Class[]{String.class}, nameTag(name));
                invokeIfExists(bukkitPlayer, "addScoreboardTag", new Class[]{String.class}, skinTag(skinName));
                invokeIfExists(bukkitPlayer, "setCanPickupItems", new Class[]{boolean.class}, true);
                invokeIfExists(bukkitPlayer, "setCollidable", new Class[]{boolean.class}, true);
            }

            Object connection = createNmsConnection();
            Object cookie = createCommonListenerCookie(gameProfile);
            Object playerList = invokeFirst(craftServer, new String[]{"getHandle"}, new Class[]{}, new Object[]{});
            if (playerList != null && connection != null && cookie != null && invokePlaceNewPlayer(playerList, connection, nmsPlayer, cookie)) {
                applyPaperSkinToBukkitPlayer(bukkitPlayer, skinName, skin);
                applySkinLayerMask(nmsPlayer);
                moveNmsPlayer(nmsPlayer, base);
                syncFakeEquipmentObjects(bukkitPlayer, nmsPlayer);
                return NmsSpawn.ok(nmsPlayer, bukkitPlayer, bukkitPlayer == null ? "unknown" : bukkitPlayer.getClass().getSimpleName());
            }

            if (tryAddEntityToWorld(nmsWorld, nmsPlayer)) {
                applySkinLayerMask(nmsPlayer);
                moveNmsPlayer(nmsPlayer, base);
                syncFakeEquipmentObjects(bukkitPlayer, nmsPlayer);
                return NmsSpawn.ok(nmsPlayer, bukkitPlayer, "world-added-no-playerlist");
            }
            return NmsSpawn.fail("Could not place player through PlayerList or World addEntity");
        } catch (Throwable throwable) {
            return NmsSpawn.fail(rootMessage(throwable));
        }
    }

    private String resolveSkinName(String requested) {
        String cleaned = sanitizeName(requested);
        Player online = findOnlinePlayerIgnoreCase(cleaned);
        if (online != null) {
            return sanitizeName(online.getName());
        }
        String mojang = lookupMojangProfileName(cleaned);
        return mojang == null ? cleaned : sanitizeName(mojang);
    }

    private Player findOnlinePlayerIgnoreCase(String rawName) {
        String name = sanitizeName(rawName);
        for (Player player : onlinePlayers()) {
            try {
                if (player.getName().equalsIgnoreCase(name)) return player;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private Player findRealOnlinePlayerIgnoreCase(String rawName) {
        Player player = findOnlinePlayerIgnoreCase(rawName);
        if (player == null) return null;
        return isLikelyFakeOnlinePlayer(player) ? null : player;
    }

    private String uniqueBotName(String realName) {
        String clean = sanitizeName(realName);
        String base = clean;
        if (base.length() > 13) base = base.substring(0, 13);
        String candidate = sanitizeName(base + "Bot");
        int i = 2;
        while (findOnlinePlayerIgnoreCase(candidate) != null || getTracked(candidate) != null) {
            String suffix = "B" + i;
            int maxBase = Math.max(1, 16 - suffix.length());
            candidate = sanitizeName(clean.substring(0, Math.min(clean.length(), maxBase)) + suffix);
            i++;
        }
        return candidate;
    }

    private String lookupMojangProfileName(String skinName) {
        try {
            String profileJson = httpGet("https://api.mojang.com/users/profiles/minecraft/" + sanitizeName(skinName));
            String canonical = jsonValue(profileJson, "name");
            return canonical == null || canonical.isEmpty() ? null : canonical;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void copySkinProfile(Object targetProfile, String skinName, SkinResult skin) {
        boolean copied = false;
        if (skin != null && skin.hasTexture()) {
            copied = putTextureProperty(targetProfile, skin.value, skin.signature);
        }
        if (!copied) {
            copied = copyOnlinePlayerSkin(targetProfile, skinName);
        }
        if (!copied) {
            copied = fetchMojangSkin(targetProfile, skinName);
        }
        if (!copied && verbose()) {
            // Expected for offline/unknown names; only mention it in verbose mode.
            getLogger().info("No skin textures found for " + skinName + ". Using default skin. Try /rug skincheck " + skinName);
        }
    }

    private SkinResult resolveSkinProfile(String skinName) {
        String clean = sanitizeName(skinName);
        learnName(clean);
        SkinResult cached = cachedSkin(clean);
        if (cached != null) return cached;

        SkinResult online = fetchOnlineSkin(clean);
        if (online.hasTexture()) return cacheSkin(clean, online);

        SkinResult paper = fetchPaperSkin(clean);
        if (paper.hasTexture()) return cacheSkin(clean, paper);

        SkinResult mojang = fetchMojangSkinResult(clean);
        if (mojang.hasTexture()) return cacheSkin(clean, mojang);

        SkinResult failed = paper.error != null && !paper.error.isEmpty() ? paper : mojang;
        if (mojang.error != null && mojang.error.contains("429") && verbose()) {
            getLogger().info("Skin lookup for " + clean + " is rate limited (HTTP 429). Using cached or default skin.");
        }
        return cacheSkin(clean, failed);
    }

    private SkinResult cachedSkin(String skinName) {
        String key = trackKey(skinName);
        SkinResult result = skinCache.get(key);
        Long time = skinCacheTime.get(key);
        if (result == null || time == null) return null;
        long age = System.currentTimeMillis() - time.longValue();
        long ttl = result.hasTexture() ? SKIN_CACHE_OK_MS : SKIN_CACHE_FAIL_MS;
        if (age > ttl) {
            skinCache.remove(key);
            skinCacheTime.remove(key);
            return null;
        }
        return result;
    }

    private SkinResult cacheSkin(String skinName, SkinResult result) {
        String key = trackKey(skinName);
        skinCache.put(key, result);
        skinCacheTime.put(key, System.currentTimeMillis());
        if (result != null) learnName(result.name);
        return result;
    }

    private SkinResult fetchOnlineSkin(String skinName) {
        try {
            Player online = findOnlinePlayerIgnoreCase(skinName);
            if (online == null) return SkinResult.fail(skinName, "online", "not online");
            Object paperProfile = invokeFlexible(online, "getPlayerProfile");
            SkinResult paper = skinFromPaperProfile(paperProfile, online.getName(), "online-player-profile");
            if (paper.hasTexture()) return paper;
            Object gameProfile = findGameProfile(online);
            if (gameProfile == null) {
                Object handle = invokeFirst(online, new String[]{"getHandle"}, new Class[]{}, new Object[]{});
                gameProfile = findGameProfile(handle);
            }
            if (gameProfile != null) {
                SkinResult game = skinFromGameProfile(gameProfile, online.getName(), "online-gameprofile");
                if (game.hasTexture()) return game;
            }
            return SkinResult.fail(skinName, "online", "online profile had no textures");
        } catch (Throwable throwable) {
            return SkinResult.fail(skinName, "online", rootMessage(throwable));
        }
    }

    private SkinResult skinFromGameProfile(Object profile, String skinName, String source) {
        try {
            Object props = profileProperties(profile);
            if (props == null) return SkinResult.fail(skinName, source, "no properties map");
            Object textures = invokeFlexible(props, "get", "textures");
            if (textures instanceof Iterable) {
                for (Object prop : (Iterable<?>) textures) {
                    String value = stringValue(invokeFlexible(prop, "value"));
                    if (value == null) value = stringValue(invokeFlexible(prop, "getValue"));
                    String signature = null;
                    try { signature = stringValue(invokeFlexible(prop, "signature")); } catch (Throwable ignored) {}
                    if (signature == null) { try { signature = stringValue(invokeFlexible(prop, "getSignature")); } catch (Throwable ignored) {} }
                    if (value != null && !value.isEmpty()) return SkinResult.ok(skinName, source, value, signature);
                }
            }
        } catch (Throwable ignored) {}
        return SkinResult.fail(skinName, source, "no textures property found");
    }

    private SkinResult fetchPaperSkin(String skinName) {
        try {
            Object offline = invokeStaticFlexible(Bukkit.class, "getOfflinePlayer", skinName);
            if (offline == null) return SkinResult.fail(skinName, "paper", "Bukkit.getOfflinePlayer returned null");
            Object profile = invokeFlexible(offline, "getPlayerProfile");
            if (profile == null) return SkinResult.fail(skinName, "paper", "OfflinePlayer profile unavailable");
            Object completed = profile;
            try {
                Object completeResult = invokeFlexible(profile, "complete");
                if (completeResult instanceof Boolean && !((Boolean) completeResult).booleanValue()) {
                    return SkinResult.fail(skinName, "paper", "profile.complete returned false");
                }
            } catch (Throwable ignored) {
                Object future = invokeFlexible(profile, "update");
                if (future != null) {
                    completed = invokeFlexible(future, "get");
                }
            }
            if (completed == null) completed = profile;
            SkinResult result = skinFromPaperProfile(completed, skinName, "paper-profile");
            if (result.hasTexture()) return result;
            return SkinResult.fail(skinName, "paper", "profile had no textures property");
        } catch (Throwable throwable) {
            return SkinResult.fail(skinName, "paper", rootMessage(throwable));
        }
    }

    private SkinResult skinFromPaperProfile(Object profile, String skinName, String source) {
        try {
            Object props = profileProperties(profile);
            if (props instanceof Iterable) {
                for (Object prop : (Iterable<?>) props) {
                    String propName = stringValue(invokeFlexible(prop, "getName"));
                    if (!"textures".equals(propName)) continue;
                    String value = stringValue(invokeFlexible(prop, "getValue"));
                    String signature = stringValue(invokeFlexible(prop, "getSignature"));
                    if (value != null && !value.isEmpty()) {
                        return SkinResult.ok(skinName, source, value, signature);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return SkinResult.fail(skinName, source, "no textures property found");
    }

    private SkinResult fetchMojangSkinResult(String skinName) {
        try {
            String profileJson = httpGet("https://api.mojang.com/users/profiles/minecraft/" + sanitizeName(skinName));
            String id = jsonValue(profileJson, "id");
            String canonicalName = jsonValue(profileJson, "name");
            if (id == null || id.isEmpty()) {
                return SkinResult.fail(skinName, "mojang", "UUID lookup returned no id");
            }
            String sessionJson = httpGet("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false");
            String value = propertyValue(sessionJson, "value");
            String signature = propertyValue(sessionJson, "signature");
            if (value == null || value.isEmpty()) {
                return SkinResult.fail(skinName, "mojang", "session profile returned no textures value");
            }
            return SkinResult.ok(canonicalName == null ? skinName : canonicalName, "mojang-session", value, signature);
        } catch (Throwable throwable) {
            return SkinResult.fail(skinName, "mojang", rootMessage(throwable));
        }
    }

    private boolean putTextureProperty(Object targetProfile, String value, String signature) {
        try {
            if (value == null || value.isEmpty()) return false;
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object property;
            try {
                Constructor<?> ctor = propertyClass.getConstructor(String.class, String.class, String.class);
                property = ctor.newInstance("textures", value, signature);
            } catch (Throwable ignored) {
                Constructor<?> ctor = propertyClass.getConstructor(String.class, String.class);
                property = ctor.newInstance("textures", value);
            }
            Object targetProps = profileProperties(targetProfile);
            if (targetProps == null) return false;
            clearTextureProperties(targetProps);
            return flexiblePutProperty(targetProps, property);
        } catch (Throwable throwable) {
            getLogger().warning("Could not put skin texture into GameProfile: " + rootMessage(throwable));
            return false;
        }
    }

    private void applyPaperSkinToBukkitPlayer(Object bukkitPlayer, String skinName, SkinResult skin) {
        if (bukkitPlayer == null || skin == null || !skin.hasTexture()) return;
        try {
            Object playerProfile = invokeFlexible(bukkitPlayer, "getPlayerProfile");
            if (playerProfile == null) return;
            addPaperProfileProperty(playerProfile, skin.value, skin.signature);
            invokeFlexible(bukkitPlayer, "setPlayerProfile", playerProfile);
        } catch (Throwable throwable) {
            getLogger().fine("Could not apply Paper skin profile to fake player " + skinName + ": " + rootMessage(throwable));
        }
    }

    private boolean addPaperProfileProperty(Object playerProfile, String value, String signature) {
        try {
            Class<?> profilePropertyClass = Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
            Object property;
            try {
                Constructor<?> ctor = profilePropertyClass.getConstructor(String.class, String.class, String.class);
                property = ctor.newInstance("textures", value, signature);
            } catch (Throwable ignored) {
                Constructor<?> ctor = profilePropertyClass.getConstructor(String.class, String.class);
                property = ctor.newInstance("textures", value);
            }
            Object props = profileProperties(playerProfile);
            if (props instanceof Collection) {
                Collection collection = (Collection) props;
                List<Object> remove = new ArrayList<Object>();
                for (Object existing : collection) {
                    try {
                        if ("textures".equals(stringValue(invokeFlexible(existing, "getName")))) {
                            remove.add(existing);
                        }
                    } catch (Throwable ignored) {}
                }
                collection.removeAll(remove);
                collection.add(property);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private Object profileProperties(Object profile) {
        if (profile == null) return null;
        try {
            return invokeFlexible(profile, "getProperties");
        } catch (Throwable ignored) {
        }
        try {
            return invokeFlexible(profile, "properties");
        } catch (Throwable ignored) {
        }
        try {
            Field field = profile.getClass().getDeclaredField("properties");
            field.setAccessible(true);
            return field.get(profile);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void clearTextureProperties(Object props) {
        try {
            Object values = invokeFlexible(props, "removeAll", "textures");
            if (values != null) return;
        } catch (Throwable ignored) {}
        try {
            Object values = invokeFlexible(props, "remove", "textures");
        } catch (Throwable ignored) {}
    }

    private boolean flexiblePutProperty(Object propertyMap, Object property) {
        try {
            for (Method method : allMethods(propertyMap.getClass())) {
                if (!"put".equals(method.getName()) || method.getParameterTypes().length != 2) continue;
                Class<?>[] types = method.getParameterTypes();
                if (!types[0].isAssignableFrom(String.class) && types[0] != Object.class && types[0] != CharSequence.class) continue;
                if (!types[1].isInstance(property) && types[1] != Object.class) continue;
                method.setAccessible(true);
                Object result = method.invoke(propertyMap, "textures", property);
                return !(result instanceof Boolean) || ((Boolean) result).booleanValue();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean copyOnlinePlayerSkin(Object targetProfile, String skinName) {
        try {
            Player online = findOnlinePlayerIgnoreCase(skinName);
            if (online == null) {
                return false;
            }
            Object sourceProfile = findGameProfile(online);
            if (sourceProfile == null) {
                Object handle = invokeFirst(online, new String[]{"getHandle"}, new Class[]{}, new Object[]{});
                sourceProfile = findGameProfile(handle);
            }
            if (sourceProfile == null) {
                return false;
            }
            Object sourceProps = profileProperties(sourceProfile);
            Object targetProps = profileProperties(targetProfile);
            Object textures = invokeFlexible(sourceProps, "get", "textures");
            boolean any = false;
            if (textures instanceof Iterable) {
                clearTextureProperties(targetProps);
                for (Object property : (Iterable<?>) textures) {
                    any = flexiblePutProperty(targetProps, property) || any;
                }
            }
            return any;
        } catch (Throwable throwable) {
            getLogger().fine("Could not copy online skin for " + skinName + ": " + rootMessage(throwable));
            return false;
        }
    }

    private Object findGameProfile(Object source) {
        if (source == null) return null;
        try {
            Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
            if (profileClass.isInstance(source)) return source;
            Class<?> clazz = source.getClass();
            while (clazz != null) {
                for (Method method : clazz.getDeclaredMethods()) {
                    try {
                        if (method.getParameterTypes().length == 0 && profileClass.isAssignableFrom(method.getReturnType())) {
                            method.setAccessible(true);
                            Object value = method.invoke(source);
                            if (profileClass.isInstance(value)) return value;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                for (Field field : clazz.getDeclaredFields()) {
                    try {
                        if (profileClass.isAssignableFrom(field.getType())) {
                            field.setAccessible(true);
                            Object value = field.get(source);
                            if (profileClass.isInstance(value)) return value;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean fetchMojangSkin(Object targetProfile, String skinName) {
        SkinResult result = fetchMojangSkinResult(skinName);
        if (!result.hasTexture()) {
            getLogger().fine("Could not fetch Mojang skin for " + skinName + ": " + result.error);
            return false;
        }
        return putTextureProperty(targetProfile, result.value, result.signature);
    }

    private String httpGet(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "Rug/" + VERSION);
        int code = connection.getResponseCode();
        if (code == 429) {
            // Mojang/Paper rate limit. Keep this quiet and let callers fall back
            // to the session cache or the default skin instead of spamming logs.
            throw new IllegalStateException("rate limited (HTTP 429)");
        }
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + " from " + urlText);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    private String jsonValue(String json, String key) {
        if (json == null) return null;
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String propertyValue(String json, String key) {
        return jsonValue(json, key);
    }

    private void installNoopOutboundHandler(Object channel) {
        try {
            Object pipeline = invokeRequired(channel, "pipeline", new Class[]{}, new Object[]{});
            Class<?> outboundClass = Class.forName("io.netty.channel.ChannelOutboundHandler");
            Class<?> handlerClass = Class.forName("io.netty.channel.ChannelHandler");
            Object handler = java.lang.reflect.Proxy.newProxyInstance(
                    outboundClass.getClassLoader(),
                    new Class[]{outboundClass},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("write".equals(methodName) && args != null && args.length >= 3) {
                            releaseNetty(args[1]);
                            promiseSuccess(args[2]);
                            return null;
                        }
                        if (("bind".equals(methodName) || "connect".equals(methodName) || "disconnect".equals(methodName) || "close".equals(methodName) || "deregister".equals(methodName))
                                && args != null && args.length >= 2) {
                            promiseSuccess(args[args.length - 1]);
                            return null;
                        }
                        return defaultValue(method.getReturnType());
                    });
            pipeline.getClass().getMethod("addLast", String.class, handlerClass).invoke(pipeline, "rug_noop_outbound_" + System.nanoTime(), handler);
        } catch (Throwable ignored) {
        }
    }

    private void releaseNetty(Object packet) {
        try {
            Class.forName("io.netty.util.ReferenceCountUtil").getMethod("release", Object.class).invoke(null, packet);
        } catch (Throwable ignored) {
        }
    }

    private void promiseSuccess(Object promise) {
        if (promise == null) {
            return;
        }
        invokeIfExists(promise, "setSuccess", new Class[]{}, new Object[]{});
        invokeIfExists(promise, "trySuccess", new Class[]{}, new Object[]{});
    }

    private Object defaultClientInformation() throws Exception {
        Class<?> infoClass = firstClass("net.minecraft.server.level.ClientInformation", "net.minecraft.server.level.ClientInformation");
        for (Method method : infoClass.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0 && infoClass.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                return method.invoke(null);
            }
        }
        for (Field field : infoClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && infoClass.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field.get(null);
            }
        }
        Constructor<?> ctor = infoClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object[] args = new Object[ctor.getParameterCount()];
        Class<?>[] types = ctor.getParameterTypes();
        for (int i = 0; i < args.length; i++) {
            args[i] = defaultValue(types[i]);
        }
        return ctor.newInstance(args);
    }

    private Object constructServerPlayer(Class<?> playerClass, Object minecraftServer, Object nmsWorld, Object gameProfile, Object clientInfo) throws Exception {
        for (Constructor<?> ctor : playerClass.getDeclaredConstructors()) {
            Class<?>[] types = ctor.getParameterTypes();
            if (types.length == 4
                    && types[0].isAssignableFrom(minecraftServer.getClass())
                    && types[1].isAssignableFrom(nmsWorld.getClass())
                    && types[2].isAssignableFrom(gameProfile.getClass())
                    && (clientInfo == null || types[3].isAssignableFrom(clientInfo.getClass()))) {
                ctor.setAccessible(true);
                return ctor.newInstance(minecraftServer, nmsWorld, gameProfile, clientInfo);
            }
        }
        return null;
    }

    private Object createNmsConnection() throws Exception {
        Class<?> connectionClass = firstClass("net.minecraft.network.NetworkManager", "net.minecraft.network.Connection");
        Class<?> directionClass = firstClass("net.minecraft.network.protocol.EnumProtocolDirection", "net.minecraft.network.protocol.PacketFlow");
        Object direction = enumOrField(directionClass, "a", "SERVERBOUND");
        Object connection = null;
        for (Constructor<?> ctor : connectionClass.getDeclaredConstructors()) {
            Class<?>[] types = ctor.getParameterTypes();
            if (types.length == 1 && types[0].isAssignableFrom(directionClass)) {
                ctor.setAccessible(true);
                connection = ctor.newInstance(direction);
                break;
            }
        }
        if (connection == null) {
            return null;
        }

        Object channel = createFakeNettyChannel();
        setFirstFieldAssignable(connection, Class.forName("io.netty.channel.Channel"), channel);
        setFirstFieldAssignable(connection, java.net.SocketAddress.class, new java.net.InetSocketAddress("127.0.0.1", 0));
        return connection;
    }

    private Object createFakeNettyChannel() throws Exception {
        Class<?> channelClass = Class.forName("io.netty.channel.Channel");
        Class<?> pipelineClass = Class.forName("io.netty.channel.ChannelPipeline");
        Class<?> promiseClass = Class.forName("io.netty.channel.ChannelPromise");
        Class<?> futureClass = Class.forName("io.netty.channel.ChannelFuture");
        Class<?> configClass = Class.forName("io.netty.channel.ChannelConfig");
        Class<?> metadataClass = Class.forName("io.netty.channel.ChannelMetadata");
        Class<?> eventLoopClass = Class.forName("io.netty.channel.DefaultEventLoop");

        final Object[] channelRef = new Object[1];
        final Object eventLoop = eventLoopClass.getConstructor().newInstance();
        final Object metadata = metadataClass.getConstructor(boolean.class).newInstance(true);
        final Object[] pipelineRef = new Object[1];
        final Object[] promiseRef = new Object[1];
        final Object[] configRef = new Object[1];

        java.lang.reflect.InvocationHandler futureHandler = (proxy, method, args) -> fakeNettyReturn(method.getReturnType(), proxy, channelRef[0], pipelineRef[0], promiseRef[0], configRef[0], eventLoop, metadata);
        Object future = java.lang.reflect.Proxy.newProxyInstance(futureClass.getClassLoader(), new Class[]{futureClass}, futureHandler);
        java.lang.reflect.InvocationHandler promiseHandler = (proxy, method, args) -> {
            String mn = method.getName();
            if (mn.equals("setSuccess") || mn.equals("trySuccess") || mn.equals("setFailure") || mn.equals("tryFailure")) return proxy;
            if (mn.equals("isSuccess") || mn.equals("isDone")) return true;
            if (mn.equals("cause")) return null;
            return fakeNettyReturn(method.getReturnType(), proxy, channelRef[0], pipelineRef[0], proxy, configRef[0], eventLoop, metadata);
        };
        Object promise = java.lang.reflect.Proxy.newProxyInstance(promiseClass.getClassLoader(), new Class[]{promiseClass}, promiseHandler);
        promiseRef[0] = promise;

        java.lang.reflect.InvocationHandler pipelineHandler = (proxy, method, args) -> {
            String mn = method.getName();
            if (mn.equals("write") || mn.equals("writeAndFlush")) {
                if (args != null && args.length > 0) releaseNetty(args[0]);
                if (args != null && args.length > 1 && promiseClass.isInstance(args[1])) return args[1];
                return future;
            }
            if (mn.equals("bind") || mn.equals("connect") || mn.equals("disconnect") || mn.equals("close") || mn.equals("deregister")) {
                if (args != null && args.length > 0 && promiseClass.isInstance(args[args.length - 1])) return args[args.length - 1];
                return future;
            }
            if (mn.equals("newPromise") || mn.equals("voidPromise")) return promise;
            if (mn.equals("newSucceededFuture")) return future;
            if (mn.equals("channel")) return channelRef[0];
            return fakeNettyReturn(method.getReturnType(), proxy, channelRef[0], proxy, promise, configRef[0], eventLoop, metadata);
        };
        Object pipeline = java.lang.reflect.Proxy.newProxyInstance(pipelineClass.getClassLoader(), new Class[]{pipelineClass}, pipelineHandler);
        pipelineRef[0] = pipeline;

        java.lang.reflect.InvocationHandler configHandler = (proxy, method, args) -> fakeNettyReturn(method.getReturnType(), proxy, channelRef[0], pipeline, promise, proxy, eventLoop, metadata);
        Object config = java.lang.reflect.Proxy.newProxyInstance(configClass.getClassLoader(), new Class[]{configClass}, configHandler);
        configRef[0] = config;

        java.lang.reflect.InvocationHandler channelHandler = (proxy, method, args) -> {
            String mn = method.getName();
            if (mn.equals("pipeline")) return pipeline;
            if (mn.equals("config")) return config;
            if (mn.equals("eventLoop")) return eventLoop;
            if (mn.equals("metadata")) return metadata;
            if (mn.equals("isOpen") || mn.equals("isActive") || mn.equals("isRegistered") || mn.equals("isWritable")) return true;
            if (mn.equals("localAddress") || mn.equals("remoteAddress")) return new java.net.InetSocketAddress("127.0.0.1", 0);
            if (mn.equals("write") || mn.equals("writeAndFlush")) {
                if (args != null && args.length > 0) releaseNetty(args[0]);
                return future;
            }
            if (mn.equals("newPromise") || mn.equals("voidPromise")) return promise;
            if (mn.equals("closeFuture") || mn.equals("newSucceededFuture")) return future;
            if (mn.equals("close") || mn.equals("disconnect") || mn.equals("deregister") || mn.equals("bind") || mn.equals("connect")) return future;
            return fakeNettyReturn(method.getReturnType(), proxy, proxy, pipeline, promise, config, eventLoop, metadata);
        };
        Object channel = java.lang.reflect.Proxy.newProxyInstance(channelClass.getClassLoader(), new Class[]{channelClass}, channelHandler);
        channelRef[0] = channel;
        return channel;
    }

    private Object fakeNettyReturn(Class<?> type, Object self, Object channel, Object pipeline, Object promise, Object config, Object eventLoop, Object metadata) throws Exception {
        if (type == Void.TYPE) return null;
        if (type == Boolean.TYPE) return false;
        if (type == Integer.TYPE) return 0;
        if (type == Long.TYPE) return 0L;
        if (type == Double.TYPE) return 0.0d;
        if (type == Float.TYPE) return 0.0f;
        if (type.isInstance(self)) return self;
        if (channel != null && type.isInstance(channel)) return channel;
        if (pipeline != null && type.isInstance(pipeline)) return pipeline;
        if (promise != null && type.isInstance(promise)) return promise;
        if (config != null && type.isInstance(config)) return config;
        if (eventLoop != null && type.isInstance(eventLoop)) return eventLoop;
        if (metadata != null && type.isInstance(metadata)) return metadata;
        if (type == String.class) return "RugFakeChannel";
        if (type == java.net.SocketAddress.class) return new java.net.InetSocketAddress("127.0.0.1", 0);
        if (type.isEnum()) return type.getEnumConstants()[0];
        return null;
    }

    private void initializeNmsPipeline(Class<?> connectionClass, Object channel, Object direction) {
        try {
            Object pipeline = invokeRequired(channel, "pipeline", new Class[]{}, new Object[]{});
            for (Method method : connectionClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 4
                        && types[0].getName().equals("io.netty.channel.ChannelPipeline")
                        && types[1].isAssignableFrom(direction.getClass())
                        && types[2] == boolean.class) {
                    method.setAccessible(true);
                    method.invoke(null, pipeline, direction, false, null);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private Object createCommonListenerCookie(Object gameProfile) throws Exception {
        Class<?> cookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
        for (Method method : cookieClass.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) && cookieClass.isAssignableFrom(method.getReturnType())
                    && types.length == 2 && types[0].isAssignableFrom(gameProfile.getClass()) && types[1] == boolean.class) {
                method.setAccessible(true);
                return method.invoke(null, gameProfile, false);
            }
        }
        return null;
    }

    private boolean invokePlaceNewPlayer(Object playerList, Object connection, Object nmsPlayer, Object cookie) {
        for (Method method : playerList.getClass().getMethods()) {
            if (tryInvokePlace(method, playerList, connection, nmsPlayer, cookie)) return true;
        }
        for (Method method : playerList.getClass().getDeclaredMethods()) {
            if (tryInvokePlace(method, playerList, connection, nmsPlayer, cookie)) return true;
        }
        return false;
    }

    private boolean tryInvokePlace(Method method, Object target, Object connection, Object nmsPlayer, Object cookie) {
        try {
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 3
                    && types[0].isAssignableFrom(connection.getClass())
                    && types[1].isAssignableFrom(nmsPlayer.getClass())
                    && types[2].isAssignableFrom(cookie.getClass())) {
                method.setAccessible(true);
                method.invoke(target, connection, nmsPlayer, cookie);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean tryAddEntityToWorld(Object nmsWorld, Object nmsPlayer) {
        String[] names = {"addFreshEntity", "addEntity", "b", "a"};
        for (String methodName : names) {
            for (Method method : nmsWorld.getClass().getMethods()) {
                if (tryInvokeOneArg(method, nmsWorld, methodName, nmsPlayer)) return true;
            }
            for (Method method : nmsWorld.getClass().getDeclaredMethods()) {
                if (tryInvokeOneArg(method, nmsWorld, methodName, nmsPlayer)) return true;
            }
        }
        return false;
    }

    private boolean tryInvokeOneArg(Method method, Object target, String name, Object arg) {
        try {
            if (!method.getName().equals(name)) return false;
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 1 && types[0].isAssignableFrom(arg.getClass())) {
                method.setAccessible(true);
                method.invoke(target, arg);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void moveNmsPlayer(Object nmsPlayer, Location loc) {
        double x = readDouble(loc, "getX"), y = readDouble(loc, "getY"), z = readDouble(loc, "getZ");
        float yaw = readFloat(loc, "getYaw"), pitch = readFloat(loc, "getPitch");
        invokeIfExists(nmsPlayer, "a", new Class[]{double.class, double.class, double.class, float.class, float.class}, x, y, z, yaw, pitch);
        invokeIfExists(nmsPlayer, "moveTo", new Class[]{double.class, double.class, double.class, float.class, float.class}, x, y, z, yaw, pitch);
        invokeIfExists(nmsPlayer, "absMoveTo", new Class[]{double.class, double.class, double.class, float.class, float.class}, x, y, z, yaw, pitch);
        invokeIfExists(nmsPlayer, "setPos", new Class[]{double.class, double.class, double.class}, x, y, z);
        applyRotationFields(nmsPlayer, yaw, pitch);
        Object bukkit = invokeFirst(nmsPlayer, new String[]{"getBukkitEntity"}, new Class[]{}, new Object[]{});
        if (bukkit instanceof Entity) {
            try { ((Entity) bukkit).teleport(loc); } catch (Throwable ignored) {}
        }
    }

    private void applyRotationFields(Object nmsPlayer, float yaw, float pitch) {
        invokeIfExists(nmsPlayer, "setYRot", new Class[]{float.class}, yaw);
        invokeIfExists(nmsPlayer, "setXRot", new Class[]{float.class}, pitch);
        invokeIfExists(nmsPlayer, "setRot", new Class[]{float.class, float.class}, yaw, pitch);
        setFloatFieldIfExists(nmsPlayer, "yRot", yaw);
        setFloatFieldIfExists(nmsPlayer, "xRot", pitch);
        setFloatFieldIfExists(nmsPlayer, "yHeadRot", yaw);
        setFloatFieldIfExists(nmsPlayer, "yBodyRot", yaw);
        setFloatFieldIfExists(nmsPlayer, "headYaw", yaw);
        setFloatFieldIfExists(nmsPlayer, "bodyYaw", yaw);
    }

    private void setFloatFieldIfExists(Object target, String name, float value) {
        if (target == null) return;
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                if (field.getType() == float.class || field.getType() == Float.class) {
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                }
            } catch (Throwable ignored) {}
            clazz = clazz.getSuperclass();
        }
    }

    private int removeNmsFake(FakeHandle fake, boolean markRemoving) {
        int removed = 0;
        Object nms = fake == null ? null : fake.nmsPlayer;
        Object bukkit = fake == null ? null : fake.bukkitPlayer;
        if (bukkit instanceof Entity) {
            Entity entity = (Entity) bukkit;
            if (markRemoving) invokeIfExists(entity, "addScoreboardTag", new Class[]{String.class}, REMOVING_TAG);
            removed++;
        }
        hardRemoveNmsPlayer(nms, bukkit);
        final Object nmsLater = nms;
        final Object bukkitLater = bukkit;
        runLater(new Runnable() {
            @Override
            public void run() {
                hardRemoveNmsPlayer(nmsLater, bukkitLater);
            }
        }, 1L);
        runLater(new Runnable() {
            @Override
            public void run() {
                hardRemoveNmsPlayer(nmsLater, bukkitLater);
            }
        }, 10L);
        return removed;
    }

    private void hardRemoveNmsPlayer(Object nmsPlayer, Object bukkitPlayer) {
        // If we only have the Bukkit entity (e.g. an untracked dead body), pull
        // the NMS handle off it so the level/PlayerList removal still runs.
        if (nmsPlayer == null && bukkitPlayer instanceof Player) {
            nmsPlayer = invokeFirst(bukkitPlayer, new String[]{"getHandle"}, new Class[]{}, new Object[]{});
        }
        // Tell every real client to drop the tab-list entry AND the in-world
        // entity. Clientless fake players are not always untracked automatically,
        // which is exactly how a dead "corpse" gets left lying around. Send these
        // first, while the entity id / uuid are still readable.
        broadcastFakeRemovalPackets(nmsPlayer, bukkitPlayer);
        if (nmsPlayer != null) {
            tryRemoveFromPlayerList(nmsPlayer);
            tryRemoveFromLevel(nmsPlayer);
            Object reason = removalReason();
            invokeAnyOneArg(nmsPlayer, new String[]{"remove", "setRemoved"}, reason);
            invokeIfExists(nmsPlayer, "discard", new Class[]{}, new Object[]{});
            invokeIfExists(nmsPlayer, "remove", new Class[]{}, new Object[]{});
            invokeIfExists(nmsPlayer, "ai", new Class[]{}, new Object[]{});
        }
        if (bukkitPlayer instanceof Entity) {
            if (bukkitPlayer instanceof Player) {
                tryRemoveBukkitPlayer((Player) bukkitPlayer);
            }
            invokeIfExists(bukkitPlayer, "kickPlayer", new Class[]{String.class}, "Rug removed");
            invokeIfExists(bukkitPlayer, "remove", new Class[]{}, new Object[]{});
        }
    }

    /**
     * Sends ClientboundPlayerInfoRemovePacket (drop the tab-list/profile entry)
     * and ClientboundRemoveEntitiesPacket (despawn the body client-side) to every
     * real viewer. This is what makes a dead fake player actually disappear
     * instead of leaving a stuck corpse. All reflective, no packet library.
     */
    private void broadcastFakeRemovalPackets(Object nmsPlayer, Object bukkitPlayer) {
        try {
            int entityId = readEntityId(bukkitPlayer, nmsPlayer);
            UUID uuid = uuidOf(bukkitPlayer);
            if (uuid == null && nmsPlayer != null) {
                try { uuid = (UUID) invokeFlexible(nmsPlayer, "getUUID"); } catch (Throwable ignored) {}
            }
            Object infoRemove = uuid == null ? null : constructPlayerInfoRemovePacket(uuid);
            Object entityRemove = entityId == 0 ? null : constructRemoveEntitiesPacket(entityId);
            if (infoRemove == null && entityRemove == null) return;
            for (Player viewer : onlinePlayers()) {
                if (isLikelyFakeOnlinePlayer(viewer)) continue;
                if (infoRemove != null) sendPacket(viewer, infoRemove);
                if (entityRemove != null) sendPacket(viewer, entityRemove);
            }
        } catch (Throwable ignored) {
        }
    }

    private int readEntityId(Object bukkitPlayer, Object nmsPlayer) {
        int id = 0;
        try { if (bukkitPlayer != null) id = ((Number) invokeFlexible(bukkitPlayer, "getEntityId")).intValue(); } catch (Throwable ignored) {}
        if (id == 0 && nmsPlayer != null) {
            try { id = ((Number) invokeFlexible(nmsPlayer, "getId")).intValue(); } catch (Throwable ignored) {}
        }
        return id;
    }

    private Object constructPlayerInfoRemovePacket(UUID uuid) {
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            List<UUID> ids = new ArrayList<UUID>();
            ids.add(uuid);
            for (Constructor<?> ctor : packetClass.getDeclaredConstructors()) {
                Class<?>[] types = ctor.getParameterTypes();
                if (types.length == 1 && types[0].isAssignableFrom(List.class)) {
                    ctor.setAccessible(true);
                    return ctor.newInstance(ids);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object constructRemoveEntitiesPacket(int entityId) {
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
            for (Constructor<?> ctor : packetClass.getDeclaredConstructors()) {
                Class<?>[] types = ctor.getParameterTypes();
                if (types.length == 1 && types[0] == int[].class) {
                    ctor.setAccessible(true);
                    return ctor.newInstance((Object) new int[]{entityId});
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void tryRemoveFromLevel(Object nmsPlayer) {
        if (nmsPlayer == null) return;
        Object reason = removalReason();
        try {
            Object level = null;
            for (String method : new String[]{"serverLevel", "level", "getCommandSenderWorld", "getLevel"}) {
                try { level = invokeFlexible(nmsPlayer, method); break; } catch (Throwable ignored) {}
            }
            if (level == null) return;
            for (Method method : allMethods(level.getClass())) {
                try {
                    Class<?>[] types = method.getParameterTypes();
                    if (types.length == 2 && types[0].isInstance(nmsPlayer) && (reason == null || types[1].isInstance(reason))) {
                        method.setAccessible(true);
                        method.invoke(level, nmsPlayer, reason);
                        return;
                    }
                    if (types.length == 1 && types[0].isInstance(nmsPlayer)) {
                        method.setAccessible(true);
                        method.invoke(level, nmsPlayer);
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void tryRemoveFromPlayerList(Object nmsPlayer) {
        if (nmsPlayer == null) {
            return;
        }
        try {
            Object craftServer = invokeStaticRequired(Bukkit.class, "getServer", new Class[]{}, new Object[]{});
            Object playerList = invokeFirst(craftServer, new String[]{"getHandle"}, new Class[]{}, new Object[]{});
            if (playerList == null) {
                return;
            }
            invokeAnyOneArg(playerList, new String[]{"remove", "removePlayer", "disconnect"}, nmsPlayer);
        } catch (Throwable ignored) {
        }
    }
    private int purgeAllFakeOnlinePlayers(boolean markRemoving) {
        int removed = 0;
        for (Player player : new ArrayList<Player>(onlinePlayers())) {
            if (isLikelyFakeOnlinePlayer(player)) {
                if (markRemoving) invokeIfExists(player, "addScoreboardTag", new Class[]{String.class}, REMOVING_TAG);
                Object handle = invokeFirst(player, new String[]{"getHandle"}, new Class[]{}, new Object[]{});
                hardRemoveNmsPlayer(handle, player);
                invokeIfExists(player, "kickPlayer", new Class[]{String.class}, "Rug purge");
                invokeIfExists(player, "remove", new Class[]{}, new Object[]{});
                removed++;
            }
        }
        return removed;
    }

    private int purgeName(String rawName, boolean markRemoving) {
        String name = sanitizeName(rawName);
        int removed = 0;
        for (Player player : new ArrayList<Player>(onlinePlayers())) {
            if (player.getName().equalsIgnoreCase(name) && isLikelyFakeOnlinePlayer(player)) {
                if (markRemoving) invokeIfExists(player, "addScoreboardTag", new Class[]{String.class}, REMOVING_TAG);
                Object handle = invokeFirst(player, new String[]{"getHandle"}, new Class[]{}, new Object[]{});
                hardRemoveNmsPlayer(handle, player);
                invokeIfExists(player, "kickPlayer", new Class[]{String.class}, "Rug purge");
                invokeIfExists(player, "remove", new Class[]{}, new Object[]{});
                removed++;
            }
        }
        return removed;
    }

    private int countFakeOnlinePlayers() {
        int count = 0;
        for (Player player : onlinePlayers()) {
            if (isLikelyFakeOnlinePlayer(player)) count++;
        }
        return count;
    }

    private boolean isLikelyFakeOnlinePlayer(Player player) {
        try {
            if (hasTag(player, NMS_TAG)) return true;
            String address = String.valueOf(player.getAddress());
            return address.contains("127.0.0.1") && address.endsWith(":0");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void tryRemoveBukkitPlayer(Player player) {
        try {
            Object handle = invokeFirst(player, new String[]{"getHandle"}, new Class[]{}, new Object[]{});
            if (handle != null) {
                tryRemoveFromPlayerList(handle);
                Object connection = getFieldAssignable(handle, "net.minecraft.server.network.ServerGamePacketListenerImpl", "net.minecraft.server.network.PlayerConnection");
                if (connection != null) {
                    invokeIfExists(connection, "disconnect", new Class[]{String.class}, "Rug removed");
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private Object getFieldAssignable(Object target, String... classNames) {
        if (target == null) return null;
        List<Class<?>> wanted = new ArrayList<Class<?>>();
        for (String name : classNames) {
            try { wanted.add(Class.forName(name)); } catch (Throwable ignored) {}
        }
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value == null) continue;
                    for (Class<?> type : wanted) {
                        if (type.isInstance(value)) return value;
                    }
                } catch (Throwable ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }


    private Object removalReason() {
        try {
            Class<?> reasonClass = Class.forName("net.minecraft.world.entity.Entity$RemovalReason");
            if (reasonClass.isEnum()) {
                for (String name : new String[]{"DISCARDED", "KILLED", "UNLOADED_TO_CHUNK"}) {
                    try {
                        return Enum.valueOf((Class<? extends Enum>) reasonClass.asSubclass(Enum.class), name);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean invokeAnyOneArg(Object target, String[] names, Object arg) {
        if (target == null || arg == null) {
            return false;
        }
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList(names));
        for (Method method : target.getClass().getMethods()) {
            if (tryInvokeAllowedOneArg(method, target, allowed, arg)) return true;
        }
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (tryInvokeAllowedOneArg(method, target, allowed, arg)) return true;
        }
        return false;
    }

    private boolean tryInvokeAllowedOneArg(Method method, Object target, Set<String> allowed, Object arg) {
        try {
            if (!allowed.contains(method.getName())) return false;
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 1 && (arg == null || types[0].isAssignableFrom(arg.getClass()))) {
                method.setAccessible(true);
                method.invoke(target, arg);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void runLater(Runnable runnable, long delayTicks) {
        try {
            Object scheduler = invokeStaticRequired(Bukkit.class, "getScheduler", new Class[]{}, new Object[]{});
            for (Method method : scheduler.getClass().getMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (method.getName().equals("runTaskLater") && types.length == 3
                        && types[0].isAssignableFrom(getClass())
                        && Runnable.class.isAssignableFrom(types[1])
                        && (types[2] == long.class || types[2] == Long.class || types[2] == int.class || types[2] == Integer.class)) {
                    method.setAccessible(true);
                    method.invoke(scheduler, this, runnable, Long.valueOf(delayTicks));
                    return;
                }
            }
        } catch (Throwable throwable) {
            getLogger().fine("Could not schedule delayed Rug task: " + rootMessage(throwable));
        }
        if (delayTicks <= 0L) {
            runnable.run();
        }
    }

    private Object invokeFirst(Object target, String[] names, Class<?>[] types, Object... args) {
        for (String name : names) {
            try {
                return invokeRequired(target, name, types, args);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private Class<?> firstClass(String... names) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ex) {
                last = ex;
            }
        }
        throw last == null ? new ClassNotFoundException("missing class") : last;
    }

    private Object enumOrField(Class<?> clazz, String obfField, String enumName) throws Exception {
        try {
            Field field = clazz.getField(obfField);
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable ignored) {
        }
        if (clazz.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) clazz.asSubclass(Enum.class), enumName);
        }
        Field field = clazz.getDeclaredField(enumName);
        field.setAccessible(true);
        return field.get(null);
    }

    private void setFirstFieldAssignable(Object target, Class<?> type, Object value) {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    if (type.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        field.set(target, value);
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return (char) 0;
        return null;
    }

    private double readDouble(Object target, String method) {
        try { return ((Number) invokeRequired(target, method, new Class[]{}, new Object[]{})).doubleValue(); } catch (Throwable ignored) { return 0.0; }
    }

    private float readFloat(Object target, String method) {
        try { return ((Number) invokeRequired(target, method, new Class[]{}, new Object[]{})).floatValue(); } catch (Throwable ignored) { return 0.0f; }
    }


    private Object spawnEntity(Location loc, String entityTypeName) {
        try {
            Object world = invokeRequired(loc, "getWorld", new Class[]{}, new Object[]{});
            Class<?> entityTypeClass = Class.forName("org.bukkit.entity.EntityType");
            Object type = Enum.valueOf(entityTypeClass.asSubclass(Enum.class), entityTypeName);
            return invokeRequired(world, "spawnEntity", new Class[]{Location.class, entityTypeClass}, loc, type);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Could not spawn " + entityTypeName + ": " + rootMessage(throwable), throwable);
        }
    }

    private void tagEntity(Object entity, String mainTag, String name, String skinName) {
        invokeIfExists(entity, "addScoreboardTag", new Class[]{String.class}, mainTag);
        invokeIfExists(entity, "addScoreboardTag", new Class[]{String.class}, nameTag(name));
        invokeIfExists(entity, "addScoreboardTag", new Class[]{String.class}, skinTag(skinName));
    }

    private void equipPlayerLook(Object entity, String skinName, SkinResult skin) {
        try {
            Object equipment = invokeRequired(entity, "getEquipment", new Class[]{}, new Object[]{});
            Object head = makePlayerHead(skinName, skin);
            if (head != null) {
                invokeIfExists(equipment, "setHelmet", new Class[]{Class.forName("org.bukkit.inventory.ItemStack")}, head);
            }
            setEquipment(equipment, "setChestplate", "LEATHER_CHESTPLATE");
            setEquipment(equipment, "setLeggings", "LEATHER_LEGGINGS");
            setEquipment(equipment, "setBoots", "LEATHER_BOOTS");
        } catch (Throwable throwable) {
            getLogger().fine("Could not equip player look: " + rootMessage(throwable));
        }
    }

    private void setEquipment(Object equipment, String method, String materialName) {
        try {
            Object item = makeItem(materialName);
            if (item != null) {
                invokeIfExists(equipment, method, new Class[]{Class.forName("org.bukkit.inventory.ItemStack")}, item);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Builds the visual-backend helmet head. We prefer the actual resolved skin
     * texture (so the dummy wears the requested player's face instead of the
     * default fallback head). We fall back to an owner-based head, then to a
     * plain head, and never spam warnings about missing skins.
     */
    private Object makePlayerHead(String skinName, SkinResult skin) {
        Object item = makeItem("PLAYER_HEAD");
        if (item == null || !ruleBool("playerHeadEnabled", true)) {
            return item;
        }
        // 1) Best: stamp the resolved texture straight onto the head's profile.
        if (skin != null && skin.hasTexture() && applyHeadTexture(item, skinName, skin)) {
            return item;
        }
        // 2) Fallback: owner-based head (resolves online/known players' faces).
        try {
            Object meta = invokeRequired(item, "getItemMeta", new Class[]{}, new Object[]{});
            Object offlinePlayer = invokeStaticRequired(Bukkit.class, "getOfflinePlayer", new Class[]{String.class}, skinName);
            invokeIfExists(meta, "setOwningPlayer", new Class[]{Class.forName("org.bukkit.OfflinePlayer")}, offlinePlayer);
            invokeIfExists(item, "setItemMeta", new Class[]{Class.forName("org.bukkit.inventory.meta.ItemMeta")}, meta);
        } catch (Throwable throwable) {
            getLogger().fine("Could not build player head for " + skinName + ": " + rootMessage(throwable));
        }
        // 3) Plain head if nothing resolved. Clean default, no warning.
        return item;
    }

    /** Writes the resolved skin texture onto a PLAYER_HEAD via its Paper profile. */
    private boolean applyHeadTexture(Object headItem, String skinName, SkinResult skin) {
        try {
            Object meta = invokeRequired(headItem, "getItemMeta", new Class[]{}, new Object[]{});
            if (meta == null) return false;
            UUID id = UUID.nameUUIDFromBytes(("RugHead:" + sanitizeName(skinName)).getBytes(StandardCharsets.UTF_8));
            Object profile = invokeStaticFlexible(Bukkit.class, "createProfile", id, sanitizeName(skinName));
            if (profile == null) return false;
            if (!addPaperProfileProperty(profile, skin.value, skin.signature)) {
                return false;
            }
            // SkullMeta#setPlayerProfile(PlayerProfile) on Paper.
            invokeFlexible(meta, "setPlayerProfile", profile);
            invokeIfExists(headItem, "setItemMeta", new Class[]{Class.forName("org.bukkit.inventory.meta.ItemMeta")}, meta);
            return true;
        } catch (Throwable throwable) {
            getLogger().fine("Could not stamp skin texture on head for " + skinName + ": " + rootMessage(throwable));
            return false;
        }
    }

    private Object makeItem(String materialName) {
        try {
            Class<?> materialClass = Class.forName("org.bukkit.Material");
            Object material = Enum.valueOf(materialClass.asSubclass(Enum.class), materialName);
            Class<?> itemStackClass = Class.forName("org.bukkit.inventory.ItemStack");
            Constructor<?> constructor = itemStackClass.getConstructor(materialClass, int.class);
            return constructor.newInstance(material, 1);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private int removeNmsEntities(String rawName, boolean markRemoving) {
        List<Object> entities = findByTag(NMS_TAG, rawName);
        int removed = 0;
        for (Object entity : entities) {
            try {
                if (markRemoving) invokeIfExists(entity, "addScoreboardTag", new Class[]{String.class}, REMOVING_TAG);
                Object handle = null;
                if (entity instanceof Player) {
                    handle = invokeFirst(entity, new String[]{"getHandle"}, new Class[]{}, new Object[]{});
                    tryRemoveBukkitPlayer((Player) entity);
                }
                hardRemoveNmsPlayer(handle, entity);
                invokeIfExists(entity, "remove", new Class[]{}, new Object[]{});
                removed++;
            } catch (Throwable ignored) {
            }
        }
        return removed;
    }

    private int removeTagged(String mainTag, String rawName, boolean markRemoving) {
        List<Object> entities = findByTag(mainTag, rawName);
        int removed = 0;
        for (Object entity : entities) {
            if (markRemoving) {
                invokeIfExists(entity, "addScoreboardTag", new Class[]{String.class}, REMOVING_TAG);
            }
            invokeIfExists(entity, "remove", new Class[]{}, new Object[]{});
            removed++;
        }
        return removed;
    }

    private List<Object> findByTag(String mainTag, String rawName) {
        List<Object> matches = new ArrayList<>();
        String wantedNameTag = rawName == null ? null : nameTag(rawName);
        try {
            Object worlds = invokeStaticRequired(Bukkit.class, "getWorlds", new Class[]{}, new Object[]{});
            if (!(worlds instanceof Iterable)) {
                return matches;
            }
            for (Object world : (Iterable<?>) worlds) {
                Object entities = invokeRequired(world, "getEntities", new Class[]{}, new Object[]{});
                if (!(entities instanceof Iterable)) {
                    continue;
                }
                for (Object entity : (Iterable<?>) entities) {
                    Set<String> tags = tagsOf(entity);
                    if (tags.contains(mainTag) && (wantedNameTag == null || tags.contains(wantedNameTag))) {
                        matches.add(entity);
                    }
                }
            }
        } catch (Throwable throwable) {
            getLogger().fine("Could not scan Rug entities: " + rootMessage(throwable));
        }
        return matches;
    }


    private void openMainGui(Player player) {
        try {
            Inventory inv = (Inventory) createInventoryReflect(27, GUI_TITLE);
            fillGui(inv);
            putGuiItem(inv, 10, Material.PLAYER_HEAD, COLOR + "aFake Players", Arrays.asList(
                    COLOR + "7Click for spawn/manage commands and",
                    COLOR + "7the list of tracked fake players.",
                    COLOR + "8Commands still work too."));
            putGuiItem(inv, 12, Material.COMPASS, COLOR + "eRules", Arrays.asList(
                    COLOR + "7Click to open the toggle/settings menu.",
                    COLOR + "8Good for non-command users."));
            putGuiItem(inv, 14, Material.NAME_TAG, COLOR + "bSkin / Profile Tools", Arrays.asList(
                    COLOR + "7Click to test skin lookup for your name",
                    COLOR + "7and see the current skin-layer mask."));
            putGuiItem(inv, 16, Material.BARRIER, COLOR + "cCleanup / Purge", Arrays.asList(
                    COLOR + "7Click to purge stuck Rug bots and",
                    COLOR + "7remove any leftover dead bodies."));
            putGuiItem(inv, 22, Material.BOOK, COLOR + "6Help / About", Arrays.asList(
                    COLOR + "7/rug player <name> spawn [skin]",
                    COLOR + "7/rug rules",
                    COLOR + "8Click for version/backend info."));
            player.openInventory(inv);
            player.sendMessage(PREFIX + "Opened the Rug control GUI.");
        } catch (Throwable throwable) {
            player.sendMessage(PREFIX + COLOR + "cGUI failed: " + safeText(rootMessage(throwable)));
            showRules(player);
        }
    }

    private void openRulesGui(Player player) {
        try {
            Inventory inv = (Inventory) createInventoryReflect(54, GUI_RULES_TITLE);
            fillGui(inv);
            putRuleItem(inv, 10, "playerBackend", Material.COMPASS);
            putRuleItem(inv, 11, "skinLayers", Material.PLAYER_HEAD);
            putRuleItem(inv, 12, "broadcastDeaths", Material.BELL);
            putRuleItem(inv, 13, "sendQuitMessage", Material.OAK_DOOR);
            putRuleItem(inv, 14, "allowDuplicateOnlineNames", Material.NAME_TAG);
            putRuleItem(inv, 15, "punchKnockback", Material.WIND_CHARGE);
            putRuleItem(inv, 16, "deathAlertSound", Material.WITHER_SKELETON_SKULL);
            putRuleItem(inv, 28, "hitboxEnabled", Material.IRON_SWORD);
            putRuleItem(inv, 29, "hitboxInvisible", Material.GLASS);
            putRuleItem(inv, 30, "hitboxFireProof", Material.LAVA_BUCKET);
            putRuleItem(inv, 31, "playerHeadEnabled", Material.PLAYER_HEAD);
            putRuleItem(inv, 32, "playerVisualEnabled", Material.ARMOR_STAND);
            putGuiItem(inv, 49, Material.ARROW, COLOR + "eBack", Arrays.asList(COLOR + "7Return to Rug Control."));
            player.openInventory(inv);
        } catch (Throwable throwable) {
            player.sendMessage(PREFIX + COLOR + "cGUI failed: " + safeText(rootMessage(throwable)));
            showRules(player);
        }
    }

    /** Fake-player list page: each tracked fake is a clickable head. */
    private void openPlayersGui(Player player) {
        try {
            Inventory inv = (Inventory) createInventoryReflect(54, GUI_PLAYERS_TITLE);
            fillGui(inv);
            int slot = 0;
            for (FakeHandle fake : new ArrayList<FakeHandle>(tracked.values())) {
                if (slot >= 45) break;
                putGuiItem(inv, slot, Material.PLAYER_HEAD, COLOR + "a" + fake.name, fakeStatusLore(fake));
                slot++;
            }
            if (tracked.isEmpty()) {
                putGuiItem(inv, 22, Material.BARRIER, COLOR + "7No fake players tracked", Arrays.asList(
                        COLOR + "8Use /rug player <name> spawn [skin]"));
            }
            putGuiItem(inv, 49, Material.ARROW, COLOR + "eBack", Arrays.asList(COLOR + "7Return to Rug Control."));
            player.openInventory(inv);
        } catch (Throwable throwable) {
            player.sendMessage(PREFIX + COLOR + "cGUI failed: " + safeText(rootMessage(throwable)));
        }
    }

    private List<String> fakeStatusLore(FakeHandle fake) {
        List<String> lore = new ArrayList<String>();
        boolean alive = true;
        try {
            if (fake.bukkitPlayer instanceof Player) {
                Object dead = invokeFlexible(fake.bukkitPlayer, "isDead");
                alive = !Boolean.TRUE.equals(dead);
            }
        } catch (Throwable ignored) {}
        lore.add(COLOR + "7State: " + (alive ? COLOR + "aalive" : COLOR + "cdead/removing"));
        lore.add(COLOR + "7Backend: " + COLOR + "f" + fake.backend);
        lore.add(COLOR + "7Skin: " + COLOR + "f" + fake.skinName);
        try {
            if (fake.bukkitPlayer instanceof Player) {
                Location loc = ((Player) fake.bukkitPlayer).getLocation();
                String world = loc.getWorld() == null ? "?" : loc.getWorld().getName();
                lore.add(COLOR + "7At: " + COLOR + "f" + world + " " + (int) loc.getX() + "," + (int) loc.getY() + "," + (int) loc.getZ());
            }
        } catch (Throwable ignored) {}
        lore.add(COLOR + "8Click to manage");
        return lore;
    }

    /** Per-fake detail page. The bound name is kept in guiBotName for the viewer. */
    private void openDetailGui(Player player, String name) {
        try {
            guiBotName.put(player.getUniqueId(), name);
            Inventory inv = (Inventory) createInventoryReflect(27, GUI_DETAIL_TITLE);
            fillGui(inv);
            FakeHandle fake = getTracked(name);
            putGuiItem(inv, 4, Material.PLAYER_HEAD, COLOR + "a" + name,
                    fake == null ? Arrays.asList(COLOR + "cNot tracked anymore") : fakeStatusLore(fake));
            putGuiItem(inv, 10, Material.IRON_SWORD, COLOR + "cKill", Arrays.asList(COLOR + "7Vanilla death, then full cleanup."));
            putGuiItem(inv, 11, Material.BARRIER, COLOR + "cRemove", Arrays.asList(COLOR + "7Quietly remove this fake player."));
            putGuiItem(inv, 12, Material.STICK, COLOR + "eRefresh hand", Arrays.asList(COLOR + "7Re-sync held item / equipment."));
            putGuiItem(inv, 13, Material.CHEST, COLOR + "bOpen inventory", Arrays.asList(COLOR + "7Edit its inventory (live, no dupes)."));
            putGuiItem(inv, 14, Material.ENDER_PEARL, COLOR + "aTeleport to it", Arrays.asList(COLOR + "7Teleport yourself to the fake."));
            putGuiItem(inv, 15, Material.ENDER_EYE, COLOR + "aBring it to you", Arrays.asList(COLOR + "7Teleport the fake to you."));
            putGuiItem(inv, 16, Material.NAME_TAG, COLOR + "bChange skin", Arrays.asList(
                    COLOR + "7Use " + COLOR + "f/rug player " + name + " skin <skinName>",
                    COLOR + "8GUI text input isn't available; command applies it."));
            putGuiItem(inv, 22, Material.ARROW, COLOR + "eBack", Arrays.asList(COLOR + "7Back to the fake-player list."));
            player.openInventory(inv);
        } catch (Throwable throwable) {
            player.sendMessage(PREFIX + COLOR + "cGUI failed: " + safeText(rootMessage(throwable)));
        }
    }

    /** Cleanup / purge page. */
    private void openCleanupGui(Player player) {
        try {
            Inventory inv = (Inventory) createInventoryReflect(27, GUI_CLEANUP_TITLE);
            fillGui(inv);
            putGuiItem(inv, 11, Material.BARRIER, COLOR + "cPurge stuck / dead bots", Arrays.asList(
                    COLOR + "7Force-remove stuck fake players and",
                    COLOR + "7any leftover dead bodies."));
            putGuiItem(inv, 13, Material.TNT, COLOR + "cRemove ALL fake players", Arrays.asList(
                    COLOR + "7Remove every tracked fake player."));
            putGuiItem(inv, 15, Material.COMPASS, COLOR + "eRefresh registry", Arrays.asList(
                    COLOR + "7Re-scan and refresh this page."));
            putGuiItem(inv, 22, Material.ARROW, COLOR + "eBack", Arrays.asList(COLOR + "7Return to Rug Control."));
            player.openInventory(inv);
        } catch (Throwable throwable) {
            player.sendMessage(PREFIX + COLOR + "cGUI failed: " + safeText(rootMessage(throwable)));
        }
    }

    /** Skin / profile tools page. */
    private void openSkinGui(Player player) {
        try {
            Inventory inv = (Inventory) createInventoryReflect(27, GUI_SKIN_TITLE);
            fillGui(inv);
            putGuiItem(inv, 11, Material.PLAYER_HEAD, COLOR + "bSkincheck me", Arrays.asList(
                    COLOR + "7Test the skin lookup for your name."));
            putGuiItem(inv, 13, Material.LEATHER_CHESTPLATE, COLOR + "eCycle skin layers", Arrays.asList(
                    COLOR + "7Current: " + COLOR + "f" + ruleText("skinLayers", "all"),
                    COLOR + "8Click to toggle all/none."));
            putGuiItem(inv, 15, Material.NAME_TAG, COLOR + "bChange a fake's skin", Arrays.asList(
                    COLOR + "7Use " + COLOR + "f/rug player <name> skin <skinName>",
                    COLOR + "8Copies that player's profile texture if found.",
                    COLOR + "8Capes only show if the source profile has one."));
            putGuiItem(inv, 22, Material.ARROW, COLOR + "eBack", Arrays.asList(COLOR + "7Return to Rug Control."));
            player.openInventory(inv);
        } catch (Throwable throwable) {
            player.sendMessage(PREFIX + COLOR + "cGUI failed: " + safeText(rootMessage(throwable)));
        }
    }

    private Object createInventoryReflect(int size, String title) throws Exception {
        for (Method method : Bukkit.class.getMethods()) {
            if (!method.getName().equals("createInventory")) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 3 && (types[1] == int.class || types[1] == Integer.class) && types[2] == String.class) {
                method.setAccessible(true);
                return method.invoke(null, null, Integer.valueOf(size), title);
            }
        }
        throw new NoSuchMethodError("Bukkit.createInventory(holder,int,String)");
    }

    private void fillGui(Inventory inv) {
        try {
            Object filler = makeNamedItem("GRAY_STAINED_GLASS_PANE", COLOR + "8", Collections.<String>emptyList());
            if (filler == null) filler = makeNamedItem("BLACK_STAINED_GLASS_PANE", COLOR + "8", Collections.<String>emptyList());
            if (!(filler instanceof ItemStack)) return;
            for (int i = 0; i < 54; i++) {
                try { inv.setItem(i, (ItemStack) filler); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }
    }

    private void putRuleItem(Inventory inv, int slot, String rule, Material material) {
        List<String> lore = new ArrayList<String>();
        lore.add(COLOR + "7Current: " + COLOR + "f" + ruleText(rule, "?"));
        lore.add(COLOR + "8Click to toggle/cycle");
        putGuiItem(inv, slot, material, COLOR + "e" + rule, lore);
    }

    private void putGuiItem(Inventory inv, int slot, Material material, String name, List<String> lore) {
        try {
            ItemStack item = new ItemStack(material, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
        } catch (Throwable ignored) {
        }
    }

    private Object makeNamedItem(String materialName, String name, List<String> lore) {
        try {
            Object item = makeItem(materialName);
            if (item == null) return null;
            Object meta = invokeFlexible(item, "getItemMeta");
            if (meta != null) {
                try { invokeFlexible(meta, "setDisplayName", name); } catch (Throwable ignored) {}
                try { invokeFlexible(meta, "setLore", lore); } catch (Throwable ignored) {}
                try { invokeFlexible(item, "setItemMeta", meta); } catch (Throwable ignored) {}
            }
            return item;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @EventHandler
    public void onRulesGuiClick(InventoryClickEvent event) {
        try {
            String title = String.valueOf(event.getView().getTitle());
            boolean ours = title.equals(GUI_TITLE) || title.equals(GUI_RULES_TITLE) || title.equals(GUI_PLAYERS_TITLE)
                    || title.equals(GUI_DETAIL_TITLE) || title.equals(GUI_CLEANUP_TITLE) || title.equals(GUI_SKIN_TITLE);
            if (!ours) return;
            event.setCancelled(true);
            Object who = event.getWhoClicked();
            if (!(who instanceof Player)) return;
            Player player = (Player) who;
            int slot = event.getRawSlot();

            if (title.equals(GUI_TITLE)) {
                if (slot == 10) openPlayersGui(player);
                else if (slot == 12) openRulesGui(player);
                else if (slot == 14) openSkinGui(player);
                else if (slot == 16) openCleanupGui(player);
                else if (slot == 22) {
                    player.sendMessage(PREFIX + DISPLAY_NAME + COLOR + "7 " + COLOR + "e" + VERSION + COLOR + "7 by " + COLOR + "eDawsonCodes" + COLOR + "7.");
                    showMainHelp(player);
                }
                return;
            }

            if (title.equals(GUI_PLAYERS_TITLE)) {
                if (slot == 49) { openMainGui(player); return; }
                String name = clickedName(event.getCurrentItem());
                if (name != null && getTracked(name) != null) openDetailGui(player, name);
                return;
            }

            if (title.equals(GUI_DETAIL_TITLE)) {
                handleDetailClick(player, slot);
                return;
            }

            if (title.equals(GUI_CLEANUP_TITLE)) {
                if (slot == 11) { purgeFakePlayers(player); openCleanupGui(player); }
                else if (slot == 13) { removeAll(player); openCleanupGui(player); }
                else if (slot == 15) openCleanupGui(player);
                else if (slot == 22) openMainGui(player);
                return;
            }

            if (title.equals(GUI_SKIN_TITLE)) {
                if (slot == 11) {
                    SkinResult result = resolveSkinProfile(player.getName());
                    player.sendMessage(PREFIX + "Skincheck " + COLOR + "e" + player.getName() + COLOR + "7: " + result.statusLine());
                } else if (slot == 13) {
                    cycleGuiRule("skinLayers");
                    player.sendMessage(PREFIX + "Skin layers: " + COLOR + "e" + ruleText("skinLayers", "all") + COLOR + "7.");
                    openSkinGui(player);
                } else if (slot == 22) {
                    openMainGui(player);
                }
                return;
            }

            // Rules page.
            if (slot == 49) { openMainGui(player); return; }
            String rule = ruleFromGuiSlot(slot);
            if (rule == null) return;
            cycleGuiRule(rule);
            player.sendMessage(PREFIX + "Set " + COLOR + "e" + rule + COLOR + "7 to " + COLOR + "e" + ruleText(rule, "?") + COLOR + "7.");
            openRulesGui(player);
        } catch (Throwable ignored) {
        }
    }

    private void handleDetailClick(Player player, int slot) {
        String name = guiBotName.get(player.getUniqueId());
        if (name == null) { openPlayersGui(player); return; }
        switch (slot) {
            case 10: killFake(player, name); player.closeInventory(); break;
            case 11: removeFake(player, name, true, false); openPlayersGui(player); break;
            case 12: refreshFakeHand(player, name); break;
            case 13: openFakeInventory(player, name); break;
            case 14: teleportToFake(player, name); player.closeInventory(); break;
            case 15: moveHere(player, name, null); break;
            case 16: player.sendMessage(PREFIX + "Use " + COLOR + "f/rug player " + name + " skin <skinName>" + COLOR + "7 to change its skin."); break;
            case 22: openPlayersGui(player); break;
            default: break;
        }
    }

    private void teleportToFake(Player player, String name) {
        FakeHandle fake = getTracked(name);
        if (fake != null && fake.bukkitPlayer instanceof Entity) {
            try {
                player.teleport(((Entity) fake.bukkitPlayer).getLocation());
                player.sendMessage(PREFIX + "Teleported to " + COLOR + "e" + name + COLOR + "7.");
                return;
            } catch (Throwable ignored) {}
        }
        List<Object> targets = findByTag(VISUAL_TAG, name);
        if (targets.isEmpty()) targets = findByTag(HITBOX_TAG, name);
        for (Object entity : targets) {
            if (entity instanceof Entity) {
                try {
                    player.teleport(((Entity) entity).getLocation());
                    player.sendMessage(PREFIX + "Teleported to " + COLOR + "e" + name + COLOR + "7.");
                    return;
                } catch (Throwable ignored) {}
            }
        }
        player.sendMessage(PREFIX + COLOR + "cCould not locate " + name + ".");
    }

    private String clickedName(ItemStack item) {
        try {
            if (item == null) return null;
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return null;
            return sanitizeName(stripColor(meta.getDisplayName()));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String stripColor(String text) {
        if (text == null) return null;
        return text.replaceAll("(?i)" + COLOR + "[0-9A-FK-OR]", "");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        try {
            Object who = event.getPlayer();
            if (!(who instanceof Player)) return;
            Player player = (Player) who;
            String name = invEditing.remove(player.getUniqueId());
            if (name == null) return;
            // Apply edits by re-syncing the fake's held item / equipment.
            FakeHandle fake = getTracked(name);
            if (fake != null) {
                syncFakeEquipment(fake);
            } else {
                Player online = findOnlinePlayerIgnoreCase(name);
                if (online != null && isLikelyFakeOnlinePlayer(online)) {
                    refreshFakePlayerEquipment(online);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private String ruleFromGuiSlot(int slot) {
        switch (slot) {
            case 10: return "playerBackend";
            case 11: return "skinLayers";
            case 12: return "broadcastDeaths";
            case 13: return "sendQuitMessage";
            case 14: return "allowDuplicateOnlineNames";
            case 15: return "punchKnockback";
            case 16: return "deathAlertSound";
            case 28: return "hitboxEnabled";
            case 29: return "hitboxInvisible";
            case 30: return "hitboxFireProof";
            case 31: return "playerHeadEnabled";
            case 32: return "playerVisualEnabled";
            default: return null;
        }
    }

    private void cycleGuiRule(String rule) {
        FileConfiguration cfg = getConfig();
        String path = "rules." + rule;
        if ("playerBackend".equals(rule)) {
            String now = ruleText(rule, "auto").toLowerCase(Locale.ROOT);
            cfg.set(path, now.equals("auto") ? "nms" : now.equals("nms") ? "visual" : "auto");
        } else if ("skinLayers".equals(rule)) {
            String now = ruleText(rule, "all").toLowerCase(Locale.ROOT);
            cfg.set(path, now.equals("all") ? "none" : "all");
        } else if ("deathAlertSound".equals(rule)) {
            String now = ruleText(rule, "wither").toLowerCase(Locale.ROOT);
            cfg.set(path, now.equals("wither") ? "none" : now.equals("none") ? "guardian" : now.equals("guardian") ? "hurt" : "wither");
        } else if ("punchKnockback".equals(rule)) {
            double now = ruleDouble(rule, 0.55d);
            double next = now <= 0.0d ? 0.55d : now < 1.0d ? 1.25d : 0.0d;
            cfg.set(path, Double.valueOf(next));
        } else {
            cfg.set(path, !ruleBool(rule, false));
        }
        saveConfig();
    }


    @EventHandler
    public void onEntityPickup(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player && isNmsFakeEntity(entity)) {
            final Player fake = (Player) entity;
            runLater(new Runnable() {
                @Override
                public void run() {
                    manuallyPickupNearbyItems(fake);
                    refreshFakePlayerEquipment(fake);
                }
            }, 1L);
            runLater(new Runnable() {
                @Override
                public void run() {
                    manuallyPickupNearbyItems(fake);
                    refreshFakePlayerEquipment(fake);
                }
            }, 5L);
        }
    }


    @EventHandler
    public void onPlayerAttemptPickup(PlayerAttemptPickupItemEvent event) {
        try {
            Player player = event.getPlayer();
            if (player != null && isNmsFakeEntity(player)) {
                final Player fake = player;
                runLater(new Runnable() { @Override public void run() { manuallyPickupNearbyItems(fake); refreshFakePlayerEquipment(fake); } }, 1L);
                runLater(new Runnable() { @Override public void run() { manuallyPickupNearbyItems(fake); refreshFakePlayerEquipment(fake); } }, 5L);
            }
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void onFakePlayerDamaged(EntityDamageByEntityEvent event) {
        try {
            Entity victim = event.getEntity();
            if (!isRugFakeEntity(victim)) return;
            Entity attacker = event.getDamager();
            if (attacker == null) return;
            double kb = ruleDouble("punchKnockback", 0.55d);
            if (kb <= 0.0d) return;
            Location vLoc = victim.getLocation();
            Location aLoc = attacker.getLocation();
            // Horizontal direction from the attacker toward the fake player.
            Vector dir = vLoc.toVector().subtract(aLoc.toVector());
            dir.setY(0.0d);
            if (dir.lengthSquared() < 0.0001d) {
                Vector look = aLoc.getDirection();
                look.setY(0.0d);
                dir = look.lengthSquared() < 0.0001d ? new Vector(0.0d, 0.0d, 1.0d) : look;
            }
            dir.normalize().multiply(kb);

            if (victim instanceof Player && isNmsFakeEntity(victim)) {
                Vector velocity = dir.clone();
                velocity.setY(0.4d);
                try { victim.setVelocity(velocity); } catch (Throwable ignored) {}
                knockbackFakePlayer(victim, dir);
            } else if (hasTag(victim, HITBOX_TAG) || hasTag(victim, VISUAL_TAG)) {
                // Visual backend: the hittable zombie reacts to Bukkit velocity; we
                // drag its paired armor-stand visual along so they move together.
                knockbackVisualFake(victim, dir);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Knocks a fake player around like a Carpet bot. A clientless player never
     * applies velocity itself, so we set NMS delta movement, broadcast a motion
     * packet for client-side animation, then slide the real server-side entity
     * over several ticks; the entity tracker broadcasts the new position too.
     */
    private void knockbackFakePlayer(final Entity victim, final Vector horizontal) {
        if (victim == null || horizontal == null) return;
        final Object nms = victim instanceof Player
                ? invokeFirst(victim, new String[]{"getHandle"}, new Class[]{}, new Object[]{})
                : null;
        setNmsDeltaMovement(nms, horizontal.getX(), 0.4d, horizontal.getZ());
        broadcastMotionPacket(victim, nms, horizontal);
        final double[] decay = {0.7d, 0.5d, 0.34d, 0.22d, 0.12d, 0.06d};
        for (int i = 0; i < decay.length; i++) {
            final double factor = decay[i];
            runLater(new Runnable() {
                @Override
                public void run() {
                    try {
                        Object dead = null;
                        try { dead = invokeFlexible(victim, "isDead"); } catch (Throwable ignored) {}
                        if (Boolean.TRUE.equals(dead)) return;
                        Location loc = victim.getLocation();
                        double dx = clampSlide(horizontal.getX() * factor);
                        double dz = clampSlide(horizontal.getZ() * factor);
                        Location moved = loc.clone();
                        moved.add(dx, 0.0d, dz);
                        moveFakePlayerTo(victim, nms, moved);
                    } catch (Throwable ignored) {
                    }
                }
            }, (long) (i + 1));
        }
    }

    /** Visual-backend knockback: shove the hitbox and keep the visual stand on it. */
    private void knockbackVisualFake(final Entity victim, final Vector horizontal) {
        if (victim == null || horizontal == null) return;
        final String name = nameFromEntityOrTags(victim);
        Vector velocity = horizontal.clone();
        velocity.setY(0.4d);
        try { victim.setVelocity(velocity); } catch (Throwable ignored) {}
        for (int i = 1; i <= 6; i++) {
            runLater(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (victim.isDead()) return;
                        Location target = victim.getLocation();
                        for (Object vis : findByTag(VISUAL_TAG, name)) {
                            if (vis instanceof Entity && vis != victim) {
                                try { ((Entity) vis).teleport(target); } catch (Throwable ignored) {}
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }, (long) i);
        }
    }

    private static double clampSlide(double value) {
        return Math.max(-1.1d, Math.min(1.1d, value));
    }

    private void broadcastMotionPacket(Entity victim, Object nms, Vector horizontal) {
        try {
            int entityId = readEntityId(victim, nms);
            if (entityId == 0) return;
            Object packet = constructMotionPacket(entityId, horizontal.getX(), 0.4d, horizontal.getZ());
            if (packet == null) return;
            for (Player viewer : onlinePlayers()) {
                if (isLikelyFakeOnlinePlayer(viewer)) continue;
                sendPacket(viewer, packet);
            }
        } catch (Throwable ignored) {
        }
    }

    private Object constructMotionPacket(int entityId, double x, double y, double z) {
        try {
            Class<?> vec3 = firstClass("net.minecraft.world.phys.Vec3");
            Object v = vec3.getConstructor(double.class, double.class, double.class).newInstance(x, y, z);
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket");
            for (Constructor<?> ctor : packetClass.getDeclaredConstructors()) {
                Class<?>[] types = ctor.getParameterTypes();
                if (types.length == 2 && (types[0] == int.class || types[0] == Integer.class) && types[1].isAssignableFrom(vec3)) {
                    ctor.setAccessible(true);
                    return ctor.newInstance(Integer.valueOf(entityId), v);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void setNmsDeltaMovement(Object nms, double x, double y, double z) {
        if (nms == null) return;
        try {
            Class<?> vec3 = firstClass("net.minecraft.world.phys.Vec3");
            Object v = vec3.getConstructor(double.class, double.class, double.class).newInstance(x, y, z);
            invokeIfExists(nms, "setDeltaMovement", new Class[]{vec3}, v);
        } catch (Throwable ignored) {
        }
        invokeIfExists(nms, "push", new Class[]{double.class, double.class, double.class}, x, y, z);
    }

    private void moveFakePlayerTo(Entity victim, Object nms, Location loc) {
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        float yaw = loc.getYaw(), pitch = loc.getPitch();
        if (nms != null) {
            invokeIfExists(nms, "absMoveTo", new Class[]{double.class, double.class, double.class, float.class, float.class}, x, y, z, yaw, pitch);
            invokeIfExists(nms, "moveTo", new Class[]{double.class, double.class, double.class, float.class, float.class}, x, y, z, yaw, pitch);
            invokeIfExists(nms, "setPos", new Class[]{double.class, double.class, double.class}, x, y, z);
        }
        try { victim.teleport(loc); } catch (Throwable ignored) {}
    }

    private void manuallyPickupNearbyItems(Player fake) {
        if (fake == null || !isNmsFakeEntity(fake)) return;
        try {
            Object nearby = invokeFlexible(fake, "getNearbyEntities", Double.valueOf(1.6d), Double.valueOf(1.2d), Double.valueOf(1.6d));
            if (!(nearby instanceof Iterable)) return;
            boolean changed = false;
            for (Object entity : new ArrayList<Object>((Collection<?>) nearby)) {
                if (entity == null) continue;
                boolean isItem = false;
                try {
                    Class<?> itemClass = Class.forName("org.bukkit.entity.Item");
                    isItem = itemClass.isInstance(entity);
                } catch (Throwable ignored) {
                    String typeName = String.valueOf(invokeFlexible(entity, "getType"));
                    isItem = "DROPPED_ITEM".equalsIgnoreCase(typeName) || "ITEM".equalsIgnoreCase(typeName);
                }
                if (!isItem) continue;
                Object stack = null;
                try { stack = invokeFlexible(entity, "getItemStack"); } catch (Throwable ignored) {}
                if (stack == null || isEmptyItem(stack)) continue;
                if (storeItemInFakeInventory(fake, stack)) {
                    try { invokeFlexible(entity, "remove"); } catch (Throwable ignored) {}
                    changed = true;
                }
            }
            if (changed) {
                refreshFakePlayerEquipment(fake);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean storeItemInFakeInventory(Player fake, Object itemStack) {
        try {
            Object inv = invokeFlexible(fake, "getInventory");
            if (inv == null || itemStack == null) return false;
            try {
                Class<?> itemStackClass = Class.forName("org.bukkit.inventory.ItemStack");
                Object array = Array.newInstance(itemStackClass, 1);
                Array.set(array, 0, itemStack);
                Object result = invokeFlexible(inv, "addItem", array);
                if (result instanceof Map) return ((Map<?, ?>) result).isEmpty();
                return true;
            } catch (Throwable ignored) {
            }
            try {
                Object firstEmpty = invokeFlexible(inv, "firstEmpty");
                int slot = ((Number) firstEmpty).intValue();
                if (slot >= 0) {
                    invokeFlexible(inv, "setItem", Integer.valueOf(slot), itemStack);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean refreshFakeHand(CommandSender sender, String rawName) {
        String name = sanitizeName(rawName);
        FakeHandle fake = getTracked(name);
        if (fake == null || !(fake.bukkitPlayer instanceof Player)) {
            Player online = findOnlinePlayerIgnoreCase(name);
            if (online != null && isLikelyFakeOnlinePlayer(online)) {
                refreshFakePlayerEquipment(online);
                sender.sendMessage(PREFIX + "Refreshed held item/equipment for " + COLOR + "e" + online.getName() + COLOR + "7.");
                return true;
            }
            sender.sendMessage(PREFIX + COLOR + "cCould not find tracked fake player " + name + ".");
            return true;
        }
        syncFakeEquipment(fake);
        sender.sendMessage(PREFIX + "Refreshed held item/equipment for " + COLOR + "e" + fake.name + COLOR + "7.");
        return true;
    }

    private void syncFakeEquipment(FakeHandle handle) {
        if (handle == null) return;
        syncFakeEquipmentObjects(handle.bukkitPlayer, handle.nmsPlayer);
    }

    private void refreshFakePlayerEquipment(Player player) {
        if (player == null) return;
        Object nms = invokeFirst(player, new String[]{"getHandle"}, new Class[]{}, new Object[]{});
        syncFakeEquipmentObjects(player, nms);
    }

    private void syncFakeEquipmentObjects(Object bukkitPlayer, Object nmsPlayer) {
        if (!(bukkitPlayer instanceof Player)) return;
        try {
            Player player = (Player) bukkitPlayer;
            invokeIfExists(player, "setCanPickupItems", new Class[]{boolean.class}, true);
            Object inv = invokeFlexible(player, "getInventory");
            if (inv == null) return;
            Object main = null;
            try { main = invokeFlexible(inv, "getItemInMainHand"); } catch (Throwable ignored) {}
            if (isEmptyItem(main)) {
                for (int i = 0; i < 36; i++) {
                    Object item = null;
                    try { item = invokeFlexible(inv, "getItem", Integer.valueOf(i)); } catch (Throwable ignored) {}
                    if (!isEmptyItem(item)) {
                        if (i >= 0 && i < 9) {
                            try { invokeFlexible(inv, "setHeldItemSlot", Integer.valueOf(i)); } catch (Throwable ignored) {}
                        } else {
                            try {
                                Object oldZero = invokeFlexible(inv, "getItem", Integer.valueOf(0));
                                invokeFlexible(inv, "setItem", Integer.valueOf(0), item);
                                invokeFlexible(inv, "setItem", Integer.valueOf(i), oldZero);
                                invokeFlexible(inv, "setHeldItemSlot", Integer.valueOf(0));
                            } catch (Throwable ignored) {}
                        }
                        main = item;
                        break;
                    }
                }
            }
            if (isEmptyItem(main)) {
                main = makeItem("AIR");
            }
            if (main != null) {
                if (!isEmptyItem(main)) {
                    try { invokeFlexible(inv, "setItemInMainHand", main); } catch (Throwable ignored) {}
                    try {
                        Object equipment = invokeFlexible(player, "getEquipment");
                        if (equipment != null) {
                            try { invokeFlexible(equipment, "setItemInMainHand", main); } catch (Throwable ignored) {}
                            try { invokeFlexible(equipment, "setItemInMainHand", main, Boolean.TRUE); } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                }
                setNmsMainHand(nmsPlayer, main);
                broadcastMainHandPacket(player, nmsPlayer, main);
                try { invokeFlexible(player, "updateInventory"); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isEmptyItem(Object item) {
        if (item == null) return true;
        try {
            Object type = invokeFlexible(item, "getType");
            String name = String.valueOf(type);
            return name.equalsIgnoreCase("AIR") || name.equalsIgnoreCase("CAVE_AIR") || name.equalsIgnoreCase("VOID_AIR");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void setNmsMainHand(Object nmsPlayer, Object bukkitItem) {
        if (nmsPlayer == null || bukkitItem == null) return;
        try {
            Object nmsItem = bukkitItem;
            try {
                Class<?> craftItem = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
                Class<?> itemStackClass = Class.forName("org.bukkit.inventory.ItemStack");
                nmsItem = craftItem.getMethod("asNMSCopy", itemStackClass).invoke(null, bukkitItem);
            } catch (Throwable ignored) {}
            Object slot = equipmentSlotMainHand();
            if (slot == null || nmsItem == null) return;
            for (Method method : allMethods(nmsPlayer.getClass())) {
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 2 && types[0].isInstance(slot) && types[1].isInstance(nmsItem)) {
                    method.setAccessible(true);
                    method.invoke(nmsPlayer, slot, nmsItem);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }


    private void broadcastMainHandPacket(Player fake, Object nmsPlayer, Object bukkitItem) {
        try {
            Object nmsItem = bukkitItem;
            try {
                Class<?> craftItem = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
                Class<?> itemStackClass = Class.forName("org.bukkit.inventory.ItemStack");
                nmsItem = craftItem.getMethod("asNMSCopy", itemStackClass).invoke(null, bukkitItem);
            } catch (Throwable ignored) {}
            Object slot = equipmentSlotMainHand();
            if (slot == null || nmsItem == null) return;
            Class<?> pairClass = Class.forName("com.mojang.datafixers.util.Pair");
            Object pair = pairClass.getMethod("of", Object.class, Object.class).invoke(null, slot, nmsItem);
            List<Object> pairs = new ArrayList<Object>();
            pairs.add(pair);
            int entityId = 0;
            try { entityId = ((Number) invokeFlexible(fake, "getEntityId")).intValue(); } catch (Throwable ignored) {}
            if (entityId == 0 && nmsPlayer != null) {
                try { entityId = ((Number) invokeFlexible(nmsPlayer, "getId")).intValue(); } catch (Throwable ignored) {}
            }
            if (entityId == 0) return;
            Object packet = constructEquipmentPacket(entityId, pairs);
            if (packet == null) return;
            for (Player viewer : onlinePlayers()) {
                if (isLikelyFakeOnlinePlayer(viewer)) continue;
                sendPacket(viewer, packet);
            }
        } catch (Throwable ignored) {
        }
    }

    private Object constructEquipmentPacket(int entityId, List<Object> pairs) {
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket");
            for (Constructor<?> ctor : packetClass.getDeclaredConstructors()) {
                Class<?>[] types = ctor.getParameterTypes();
                if (types.length == 2 && (types[0] == int.class || types[0] == Integer.class) && List.class.isAssignableFrom(types[1])) {
                    ctor.setAccessible(true);
                    return ctor.newInstance(Integer.valueOf(entityId), pairs);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void sendPacket(Player player, Object packet) {
        try {
            Object handle = invokeFirst(player, new String[]{"getHandle"}, new Class[]{}, new Object[]{});
            if (handle == null) return;
            Object connection = getFieldAssignable(handle, "net.minecraft.server.network.ServerGamePacketListenerImpl", "net.minecraft.server.network.PlayerConnection");
            if (connection == null) return;
            for (Method method : allMethods(connection.getClass())) {
                if (!method.getName().equals("send")) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 1 && types[0].isInstance(packet)) {
                    method.setAccessible(true);
                    method.invoke(connection, packet);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private Object equipmentSlotMainHand() {
        try {
            Class<?> slotClass = firstClass("net.minecraft.world.entity.EnumItemSlot", "net.minecraft.world.entity.EquipmentSlot");
            try { return Enum.valueOf((Class<? extends Enum>) slotClass.asSubclass(Enum.class), "MAINHAND"); } catch (Throwable ignored) {}
            String[] fieldNames = {"MAINHAND", "a", "HAND"};
            for (String fieldName : fieldNames) {
                try {
                    Field field = slotClass.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (slotClass.isInstance(value)) return value;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private byte skinLayerMask() {
        String raw = ruleText("skinLayers", "all").toLowerCase(Locale.ROOT).replace(' ', '_');
        if (raw.equals("all") || raw.equals("true") || raw.equals("on")) return (byte) 0x7F;
        if (raw.equals("none") || raw.equals("false") || raw.equals("off")) return (byte) 0x00;
        int mask = 0;
        if (raw.contains("cape")) mask |= 0x01;
        if (raw.contains("jacket") || raw.contains("body") || raw.contains("coat")) mask |= 0x02;
        if (raw.contains("left_sleeve") || raw.contains("leftsleeve") || raw.contains("sleeves") || raw.contains("arms")) mask |= 0x04;
        if (raw.contains("right_sleeve") || raw.contains("rightsleeve") || raw.contains("sleeves") || raw.contains("arms")) mask |= 0x08;
        if (raw.contains("left_pants") || raw.contains("leftpants") || raw.contains("pants") || raw.contains("legs")) mask |= 0x10;
        if (raw.contains("right_pants") || raw.contains("rightpants") || raw.contains("pants") || raw.contains("legs")) mask |= 0x20;
        if (raw.contains("hat") || raw.contains("head") || raw.contains("helmet")) mask |= 0x40;
        return (byte) mask;
    }

    private void applySkinLayerMask(Object nmsPlayer) {
        if (nmsPlayer == null) return;
        byte mask = skinLayerMask();
        try {
            Object data = invokeFlexible(nmsPlayer, "getEntityData");
            if (data == null) return;
            Object accessor = findSkinLayerAccessor(nmsPlayer, data);
            if (accessor != null) {
                invokeFlexible(data, "set", accessor, Byte.valueOf(mask));
            }
        } catch (Throwable throwable) {
            getLogger().fine("Could not apply skin layer mask: " + rootMessage(throwable));
        }
    }

    private Object findSkinLayerAccessor(Object nmsPlayer, Object data) {
        Class<?> clazz = nmsPlayer.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    if (!Modifier.isStatic(field.getModifiers())) continue;
                    String fieldName = field.getName().toLowerCase(Locale.ROOT);
                    if (!(fieldName.contains("custom") || fieldName.contains("model") || fieldName.contains("skin") || fieldName.contains("mode"))) continue;
                    field.setAccessible(true);
                    Object accessor = field.get(null);
                    Object current = invokeFlexible(data, "get", accessor);
                    if (current instanceof Byte) return accessor;
                } catch (Throwable ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        clazz = nmsPlayer.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    if (!Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    Object accessor = field.get(null);
                    Object current = invokeFlexible(data, "get", accessor);
                    if (current instanceof Byte && ((Byte) current).byteValue() == 0) return accessor;
                } catch (Throwable ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    @EventHandler
    public void onEntityBurn(EntityCombustEvent event) {
        if (hasTag(event.getEntity(), HITBOX_TAG) || hasTag(event.getEntity(), VISUAL_TAG)) {
            event.setCancelled(true);
            invokeIfExists(event.getEntity(), "setFireTicks", new Class[]{int.class}, 0);
        }
    }

    /**
     * Real NMS fake players fire {@link PlayerDeathEvent}, not the generic
     * {@link EntityDeathEvent}. Without this handler the vanilla death message
     * still appeared but the body/corpse was never cleaned up. We let Paper
     * broadcast the death line, then fully remove the fake player so nothing is
     * left lying on the ground.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player entity = event.getEntity();
        if (entity == null || !isNmsFakeEntity(entity)) {
            return;
        }
        if (hasTag(entity, REMOVING_TAG)) {
            return;
        }
        // Don't scatter the fake's gear and don't keep its body/inventory around.
        try {
            event.getDrops().clear();
        } catch (Throwable ignored) {
        }
        invokeIfExists(event, "setDroppedExp", new Class[]{int.class}, 0);
        invokeIfExists(event, "setKeepInventory", new Class[]{boolean.class}, false);
        invokeIfExists(event, "setKeepLevel", new Class[]{boolean.class}, false);
        invokeIfExists(entity, "addScoreboardTag", new Class[]{String.class}, REMOVING_TAG);

        final String name = nameFromEntityOrTags(entity);
        FakeHandle dyingFake = getTracked(name);
        // Delayed removal so the vanilla death message broadcasts first, then the
        // PlayerList/world/tracking cleanup removes the corpse cleanly.
        scheduleNmsRemoval(name, entity, dyingFake);
        untrack(name);
        // A clientless fake never produces its own vanilla "left the game" line,
        // which is why leave behaviour looked broken. Emit one clean leave line
        // just after the death message instead of relying on the disconnect path.
        if (ruleBool("sendQuitMessage", true)) {
            runLater(new Runnable() {
                @Override
                public void run() {
                    broadcastFakeQuit(name);
                }
            }, 3L);
        }
        playConfiguredSound("deathAlertSound", 1.0f, 1.0f);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!isRugFakeEntity(entity)) {
            return;
        }
        if (hasTag(entity, REMOVING_TAG)) {
            return;
        }
        clearDeathDrops(event);
        String name = nameFromEntityOrTags(entity);
        boolean nmsFake = isNmsFakeEntity(entity);
        FakeHandle dyingFake = getTracked(name);
        if (nmsFake) {
            // Let Minecraft/Paper finish and broadcast the real death message first.
            // The delayed PlayerList removal below creates the vanilla yellow "left the game" line after it.
            scheduleNmsRemoval(name, entity, dyingFake);
            untrack(name);
        } else {
            untrack(name);
            removeEntities(name, true);
            String existingDeathMessage = readDeathMessage(event);
            if (existingDeathMessage == null || existingDeathMessage.trim().isEmpty()) {
                broadcastFakeDeath(name, killerName(entity));
            }
        }
        playConfiguredSound("deathAlertSound", 1.0f, 1.0f);
    }

    private boolean isRugFakeEntity(Entity entity) {
        if (entity == null) return false;
        if (hasTag(entity, HITBOX_TAG) || hasTag(entity, VISUAL_TAG) || hasTag(entity, NMS_TAG)) return true;
        try {
            if (entity instanceof Player) {
                return isLikelyFakeOnlinePlayer((Player) entity);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isNmsFakeEntity(Entity entity) {
        if (entity == null) return false;
        if (hasTag(entity, NMS_TAG)) return true;
        return entity instanceof Player && isLikelyFakeOnlinePlayer((Player) entity);
    }

    private String nameFromEntityOrTags(Entity entity) {
        if (entity instanceof Player) {
            try { return sanitizeName(((Player) entity).getName()); } catch (Throwable ignored) {}
        }
        return nameFromTags(entity);
    }

    private void scheduleNmsRemoval(String name, Entity entity, FakeHandle fakeHandle) {
        final FakeHandle fake = fakeHandle;
        final Entity entityLater = entity;
        Runnable cleanup = new Runnable() {
            @Override
            public void run() {
                if (fake != null) {
                    removeNmsFake(fake, true);
                }
                purgeName(name, true);
                removeNmsEntities(name, true);
                hardRemoveNmsPlayer(fake == null ? null : fake.nmsPlayer, entityLater);
            }
        };
        // Several passes across a few ticks: the first runs after Paper finishes
        // broadcasting the death, later passes catch any corpse Paper re-adds.
        runLater(cleanup, 1L);
        runLater(cleanup, 2L);
        runLater(cleanup, 10L);
        runLater(cleanup, 40L);
        runLater(cleanup, 60L);
    }

    private void clearDeathDrops(EntityDeathEvent event) {
        try {
            Object drops = event.getClass().getMethod("getDrops").invoke(event);
            if (drops instanceof java.util.List) {
                ((java.util.List<?>) drops).clear();
            }
        } catch (Throwable ignored) {
        }
        invokeIfExists(event, "setDroppedExp", new Class[]{int.class}, 0);
    }

    private void broadcastFakeDeath(String name, String killer) {
        if (!ruleBool("broadcastDeaths", true)) {
            return;
        }
        String deathLine = killer == null || killer.isEmpty()
                ? COLOR + "e" + name + " died"
                : COLOR + "e" + name + " was slain by " + killer;
        for (Player player : onlinePlayers()) {
            player.sendMessage(deathLine);
        }
        if (ruleBool("sendQuitMessage", true)) {
            broadcastFakeQuit(name);
        }
    }

    private void broadcastFakeQuit(String name) {
        String leaveLine = COLOR + "e" + name + " left the game";
        for (Player player : onlinePlayers()) {
            player.sendMessage(leaveLine);
        }
    }

    private String readDeathMessage(EntityDeathEvent event) {
        try {
            Object value = event.getClass().getMethod("getDeathMessage").invoke(event);
            return value == null ? null : String.valueOf(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String killerName(Entity entity) {
        try {
            Object killer = entity.getClass().getMethod("getKiller").invoke(entity);
            if (killer instanceof Player) {
                return ((Player) killer).getName();
            }
            if (killer != null) {
                Object name = killer.getClass().getMethod("getName").invoke(killer);
                return String.valueOf(name);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void playConfiguredSound(String configKey, float volume, float pitch) {
        String wanted = String.valueOf(getConfig().get("rules." + configKey));
        if (wanted == null || wanted.equalsIgnoreCase("none") || wanted.equalsIgnoreCase("off") || wanted.equalsIgnoreCase("false")) {
            return;
        }
        Sound sound = soundFromConfig(wanted);
        for (Player player : onlinePlayers()) {
            try {
                Location location = player.getLocation();
                player.playSound(location, sound, SoundCategory.MASTER, volume, pitch);
            } catch (Throwable ignored) {
            }
        }
    }

    private Sound soundFromConfig(String wanted) {
        if (wanted == null) {
            return Sound.ENTITY_WITHER_SPAWN;
        }
        String key = wanted.toLowerCase(Locale.ROOT).trim();
        if (key.equals("guardian") || key.equals("elder_guardian")) {
            return Sound.ENTITY_ELDER_GUARDIAN_CURSE;
        }
        if (key.equals("dragon") || key.equals("ender_dragon")) {
            return Sound.ENTITY_ENDER_DRAGON_GROWL;
        }
        if (key.equals("hurt") || key.equals("player_hurt")) {
            return Sound.ENTITY_PLAYER_HURT;
        }
        return Sound.ENTITY_WITHER_SPAWN;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("rug")) {
            return Collections.emptyList();
        }
        return completeRug(args);
    }

    private List<String> completeRug(String[] args) {
        if (args.length == 1) {
            return filter(RUG_SUGGEST, args[0]);
        }
        if (is(args[0], "rule")) {
            if (args.length == 2) return filter(RULES, args[1]);
            if (args.length == 3) return filter(ruleValueSuggestions(args[1]), args[2]);
            return Collections.emptyList();
        }
        if (is(args[0], "skincheck")) {
            return args.length == 2 ? filter(unique(suggestableNames()), args[1]) : Collections.<String>emptyList();
        }
        if (isPlayerNamespace(args[0])) {
            return completePlayer(tail(args));
        }
        // Shortcut form: /rug <name> <action> [skin].
        if (args.length == 2) {
            return filter(PLAYER_ACTION_SUGGEST, args[1]);
        }
        if (args.length == 3 && (is(args[1], "spawn") || is(args[1], "skin"))) {
            return filter(unique(suggestableNames()), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> ruleValueSuggestions(String rule) {
        String r = rule == null ? "" : rule.toLowerCase(Locale.ROOT);
        if (r.equals("playerbackend")) return Arrays.asList("auto", "nms", "visual");
        if (r.equals("skinlayers")) return Arrays.asList("all", "none", "cape,jacket,sleeves,pants,hat");
        if (r.equals("punchknockback")) return Arrays.asList("0", "0.55", "1.25");
        if (r.equals("deathalertsound") || r.equals("summonalertsound")) return Arrays.asList("none", "wither", "guardian", "dragon", "hurt");
        return Arrays.asList("true", "false");
    }

    private List<String> completePlayer(String[] args) {
        // Name position (or root action): tracked fakes + generic defaults + roots.
        if (args.length <= 1) {
            String typed = args.length == 0 ? "" : (args[0] == null ? "" : args[0]);
            List<String> out = new ArrayList<String>();
            out.add("purge");
            out.add("removeall");
            out.addAll(trackedNames());
            out.addAll(DEFAULT_NAME_SUGGESTIONS);
            return filter(unique(out), typed);
        }
        // Action position. Root keywords take no further args.
        if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("purge") || first.equals("removeall") || first.equals("killall")
                    || first.equals("list") || first.equals("help")) {
                return Collections.emptyList();
            }
            return filter(PLAYER_ACTION_SUGGEST, args[1]);
        }
        // Skin position for spawn/skin only.
        if (args.length == 3) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("spawn") || action.equals("skin")) {
                return filter(unique(suggestableNames()), args[2]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> suggestableNames() {
        List<String> out = new ArrayList<String>();
        out.addAll(trackedNames());
        for (Player player : onlinePlayers()) {
            try { out.add(player.getName()); } catch (Throwable ignored) {}
        }
        out.addAll(learnedNames);
        out.addAll(DEFAULT_NAME_SUGGESTIONS);
        return out;
    }

    private List<String> unique(Collection<String> values) {
        return new ArrayList<String>(new LinkedHashSet<String>(values));
    }

    private void learnName(String rawName) {
        String clean = sanitizeName(rawName);
        if (!clean.equalsIgnoreCase(DEFAULT_NAME) || rawName != null) {
            learnedNames.add(clean);
            if (learnedNames.size() > 80) {
                String first = learnedNames.iterator().next();
                learnedNames.remove(first);
            }
        }
    }

    /**
     * Strict parse of {@code <name> <action> [skin]}. The first token is always
     * the fake player's name; the second must be a known action. Returns null
     * (caller shows help, spawns nothing) for anything else.
     */
    private ParsedPlayerCommand parsePlayerCommand(String[] args) {
        if (args.length < 2) {
            return null;
        }
        String second = args[1].toLowerCase(Locale.ROOT);
        if (!PLAYER_ACTIONS.contains(second)) {
            return null;
        }
        String rawName = args[0];
        String name = sanitizeName(rawName);
        String skin = args.length >= 3 ? sanitizeName(args[2]) : name;
        return new ParsedPlayerCommand(rawName, name, normalizeAction(second), skin);
    }

    private String normalizeAction(String action) {
        String lower = action.toLowerCase(Locale.ROOT);
        if (lower.equals("move") || lower.equals("move_here") || lower.equals("tphere")) {
            return "tp";
        }
        return lower;
    }

    private Location cloneAndOffset(Location base, double x, double y, double z) {
        try {
            Object clone = invokeRequired(base, "clone", new Class[]{}, new Object[]{});
            invokeIfExists(clone, "add", new Class[]{double.class, double.class, double.class}, x, y, z);
            return (Location) clone;
        } catch (Throwable ignored) {
            return base;
        }
    }

    private Object invokeStaticRequired(Class<?> clazz, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = clazz.getMethod(method, types);
        return m.invoke(null, args);
    }

    private Object invokeStaticFlexible(Class<?> clazz, String method, Object... args) throws Exception {
        for (Method m : allMethods(clazz)) {
            if (!m.getName().equals(method) || m.getParameterTypes().length != args.length) continue;
            if (!accepts(m.getParameterTypes(), args)) continue;
            m.setAccessible(true);
            return m.invoke(null, args);
        }
        throw new NoSuchMethodException(clazz.getName() + "." + method + "/" + args.length);
    }

    private Object invokeFlexible(Object target, String method, Object... args) throws Exception {
        if (target == null) return null;
        for (Method m : allMethods(target.getClass())) {
            if (!m.getName().equals(method) || m.getParameterTypes().length != args.length) continue;
            if (!accepts(m.getParameterTypes(), args)) continue;
            m.setAccessible(true);
            return m.invoke(target, args);
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + method + "/" + args.length);
    }

    private List<Method> allMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<Method>();
        Class<?> c = clazz;
        while (c != null) {
            methods.addAll(Arrays.asList(c.getDeclaredMethods()));
            c = c.getSuperclass();
        }
        methods.addAll(Arrays.asList(clazz.getMethods()));
        return methods;
    }

    private boolean accepts(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            if (args[i] == null) continue;
            Class<?> want = wrap(types[i]);
            Class<?> got = wrap(args[i].getClass());
            if (!want.isAssignableFrom(got)) return false;
        }
        return true;
    }

    private Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Object invokeRequired(Object target, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getMethod(method, types);
        return m.invoke(target, args);
    }

    private void invokeIfExists(Object target, String method, Class<?>[] types, Object... args) {
        if (target == null) {
            return;
        }
        try {
            Method m = target.getClass().getMethod(method, types);
            m.invoke(target, args);
        } catch (Throwable ignored) {
        }
    }

    private static UUID uuidOf(Object entity) {
        try {
            Object value = entity.getClass().getMethod("getUniqueId").invoke(entity);
            if (value instanceof UUID) {
                return (UUID) value;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Set<String> tagsOf(Object entity) {
        try {
            if (entity instanceof Entity) {
                Set<String> tags = ((Entity) entity).getScoreboardTags();
                return tags == null ? Collections.<String>emptySet() : tags;
            }
            Object tags = entity.getClass().getMethod("getScoreboardTags").invoke(entity);
            if (tags instanceof Set) {
                return (Set<String>) tags;
            }
        } catch (Throwable ignored) {
        }
        return Collections.emptySet();
    }

    private String ruleText(String rule, String fallback) {
        try {
            Object value = getConfig().get("rules." + rule);
            return value == null ? fallback : String.valueOf(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private boolean ruleBool(String rule, boolean fallback) {
        try {
            return getConfig().getBoolean("rules." + rule, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private double ruleDouble(String rule, double fallback) {
        try {
            Object value = getConfig().get("rules." + rule);
            if (value instanceof Number) return ((Number) value).doubleValue();
            if (value != null) return Double.parseDouble(String.valueOf(value));
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private String canonicalRule(String input) {
        for (String rule : RULES) {
            if (rule.equalsIgnoreCase(input)) {
                return rule;
            }
        }
        return null;
    }

    private static String[] tail(String[] args) {
        if (args.length <= 1) {
            return new String[0];
        }
        String[] out = new String[args.length - 1];
        System.arraycopy(args, 1, out, 0, out.length);
        return out;
    }

    private static Collection<? extends Player> onlinePlayers() {
        try {
            return Bukkit.getOnlinePlayers();
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private static boolean hasTag(Entity entity, String tag) {
        try {
            Set<String> tags = entity.getScoreboardTags();
            return tags != null && tags.contains(tag);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String nameFromTags(Entity entity) {
        try {
            for (String tag : entity.getScoreboardTags()) {
                if (tag.startsWith(NAME_TAG_PREFIX)) {
                    return sanitizeName(tag.substring(NAME_TAG_PREFIX.length()));
                }
            }
        } catch (Throwable ignored) {
        }
        return "FakePlayer";
    }

    private static String fakeId(String name) {
        return "rug_" + sanitizeName(name).toLowerCase(Locale.ROOT);
    }

    private static String nameTag(String name) {
        return NAME_TAG_PREFIX + sanitizeName(name).toLowerCase(Locale.ROOT);
    }

    private static String skinTag(String name) {
        return SKIN_TAG_PREFIX + sanitizeName(name).toLowerCase(Locale.ROOT);
    }

    private static String sanitizeName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_NAME;
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.isEmpty()) {
            cleaned = DEFAULT_NAME;
        }
        if (cleaned.length() > 16) {
            cleaned = cleaned.substring(0, 16);
        }
        return cleaned;
    }

    private static String safeText(String raw) {
        return raw == null ? "no message" : raw.replace('\n', ' ').replace('\r', ' ');
    }

    private static String rootMessage(Throwable throwable) {
        Throwable t = throwable;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private static boolean is(String actual, String expected) {
        return actual != null && actual.equalsIgnoreCase(expected);
    }

    private static List<String> filter(List<String> values, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<String>(values);
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(value);
            }
        }
        return out;
    }

    private static String repeat(String text, int count) {
        StringBuilder builder = new StringBuilder(text.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(text);
        }
        return builder.toString();
    }

    private static String join(String[] values, int start) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < values.length; i++) {
            if (i > start) out.append(' ');
            out.append(values[i]);
        }
        return out.toString();
    }

    private static String shortId(UUID uuid) {
        return uuid == null ? "none" : uuid.toString().substring(0, 8);
    }

    private static final class ParsedPlayerCommand {
        final String rawName;
        final String name;
        final String action;
        final String skinName;

        ParsedPlayerCommand(String rawName, String name, String action, String skinName) {
            this.rawName = rawName == null ? name : rawName;
            this.name = Objects.requireNonNull(name, "name");
            this.action = Objects.requireNonNull(action, "action");
            this.skinName = sanitizeName(skinName == null ? name : skinName);
        }
    }

    private static final class SkinResult {
        final String name;
        final String source;
        final String value;
        final String signature;
        final String error;

        private SkinResult(String name, String source, String value, String signature, String error) {
            this.name = sanitizeName(name);
            this.source = source == null ? "unknown" : source;
            this.value = value;
            this.signature = signature;
            this.error = error;
        }

        static SkinResult ok(String name, String source, String value, String signature) {
            return new SkinResult(name, source, value, signature, null);
        }

        static SkinResult fail(String name, String source, String error) {
            return new SkinResult(name, source, null, null, error == null ? "unknown error" : error);
        }

        boolean hasTexture() {
            return value != null && !value.isEmpty();
        }

        String statusLine() {
            if (hasTexture()) {
                return COLOR + "aOK " + COLOR + "7source=" + COLOR + "e" + source + COLOR + "7 valueChars=" + COLOR + "e" + value.length() + COLOR + "7 signed=" + COLOR + "e" + (signature != null && !signature.isEmpty());
            }
            return COLOR + "cFAILED " + COLOR + "7source=" + COLOR + "e" + source + COLOR + "7 error=" + COLOR + "c" + safeText(error);
        }
    }

    private static final class FakeHandle {
        final String name;
        final String skinName;
        final UUID visualId;
        final UUID hitboxId;
        final long createdAtMillis;
        final String backend;
        final Object bukkitPlayer;
        final Object nmsPlayer;

        FakeHandle(String name, String skinName, UUID visualId, UUID hitboxId, long createdAtMillis, String backend, Object bukkitPlayer, Object nmsPlayer) {
            this.name = name;
            this.skinName = skinName;
            this.visualId = visualId;
            this.hitboxId = hitboxId;
            this.createdAtMillis = createdAtMillis;
            this.backend = backend;
            this.bukkitPlayer = bukkitPlayer;
            this.nmsPlayer = nmsPlayer;
        }
    }

    private static final class NmsSpawn {
        final boolean success;
        final String message;
        final Object nmsPlayer;
        final Object bukkitPlayer;
        final String bukkitClass;

        private NmsSpawn(boolean success, String message, Object nmsPlayer, Object bukkitPlayer, String bukkitClass) {
            this.success = success;
            this.message = message;
            this.nmsPlayer = nmsPlayer;
            this.bukkitPlayer = bukkitPlayer;
            this.bukkitClass = bukkitClass;
        }

        static NmsSpawn ok(Object nmsPlayer, Object bukkitPlayer, String bukkitClass) {
            return new NmsSpawn(true, "ok", nmsPlayer, bukkitPlayer, bukkitClass);
        }

        static NmsSpawn fail(String message) {
            return new NmsSpawn(false, message, null, null, "none");
        }
    }
}
