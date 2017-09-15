import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.sql.*;

/**
 * Created by Gerwa on 2017/9/11.
 */
public class LoginThread extends Thread {
    static final String databasePath = "jdbc:sqlite://D:/PycharmProjects/GwentRegisterServer/db.sqlite3";

    private Socket clientSocket;
    private PlayerAssets playerAssets;

    LoginThread(Socket clientSocket, PlayerAssets playerAssets) {
        this.clientSocket = clientSocket;
        this.playerAssets = playerAssets;
    }

    private void putPlayerInHall(JSONObject replyInfo, BufferedReader in, BufferedWriter out, String username) throws JSONException, IOException {
        replyInfo.put("validation", true);
        replyInfo.put("resume", false);
        out.write(replyInfo + "\n");
        out.flush();
        //respond to player

        //create a new player instance
        Player player = new Player(playerAssets);
        player.setName(username);
        player.setSocket(clientSocket);
        player.setIn(in);
        player.setOut(out);
        player.setStatus(Player.Status.Idle);
        playerAssets.getPlayerMap().put(username, player);
        playerAssets.getHallPlayers().add(username);//add it to the hall players

        //start the mainmenu thread
        IdleThread idleThread = new IdleThread(player.getName() + " idlethread", player, playerAssets);
        idleThread.start();
    }

    @Override
    public void run() {
        System.out.println("login thread started");
        String username = null;
        BufferedReader in = null;
        BufferedWriter out = null;
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            JSONObject loginInfo = new JSONObject(in.readLine());
            username = loginInfo.getString("username");
            String password = loginInfo.getString("password");

            JSONObject replyInfo = new JSONObject();

            if (isPlayerExits(username, password)) {
                if (playerAssets.getPlayerMap().containsKey(username)) {//already have one
                    Player existingPlayer = playerAssets.getPlayerMap().get(username);
                    if (existingPlayer.getGame() != null) {//resume game
                        existingPlayer.setStatus(Player.Status.Offline);

                        System.out.println(username + "is resuming game");

                        replyInfo.put("validation", true);
                        replyInfo.put("resume", true);

                        //put player to the game pending list to wait for the next round

                        existingPlayer.setSocket(clientSocket);
                        existingPlayer.setIn(in);
                        existingPlayer.setOut(out);
                        //update the player's new IO device

                        existingPlayer.getGame().getPendingPlayers().add(existingPlayer);//WARNING:game can not be null
                        //put it to the pending list3
                        out.write(replyInfo + "\n");
                        out.flush();
                        //respond to player
                    } else {

                        System.out.println(username + " duplicate login");
                        playerAssets.removePlayerByName(username);//remove the prelogin player

                        putPlayerInHall(replyInfo, in, out, username);
                        /*
                        replyInfo.put("validation", false);
                        replyInfo.put("reason", "user has already logged in.");
                        out.write(replyInfo + "\n");
                        out.flush();
                        //respond to player
                        */
                    }
                } else {//normal way

                    System.out.println(username + "is entering mainmenu");

                    putPlayerInHall(replyInfo, in, out, username);

                }
            } else {

                System.out.println(username + " " + password + " incorrect");

                replyInfo.put("validation", false);
                replyInfo.put("reason", "user name or password incorrect.");
                out.write(replyInfo + "\n");
                out.flush();
                //respond to player
            }


            //*just use exception to close the thread
            if (!replyInfo.getBoolean("validation")) {
                //reject player's login
                in.close();
                out.close();
                clientSocket.close();
            }

        } catch (IOException | JSONException e) {
            e.printStackTrace();
            if (username != null) {
                playerAssets.removePlayerByName(username);//delete the user, so he can login later
            }
//            this will remove player from game because of others' duplicate login, obselete
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            try {
                clientSocket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        System.out.println("login thread finished");
    }

    private boolean isPlayerExits(String username, String password) {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection connection = DriverManager.getConnection(databasePath);
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT * FROM gwent_player WHERE username='" + username + "' AND password='" + password + "'");
            boolean result = resultSet.isBeforeFirst();//if such user exists
            resultSet.close();
            statement.close();
            connection.close();
            return result;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.err.println("cannot find sql class");
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("cannot access the datebase");
            return false;
        }
    }

}
