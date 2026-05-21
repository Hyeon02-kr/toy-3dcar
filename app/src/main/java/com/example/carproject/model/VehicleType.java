package com.example.carproject.model;

import com.badlogic.gdx.graphics.Color;

public enum VehicleType {
    COMPACT("COMPACT", "Small & fast turning",    1.8f, 1.5f, 3.5f, 0.005f, 0.30f, new Color(1f, 1f, 0f, 1f)),
    SEDAN  ("SEDAN",   "Standard & balanced",     2.0f, 1.4f, 4.5f, 0.004f, 0.35f, new Color(0f, 0.53f, 1f, 1f)),
    TRUCK  ("TRUCK",   "Large & hard to handle",  2.6f, 2.5f, 6.5f, 0.003f, 0.25f, new Color(1f, 0.2f, 0.2f, 1f));

    public final String displayName, desc;
    public final float width, height, length;
    public final float accel, maxSpeed;
    public final Color color;

    VehicleType(String name, String desc,
                float w, float h, float l,
                float accel, float maxSpeed, Color color) {
        this.displayName = name;
        this.desc = desc;
        this.width = w;
        this.height = h;
        this.length = l;
        this.accel = accel;
        this.maxSpeed = maxSpeed;
        this.color = color;
    }
}
