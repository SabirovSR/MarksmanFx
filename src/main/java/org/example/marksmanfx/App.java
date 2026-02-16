package org.example.marksmanfx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.marksmanfx.Controllers.MainViewController;

import java.io.IOException;

public class App extends Application {
    static {
        // Fallback to software pipeline to avoid RTTexture crashes on some GPU drivers.
        System.setProperty("prism.order", "sw");
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("MainView.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        scene.getStylesheets().add(App.class.getResource("game.css").toExternalForm());

        MainViewController controller = fxmlLoader.getController();
        stage.setOnCloseRequest(event -> controller.shutdown());

        stage.setTitle("Меткий стрелок");
        stage.setMinWidth(1420);
        stage.setMinHeight(850);
        stage.setWidth(1420);
        stage.setHeight(850);
        stage.setResizable(true);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}