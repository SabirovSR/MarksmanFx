/**
 * Общий модуль сетевого протокола MarksmanFx.
 *
 * <p>Содержит типы, которыми обмениваются клиент и сервер:
 * события от клиента, сообщения от сервера и сериализуемые DTO модели.</p>
 */
module org.example.marksmanfx.common {
    exports org.example.marksmanfx.common.event;
    exports org.example.marksmanfx.common.message;
    exports org.example.marksmanfx.common.model;
}