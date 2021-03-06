package org.mcsg.double0negative.tabconfig;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcsg.double0negative.tabapi.TabAPI;

public class TabConfig extends JavaPlugin implements Listener, CommandExecutor {

    private String[][] tab;
    private int updateTimerSeconds = -1;
    private final ConcurrentHashMap<String, int[]> ping = new ConcurrentHashMap<>();
    private int playerCount = 0;

    private TabConfig plugin;

    @Override
    public void onEnable() {
        plugin = this;

        log("Plugin version " + getDescription().getVersion() + " starting");

        saveDefaultConfig();

        PluginManager pm = getServer().getPluginManager();

        if (pm.getPlugin("TabAPI") == null) {
            log("TabAPI not detected! - disabling");
            return;
        }

        tab = new String[TabAPI.getVertSize()][TabAPI.getHorizSize()];

        getCommand("tabconfig").setExecutor(this);

        pm.registerEvents(this, this);

        load();

        if (updateTimerSeconds > 0) {
            getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
                @Override
                public synchronized void run() {
                    try {
                        playerCount = getServer().getOnlinePlayers().length;
                        updateAll();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }, updateTimerSeconds * 20, updateTimerSeconds * 20);
        }

        log("Plugin version " + getDescription().getVersion() + " started");
    }

    @Override
    public void onDisable() {
        log("Plugin version " + getDescription().getVersion() + " shutting down");
    }

    private void log(String message) {
        getLogger().info(message);
    }

    public void load() {
        this.reloadConfig();
        FileConfiguration f = this.getConfig();

        updateTimerSeconds = f.getInt("updateTimer");

        for (int a = 0; a < TabAPI.getVertSize(); a++) {
            int b = 0;
            for (String s : f.getStringList("tab." + (a + 1))) {
                int c = 0;
                while ((c = s.indexOf("{ping", c)) != -1) {
                    int v = s.indexOf("}", c);
                    String t = s.substring(c, v);
                    String[] spl = t.split("!");
                    ping.put(spl[1], new int[2]);

                    c = v;

                }
                tab[a][b] = s;
                b++;

                if (b > 2) {
                    break;
                }
            }
        }

        if (ping.size() > 0) {
            getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
                @Override
                public synchronized void run() {
                    ping.keySet().stream().forEach((s) -> {
                        String full = (s.contains(":")) ? s : s + ":25565";
                        String[] ip = full.split(":");

                        try {
                            ping.put(s, Pinger.ping(ip[0], Integer.parseInt(ip[1])));
                        } catch (NumberFormatException | IOException exception) {
                            exception.printStackTrace();
                        }
                    });
                }

            }, 20L, 20L);
        }

        updateAll();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd1, String commandLabel, String[] args) {
        PluginDescriptionFile pdfFile = this.getDescription();

        Player player = null;
        if (sender instanceof Player) {
            player = (Player) sender;
            if (player.hasPermission("tabapi.reload")) {
                load();
                updateAll();
                player.sendMessage(ChatColor.GOLD + "TabConfig reloaded!");
                player.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "TabConfig - Double0negative" + ChatColor.RESET + ChatColor.RED + " Version: " + pdfFile.getVersion());
            } else {
                player.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "TabAPI - Double0negative" + ChatColor.RESET + ChatColor.RED + " Version: " + pdfFile.getVersion());

            }
        } else {
            sender.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "TabAPI - Double0negative" + ChatColor.RESET + ChatColor.RED + " Version: " + pdfFile.getVersion());
            return true;
        }

        return true;

    }

    public String replaceVars(String s, Player p) {
        if (p == null || !p.isOnline()) {
            return "";
        }

        String r = s;
        r = r.replace("{online}", playerCount + "");
        r = r.replace("{max}", Bukkit.getMaxPlayers() + "");
        r = r.replace("{player}", p.getName());
        r = r.replace("{displayname}", p.getDisplayName());
        r = r.replace("{tabname}", p.getPlayerListName());
        r = r.replace("{servername}", Bukkit.getServerName());

        r = ChatColor.translateAlternateColorCodes('&', r);

        int c = 0;
        while ((c = r.indexOf("{ping", c)) != -1) {
            int v = r.indexOf("}", c);
            String t = r.substring(c, v);
            String[] spl = t.split("!");
            String sy = "";
            if (spl[2].equalsIgnoreCase("online")) {
                sy = ping.get(spl[1])[0] + "";
            }
            if (spl[2].equalsIgnoreCase("max")) {
                sy = ping.get(spl[1])[1] + "";
            }
            r = r.substring(0, c)
                    + sy
                    + r.substring(v + 1);

            c = c + sy.length();
        }
        return r;
    }

    public void updateAll() {
        final Player[] onlinePlayers = getServer().getOnlinePlayers();

        try {
            for (Player p : onlinePlayers) {
                update(p);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }
    }

    public void update(Player p) {
        try {
            if (p == null || !p.isOnline()) {
                return;
            }

            for (int a = 0; a < tab.length; a++) {
                for (int b = 0; b < tab[a].length; b++) {
                    if (tab[a][b] != null) {
                        String y = replaceVars(tab[a][b], p);
                        if (y.equalsIgnoreCase("{fillplayers}")) {
                            fillPlayers(p, a, b);
                            break;
                        }
                        TabAPI.setTabString(this, p, a, b, y + ((y.length() < 15) ? TabAPI.nextNull() : ""));
                    }
                }
            }
            if (p.isOnline()) {
                TabAPI.updatePlayer(p);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }
    }

    private void fillPlayers(Player p, int a, int b) {
        final Player[] onlinePlayers = getServer().getOnlinePlayers();

        for (Player pl : onlinePlayers) {
            if (pl == null || !pl.isOnline()) {
                continue;
            }

            TabAPI.setTabString(this, p, a, b, buildSpaces(pl.getName(), 16 - pl.getName().length()));

            b++;
            if (b == 3) {
                b = 0;
                a++;
            }
            if (a > TabAPI.getVertSize() - 1) {
                return;
            }
        }
    }

    private String buildSpaces(String string, int amount) {
        if (string.length() >= 16) {
            string = string.substring(0, 15);
            amount = 1;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(string);

        for (int i = 0; i <= amount; i++) {
            builder.append(" ");
        }

        return builder.toString();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void playerLogin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                try {
                    TabAPI.setPriority(plugin, p, 0);
                    playerCount = getServer().getOnlinePlayers().length;
                    update(p.getPlayer());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return;
                }
            }
        });
    }
}
