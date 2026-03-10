module org.example.marksmanfx.client {
    requires org.example.marksmanfx.common;
    requires javafx.controls;
    requires javafx.fxml;
    requires java.logging;

    // Мы добавляем java.net.http для REST API запросов (ApiClient, AutoLoginService)
    requires java.net.http;

    // Мы добавляем java.prefs для постоянного хранения JWT-токена (TokenStorage)
    requires java.prefs;

    // Jackson для разбора JSON-ответов REST API (логин, /api/auth/me)
    requires com.fasterxml.jackson.databind;

    // Java-WebSocket для STOMP/WebSocket клиента
    requires Java.WebSocket;

    opens org.example.marksmanfx.client              to javafx.fxml;
    opens org.example.marksmanfx.client.ui           to javafx.fxml;
    opens org.example.marksmanfx.client.ui.login     to javafx.fxml;
    opens org.example.marksmanfx.client.ui.lobby     to javafx.fxml;
    opens org.example.marksmanfx.client.ui.game      to javafx.fxml;

    // Мы открываем auth-пакет для Jackson, чтобы он мог десериализовать UserProfileDto
    opens org.example.marksmanfx.client.auth         to com.fasterxml.jackson.databind;

    exports org.example.marksmanfx.client;
}
