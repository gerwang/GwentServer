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
                        playerName[i] = playerAssets.getMatchingPlayers().take();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
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
