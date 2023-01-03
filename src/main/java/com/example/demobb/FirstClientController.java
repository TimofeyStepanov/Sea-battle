package com.example.demobb;

import com.example.demobb.PlayingClasses.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.example.demobb.server.ServerConstants.*;

public class FirstClientController {
    @FXML
    private GridPane myFieldGrid;

    @FXML
    private GridPane opponentFieldGrid;

    @FXML
    private TextField mainTextField;

    @FXML
    private TextField shipInfoTextField;

    @FXML
    private GridPane shipsNumberGrid;

    @FXML
    private VBox vBox;

    @FunctionalInterface
    private interface CreatorShipImageView {
        ImageView create();
    }

    private Node selectedShipFRomMyField = null;

    private boolean shipFillingIsStarted = false;
    private boolean needToStopListening = false;

    private Socket socket;
    private Phone phone;

    private Label myFieldCell;

    private final Map<GridPane, Owner> gridAndItsOwner = new HashMap<>();

    private Image shipImage;
    private Image shipAttackedImage;
    private Image missImage;

    private final Map<PointType, CreatorShipImageView> pointTypeAndItsImageViewCreator = new HashMap<>();
    private final String shipImageUrl = "ship.png";
    private final String shipAttackedImageUrl = "shipAttacked.png";
    private final String missImageUrl = "miss.png";

    private final int shipCellSize = 40, shipInfoSize = 30, sizeOfAttackedShipImg = 40, sizeOfShipImg = 40, sizeOfMissImg = 40;

    @FXML
    public void initialize() {
        try {
            uploadImages();
        } catch (Exception e) {
            System.err.println("No images!");
        }

        initMyFieldGridAndShipGrid();
        openConnection();
        startListeningToChangeTextField();

        activatePlayerFieldGrid();
    }

    private void uploadImages() {
        shipImage = new Image(getClass().getResourceAsStream(shipImageUrl));
        shipAttackedImage = new Image(getClass().getResourceAsStream(shipAttackedImageUrl));
        missImage = new Image(getClass().getResourceAsStream(missImageUrl));

        pointTypeAndItsImageViewCreator.put(PointType.DESTROYED_SHIP, () -> {
            ImageView imageView = new ImageView(shipAttackedImage);
            imageView.setFitWidth(sizeOfAttackedShipImg); imageView.setFitHeight(sizeOfAttackedShipImg);
            return imageView;
        });

        pointTypeAndItsImageViewCreator.put(PointType.SHIP, () -> {
            ImageView imageView = new ImageView(shipImage);
            imageView.setFitWidth(sizeOfShipImg); imageView.setFitHeight(sizeOfShipImg);
            return imageView;
        });

        pointTypeAndItsImageViewCreator.put(PointType.MISS, () -> {
            ImageView imageView = new ImageView(missImage);
            imageView.setFitWidth(sizeOfMissImg); imageView.setFitHeight(sizeOfMissImg);
            return imageView;
        });
    }

    private void initMyFieldGridAndShipGrid() {
        int rows = myFieldGrid.getRowCount(), columns = myFieldGrid.getColumnCount();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                myFieldCell = createCell(shipCellSize);
                myFieldGrid.add(myFieldCell, i, j);
                opponentFieldGrid.add(createCell(shipCellSize), i, j);
            }
        }
        gridAndItsOwner.put(myFieldGrid, Owner.PLAYER);
        gridAndItsOwner.put(opponentFieldGrid, Owner.OPPONENT);

        rows = shipsNumberGrid.getRowCount();
        columns = shipsNumberGrid.getColumnCount();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                shipsNumberGrid.add(createCell(shipInfoSize), j, i);
            }
        }
    }

    private Label createCell(int length) {
        Label label = new Label();
        label.setPrefSize(length, length);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    private void openConnection() {
        try {
            socket = new Socket("localhost", 40);
            phone = new Phone(socket);
        } catch (IOException e) {
            mainTextField.setText("Server is not available now");
        }
    }

    private void startListeningToChangeTextField() {
        CompletableFuture
            .supplyAsync(() -> {
                try {
                    return getMsgFromServer(phone);
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
                return null;
            });
    }

    private  Void getMsgFromServer(Phone phone) throws IOException, ClassNotFoundException {
        while (true) {
            Object dto = phone.receive();
            if (dto instanceof String) {
                processString((String)dto);
                if (needToStopListening) break;
            } else if (dto instanceof PointDto) {
                updateShipCell((PointDto)dto);
            } else if (dto instanceof ShipInfoDto) {
                updateShips((ShipInfoDto)dto);
            }
        }
        return null;
    }

    private void activatePlayerFieldGrid() {
        myFieldGrid.setOnMouseClicked(e -> {
            if (!(e.getTarget() instanceof Label)) {
                return;
            }

            Label tartNode = (Label)e.getTarget();
            String style = "-fx-background-color: #58588c";

            if (selectedShipFRomMyField == null) {
                tartNode.setStyle(style);
                selectedShipFRomMyField = tartNode;
            } else {
                int startColumn = GridPane.getColumnIndex(selectedShipFRomMyField);
                int startRow = GridPane.getRowIndex(selectedShipFRomMyField);

                int finishColumn = GridPane.getColumnIndex(tartNode);
                int finishRow = GridPane.getRowIndex(tartNode);

                if (shipFillingIsStarted) {
                    System.out.printf("Sent to server %d %d %d %d%n", startRow, startColumn, finishRow, finishColumn);
                    try {
                        phone.send(new FieldFillingDto(new Point(startRow, startColumn), new Point(finishRow, finishColumn)));
                    } catch (IOException ex) {
                        mainTextField.setText("Error. Can't sent points");
                    }
                }
                selectedShipFRomMyField.setStyle(null);
                selectedShipFRomMyField = null;
            }
        });
    }


    private void processString(String messageFromServer) {
        if (MSG_FOR_WINNER_ABOUT_GAME_FINISH.equals(messageFromServer) | MSG_FOR_LOSER_ABOUT_GAME_FINISH.equals(messageFromServer)) {
            needToStopListening = true;
            opponentFieldGrid.setOnMouseClicked(null);
        } else if (MSG_TO_START_FIELD_FILLING.equals(messageFromServer)) {
            shipFillingIsStarted = true;
        } else if (MSG_TO_STOP_FIELD_FILLING.equals(messageFromServer)) {
            myFieldGrid.setOnMouseClicked(null);
        } else if (MSG_TO_START_GAME.equals(messageFromServer)) {
            Platform.runLater(()->shipInfoTextField.setText("Opponent ships"));
            activateOpponentField();
        } else {
            System.out.println(messageFromServer);
        }
        Platform.runLater(() -> mainTextField.setText(messageFromServer));

    }

    private void updateShipCell(PointDto pointDto) {
        GridPane fieldGrid = (pointDto.pointOwner == Owner.PLAYER) ?  myFieldGrid : opponentFieldGrid;
        Label cellToUpdate = (Label) getFromGrid(fieldGrid, pointDto.point.row, pointDto.point.column);
        if (cellToUpdate != null) {
            CreatorShipImageView creatorShipImageView = pointTypeAndItsImageViewCreator.get(pointDto.pointType);
            if (creatorShipImageView == null) return;
            Platform.runLater(() -> cellToUpdate.setGraphic(creatorShipImageView.create()));

        }
    }

    private void updateShips(ShipInfoDto shipInfo) {
        Set<Integer> currentNumberOfShipsAndItsCellValues  = shipInfo.cellValueOfShipsAndTheirsNumbers.keySet();
        int column = 0;
        for (Integer shipCellValue : currentNumberOfShipsAndItsCellValues) {
            Label numberLabel = (Label) getFromGrid(shipsNumberGrid, 0, column);
            Label valueLabel = (Label) getFromGrid(shipsNumberGrid, 1, column);

            if (numberLabel == null || valueLabel == null) return;

            Platform.runLater(() -> {
                numberLabel.setText(String.valueOf(shipCellValue));
                valueLabel.setText(String.valueOf(shipInfo.cellValueOfShipsAndTheirsNumbers.get(shipCellValue)));
            });

            column++;
        }
    }
    private Node getFromGrid(GridPane gridPane, int row, int column) {
        for (Node node : gridPane.getChildren()) {
            if(GridPane.getColumnIndex(node) != null
                    && GridPane.getColumnIndex(node) != null
                    && GridPane.getRowIndex(node) == row
                    && GridPane.getColumnIndex(node) == column) {
                return node;
            }
        }
        return null;
    }

    private void activateOpponentField() {
        opponentFieldGrid.setOnMouseClicked(e -> {
            if (!(e.getTarget() instanceof Label)) {
                return;
            }

            Label pressedNode = (Label) e.getTarget();

            int column = GridPane.getColumnIndex(pressedNode);
            int row = GridPane.getRowIndex(pressedNode);

            System.out.printf("Sent to server %d %d\n", row, column);
            try {
                phone.send(new PointDto(new Point(row, column)));
            } catch (IOException ex) {
                mainTextField.setText("Error. Can't sent point");
            }
        });
    }

    @FXML
    public void stop() {
        try {
            phone.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}