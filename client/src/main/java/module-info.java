module org.example.marksmanfx.client {
    requires org.example.marksmanfx.common;   // automatic module via Manifest entry
    requires javafx.controls;
    requires javafx.fxml;
    requires java.logging;

    opens org.example.marksmanfx.client              to javafx.fxml;
    opens org.example.marksmanfx.client.ui           to javafx.fxml;
    opens org.example.marksmanfx.client.ui.login     to javafx.fxml;
    opens org.example.marksmanfx.client.ui.lobby     to javafx.fxml;
    opens org.example.marksmanfx.client.ui.game      to javafx.fxml;

    exports org.example.marksmanfx.client;
}
