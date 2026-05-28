package com.example.carproject.model;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

public class CarPhysics {
    public float x, z, speed, angle;
    public float steeringAngle;

    public static final float MAX_STEERING = 0.5f;

    public void reset(float startX, float startZ, float startAngle) {
        x = startX;
        z = startZ;
        angle = startAngle;
        speed = 0f;
        steeringAngle = 0f;
    }

    // gas / brake: 0.0(없음) ~ 1.0(최대) 아날로그 압력값
    public void update(VehicleType v, float gas, float brake) {
        // Ackermann 조향 근사치: 속도가 있을 때만 회전각 적용
        float wheelbase = v.length * 0.7f;
        if (Math.abs(speed) > 0.005f) {
            float angularVelocity = (speed / wheelbase) * MathUtils.tan(steeringAngle);
            angle += angularVelocity;
        }

        if (gas > 0f) {
            speed += v.accel * gas;
        } else if (brake > 0f) {
            speed -= v.accel * brake;
        } else {
            speed *= 0.95f;
            if (Math.abs(speed) < 0.001f) speed = 0f;
        }

        speed = MathUtils.clamp(speed, -v.maxSpeed / 2f, v.maxSpeed);

        // Three.js 와 동일한 이동 공식 (-Z 가 전진)
        x -= MathUtils.sin(angle) * speed;
        z -= MathUtils.cos(angle) * speed;
    }

    /** 차량 4 꼭지점 (월드 좌표) 반환 — 충돌 판정용 */
    public float[][] getCorners(VehicleType v) {
        float cos = MathUtils.cos(angle);
        float sin = MathUtils.sin(angle);
        float hw = v.width / 2f;
        float hl = v.length / 2f;
        float[][] offsets = { {-hw, -hl}, {hw, -hl}, {hw, hl}, {-hw, hl} };
        float[][] corners = new float[4][2];
        for (int i = 0; i < 4; i++) {
            corners[i][0] = x + offsets[i][0] * cos + offsets[i][1] * sin;
            corners[i][1] = z + (-offsets[i][0] * sin + offsets[i][1] * cos);
        }
        return corners;
    }

    public boolean checkCollision(Array<Rectangle> walls, VehicleType v) {
        float[][] corners = getCorners(v);
        for (Rectangle wall : walls) {
            for (float[] c : corners) {
                if (wall.contains(c[0], c[1])) return true;
            }
        }
        return false;
    }

    public boolean checkWin(float parkX, float parkZ) {
        float dist = (float) Math.hypot(x - parkX, z - parkZ);
        float angleMod = Math.abs(angle % MathUtils.PI);
        boolean aligned = angleMod < 0.2f || angleMod > (MathUtils.PI - 0.2f);
        return dist < 1.5f && aligned && Math.abs(speed) < 0.05f;
    }
}
