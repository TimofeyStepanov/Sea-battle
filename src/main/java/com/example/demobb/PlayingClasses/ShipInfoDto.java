package com.example.demobb.PlayingClasses;

import java.io.Serializable;
import java.util.Map;

public class ShipInfoDto implements Serializable {
    public Map<Integer, Integer> cellValueOfShipsAndTheirsNumbers;

    public ShipInfoDto(Map<Integer, Integer> cellValueOfShipsAndTheirsNumbers) {
        this.cellValueOfShipsAndTheirsNumbers = cellValueOfShipsAndTheirsNumbers;
    }
}
