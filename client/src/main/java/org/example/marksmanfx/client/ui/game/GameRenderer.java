package org.example.marksmanfx.client.ui.game;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.example.marksmanfx.common.message.GameStateMessage;
import org.example.marksmanfx.common.model.ArrowDto;
import org.example.marksmanfx.common.model.GamePhase;
import org.example.marksmanfx.common.model.PlayerStateDto;
import org.example.marksmanfx.common.model.TargetDto;

import java.util.List;
import java.util.Locale;

/**
 * Stateless-рендерер: принимает снимок состояния мира и рисует кадр на Canvas.
 *
 * Визуальные соглашения:
 *   • Локальный игрок — обычные цвета, белый никнейм
 *   • Соперники — 40 % прозрачность («тени»), красный полупрозрачный никнейм
 *   • Стрелы соперников — красный оттенок + 45 % прозрачность
 *   • Никнеймы «парят» над головой (Fix 1): позиция рассчитывается
 *     от верхней точки фигурки, а не от archerY напрямую
 */
public final class GameRenderer {

    static final double WORLD_WIDTH  = 960.0;
    static final double WORLD_HEIGHT = 560.0;

    private GameRenderer() {}

    // ─── Главный метод отрисовки кадра ────────────────────────────────────────

    public static void render(GraphicsContext gc,
                              GameStateMessage state,
                              String localPlayerId,
                              double chargeRatio) {
        gc.clearRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        gc.save();
        applyRoundedClip(gc, 0, 0, WORLD_WIDTH, WORLD_HEIGHT, 18);

        drawBackground(gc, WORLD_WIDTH, WORLD_HEIGHT);
        drawGuides(gc, WORLD_HEIGHT, state.nearTarget(), state.farTarget());

        // Сначала рисуем соперников (тени), чтобы локальный игрок был поверх
        List<PlayerStateDto> players = state.players();
        for (PlayerStateDto p : players) {
            if (!p.playerId().equals(localPlayerId)) {
                renderPlayer(gc, p, false, state.arrows());
            }
        }
        // Поверх всех рисуем локального игрока
        for (PlayerStateDto p : players) {
            if (p.playerId().equals(localPlayerId)) {
                renderPlayer(gc, p, true, state.arrows());
            }
        }

        drawTarget(gc, state.nearTarget(), true);
        drawTarget(gc, state.farTarget(), false);

        // Рисуем оверлей паузы, если игра остановлена
        if (state.phase() == GamePhase.PAUSED) {
            drawPauseOverlay(gc, "ПАУЗА");
        }

        // Рисуем шкалу заряда выстрела
        if (chargeRatio > 0.001) {
            drawChargeBar(gc, chargeRatio);
        }

        gc.restore();
    }

    // ─── Оверлеи окончания / паузы ─────────────────────────────────────────────

    /** Стандартный экран победы: имя победителя на тёмном фоне. */
    public static void renderGameOver(GraphicsContext gc, String winnerNickname) {
        gc.save();
        gc.setFill(Color.rgb(4, 10, 22, 0.78));
        gc.fillRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);

        gc.setFill(Color.web("#e2e8f0"));
        gc.fillRoundRect(WORLD_WIDTH * 0.5 - 210, WORLD_HEIGHT * 0.5 - 72, 420, 144, 24, 24);

        gc.setFont(Font.font("System", FontWeight.BOLD, 28));
        gc.setFill(Color.web("#0f172a"));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("ПОБЕДИТЕЛЬ", WORLD_WIDTH * 0.5, WORLD_HEIGHT * 0.5 - 24);

        gc.setFont(Font.font("System", FontWeight.BOLD, 38));
        gc.setFill(Color.web("#16a34a"));
        gc.fillText(winnerNickname, WORLD_WIDTH * 0.5, WORLD_HEIGHT * 0.5 + 30);

        gc.setFont(Font.font("System", 14));
        gc.setFill(Color.web("#64748b"));
        gc.fillText("Нажмите «Реванш» или «Выйти в лобби»",
                WORLD_WIDTH * 0.5, WORLD_HEIGHT * 0.5 + 58);
        gc.restore();
    }

    /**
     * Fix 2: Техническая победа из-за дисконнекта.
     * Показываем специальный баннер вместо стандартного экрана победы,
     * чтобы оставшийся игрок понял, почему матч завершился.
     */
    public static void renderTechnicalWin(GraphicsContext gc, String disconnectedNickname) {
        gc.save();
        gc.setFill(Color.rgb(4, 10, 22, 0.78));
        gc.fillRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);

        gc.setFill(Color.web("#e2e8f0"));
        gc.fillRoundRect(WORLD_WIDTH * 0.5 - 260, WORLD_HEIGHT * 0.5 - 76, 520, 152, 24, 24);

        // Заголовок
        gc.setFont(Font.font("System", FontWeight.BOLD, 24));
        gc.setFill(Color.web("#0f172a"));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("ТЕХНИЧЕСКАЯ ПОБЕДА", WORLD_WIDTH * 0.5, WORLD_HEIGHT * 0.5 - 34);

        // Пояснение: кто ушёл
        gc.setFont(Font.font("System", 16));
        gc.setFill(Color.web("#475569"));
        gc.fillText("Игрок «" + disconnectedNickname + "» покинул матч.",
                WORLD_WIDTH * 0.5, WORLD_HEIGHT * 0.5 + 4);

        // Итог
        gc.setFont(Font.font("System", FontWeight.BOLD, 20));
        gc.setFill(Color.web("#16a34a"));
        gc.fillText("Вы побеждаете!", WORLD_WIDTH * 0.5, WORLD_HEIGHT * 0.5 + 40);

        gc.setFont(Font.font("System", 13));
        gc.setFill(Color.web("#64748b"));
        gc.fillText("Нажмите «Реванш» или «Выйти в лобби»",
                WORLD_WIDTH * 0.5, WORLD_HEIGHT * 0.5 + 64);
        gc.restore();
    }

    /**
     * Баннер «запрошена пауза» поверх игрового поля.
     * Показываем, пока не все подтвердят — это состояние PAUSE_REQUESTED.
     */
    public static void renderPauseRequested(GraphicsContext gc, String requesterNickname) {
        gc.save();
        gc.setFill(Color.rgb(4, 10, 22, 0.45));
        gc.fillRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);

        gc.setFill(Color.web("#fef3c7"));
        gc.fillRoundRect(WORLD_WIDTH * 0.5 - 280, WORLD_HEIGHT * 0.5 - 44, 560, 88, 16, 16);

        gc.setFill(Color.web("#78350f"));
        gc.setFont(Font.font("System", FontWeight.BOLD, 20));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("«" + requesterNickname + "» запрашивает паузу",
                WORLD_WIDTH * 0.5, WORLD_HEIGHT * 0.5 - 6);
        gc.setFont(Font.font("System", 15));
        gc.fillText("Нажмите P, чтобы подтвердить",
                WORLD_WIDTH * 0.5, WORLD_HEIGHT * 0.5 + 22);
        gc.restore();
    }

    // ─── Рендер одного игрока (лучник + никнейм + его стрела) ─────────────────

    private static void renderPlayer(GraphicsContext gc, PlayerStateDto p,
                                     boolean isLocal, List<ArrowDto> arrows) {
        // Рисуем фигурку лучника (тень — полупрозрачная)
        double opacity = isLocal ? 1.0 : 0.40;
        gc.save();
        gc.setGlobalAlpha(opacity);
        drawArcher(gc, p.archerX(), p.archerY(), p.aimAngleDegrees(), p.crouched());
        gc.restore();

        // Fix 1: Вычисляем верхнюю точку фигурки динамически,
        // чтобы никнейм «парил» ровно над головой, а не поверх неё.
        // Для стоящего лучника голова находится примерно на archerY - 66,
        // шляпа/причёска чуть выше — archerY - 70. Для присевшего всё ниже.
        double archerTopY = p.crouched()
                ? p.archerY() - 4    // верхний край присевшей фигурки
                : p.archerY() - 72;  // верхний край стоящей (голова + шляпа)
        double nickY = archerTopY - 10; // отступаем ещё 10 пикселей вверх

        // Рисуем тень текста для лучшей читаемости на любом фоне
        gc.save();
        gc.setFont(Font.font("System", FontWeight.BOLD, 13));
        gc.setTextAlign(TextAlignment.CENTER);
        double textX = p.archerX() + 18; // центруем по торсу

        gc.setFill(Color.rgb(0, 0, 0, 0.55));
        gc.fillText(p.nickname(), textX, nickY + 1); // тень смещена на 1 пиксель

        gc.setFill(isLocal
                ? Color.rgb(255, 255, 255, 0.95)   // белый для локального
                : Color.rgb(255, 90, 90, 0.88));    // красноватый для соперника
        gc.fillText(p.nickname(), textX, nickY);
        gc.restore();

        // Рисуем стрелу, принадлежащую этому игроку
        for (ArrowDto a : arrows) {
            if (a.ownerId().equals(p.playerId()) && a.active()) {
                gc.save();
                gc.setGlobalAlpha(isLocal ? 1.0 : 0.45);
                drawArrow(gc, a, isLocal);
                gc.restore();
            }
        }
    }

    // ─── Примитивы отрисовки (перенесены из MainViewController) ──────────────

    private static void drawBackground(GraphicsContext gc, double w, double h) {
        // Рисуем градиентный фон: ночное небо → лес
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, null,
                new Stop(0,    Color.web("#091224")),
                new Stop(0.38, Color.web("#10233f")),
                new Stop(0.72, Color.web("#164e63")),
                new Stop(1,    Color.web("#14532d"))));
        gc.fillRect(0, 0, w, h);

        // Световые пятна для атмосферы
        gc.setFill(Color.rgb(255, 255, 255, 0.08));
        gc.fillOval(-170, -200, 520, 520);
        gc.fillOval(w - 310, -120, 430, 430);

        // Затемняем нижнюю часть — «земля»
        gc.setFill(Color.rgb(8, 24, 30, 0.35));
        gc.fillRect(0, h * 0.76, w, h * 0.24);
    }

    /** Рисуем вертикальные направляющие линии по осям движения мишеней. */
    private static void drawGuides(GraphicsContext gc, double h,
                                   TargetDto near, TargetDto far) {
        gc.setStroke(Color.rgb(188, 205, 255, 0.42));
        gc.setLineWidth(3);
        gc.strokeLine(near.x() + near.size() * 0.5, 24,
                      near.x() + near.size() * 0.5, h - 24);

        gc.setStroke(Color.rgb(255, 237, 171, 0.55));
        gc.setLineWidth(2);
        gc.strokeLine(far.x() + far.size() * 0.5, 24,
                      far.x() + far.size() * 0.5, h - 24);
    }

    /** Рисуем фигурку лучника из геометрических примитивов. */
    static void drawArcher(GraphicsContext gc, double archerX, double archerY,
                           double aimAngleDegrees, boolean crouched) {
        double crouchDrop = crouched ? 58 : 0;
        double shoulderY  = archerY - 38 + crouchDrop;
        double hipY       = archerY + (crouched ? 42 : 6);
        double headY      = shoulderY - (crouched ? 20 : 28);
        double bodyHeight = crouched ? 42 : 58;

        // Тень под лучником
        gc.setFill(Color.rgb(0, 0, 0, 0.30));
        gc.fillOval(archerX - 46, hipY + 52, 190, 20);

        // Торс и одежда
        gc.setFill(Color.web("#0f172a"));
        gc.fillRoundRect(archerX - 8, shoulderY - 6, 54, bodyHeight, 20, 20);
        gc.setFill(Color.web("#be123c"));
        gc.fillRoundRect(archerX - 4, shoulderY - 2, 46, bodyHeight - 12, 16, 16);
        gc.setFill(Color.web("#f59e0b"));
        gc.fillRoundRect(archerX + 12, shoulderY + 6, 8, bodyHeight - 20, 8, 8);

        // Голова и черты лица
        gc.setFill(Color.web("#f7dfc7"));
        gc.fillOval(archerX + 2, headY, 34, 34);
        gc.setFill(Color.web("#0f172a"));
        gc.fillArc(archerX, headY - 4, 38, 20, 188, 166, ArcType.ROUND);
        gc.fillOval(archerX + 20, headY + 13, 3.8, 3.8);

        // Рука, держащая лук
        gc.setStroke(Color.web("#f7dfc7"));
        gc.setLineWidth(8);
        gc.strokeLine(archerX + 16, shoulderY + 10, archerX + 44, shoulderY + 5);

        double aimRad = Math.toRadians(aimAngleDegrees);
        double dirX   = Math.cos(aimRad);
        double dirY   = -Math.sin(aimRad);
        double perpX  = -dirY;
        double perpY  = dirX;

        double handX  = archerX + 58;
        double handY  = shoulderY + 4;
        double elbowX = handX - dirX * 14;
        double elbowY = handY - dirY * 14;
        gc.strokeLine(archerX + 14, shoulderY + 14, elbowX, elbowY);

        // Ноги: стоячая или присевшая поза
        gc.setStroke(Color.web("#f8fafc"));
        gc.setLineWidth(7);
        if (crouched) {
            gc.strokeLine(archerX + 14, hipY + 12, archerX - 34, hipY + 34);
            gc.strokeLine(archerX + 14, hipY + 12, archerX + 108, hipY + 32);
        } else {
            gc.strokeLine(archerX + 14, hipY + 24, archerX - 4,  hipY + 60);
            gc.strokeLine(archerX + 14, hipY + 24, archerX + 30, hipY + 60);
        }

        // Рисуем лук (две линии + тетива)
        double bowTopX    = handX + perpX * 42 + dirX * 8;
        double bowTopY    = handY + perpY * 42 + dirY * 8;
        double bowMidX    = handX + dirX * 24;
        double bowMidY    = handY + dirY * 24;
        double bowBottomX = handX - perpX * 42 + dirX * 8;
        double bowBottomY = handY - perpY * 42 + dirY * 8;

        gc.setStroke(Color.web("#f59e0b"));
        gc.setLineWidth(4);
        gc.strokeLine(bowTopX, bowTopY, bowMidX, bowMidY);
        gc.strokeLine(bowMidX, bowMidY, bowBottomX, bowBottomY);

        gc.setStroke(Color.web("#fef3c7"));
        gc.setLineWidth(2);
        gc.strokeLine(bowTopX, bowTopY, bowBottomX, bowBottomY);
    }

    /** Рисуем одну из двух мишеней концентрическими кольцами. */
    private static void drawTarget(GraphicsContext gc, TargetDto target, boolean isNear) {
        double x    = target.x();
        double y    = target.y();
        double size = target.size();

        if (isNear) {
            // Ближняя мишень: красно-белые кольца
            gc.setFill(Color.web("#ffffff")); gc.fillOval(x, y, size, size);
            gc.setFill(Color.web("#ef4444")); gc.fillOval(x + size * 0.14, y + size * 0.14, size * 0.72, size * 0.72);
            gc.setFill(Color.web("#ffffff")); gc.fillOval(x + size * 0.28, y + size * 0.28, size * 0.44, size * 0.44);
            gc.setFill(Color.web("#ef4444")); gc.fillOval(x + size * 0.40, y + size * 0.40, size * 0.20, size * 0.20);
        } else {
            // Дальняя мишень: жёлто-оранжевые кольца
            gc.setFill(Color.web("#fde68a")); gc.fillOval(x, y, size, size);
            gc.setFill(Color.web("#f97316")); gc.fillOval(x + size * 0.17, y + size * 0.17, size * 0.66, size * 0.66);
            gc.setFill(Color.web("#111827")); gc.fillOval(x + size * 0.38, y + size * 0.38, size * 0.24, size * 0.24);
        }

        // Подпись очков над мишенью
        gc.setFill(Color.rgb(255, 255, 255, 0.75));
        gc.setFont(Font.font("System", FontWeight.BOLD, 13));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("+" + target.points(), x + size * 0.5, y - 6);
    }

    /** Рисуем летящую стрелу: древко + наконечник + оперение. */
    private static void drawArrow(GraphicsContext gc, ArrowDto arrow, boolean isLocal) {
        double rad    = Math.toRadians(arrow.angleDegrees());
        double dirX   = Math.cos(rad);
        double dirY   = -Math.sin(rad);
        double perpX  = -dirY;
        double perpY  = dirX;

        double tailX      = arrow.x();
        double tailY      = arrow.y();
        double tipX       = tailX + dirX * arrow.width();
        double tipY       = tailY + dirY * arrow.width();
        double headLen    = 11.0;
        double headHalf   = 5.0;
        double shaftEndX  = tipX - dirX * headLen;
        double shaftEndY  = tipY - dirY * headLen;

        // Цвета: своя стрела — нейтральная, чужая — красная
        String shaftColor  = isLocal ? "#f8fafc" : "#ff9999";
        String headColor   = isLocal ? "#f59e0b" : "#ff4444";
        String fletchColor = isLocal ? "#cbd5e1" : "#ffaaaa";

        // Древко
        gc.setStroke(Color.web(shaftColor));
        gc.setLineWidth(3);
        gc.strokeLine(tailX, tailY, shaftEndX, shaftEndY);

        // Наконечник-треугольник
        gc.setFill(Color.web(headColor));
        gc.fillPolygon(
                new double[]{tipX, shaftEndX + perpX * headHalf, shaftEndX - perpX * headHalf},
                new double[]{tipY, shaftEndY + perpY * headHalf, shaftEndY - perpY * headHalf},
                3);

        // Оперение
        gc.setStroke(Color.web(fletchColor));
        gc.setLineWidth(2);
        gc.strokeLine(tailX, tailY,
                tailX - dirX * 8 + perpX * (arrow.height() * 0.5),
                tailY - dirY * 8 + perpY * (arrow.height() * 0.5));
        gc.strokeLine(tailX, tailY,
                tailX - dirX * 8 - perpX * (arrow.height() * 0.5),
                tailY - dirY * 8 - perpY * (arrow.height() * 0.5));
    }

    /** Рисуем полупрозрачный оверлей паузы. */
    private static void drawPauseOverlay(GraphicsContext gc, String text) {
        gc.setFill(Color.rgb(4, 10, 22, 0.52));
        gc.fillRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);

        gc.setFill(Color.web("#e2e8f0"));
        gc.fillRoundRect(WORLD_WIDTH * 0.5 - 170, WORLD_HEIGHT * 0.5 - 48, 340, 96, 20, 20);

        gc.setFill(Color.web("#0f172a"));
        gc.setFont(Font.font("System", FontWeight.BOLD, 34));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(text, WORLD_WIDTH * 0.5, WORLD_HEIGHT * 0.5 + 12);
    }

    /** Рисуем шкалу заряда выстрела в нижнем левом углу поля. */
    private static void drawChargeBar(GraphicsContext gc, double ratio) {
        double barW = 160.0;
        double barH = 14.0;
        double barX = 16.0;
        double barY = WORLD_HEIGHT - 36.0;

        // Подложка
        gc.setFill(Color.rgb(0, 0, 0, 0.45));
        gc.fillRoundRect(barX - 2, barY - 2, barW + 4, barH + 4, 8, 8);

        // Фон шкалы
        gc.setFill(Color.web("#1e3a5f"));
        gc.fillRoundRect(barX, barY, barW, barH, 6, 6);

        // Заполненная часть: зелёный → жёлтый → красный по мере заряда
        Color fill = ratio < 0.5  ? Color.web("#22c55e")
                   : ratio < 0.85 ? Color.web("#f59e0b")
                                  : Color.web("#ef4444");
        gc.setFill(fill);
        gc.fillRoundRect(barX, barY, barW * ratio, barH, 6, 6);

        // Числовой процент рядом со шкалой
        gc.setFill(Color.rgb(255, 255, 255, 0.85));
        gc.setFont(Font.font("System", FontWeight.BOLD, 11));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(String.format(Locale.US, "%.0f%%", ratio * 100),
                barX + barW + 6, barY + barH - 1);
    }

    /** Создаём скруглённую область отсечения, чтобы поле не обрезалось прямыми углами. */
    private static void applyRoundedClip(GraphicsContext gc,
                                         double x, double y,
                                         double w, double h, double r) {
        r = Math.min(r, Math.min(w, h) * 0.5);
        gc.beginPath();
        gc.moveTo(x + r, y);
        gc.lineTo(x + w - r, y);
        gc.quadraticCurveTo(x + w, y, x + w, y + r);
        gc.lineTo(x + w, y + h - r);
        gc.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
        gc.lineTo(x + r, y + h);
        gc.quadraticCurveTo(x, y + h, x, y + h - r);
        gc.lineTo(x, y + r);
        gc.quadraticCurveTo(x, y, x + r, y);
        gc.closePath();
        gc.clip();
    }
}
