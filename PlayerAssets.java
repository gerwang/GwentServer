import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Gerwa on 2017/9/11.
 */
class PlayerAssets {
    synchronized HashMap<String, Player> getPlayerMap() {
        return playerMap;
    }

    synchronized ArrayList<String> getHallPlayers() {
        return hallPlayers;
    }

    private HashMap<String, Player> playerMap;
    private ArrayList<String> hallPlayers;

    synchronized ArrayList<Game> getOngoingGames() {
        return ongoingGames;
    }

    private ArrayList<Game> ongoingGames;
    private LinkedBlockingQueue<String> matchingPlayers;

    LinkedBlockingQueue<String> getMatchingPlayers() {
        return matchingPlayers;
    }


    PlayerAssets() {
        playerMap = new HashMap<>();
        hallPlayers = new ArrayList<>();
        ongoingGames = new ArrayList<>();
        matchingPlayers = new LinkedBlockingQueue<>();
    }

    synchronized void removePlayerByName(String username) {
        System.out.println("removing " + username);
        Player player = playerMap.get(username);
        if (player != null) {
            player.closeIODevices();
        }
        playerMap.remove(username);
        hallPlayers.remove(username);
        System.out.println("removed " + username);
    }


    synchronized void removePlayerFromHall(String username) {
        hallPlayers.remove(username);
        if (playerMap.containsKey(username)) {
            Player player = playerMap.get(username);
            while (!player.getInviters().isEmpty()) {
                String inviterName = player.getInviters().poll();
                player.rejectPlayer(inviterName);
            }
        }
    }

    synchronized JSONArray getGameArray() {
        JSONArray result = new JSONArray();
        for (Game game : ongoingGames) {
            result.put(game.toString());
        }
        return result;
    }

    synchronized JSONArray getJSONHallPlayer() {
        JSONArray result = new JSONArray();
        for (String player : hallPlayers) {
            result.put(player);
        }
        return result;
    }

    synchronized int indexOfGame(String gameName) {
        for (int i = 0; i < ongoingGames.size(); i++) {
            if (ongoingGames.get(i).toString().equals(gameName)) {
                return i;
            }
        }
        return -1;
    }

    synchronized void movePlayerToHall(Player player) {
        playerMap.put(player.getName(), player);
        hallPlayers.add(player.getName());
        player.setStatus(Player.Status.Idle);
        player.setGame(null);//release the game resource
        IdleThread idleThread = new IdleThread(player.getName() + " idle thread", player, this);
        idleThread.start();
    }
}
