import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Random;

/**
 * Created by Gerwa on 2017/9/11.
 */
public class Game extends Thread {
    private Player[] gamers;
    private PlayerAssets playerAssets;
    private ArrayList<Player> pendingPlayers;
    private ArrayList<Player> watchers;
    private Random random;
    private JSONObject gameState;
    private static final double K = 32;
    private boolean gameEndFlag;
    private int currentPlayer;

    private ArrayList<JSONObject> states;

    Game(Player[] gamers, PlayerAssets playerAssets) {
        this.gamers = gamers;
        this.playerAssets = playerAssets;
        pendingPlayers = new ArrayList<>();
        watchers = new ArrayList<>();
        random = new Random();
        currentPlayer = 0;
        for (int i = 0; i < 2; i++) {
            gamers[i].setGame(this);
        }
        states = new ArrayList<>();
        start();//start the thread
    }

    synchronized ArrayList<Player> getPendingPlayers() {
        return pendingPlayers;
    }

    @Override
    public void run() {
        int randomSeed = random.nextInt();
        //initialize and start the game
        for (int i = 0; i < 2; i++) {
            JSONObject output = new JSONObject();
            try {
                output.put("command", "init");
                output.put("deck", gamers[i ^ 1].getDeck());
                output.put("name", gamers[i ^ 1].getName());
                output.put("seed", randomSeed);
                output.put("localplayer", i);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            try {
                gamers[i].writeAndflush(output + "\n");
            } catch (IOException e) {
                e.printStackTrace();
                removeGamer(gamers[i]);
            }
        }

        System.out.println("starting gameloop");

        //start game loop
        while (true) {

            //get all players' ready and save the game state
            boolean atLeastOne = false;
            for (int i = watchers.size() - 1; i >= 0; i--) {
                atLeastOne |= handleStateUpload(watchers.get(i));
            }
            for (int i = 0; i < 2; i++) {
                if (gamers[i].getStatus() != Player.Status.Offline) {
                    atLeastOne |= (handleStateUpload(gamers[i]));
                }
            }

            for (int i = 1; i < states.size(); i++) {
                if (!states.get(i - 1).toString().equals(states.get(i).toString())) {
                    System.err.println("mismatch!");
                    System.err.println(states.get(i));
                    System.err.println(states.get(i - 1));
                }
            }
            states.clear();

            if (atLeastOne) {

                if (gameEndFlag) {//the game ends
                    cleanUpGame();
                    handleRatingChange(gamers, currentPlayer);//the current player field at game end has a SPECIAL meaning, means the winner, or -1 if draw
                    return;
                }

                boolean shouldReset;

                synchronized (this) {//synchronize pending players
                    shouldReset = !pendingPlayers.isEmpty();
                    while (!pendingPlayers.isEmpty()) {
                        Player pender = pendingPlayers.get(pendingPlayers.size() - 1);
                        pendingPlayers.remove(pendingPlayers.size() - 1);
                        handlePendingPlayer(pender);
                    }
                }

                if (currentPlayer == -1) {
                    currentPlayer = Math.abs(random.nextInt() % 2);
                    if (currentPlayer < 0) {
                        System.err.println("negative!");
                    }
                }

                for (int i = watchers.size() - 1; i >= 0; i--) {
                    System.out.println("starting round " + watchers.get(i).getName());
                    startRound(shouldReset, currentPlayer, watchers.get(i));
                }
                for (Player gamer : gamers) {
                    if (gamer.getStatus() != Player.Status.Offline) {
                        System.out.println("starting player " + gamer.getName());
                        startRound(shouldReset, currentPlayer, gamer);
                    }
                }

                System.out.println("start listening " + gamers[currentPlayer].getName());

                while (true) {
                    JSONObject input = null;
                    String command = null;
                    boolean offline = (gamers[currentPlayer].getStatus() == Player.Status.Offline);
                    if (!offline) {
                        try {
                            String line = gamers[currentPlayer].getIn().readLine();
                            if (line == null) {
                                throw new JSONException("player send empty line");
                            }
                            input = new JSONObject(line);
                            command = input.getString("command");
                        } catch (JSONException | IOException e) {
                            e.printStackTrace();
                            removeGamer(gamers[currentPlayer]);
                            offline = true;
                            System.out.println(gamers[currentPlayer].getName() + " offline");
                        }
                    }

                    if (offline) {
                        input = new JSONObject();
                        try {
                            input.put("command", "transmit");
                            input.put("online", false);
                            command = "transmit";
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            input.put("online", true);//add a new field to tell the watchers if the gamer is online
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    assert command != null;
                    if (command.equals("transmit")) {
                        System.out.println("transmiting data");

                        for (int i = watchers.size() - 1; i >= 0; i--) {
                            handleTransmit(watchers.get(i), input);//now the input simply becomes output
                        }

                        for (int i = 0; i < 2; i++) {
                            if (currentPlayer != i && gamers[i].getStatus() != Player.Status.Offline) {
                                System.out.println("transmit to " + gamers[i].getName());
                                handleTransmit(gamers[i], input);//now the input simply becomes output
                            }
                        }

                        try {
                            if (!input.getBoolean("online")) {
                                break;//force the current player to end input
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    } else if (command.equals("end_transmit")) {//current player finished the transmit
                        System.out.println("end transmit");
                        break;//end normally
                    }
                }


            } else {//no one is seeing the game world, discard
                System.out.println("no one is seeing the game");
                cleanUpGame();
                return;
            }
        }

    }

    private void handleRatingChange(Player[] gamers, int winner) {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection connection = DriverManager.getConnection(LoginThread.databasePath);
            Statement statement = connection.createStatement();
            int[] R = new int[2];
            for (int i = 0; i < 2; i++) {
                ResultSet resultSet = statement.executeQuery("SELECT rating FROM gwent_player WHERE username='" + gamers[i].getName() + "'");
                while (resultSet.next()) {
                    R[i] = resultSet.getInt("rating");
                }
                resultSet.close();
            }
            double[] E = new double[2];
            double[] S = new double[2];
            for (int i = 0; i < 2; i++) {
                E[i] = 1.0 / (1.0 + Math.pow(10.0, (R[i ^ 1] - R[i]) / 400.0));
                if (winner == -1) {
                    S[i] = 0.5;
                } else if (winner == i) {
                    S[i] = 1;
                } else {
                    S[i] = 0;
                }
                R[i] = (int) (R[i] + K * (S[i] - E[i]));
                statement.executeUpdate("UPDATE gwent_player SET rating=" + R[i] + " WHERE username='" + gamers[i].getName() + "'");
            }
            connection.commit();
            statement.close();
            connection.close();

        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    private void handlePlayerGameEnd(Player player) {
        JSONObject output = new JSONObject();
        try {
            output.put("command", "end");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (!handleTransmit(player, output)) {
            playerAssets.movePlayerToHall(player);
        }
    }

    private boolean handleTransmit(Player player, JSONObject output) {//return false if success
        try {
            player.writeAndflush(output + "\n");
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            remove(player);
            return true;
        }
    }

    private boolean handleStateUpload(Player player) {
        boolean valid = false;
        try {
            String line = player.getIn().readLine();
            if (line == null) {
                throw new JSONException("player send empty line!");
            }
            JSONObject input = new JSONObject(line);
            if (input.getString("command").equals("ready")) {
                gameState = input.getJSONObject("gameState");//save current state
                gameEndFlag = input.getBoolean("gameEndFlag");
                currentPlayer = input.getInt("currentPlayer");

                states.add(gameState);

                valid = true;
            } else {
                System.err.println("unhandled game input!");
            }
        } catch (JSONException | IOException e) {
            e.printStackTrace();
            remove(player);
        }
        return valid;
    }

    private void handlePendingPlayer(Player pender) {
        int index = getPlayerType(pender);

        JSONObject output = new JSONObject();
        try {
            if (index == -1) {
                output.put("command", "startwatch");
            } else {
                output.put("command", "resumegame");
            }
            output.put("gameState", gameState);
            //player can judge if he is a gamer by game state's playername
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            pender.writeAndflush(output + "\n");
        } catch (IOException e) {
            e.printStackTrace();
            return; //discard the player
        }

        if (index == -1) {
            watchers.add(pender);
        } else {
            pender.setStatus(Player.Status.Playing);
        }
    }

    private void startRound(boolean shouldReset, int currentPlayer, Player player) {
        JSONObject output = new JSONObject();
        try {
            output.put("command", "startround");
            output.put("shouldreset", shouldReset);
            output.put("currentplayer", currentPlayer);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        handleTransmit(player, output);
    }

    private void cleanUpGame() {
        playerAssets.getOngoingGames().remove(this);//remove the game from ongoing games
        synchronized (this) {
            while (!pendingPlayers.isEmpty()) {
                Player pender = pendingPlayers.get(pendingPlayers.size() - 1);
                pendingPlayers.remove(pendingPlayers.size() - 1);
                handlePlayerGameEnd(pender);//and move them to hall
            }
        }
        for (Player gamer : gamers) {
            handlePlayerGameEnd(gamer);
            if (gamer.getStatus() == Player.Status.Offline) {//delete the player from game
                playerAssets.removePlayerByName(gamer.getName());
            }
        }
        for (int i = watchers.size() - 1; i >= 0; i--) {
            handlePlayerGameEnd(watchers.get(i));
        }
    }

    private void removeWatcher(Player watcher) {
        watchers.remove(watcher);
        watcher.closeIODevices();
        playerAssets.removePlayerByName(watcher.getName());//completely move player out of the game
    }

    private void removeGamer(Player gamer) {
        gamer.setStatus(Player.Status.Offline);
        gamer.closeIODevices();
    }

    private void remove(Player player) {
        int index = getPlayerType(player);
        if (index == -1) {
            removeWatcher(player);
        } else {
            removeGamer(player);
        }
    }

    private int getPlayerType(Player player) {
        int index = -1;
        for (int i = 0; i < 2; i++) {
            if (gamers[i].getName().equals(player.getName())) {
                index = i;
                break;
            }
        }
        return index;
    }

    @Override
    public String toString() {
        if (gamers != null && gamers.length >= 2) {
            return gamers[0].getName() + " VS " + gamers[1].getName();
        } else {
            return "null_game";//for test and debug
        }
    }
}
