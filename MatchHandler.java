import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Created by Gerwa on 2017/9/11.
 */
public class MatchHandler extends Thread {
    private PlayerAssets playerAssets;

    MatchHandler(PlayerAssets playerAssets) {
        this.playerAssets = playerAssets;
    }

    @Override
    public void run() {
        String[] playerName = new String[2];
        while (true) {//loop always
            for (int i = 0; i < 2; i++) {
                if (playerName[i] == null || !playerAssets.getPlayerMap().containsKey(playerName[i])) {
                    try {
                        playerName[i] = playerAssets.getMatchingPlayers().poll(100, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (playerName[i] != null) {
                    Player player = playerAssets.getPlayerMap().get(playerName[i]);
                    JSONObject heartbeat = new JSONObject();
                    try {
                        heartbeat.put("command", "heartbeat");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    try {
                        player.writeAndflush(heartbeat + "\n");
                        heartbeat = new JSONObject(player.getIn().readLine());
                        if (!heartbeat.getString("command").equals("heartbeat_response")) {
                            throw new IOException("mismatch network message");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        player.closeIODevices();
                        playerAssets.removePlayerByName(playerName[i]);
                        playerName[i] = null;
                    }
                }
            }
            boolean okay = true;
            for (int i = 0; i < 2; i++) {
                if (playerName[i] == null || !playerAssets.getPlayerMap().containsKey(playerName[i])) {
                    okay = false;
                    break;
                }
            }
            if (okay) {
                Player[] players = new Player[2];
                for (int i = 0; i < 2; i++) {
                    players[i] = playerAssets.getPlayerMap().get(playerName[i]);
                    playerName[i] = null;
                    players[i].setStatus(Player.Status.Playing);
                }
                playerAssets.getOngoingGames().add(new Game(players, playerAssets));
            }
        }
    }
}
