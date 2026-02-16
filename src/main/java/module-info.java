module org.example.marksmanfx {
    requires javafx.controls;
    requires javafx.fxml;

    opens org.example.marksmanfx to javafx.fxml;
    exports org.example.marksmanfx;

    exports org.example.marksmanfx.Controllers;
    opens org.example.marksmanfx.Controllers to javafx.fxml;

    exports org.example.marksmanfx.Models;
    opens org.example.marksmanfx.Models to javafx.fxml;

    exports org.example.marksmanfx.Engine;
}
