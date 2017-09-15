import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Created by Gerwa on 2017/9/11.
 */
class IdleThread extends Thread {
    private Player player;
    private PlayerAssets playerAssets;

    IdleThread(String name, Player player, PlayerAssets playerAssets) {
        super(name);
        this.player = player;
        this.playerAssets = playerAssets;
    }

    @Override
    public void run() {
        while (true) {

            System.out.println("reading " + player.getName() + "'s data");
            JSONObject input;
            String command;
            try {
                String line = player.getIn().readLine();
                if (line == null) {
                    throw new JSONException("player send empty line!");
                }
                input = new JSONObject(line);
                command = input.getString("command");
            } catch (JSONException | IOException e) {
                e.printStackTrace();
                playerAssets.removePlayerByName(player.getName());//disconnect the player
                player.closeIODevices();
                return;
            }
            System.out.println("readed " + player.getName() + "'s data, command " + command);

            JSONObject output = new JSONObject();
            switch (command) {
                case "invite_response": {
                    try {
                        String inviterName = input.getString("inviterName");
                        Player inviter = playerAssets.getPlayerMap().get(inviterName);
                        if (inviter != null) {//check if the inviter really online
                            if (input.getBoolean("accept")) {//user accept the invitation and choose a deck
                                player.setDeck(input.getJSONObject("deck"));
                                inviter.getInviters().put("accept$" + player.getName());
                                playerAssets.removePlayerFromHall(player.getName());
                                //game will start later by inviter, return
                                player.setStatus(Player.Status.Playing);
                                return;
                            } else {//player rejects the invitation
                                inviter.getInviters().put("reject$" + player.getName());
                                playerAssets.getHallPlayers().add(player.getName());
                                player.setStatus(Player.Status.Idle);
                                continue;//already outputed,continue
                            }
                        } else {
                            playerAssets.removePlayerByName(player.getName());//disconnect the player
                            player.closeIODevices();
                            return;//kick the player, this is a bad solution, TODO: use a more elegant way to notify player
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        playerAssets.removePlayerByName(player.getName());//disconnect the player
                        player.closeIODevices();
                        return;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                break;
                case "isinvited":

                    //check the list if some player is inviting you
                    if (player.getInviters().isEmpty()) {//no invitation
                        try {
                            output.put("command", "respond_isinvited");
                            output.put("isinvited", false);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            playerAssets.removePlayerFromHall(player.getName());//remove player from hall
                            player.setStatus(Player.Status.Inviting);

                            String inviterName = player.getInviters().poll();
                            output.put("command", "respond_isinvited");
                            output.put("isinvited", true);
                            output.put("inviter", inviterName);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }


                    break;
                case "get_list":

                    try {
                        output.put("command", "respond_list");
                        output.put("hallplayers", playerAssets.getJSONHallPlayer());
                        output.put("ongames", playerAssets.getGameArray());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    //reponse the player players in the hall

                    break;
                case "start_match":

                    try {
                        player.setDeck(input.getJSONObject("deck"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                        playerAssets.removePlayerByName(player.getName());//disconnect the player
                        player.closeIODevices();
                        return;
                    }
                    playerAssets.removePlayerFromHall(player.getName());
                    //start matching, respond UNTIL get a match
                    try {
                        playerAssets.getMatchingPlayers().put(player.getName());
                        player.setStatus(Player.Status.Matching);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return;//game will be started by match handler


                case "invite":

                    //player wants to invite a player in hall
                    String targetName;
                    try {
                        targetName = input.getString("target");
                        player.setDeck(input.getJSONObject("deck"));//load its deck
                    } catch (JSONException e) {
                        e.printStackTrace();
                        playerAssets.removePlayerByName(player.getName());//disconnect the player
                        player.closeIODevices();
                        return;
                    }
                    if (playerAssets.getHallPlayers().contains(targetName)) {//target player really exists in hall

                        playerAssets.removePlayerFromHall(player.getName());//now the player is not in the hall
                        player.setStatus(Player.Status.Inviting);

                        Player targetPlayer = playerAssets.getPlayerMap().get(targetName);//we've check target player's existense in the hall just before
                        try {
                            targetPlayer.getInviters().put(player.getName());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        while (true) {
                            try {
                                String tempName = player.getInviters().take();
                                if (tempName.equals("accept$" + targetName)) {//other player accepts it
                                    //start a new game // by inviter

                                    playerAssets.getOngoingGames().add(new Game(new Player[]{player, targetPlayer}, playerAssets));
                                    player.setStatus(Player.Status.Playing);

                                    return;
                                } else if (tempName.equals("reject$" + targetName)) {//other player rejects it
                                    output.put("command", "respond_invite");
                                    output.put("validation", false);
                                    output.put("reason", targetName + " rejects your invitation");

                                    playerAssets.getHallPlayers().add(player.getName());//back to the hall
                                    player.setStatus(Player.Status.Idle);

                                    break;
                                } else {
                                    player.rejectPlayer(tempName);//other player, rejects it
                                }
                            } catch (InterruptedException | JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        try {
                            output.put("command", "respond_invite");
                            output.put("validation", false);
                            output.put("reason", "user not in hall");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case "watch":
                    String gameName;
                    try {
                        gameName = input.getString("gameName");
                    } catch (JSONException e) {
                        e.printStackTrace();
                        playerAssets.removePlayerByName(player.getName());//disconnect the player
                        player.closeIODevices();
                        return;
                    }
                    int index = playerAssets.indexOfGame(gameName);
                    if (index == -1) {//no such game
                        try {
                            output.put("command", "respond_watch");
                            output.put("validation", false);
                            output.put("reason", "game does not exist.");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {//add to watch the game
                        try {
                            output.put("command", "respond_watch");
                            output.put("validation", true);
                            player.writeAndflush(output + "\n");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            playerAssets.removePlayerByName(player.getName());//disconnect the player
                            player.closeIODevices();
                            return;
                        }
                        playerAssets.getOngoingGames().get(index).getPendingPlayers().add(player);
                        playerAssets.removePlayerFromHall(player.getName());
                        player.setStatus(Player.Status.Playing);
                        return;
                    }
                    break;
            }

            try {
                player.writeAndflush(output + "\n");
            } catch (IOException e) {
                e.printStackTrace();
                playerAssets.removePlayerByName(player.getName());//disconnect the player
                player.closeIODevices();
                return;
            }

        }
    }
}
