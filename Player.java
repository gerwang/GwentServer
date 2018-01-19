import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Gerwa on 2017/9/11.
 */
class Player {
    private String name;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private Status status;
    private Game game;
    private PlayerAssets playerAssets;


    public enum Status {
        Idle, Matching, Playing, Offline
    }

    void setGame(Game game) {
        this.game = game;
    }

    JSONObject getDeck() {
        return deck;
    }

    void setDeck(JSONObject deck) {
        this.deck = deck;
    }

    private JSONObject deck;

    synchronized LinkedBlockingQueue<String> getInviters() {
        return inviters;
    }

    private LinkedBlockingQueue<String> inviters;

    Player(PlayerAssets playerAssets) {
        this.playerAssets = playerAssets;
        inviters = new LinkedBlockingQueue<>();
    }

    Game getGame() {
        return game;
    }


    void setName(String name) {
        this.name = name;
    }

    void setIn(BufferedReader in) {
        this.in = in;
    }

    void setOut(BufferedWriter out) {
        this.out = out;
    }

    synchronized Status getStatus() {
        return status;
    }

    synchronized void setStatus(Status status) {
        this.status = status;
    }

    String getName() {
        return name;
    }

    BufferedReader getIn() {
        return in;
    }

//    public BufferedWriter getOut() {
//        return out;
//    }

    void setSocket(Socket socket) {
        this.socket = socket;
    }

    void rejectPlayer(String name) {//the name must appear in the inviter
        if (playerAssets.getPlayerMap().containsKey(name)) {
            Player targetPlayer = playerAssets.getPlayerMap().get(name);
            if (targetPlayer != null) {
                try {
                    targetPlayer.getInviters().put("reject$" + this.name);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    void writeAndflush(String line) throws IOException {
        out.write(line);
        out.flush();
    }

    void closeIODevices() {
        try {
            in.close();
            out.close();
            socket.close();
            for (String inviterName : inviters) {
                rejectPlayer(inviterName);//rejectall players to avoid dead lock
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
