package com.example.demobb.server;

import com.example.demobb.PlayingClasses.*;
import com.example.demobb.Phone;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.example.demobb.server.ServerConstants.*;

public class AServer {
    private static boolean allMembersAreConnected = false;
    private static int currentPlayersNumber = 0;
    private static final Map<Phone, PlayingField> phoneAndPlayingField = new HashMap<>();
    private static Phone attackerPhone, defenderPhone;
    private static ServerSocket serverSocket;

    public static void main(String[] args) {
        try {
            startServer();
        } catch (IOException | ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        closeServer();
    }
    private static void startServer() throws IOException, ExecutionException, InterruptedException {
        System.out.println("Server is started");
        serverSocket = new ServerSocket(40);

        Socket firstSocket = serverSocket.accept();
        Phone firstPhone = new Phone(firstSocket);
        attackerPhone = firstPhone;
        CompletableFuture<Void> firstFieldFillingTask = CompletableFuture
            .runAsync(() -> {
                try {
                    firstPhone.send("We are waiting your opponent");
                    fillField(firstPhone);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            })
            .thenRunAsync(() -> {
                    sentMsgAboutGameStart(firstPhone);
                try {
                    playGame(firstPhone);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

        Socket secondSocket = serverSocket.accept();
        Phone secondPhone = new Phone(secondSocket);
        defenderPhone = secondPhone;
        allMembersAreConnected = true;

        System.out.println("All members are hear");

        firstPhone.send(MSG_TO_START_FIELD_FILLING);
        secondPhone.send(MSG_TO_START_FIELD_FILLING);

        CompletableFuture<Void> secondFieldFillingTask = CompletableFuture
            .runAsync(() -> {
                try {
                    fillField(secondPhone);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            })
            .thenRunAsync(() -> {
                sentMsgAboutGameStart(secondPhone);
                try {
                    playGame(secondPhone);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

        CompletableFuture<?> startGameResults = CompletableFuture.anyOf(firstFieldFillingTask, secondFieldFillingTask);
        startGameResults.get();
    }

    private static void fillField(Phone phone) throws IOException {
        PlayingField playingField = new PlayingField();
        phone.send(new ShipInfoDto(playingField.getInfoAboutShipsNeededToPut()));
        while (true) {
            try
            {
                Object clientMsg = phone.receive();
                if (!allMembersAreConnected) {
                    phone.send("Waiting your opponent");
                    continue;
                }

                FieldFillingDto fieldFillingDto = getEnterData(clientMsg);
                List<Point> puttedPoints = playingField.fillField(fieldFillingDto.startPoint, fieldFillingDto.finishPoint);
                for (Point puttedPoint : puttedPoints) {
                    phone.send(new PointDto(puttedPoint, PointType.SHIP, Owner.PLAYER));
                }

                //phone.send("Ship is filled");
                if (playingField.allShipsAreFilled()) {
                    saveUser(phone, playingField);
                    phone.send(new ShipInfoDto(playingField.getInfoAboutShipsNeededToPut()));
                    phone.send(MSG_TO_STOP_FIELD_FILLING);
                    return;
                }
                phone.send(new ShipInfoDto(playingField.getInfoAboutShipsNeededToPut()));
                phone.send("Success");
                System.out.println("Fill");

            } catch (IOException e) {
                phone.send("Socket error");
            } catch (IllegalArgumentException | WrongFieldFillingException e) {
                phone.send(e.getMessage());
            } catch (ClassNotFoundException e) {
                System.err.println("Cast exception. Can't fill cell");
            }
            //phone.send(MSG_TO_START_FIELD_FILLING);
        }
    }
    private static FieldFillingDto getEnterData(Object clientMessage) {
        if (!(clientMessage instanceof FieldFillingDto)) {
            throw new IllegalArgumentException("Wrong input format");
        }
        return (FieldFillingDto) clientMessage;
    }
    private static synchronized void saveUser(Phone phone, PlayingField playingField) {
        phoneAndPlayingField.put(phone, playingField);
        currentPlayersNumber++;
    }
    private static void sentMsgAboutGameStart(Phone phone) {
        try {
            if (!otherPlayerIsNotReady()) {
                attackerPhone.send(MSG_TO_START_GAME);
                attackerPhone.send(MSG_TO_ATTACKER_ABOUT_GAME_RULES);
                defenderPhone.send(MSG_TO_START_GAME);
                defenderPhone.send(MSG_TO_DEFENDER_ABOUT_GAME_RULES);

                PlayingField attackerField = phoneAndPlayingField.get(attackerPhone);
                PlayingField defenderField = phoneAndPlayingField.get(defenderPhone);

                attackerPhone.send(new ShipInfoDto(defenderField.getInfoAboutShipsNeededToAttack()));
                defenderPhone.send(new ShipInfoDto(attackerField.getInfoAboutShipsNeededToAttack()));
            } else {
                phone.send("We are waiting your opponent");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void playGame(Phone phone) throws IOException {
        while (true) {
            try {
                Object clientMsg = phone.receive();
                if (otherPlayerIsNotReady()) {
                    phone.send("Sorry. We are waiting your opponent");
                    continue;
                }

                if (defenderPhone.equals(phone)) {
                    phone.send(MSG_TO_DEFENDER_ABOUT_GAME_RULES);
                } else {
                    Point pointToAttack = getPointFromAttackerMsg(clientMsg);
                    PlayingField fieldToAttack = phoneAndPlayingField.get(defenderPhone);

                    Point attackedPoint = new Point(pointToAttack.row, pointToAttack.column);
                    boolean attackIsSuccess = fieldToAttack.attackIsSuccess(pointToAttack);
                    if (attackIsSuccess) {
                        //attackerPhone.send(new ShipInfoDto(fieldToAttack.getInfoAboutShipsNeededToAttack()));
                        attackerPhone.send(new PointDto(attackedPoint, PointType.DESTROYED_SHIP, Owner.OPPONENT));
                        defenderPhone.send(new PointDto(attackedPoint, PointType.DESTROYED_SHIP, Owner.PLAYER));
                    } else {
                        attackerPhone.send(new PointDto(attackedPoint, PointType.MISS, Owner.OPPONENT));
                        defenderPhone.send(new PointDto(attackedPoint, PointType.MISS, Owner.PLAYER));
                    }

                    if (fieldToAttack.allShipsAreDestroyed()) {
                        attackerPhone.send(MSG_FOR_WINNER_ABOUT_GAME_FINISH);
                        defenderPhone.send(MSG_FOR_LOSER_ABOUT_GAME_FINISH);
                        return;
                    }

                    if (!attackIsSuccess) {
                        Phone tmp = attackerPhone;
                        attackerPhone = defenderPhone;
                        defenderPhone = tmp;
                    }

                    attackerPhone.send(MSG_TO_ATTACKER_ABOUT_GAME_RULES);
                    defenderPhone.send(MSG_TO_DEFENDER_ABOUT_GAME_RULES);
                }
            } catch (IOException e) {
                phone.send("Socket error. Try again");
            } catch (WrongAttackArgumentException | IllegalArgumentException e) {
                phone.send(e.getMessage());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
    private static boolean otherPlayerIsNotReady() {
        return NUMBER_OF_PLAYERS != currentPlayersNumber;
    }
    private static Point getPointFromAttackerMsg(Object clientMsg) {
        if (!(clientMsg instanceof PointDto)) {
            throw new IllegalArgumentException("Wrong input format");
        }
        return ((PointDto) clientMsg).point;
    }
    private static void closeServer() {
        try {
//            attackerPhone.send(MSG_TO_CLOSE_SOCKET);
//            defenderPhone.send(MSG_TO_CLOSE_SOCKET);

            serverSocket.close();
            attackerPhone.close();
            defenderPhone.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
