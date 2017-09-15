import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by Gerwa on 2017/9/11.
 */
class GwentServer {

    private static final int port = 2333;

    void startProcess() {

        PlayerAssets playerAssets = new PlayerAssets();
        new MatchHandler(playerAssets).start();//handle match queue

        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            System.err.println("open serversocket fail!");
            e.printStackTrace();
            return;
        }

        System.out.println("Server started");
        while (true) {//listen always
            try {
                Socket clientSocket = serverSocket.accept();
                LoginThread loginThread = new LoginThread(clientSocket, playerAssets);
                loginThread.start();
            } catch (IOException e) {
                System.err.println("accept fail");
                e.printStackTrace();//skip the client
            }
        }
    }
}
