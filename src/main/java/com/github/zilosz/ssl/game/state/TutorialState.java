package com.github.zilosz.ssl.game.state;

import com.connorlinfoot.actionbarapi.ActionBarAPI;
import com.github.zilosz.ssl.utils.math.VectorUtils;
import com.github.zilosz.ssl.utils.message.Replacers;
import com.github.zilosz.ssl.SSL;
import com.github.zilosz.ssl.arena.Arena;
import com.github.zilosz.ssl.game.GameManager;
import com.github.zilosz.ssl.kit.Kit;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class TutorialState extends GameState {
    private final Set<Player> playersInTutorial = new HashSet<>();
    private final Map<UUID, BukkitTask> movers = new HashMap<>();
    private final Map<UUID, BukkitTask> moveDelayers = new HashMap<>();
    private final Map<UUID, BukkitTask> tutorialSchedulers = new HashMap<>();
    private final Map<UUID, BukkitTask> ruleDisplayers = new HashMap<>();
    private final Map<UUID, BukkitTask> skinChangers = new HashMap<>();
    private BukkitTask skipTask;

    public TutorialState(SSL plugin) {
        super(plugin);
    }

    @Override
    public String getConfigName() {
        return "Tutorial";
    }

    @Override
    public boolean isInArena() {
        return true;
    }

    @Override
    public boolean allowKitSelection() {
        return false;
    }

    @Override
    public boolean updatesKitSkins() {
        return false;
    }

    @Override
    public boolean allowsDamage() {
        return false;
    }

    @Override
    public boolean allowSpecCommand() {
        return false;
    }

    @Override
    public List<String> getScoreboard(Player player) {

        List<String> lines = new ArrayList<>(Arrays.asList(
                this.getScoreboardLine(),
                "&f&lStatus",
                "&7Enjoy the tutorial!",
                "",
                "&f&lArena",
                "{ARENA}",
                "",
                "&f&lAuthors",
                "&7{AUTHORS}"
        ));

        Arena arena = this.plugin.getArenaManager().getArena();

        Replacers replacers = new Replacers()
                .add("ARENA", arena.getName())
                .add("AUTHORS", arena.getAuthors());

        if (!this.plugin.getGameManager().isSpectator(player)) {
            lines.add("");
            lines.add("&f&lKit");
            lines.add("{KIT}");
            replacers.add("KIT", this.plugin.getKitManager().getSelectedKit(player).getDisplayName());
        }

        lines.add(this.getScoreboardLine());
        return replacers.replaceLines(lines);
    }

    private void stopPlayerAfterCompletion(Player player) {
        player.setVelocity(new Vector(0, 0, 0));
        player.setGameMode(GameMode.SURVIVAL);
        player.setFlySpeed(0.1f);
        this.playersInTutorial.remove(player);
    }

    private void stopPlayerDuringMovement(Player player) {
        stopPlayerAfterCompletion(player);
        UUID uuid = player.getUniqueId();
        Optional.ofNullable(this.movers.remove(uuid)).ifPresent(BukkitTask::cancel);
        Optional.ofNullable(this.moveDelayers.remove(uuid)).ifPresent(BukkitTask::cancel);
        Optional.ofNullable(this.tutorialSchedulers.remove(uuid)).ifPresent(BukkitTask::cancel);
    }

    private void startTutorialMovement(Player player, List<Location> points, double speed, int from, int to) {

        if (from == points.size() || points.size() <= 1) {
            stopPlayerAfterCompletion(player);

            if (playersInTutorial.size() == 0) {
                plugin.getGameManager().advanceState();
            }

            return;
        }

        Vector velocity = VectorUtils.fromTo(points.get(from), points.get(to)).normalize().multiply(speed);
        BukkitTask moveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> player.setVelocity(velocity), 0, 0);
        this.movers.put(player.getUniqueId(), moveTask);

        int stepDuration = (int) ((points.get(from).distance(points.get(to))) / speed);

        this.moveDelayers.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> {
            moveTask.cancel();
            startTutorialMovement(player, points, speed, from + 1, (to + 1) % points.size());
        }, stepDuration));
    }

    @Override
    public void start() {
        GameManager gameManager = this.plugin.getGameManager();

        int specCount = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {

            if (gameManager.isSpectator(player)) {
                player.teleport(this.plugin.getArenaManager().getArena().getWaitLocation());
                specCount++;
            }
        }

        if (specCount == Bukkit.getOnlinePlayers().size()) {
            this.skipTask = Bukkit.getScheduler().runTaskLater(this.plugin, gameManager::advanceState, 5);
            return;
        }

        Set<Player> players = gameManager.getAlivePlayers();
        this.playersInTutorial.addAll(players);

        Arena arena = plugin.getArenaManager().getArena();
        List<Location> tutorialLocations = arena.getTutorialLocations();

        double totalDistance = tutorialLocations.get(0).distance(tutorialLocations.get(tutorialLocations.size() - 1));

        for (int i = 1; i < tutorialLocations.size(); i++) {
            totalDistance += tutorialLocations.get(i).distance(tutorialLocations.get(i - 1));
        }

        totalDistance += tutorialLocations.get(tutorialLocations.size() - 1).distance(tutorialLocations.get(0));

        int tutorialDuration = plugin.getResources().getConfig().getInt("Game.Tutorial.DurationTicks");
        double velocity = totalDistance / tutorialDuration;
        int delay = plugin.getResources().getConfig().getInt("Game.Tutorial.DelayTicks");

        List<String> rules = new Replacers()
                .add("LIVES", plugin.getResources().getConfig().getInt("Game.Lives"))
                .replaceLines(plugin.getResources().getConfig().getStringList("Rules"));

        for (Player player : players) {
            Kit kit = this.plugin.getKitManager().getSelectedKit(player);
            player.setGameMode(GameMode.SPECTATOR);

            this.skinChangers.put(player.getUniqueId(), kit.getSkin().applyAcrossTp(this.plugin, player, () -> {
                player.teleport(tutorialLocations.get(0));
                player.playSound(player.getLocation(), Sound.ENDERMAN_TELEPORT, 2, 1);
                player.setFlySpeed(0);

                this.tutorialSchedulers.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(this.plugin,
                        () -> startTutorialMovement(player, tutorialLocations, velocity, 0, 1), delay));

                int ruleDelay = ((int) (double) tutorialDuration / (rules.size() + 1));

                this.ruleDisplayers.put(player.getUniqueId(), new BukkitRunnable() {
                    int i = 0;

                    @Override
                    public void run() {

                        if (this.i == rules.size()) {
                            cancel();
                            return;
                        }

                        ActionBarAPI.sendActionBar(player, rules.get(this.i++));
                        player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1, 0.5F);
                    }

                }.runTaskTimer(this.plugin, ruleDelay + 15, ruleDelay));
            }));
        }
    }

    @Override
    public void end() {
        this.movers.values().forEach(BukkitTask::cancel);
        this.movers.clear();

        this.ruleDisplayers.values().forEach(BukkitTask::cancel);
        this.ruleDisplayers.clear();

        this.tutorialSchedulers.values().forEach(BukkitTask::cancel);
        this.tutorialSchedulers.clear();

        this.moveDelayers.values().forEach(BukkitTask::cancel);
        this.moveDelayers.clear();

        this.skinChangers.values().forEach(BukkitTask::cancel);
        this.skinChangers.clear();

        new ArrayList<>(this.playersInTutorial).forEach(this::stopPlayerAfterCompletion);

        if (this.skipTask != null) {
            this.skipTask.cancel();
        }
    }

    @EventHandler
    public void onTutorialQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (this.playersInTutorial.contains(player)) {
            this.playersInTutorial.remove(player);
            this.stopPlayerDuringMovement(player);
        }
    }
}