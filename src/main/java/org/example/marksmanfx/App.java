package org.example.marksmanfx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.marksmanfx.Controllers.MainViewController;

import java.io.IOException;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Кастомим системную рамку
        stage.initStyle(StageStyle.UNDECORATED);

        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("MainView.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        scene.getStylesheets().add(App.class.getResource("game.css").toExternalForm());

        MainViewController controller = fxmlLoader.getController();
        controller.attachStage(stage);
        stage.setOnCloseRequest(event -> controller.shutdown());

        stage.setMinWidth(1420);
        stage.setMinHeight(850);
        stage.setWidth(1420);
        stage.setHeight(850);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}