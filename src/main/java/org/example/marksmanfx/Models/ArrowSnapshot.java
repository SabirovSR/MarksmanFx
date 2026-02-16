package org.example.marksmanfx.Models;

public record ArrowSnapshot(
        boolean active,
        double x,
        double y,
        double width,
        double height,
        double angleDegrees
) {
}
