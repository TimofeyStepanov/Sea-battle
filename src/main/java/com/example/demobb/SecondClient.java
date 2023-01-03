package com.example.demobb;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class SecondClient extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("secondClientView.fxml"));
        stage.setTitle("Game");
        stage.setScene(new Scene(root, 1000, 700));
        stage.setResizable(false);
        stage.show();
    }
    public static void main(String[] args) {
        launch();
    }


}