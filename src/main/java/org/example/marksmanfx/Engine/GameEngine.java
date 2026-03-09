package org.example.marksmanfx.Engine;

import org.example.marksmanfx.Models.GameModel;
import org.example.marksmanfx.Models.GameSnapshot;

import java.util.function.Consumer;

/// Прослойка между UI и моделью
/// - принимать команды от контроллера;
/// - запускать и останавливать игровые циклы;
/// - после каждого изменения публиковать снимок состояния в UI.
public class GameEngine {
    private final GameModel model;

    public GameEngine(GameModel model) {
        this.model = model;
    }

    public void startNewGame() {
        model.startNewGame();
    }

    /// Останавливаем игру в модели, отдельно прерывает поток стрелы и обновляет экран
    public void stopGame() {
        model.stopGame();
    }

    /// Переключаем паузу в модели и обновляет кадр
    public void togglePause() {
        model.togglePause();
    }

    public boolean fireArrow(double chargeRatio) {
        return model.fireArrow(chargeRatio);
    }

    /// Сдвигаем лучника на переданную величину и обновляем экран.
    public void moveArcher(double deltaX, double deltaY) {
        model.moveArcher(deltaX, deltaY);
    }

    /// Меняем угол на переданную величину и обновляем экран
    public void aim(double deltaAngle) {
        model.aim(deltaAngle);
    }

    public void moveArcherUp() {
        model.moveArcherUp();
    }

    public void moveArcherDown() {
        model.moveArcherDown();
    }

    public void moveArcherLeft() {
        model.moveArcherLeft();
    }

    public void moveArcherRight() {
        model.moveArcherRight();
    }

    public void aimUp() {
        model.aimUp();
    }

    public void aimDown() {
        model.aimDown();
    }

    /// Переключаем стойку и публикуем кадр
    public void toggleCrouch() {
        model.toggleCrouch();
    }

    /// Обновляем мишени и стрелу
    public void update(double deltaSeconds) {
        model.updateTargets(deltaSeconds);
        model.updateArrow(deltaSeconds);
    }

    public GameSnapshot snapshot() {
        return model.snapshot();
    }
}
