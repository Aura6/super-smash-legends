package io.github.aura6.supersmashlegends.game.state;

import com.connorlinfoot.titleapi.TitleAPI;
import io.github.aura6.supersmashlegends.SuperSmashLegends;
import io.github.aura6.supersmashlegends.attribute.Attribute;
import io.github.aura6.supersmashlegends.attribute.Nameable;
import io.github.aura6.supersmashlegends.damage.DamageManager;
import io.github.aura6.supersmashlegends.game.InGameProfile;
import io.github.aura6.supersmashlegends.kit.Kit;
import io.github.aura6.supersmashlegends.kit.KitManager;
import io.github.aura6.supersmashlegends.team.Team;
import io.github.aura6.supersmashlegends.team.TeamManager;
import io.github.aura6.supersmashlegends.utils.NmsUtils;
import io.github.aura6.supersmashlegends.utils.effect.DeathNPC;
import io.github.aura6.supersmashlegends.utils.math.MathUtils;
import io.github.aura6.supersmashlegends.utils.message.Chat;
import io.github.aura6.supersmashlegends.utils.message.MessageUtils;
import io.github.aura6.supersmashlegends.utils.message.Replacers;
import net.minecraft.server.v1_8_R3.PacketPlayInClientCommand;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InGameState extends GameState {
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();

    public InGameState(SuperSmashLegends plugin) {
        super(plugin);
    }

    @Override
    public String getConfigName() {
        return "InGame";
    }

    private String getPlayerLivesText(Player player, int lifeCap, String nameColor) {
        int lives = plugin.getGameManager().getProfile(player).getLives();
        double lifePercentage = (double) lives / lifeCap;

        String lifeColor;

        if (lifePercentage <= 0.25) {
            lifeColor = "&c";

        } else if (lifePercentage <= 0.5) {
            lifeColor = "&6";

        } else if (lifePercentage <= 0.75) {
            lifeColor = "&e";

        } else {
            lifeColor = "&a";
        }

        return MessageUtils.color(String.format("%s%s: %s%s", nameColor, player.getName(), lifeColor, lives));
    }

    @Override
    public List<String> getScoreboard(Player player) {

        List<String> scoreboard = new ArrayList<>(Arrays.asList(
                "&5&l---------------------",
                "&cAttack &7others to win!",
                ""
        ));

        TeamManager teamManager = plugin.getTeamManager();
        int lifeCap = plugin.getResources().getConfig().getInt("Game.Lives");

        if (teamManager.getTeamSize() == 1) {
            scoreboard.add("&5&lPlayers");

            for (Player alive : plugin.getGameManager().getAlivePlayers()) {
                scoreboard.add(getPlayerLivesText(alive, lifeCap, "&7"));
            }

            scoreboard.add("");

        } else {
            scoreboard.add("&5&lTeams");

            for (Team team : teamManager.getAliveTeams()) {

                for (Player member : team.getPlayers()) {
                    scoreboard.add(getPlayerLivesText(member, lifeCap, team.getColor()));
                }
            }

            scoreboard.add("");
        }

        Replacers replacers = new Replacers();

        List<String> lore = new ArrayList<>(List.of(
                "&5&l---------------------"
        ));

        if (!plugin.getGameManager().isSpectator(player)) {
            lore.add(0, "&fKit: {KIT}");
            replacers.add("KIT", plugin.getKitManager().getSelectedKit(player).getBoldedDisplayName());
        }

        scoreboard.addAll(replacers.replaceLines(lore));

        return scoreboard;
    }

    @Override
    public boolean isInGame() {
        return true;
    }

    @Override
    public void start() {
        plugin.getPowerManager().startPowerTimer();

        List<Location> spawnLocations = plugin.getArenaManager().getArena().getSpawnLocations();
        List<Location> spawnsLeft = new ArrayList<>(spawnLocations);

        for (Player player : Bukkit.getOnlinePlayers()) {
            Location spawn = MathUtils.selectRandom(spawnsLeft);
            spawnsLeft.remove(spawn);
            player.teleport(spawn);

            if (spawnsLeft.size() == 0) {
                spawnsLeft = spawnLocations;
            }

            plugin.getKitManager().getSelectedKit(player).activate();

            player.playSound(player.getLocation(), Sound.WOLF_HOWL, 2, 0.8f);
            TitleAPI.sendTitle(player, MessageUtils.color("&7The &5game &7has started!"), "", 5, 30, 5);
        }
    }

    private void respawnPlayer(Player player) {
        player.teleport(plugin.getArenaManager().getArena().getFarthestSpawnFromPlayers());

        TitleAPI.sendTitle(player, MessageUtils.color("&7You have &arespawned&7."), MessageUtils.color("&cAvenge &7your death!"), 10, 30, 10);
        player.playSound(player.getLocation(), Sound.ENDERMAN_TELEPORT, 3, 2);
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 3, 1);

        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20);

        Kit kit = plugin.getKitManager().getSelectedKit(player);
        kit.giveItems();
        kit.activate();
    }

    @Override
    public void end() {
        respawnTasks.forEach((uuid, respawnTask) -> {
            respawnPlayer(Bukkit.getPlayer(uuid));
            respawnTask.cancel();
        });

        respawnTasks.clear();

        for (Player player : plugin.getGameManager().getAlivePlayers()) {
            plugin.getKitManager().getSelectedKit(player).deactivate();
            TitleAPI.clearTitle(player);
        }
    }

    private void handleDeath(Player died, boolean spawnNpc) {
        died.setVelocity(new Vector(0, 0, 0));

        KitManager kitManager = plugin.getKitManager();
        Kit diedKit = kitManager.getSelectedKit(died);
        diedKit.destroy();

        if (spawnNpc) {
            DeathNPC.spawn(plugin, died);
        }

        died.setGameMode(GameMode.SPECTATOR);
        NmsUtils.getConnection(died).a(new PacketPlayInClientCommand(PacketPlayInClientCommand.EnumClientCommand.PERFORM_RESPAWN));

        InGameProfile profile = plugin.getGameManager().getProfile(died);
        profile.setLives(profile.getLives() - 1);
        profile.setDeaths(profile.getDeaths() + 1);

        String deathMessage;
        Location tpLocation;

        DamageManager damageManager = plugin.getDamageManager();
        Attribute killingAttribute = damageManager.getLastDamagingAttribute(died);

        String diedName = diedKit.getColor() + died.getName();

        if (killingAttribute == null) {
            tpLocation = plugin.getArenaManager().getArena().getWaitLocation();
            deathMessage = String.format("%s &7died.", diedName);

        } else {
            Player killer = killingAttribute.getPlayer();
            Kit killerKit = kitManager.getSelectedKit(killer);

            killer.playSound(killer.getLocation(), Sound.LEVEL_UP, 2, 2);
            killer.playSound(killer.getLocation(), Sound.WOLF_HOWL, 3, 2);

            InGameProfile killerProfile = plugin.getGameManager().getProfile(killer);
            killerProfile.setKills(killerProfile.getKills() + 1);
            killerProfile.setKillStreak(killerProfile.getKillStreak() + 1);

            String killerName = killerKit.getColor() + killer.getName();

            if (killingAttribute instanceof Nameable) {
                String killName = ((Nameable) killingAttribute).getDisplayName();
                deathMessage = String.format("%s &7killed by %s &7with %s.", diedName, killerName, killName);

            } else {
                deathMessage = String.format("%s &7was killed by %s.", diedName, killerName);
            }

            tpLocation = killer.getLocation();
        }

        Chat.DEATH.broadcast(deathMessage);
        died.teleport(tpLocation);

        damageManager.destroyIndicator(died);
        damageManager.removeDamageSource(died);
        damageManager.clearImmunities(died);

        if (profile.getLives() == 0) {
            died.playSound(died.getLocation(), Sound.WITHER_DEATH, 2, 1);
            TitleAPI.sendTitle(died, MessageUtils.color("&7You have been"), MessageUtils.color("&celiminated!"), 7, 25, 7);
            Chat.DEATH.broadcast(MessageUtils.color(String.format("%s%s &7has been &celiminated!", diedKit.getColor(), died.getName())));

            Team diedTeam = plugin.getTeamManager().getPlayerTeam(died);

            if (!diedTeam.isAlive()) {
                diedTeam.setLifespan(plugin.getGameManager().getTicksActive());
            }

            if (plugin.getTeamManager().isGameTieOrWin()) {
                plugin.getGameManager().advanceState();
            }

            return;
        }

        TitleAPI.sendTitle(died, MessageUtils.color("&7You &cdied!"), MessageUtils.color("&7Respawning soon..."), 7, 25, 7);
        died.playSound(died.getLocation(), Sound.ENDERMAN_TELEPORT, 3, 1);

        respawnTasks.put(died.getUniqueId(), new BukkitRunnable() {
            int secondsLeft = plugin.getResources().getConfig().getInt("Game.DeathWaitSeconds");
            float pitch = 0.5f;
            final double pitchStep = 1.5 / secondsLeft;

            @Override
            public void run() {

                if (secondsLeft == 0) {
                    respawnPlayer(died);
                    respawnTasks.remove(died.getUniqueId());
                    cancel();
                    return;
                }

                TitleAPI.sendTitle(died, MessageUtils.color("&7Respawning in..."), MessageUtils.color("&5&l" + secondsLeft), 4, 12, 4);
                died.playSound(died.getLocation(), Sound.ENDERDRAGON_HIT, 2, pitch);

                pitch += pitchStep;
                secondsLeft--;
            }

        }.runTaskTimer(plugin, 60, 20));
    }

    @EventHandler
    public void onRegularDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();

        if (event.getCause() != EntityDamageEvent.DamageCause.FALL && plugin.getGameManager().isPlayerAlive(player)) {
            InGameProfile profile = plugin.getGameManager().getProfile(player);
            profile.setDamageTaken(profile.getDamageTaken() + event.getFinalDamage());
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {

            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.teleport(plugin.getArenaManager().getArena().getWaitLocation());

            } else {
                handleDeath(player, false);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.getDrops().clear();
        event.setDeathMessage("");
        handleDeath(event.getEntity(), true);
    }

    @EventHandler
    public void onQuitMidGame(PlayerQuitEvent event) {
        if (plugin.getGameManager().isPlayerAlive(event.getPlayer())) {
            plugin.getGameManager().uploadPlayerStatsMidGame(event.getPlayer());
        }
    }
}
