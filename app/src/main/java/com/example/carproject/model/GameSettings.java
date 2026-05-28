package com.example.carproject.model;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

public class GameSettings {

    public enum SteeringMode { WHEEL, SLIDE, BUTTONS }
    public enum ThrottleMode { TAP, SLIDE_UP, SLIDE_DOWN }
    public enum BrakeMode    { TAP, SLIDE_UP, SLIDE_DOWN }
    public enum WinCondition { ENTER, FULL_STOP }
    public enum DriveMode    { SIMULATION, REALISTIC }

    private static GameSettings instance;

    public SteeringMode steeringMode = SteeringMode.WHEEL;
    public ThrottleMode throttleMode = ThrottleMode.TAP;
    public BrakeMode    brakeMode    = BrakeMode.TAP;
    public WinCondition winCondition = WinCondition.FULL_STOP;
    public DriveMode    driveMode    = DriveMode.SIMULATION;

    private GameSettings() {}

    public static GameSettings get() {
        if (instance == null) instance = new GameSettings();
        return instance;
    }

    public void load() {
        try {
            Preferences p = Gdx.app.getPreferences("car_parking_settings");
            steeringMode = SteeringMode.valueOf(p.getString("steering", SteeringMode.WHEEL.name()));
            throttleMode = ThrottleMode.valueOf(p.getString("throttle", ThrottleMode.TAP.name()));
            brakeMode    = BrakeMode.valueOf(p.getString("brake",    BrakeMode.TAP.name()));
            winCondition = WinCondition.valueOf(p.getString("win",   WinCondition.FULL_STOP.name()));
            driveMode    = DriveMode.valueOf(p.getString("drive",   DriveMode.SIMULATION.name()));
        } catch (Exception ignored) { /* 기본값 유지 */ }
    }

    public void save() {
        Preferences p = Gdx.app.getPreferences("car_parking_settings");
        p.putString("steering", steeringMode.name());
        p.putString("throttle", throttleMode.name());
        p.putString("brake",    brakeMode.name());
        p.putString("win",      winCondition.name());
        p.putString("drive",    driveMode.name());
        p.flush();
    }
}
