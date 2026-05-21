package com.example.carproject.model;

import com.badlogic.gdx.graphics.Color;

public class DifficultyConfig {

    public int    timerSeconds;
    public float  parkX, parkZ, parkW, parkL;
    public Color  floorColor, wallColor, bgColor, ambientColor, dirColor, spotColor;

    public static DifficultyConfig forDifficulty(String difficulty) {
        DifficultyConfig c = new DifficultyConfig();
        switch (difficulty) {
            case "EASY":
                c.timerSeconds = 40;
                c.parkX = 0;  c.parkZ = -30; c.parkW = 4f;   c.parkL = 8f;
                c.floorColor   = new Color(0x2e4a11ff);
                c.wallColor    = new Color(0x5c3a21ff);
                c.bgColor      = new Color(0xa0d8efff);
                c.ambientColor = new Color(0.4f, 0.4f, 0.4f, 1f);
                c.dirColor     = new Color(1f, 0.96f, 0.8f, 1f);
                c.spotColor    = new Color(0f, 1f, 0f, 0.5f);
                break;

            case "MEDIUM":
                c.timerSeconds = 30;
                c.parkX = 20; c.parkZ = -30; c.parkW = 3.5f; c.parkL = 7f;
                c.floorColor   = new Color(0x111111ff);
                c.wallColor    = new Color(0x1c2b39ff);
                c.bgColor      = new Color(0x050510ff);
                c.ambientColor = new Color(0.19f, 0.19f, 0.31f, 1f);
                c.dirColor     = new Color(0.53f, 0.67f, 1f, 1f);
                c.spotColor    = new Color(0f, 1f, 0f, 0.5f);
                break;

            case "DIFFICULT":
            default:
                c.timerSeconds = 25;
                c.parkX = 40; c.parkZ = -40; c.parkW = 3f;   c.parkL = 7f;
                c.floorColor   = new Color(0x220500ff);
                c.wallColor    = new Color(0x1a0a05ff);
                c.bgColor      = new Color(0x330000ff);
                c.ambientColor = new Color(0.38f, 0.13f, 0.13f, 1f);
                c.dirColor     = new Color(1f, 0.33f, 0.13f, 1f);
                c.spotColor    = new Color(1f, 1f, 0f, 0.5f);
                break;
        }
        return c;
    }
}
