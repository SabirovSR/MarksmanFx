package org.example.marksmanfx.client.ui;

import javafx.scene.Node;
import javafx.stage.Stage;

/**
 * Utility for making undecorated windows draggable by a designated handle node
 * (usually the custom title bar HBox).
 *
 * Usage: call {@code WindowDragUtil.enable(titleBar, stage)} inside the
 * controller's {@code init()} method, after the FXML is loaded.
 */
public final class WindowDragUtil {

    private WindowDragUtil() {}

    /**
     * Attaches press-and-drag handlers to {@code handle} so that the stage
     * follows the mouse cursor.
     *
     * @param handle a node that will act as the drag handle (title bar)
     * @param stage  the stage to reposition
     */
    public static void enable(Node handle, Stage stage) {
        double[] dragOffset = {0.0, 0.0};

        handle.setOnMousePressed(e -> {
            dragOffset[0] = e.getScreenX() - stage.getX();
            dragOffset[1] = e.getScreenY() - stage.getY();
        });

        handle.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - dragOffset[0]);
            stage.setY(e.getScreenY() - dragOffset[1]);
        });
    }
}
