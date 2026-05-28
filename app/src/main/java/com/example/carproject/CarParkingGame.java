package com.example.carproject;

import com.badlogic.gdx.Game;
import com.example.carproject.model.GameSettings;
import com.example.carproject.screen.SplashScreen;

public class CarParkingGame extends Game {

    @Override
    public void create() {
        GameSettings.get().load();
        setScreen(new SplashScreen(this));
    }
}
