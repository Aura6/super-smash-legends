package io.github.aura6.supersmashlegends.game.state;

import com.connorlinfoot.titleapi.TitleAPI;
import io.github.aura6.supersmashlegends.SuperSmashLegends;
import io.github.aura6.supersmashlegends.database.Database;
import io.github.aura6.supersmashlegends.game.GameManager;
import io.github.aura6.supersmashlegends.game.GameResult;
import io.github.aura6.supersmashlegends.game.InGameProfile;
import io.github.aura6.supersmashlegends.kit.Kit;
import io.github.aura6.supersmashlegends.team.Team;
import io.github.aura6.supersmashlegends.team.TeamManager;
import io.github.aura6.supersmashlegends.utils.CollectionUtils;
import io.github.aura6.supersmashlegends.utils.message.Chat;
import io.github.aura6.supersmashlegends.utils.message.MessageUtils;
import io.github.aura6.supersmashlegends.utils.message.Replacers;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class EndState extends GameState {
    private String winnerString;
    private BukkitTask endCountdown;

    public EndState(SuperSmashLegends plugin) {
        super(plugin);
    }

    @Override
    public String getConfigName() {
        return "End";
    }

    @Override
    public boolean isInArena() {
        return true;
    }

    @Override
    public List<String> getScoreboard(Player player) {

        List<String> lines = new ArrayList<>(Arrays.asList(
                "&5&l---------------------",
                "&7Ending the game...",
                ""
        ));

        Replacers replacers = new Replacers();

        if (this.winnerString == null) {
            lines.add("&fResult: &eTie");

        } else {
            lines.add("&fWinner: {WINNER}");
            replacers.add("WINNER", this.winnerString);
        }

        GameManager gameManager = this.plugin.getGameManager();

        if (this.plugin.getTeamManager().doesPlayerHaveTeam(player)) {
            InGameProfile profile = gameManager.getProfile(player);

            replacers.add("KILLS", String.valueOf(profile.getKills()));
            replacers.add("KIT", profile.getKit().getBoldedDisplayName());

            lines.add("");
            lines.addAll(Arrays.asList("&fKills: &5{KILLS}", "&fKit: {KIT}"));
        }

        lines.add("&5&l---------------------");
        return replacers.replaceLines(lines);
    }

    @Override
    public void start() {
        GameManager gameManager = this.plugin.getGameManager();
        TeamManager teamManager = this.plugin.getTeamManager();

        teamManager.getAliveTeams().forEach(team -> team.setLifespan(gameManager.getTicksActive() + 1));
        Comparator<Team> comp = Comparator.comparingInt(Team::getLifespan).reversed();
        List<List<Team>> rankedTeams = CollectionUtils.getRankedGroups(teamManager.getTeamList(), comp);

        List<String> ranking = new ArrayList<>(Arrays.asList(
                "&5--------------------------",
                "&d&lFinal Player Ranking",
                ""
        ));

        Map<Player, Integer> playerRanks = new HashMap<>();
        int currRankIndex = 0;

        while (currRankIndex < Math.min(3, rankedTeams.size())) {
            StringBuilder players = new StringBuilder();

            for (Team team : rankedTeams.get(currRankIndex)) {

                for (Player player : team.getPlayers()) {
                    players.append(teamManager.getPlayerColor(player)).append(player.getName()).append("&7, ");
                    playerRanks.put(player, currRankIndex + 1);
                }
            }

            if (players.toString().endsWith(", ")) {
                players.delete(players.length() - 2, players.length());
            }

            String rankColor;

            switch (currRankIndex) {

                case 0:
                    rankColor = "&a";
                    break;

                case 1:
                    rankColor = "&e";
                    break;

                case 2:
                    rankColor = "&6";
                    break;

                default:
                    rankColor = "&9";
            }

            ranking.add(String.format("%s&l%d. %s", rankColor, currRankIndex + 1, players));
            currRankIndex++;
        }

        ranking.add("&5--------------------------");

        if (rankedTeams.get(0).size() == 1) {
            Team winningTeam = rankedTeams.get(0).get(0);
            Set<Player> winningSet = winningTeam.getPlayers();

            if (teamManager.getTeamSize() == 1) {
                Player winner = winningSet.iterator().next();
                Kit winnerKit = this.plugin.getKitManager().getSelectedKit(winner);
                this.winnerString = winnerKit.getColor() + winner.getName();

            } else {
                this.winnerString = winningTeam.getColor() + winningTeam.getName();
            }

            for (Player player : playerRanks.keySet()) {

                if (winningSet.contains(player)) {
                    gameManager.getProfile(player).setGameResult(GameResult.WIN);
                    Chat.GAME.send(player, "&7You have &a&lwon!");

                } else {
                    gameManager.getProfile(player).setGameResult(GameResult.LOSE);
                    Chat.GAME.send(player, String.format("%s &7has &awon!", this.winnerString));
                }
            }

        } else {
            Set<Player> tiedPlayers = rankedTeams.get(0).stream()
                    .flatMap(team -> team.getPlayers().stream()).collect(Collectors.toSet());

            for (Player player : playerRanks.keySet()) {
                String tieString;
                GameResult result;

                if (tiedPlayers.contains(player)) {
                    result = GameResult.TIE;
                    tieString = "&7You have &e&ltied.";

                } else {
                    result = GameResult.LOSE;

                    if (teamManager.getTeamSize() == 1) {
                        tieString = "&7There has been a &etie.";

                    } else {
                        tieString = "&7There has been a &etie &7between teams.";
                    }
                }

                Chat.GAME.send(player, tieString);
                gameManager.getProfile(player).setGameResult(result);
            }
        }

        for (Player player : playerRanks.keySet()) {
            UUID uuid = player.getUniqueId();
            InGameProfile profile = gameManager.getProfile(player);
            Database db = this.plugin.getDb();

            if (teamManager.getTeamList().size() > 1) {
                db.increaseInt(uuid, profile.getGameResult().getDbString(), 1);
                db.increaseInt(uuid, "result." + playerRanks.get(player), 1);
                db.increaseInt(uuid, "kills", profile.getKills());
                db.increaseInt(uuid, "deaths", profile.getDeaths());
                db.increaseDouble(uuid, "damageDealt", profile.getDamageDealt());
                db.increaseDouble(uuid, "damageTaken", profile.getDamageTaken());
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            ranking.forEach(line -> player.sendMessage(MessageUtils.color(line)));
            player.playSound(player.getLocation(), Sound.FIREWORK_LARGE_BLAST, 2, 1);

            if (!gameManager.isSpectator(player)) {
                Kit kit = this.plugin.getKitManager().getSelectedKit(player);
                kit.destroy();

                player.setAllowFlight(true);
            }
        }

        this.plugin.getPowerManager().stop();

        this.endCountdown = new BukkitRunnable() {
            int secondsLeft = plugin.getResources().getConfig().getInt("Game.EndWaitSeconds");
            float pitch = 0.5f;
            final double pitchStep = 1.5 / secondsLeft;

            @Override
            public void run() {

                if (this.secondsLeft == 0) {
                    gameManager.advanceState();
                    return;
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    String title = MessageUtils.color("&7Resetting in...");
                    String subtitle = MessageUtils.color("&5&l" + this.secondsLeft);
                    TitleAPI.sendTitle(player, title, subtitle, 4, 12, 4);
                    player.playSound(player.getLocation(), Sound.CLICK, 1, this.pitch);
                }

                this.pitch += this.pitchStep;
                this.secondsLeft--;
            }

        }.runTaskTimer(plugin, 70, 20);
    }

    @Override
    public void end() {
        this.winnerString = null;
        this.endCountdown.cancel();

        this.plugin.getTeamManager().reset();
        this.plugin.getWorldManager().resetWorld("arena");

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            TitleAPI.clearTitle(player);
        }
    }

    @EventHandler
    public void handleVoid(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID && event.getEntity() instanceof Player) {
            event.setCancelled(true);
            event.getEntity().teleport(this.plugin.getArenaManager().getArena().getWaitLocation());
        }
    }
}
