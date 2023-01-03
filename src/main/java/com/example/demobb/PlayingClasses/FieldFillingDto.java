package com.example.demobb.PlayingClasses;

import java.io.Serializable;

public class FieldFillingDto implements Serializable {
    public Point startPoint;
    public Point finishPoint;

    public FieldFillingDto(Point startPoint, Point finishPoint) {
        this.startPoint = startPoint;
        this.finishPoint = finishPoint;
    }
}
