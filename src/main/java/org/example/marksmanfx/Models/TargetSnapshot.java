package org.example.marksmanfx.Models;

public record TargetSnapshot(
        TargetType type,
        double x,
        double y,
        double size
) {
}
