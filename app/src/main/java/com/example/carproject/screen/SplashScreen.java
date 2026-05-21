package com.example.carproject.screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class SplashScreen extends ScreenAdapter {

    private final Game game;
    private SpriteBatch batch;
    private BitmapFont titleFont;
    private BitmapFont subFont;
    private float elapsed = 0f;
    private static final float SPLASH_DURATION = 3f;

    public SplashScreen(Game game) {
        this.game = game;
        batch = new SpriteBatch();

        titleFont = new BitmapFont();
        titleFont.getData().setScale(4f);
        titleFont.setColor(Color.CYAN);

        subFont = new BitmapFont();
        subFont.getData().setScale(1.8f);
        subFont.setColor(new Color(0.6f, 0.9f, 1f, 1f));
    }

    @Override
    public void render(float delta) {
        elapsed += delta;

        Gdx.gl.glClearColor(0f, 0f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        // 페이드인 알파 효과
        float alpha = Math.min(elapsed / 0.8f, 1f);
        titleFont.setColor(0f, 1f, 1f, alpha);
        subFont.setColor(0.6f, 0.9f, 1f, alpha);

        batch.begin();

        GlyphLayout titleLayout = new GlyphLayout(titleFont, "CAR PARKING");
        titleFont.draw(batch, titleLayout,
                (w - titleLayout.width) / 2f,
                h / 2f + titleLayout.height + 20f);

        GlyphLayout subLayout = new GlyphLayout(subFont, "3D Driving Simulator");
        subFont.draw(batch, subLayout,
                (w - subLayout.width) / 2f,
                h / 2f - 10f);

        batch.end();

        if (elapsed >= SPLASH_DURATION) {
            game.setScreen(new MenuScreen(game));
            dispose();
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        titleFont.dispose();
        subFont.dispose();
    }
}
