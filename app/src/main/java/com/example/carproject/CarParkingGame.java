package com.example.carproject;

import com.badlogic.gdx.Game;
import com.example.carproject.screen.SplashScreen;

public class CarParkingGame extends Game {

    @Override
    public void create() {
        setScreen(new SplashScreen(this));
    }
}
