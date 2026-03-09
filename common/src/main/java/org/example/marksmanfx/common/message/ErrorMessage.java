package org.example.marksmanfx.common.message;

import java.io.Serial;

public record ErrorMessage(String text) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}
