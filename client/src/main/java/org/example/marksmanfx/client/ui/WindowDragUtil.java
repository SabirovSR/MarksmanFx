package org.example.marksmanfx.client.ui;

import javafx.scene.Node;
import javafx.stage.Stage;

/**
 * Утилита для перетаскивания окон без системных рамок за выбранный узел.
 * Обычно в роли такого узла выступает кастомная шапка окна.
 *
 * Использование: вызовите {@code WindowDragUtil.enable(titleBar, stage)}
 * в методе {@code init()} контроллера после загрузки FXML.
 */
public final class WindowDragUtil {

    private WindowDragUtil() {}

    /**
     * Подвешивает обработчики нажатия и перетаскивания на {@code handle},
     * чтобы окно следовало за курсором мыши.
     *
     * @param handle узел, который используется как область перетаскивания
     * @param stage  окно, которое нужно перемещать
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

