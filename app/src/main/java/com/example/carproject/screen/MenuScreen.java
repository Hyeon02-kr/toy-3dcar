package com.example.carproject.screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.example.carproject.model.VehicleType;

public class MenuScreen extends ScreenAdapter {

    private enum State { VEHICLE, DIFFICULTY }

    private final Game game;
    private State state = State.VEHICLE;
    private VehicleType selectedVehicle = null;

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont titleFont, btnFont, descFont;

    // 버튼 영역
    private Rectangle[] vehicleBtns = new Rectangle[3];
    private Rectangle[] diffBtns    = new Rectangle[3];
    private Rectangle backBtn;

    private static final Color BTN_BG     = new Color(0.12f, 0.12f, 0.22f, 1f);
    private static final Color BTN_BORDER  = new Color(0f, 0.8f, 1f, 1f);
    private static final Color BTN_PRESSED = new Color(0.22f, 0.22f, 0.44f, 1f);
    private static final Color EASY_COLOR  = new Color(0.2f, 0.8f, 0.2f, 1f);
    private static final Color MED_COLOR   = new Color(0.9f, 0.7f, 0.1f, 1f);
    private static final Color HARD_COLOR  = new Color(1f,   0.2f, 0.1f, 1f);

    private int pressedIndex = -1;   // 터치 피드백용

    public MenuScreen(Game game) {
        this.game = game;

        batch  = new SpriteBatch();
        shapes = new ShapeRenderer();

        titleFont = new BitmapFont();
        titleFont.getData().setScale(3.5f);
        titleFont.setColor(Color.CYAN);

        btnFont = new BitmapFont();
        btnFont.getData().setScale(2.2f);
        btnFont.setColor(Color.WHITE);

        descFont = new BitmapFont();
        descFont.getData().setScale(1.4f);
        descFont.setColor(new Color(0.7f, 0.7f, 0.7f, 1f));

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                float tx = sx;
                float ty = Gdx.graphics.getHeight() - sy;
                pressedIndex = hitTest(tx, ty);
                return true;
            }
            @Override
            public boolean touchUp(int sx, int sy, int pointer, int button) {
                float tx = sx;
                float ty = Gdx.graphics.getHeight() - sy;
                handleTap(tx, ty);
                pressedIndex = -1;
                return true;
            }
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK && state == State.DIFFICULTY) backToVehicle();
                return true;
            }
        });
    }

    @Override
    public void show() {
        layoutButtons();
    }

    @Override
    public void resize(int width, int height) {
        layoutButtons();
    }

    private void layoutButtons() {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        float bw = w * 0.75f;
        float bh = h * 0.085f;
        float cx = (w - bw) / 2f;

        // 차량 선택 버튼 (3개, 화면 중앙~하단)
        float startY = h * 0.55f;
        float gap    = bh + h * 0.025f;
        for (int i = 0; i < 3; i++) {
            vehicleBtns[i] = new Rectangle(cx, startY - i * gap, bw, bh);
        }

        // 난이도 버튼 (3개)
        for (int i = 0; i < 3; i++) {
            diffBtns[i] = new Rectangle(cx, startY - i * gap, bw, bh);
        }

        // 뒤로가기 버튼
        backBtn = new Rectangle(cx, startY - 3 * gap, bw * 0.5f, bh * 0.85f);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.04f, 0.04f, 0.1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (state == State.VEHICLE) drawVehicleSelect();
        else                        drawDifficultySelect();
    }

    // ─── 차량 선택 화면 ────────────────────────────────────────────
    private void drawVehicleSelect() {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        // 타이틀
        batch.begin();
        GlyphLayout layout = new GlyphLayout(titleFont, "CAR PARKING");
        titleFont.draw(batch, layout, (w - layout.width) / 2f, h * 0.88f);

        GlyphLayout subLayout = new GlyphLayout(descFont, "SELECT YOUR VEHICLE");
        descFont.draw(batch, subLayout, (w - subLayout.width) / 2f, h * 0.63f);
        batch.end();

        // 차량 버튼
        String[] labels = { "COMPACT  — Small & Agile",
                            "SEDAN    — Standard Balance",
                            "TRUCK    — Large & Heavy" };
        Color[] colors  = { VehicleType.COMPACT.color,
                            VehicleType.SEDAN.color,
                            VehicleType.TRUCK.color };

        for (int i = 0; i < 3; i++) {
            drawButton(vehicleBtns[i], labels[i], colors[i], pressedIndex == i);
        }
    }

    // ─── 난이도 선택 화면 ────────────────────────────────────────────
    private void drawDifficultySelect() {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        batch.begin();
        GlyphLayout layout = new GlyphLayout(titleFont, "DIFFICULTY");
        titleFont.draw(batch, layout, (w - layout.width) / 2f, h * 0.88f);

        GlyphLayout subLayout = new GlyphLayout(descFont,
                "VEHICLE: " + (selectedVehicle != null ? selectedVehicle.displayName : ""));
        descFont.draw(batch, subLayout, (w - subLayout.width) / 2f, h * 0.63f);
        batch.end();

        String[] labels = { "EASY   — 40 sec", "MEDIUM  — 30 sec", "DIFFICULT — 25 sec" };
        Color[]  colors = { EASY_COLOR, MED_COLOR, HARD_COLOR };
        for (int i = 0; i < 3; i++) {
            drawButton(diffBtns[i], labels[i], colors[i], pressedIndex == (10 + i));
        }

        // 뒤로 버튼
        drawButton(backBtn, "BACK", BTN_BORDER, pressedIndex == 20);
    }

    /** 버튼 1개 그리기 */
    private void drawButton(Rectangle r, String text, Color accentColor, boolean pressed) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(pressed ? BTN_PRESSED : BTN_BG);
        shapes.rect(r.x, r.y, r.width, r.height);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(accentColor);
        shapes.rect(r.x, r.y, r.width, r.height);
        // 왼쪽 강조 테두리 (3px 두께는 Line이 1px이므로 3회 그리기)
        shapes.rect(r.x + 1, r.y + 1, 4f, r.height - 2f);
        shapes.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();
        btnFont.setColor(pressed ? Color.LIGHT_GRAY : Color.WHITE);
        GlyphLayout gl = new GlyphLayout(btnFont, text);
        btnFont.draw(batch, gl,
                r.x + 24f,
                r.y + r.height / 2f + gl.height / 2f);
        batch.end();
    }

    // ─── 터치 판정 ────────────────────────────────────────────────
    private int hitTest(float x, float y) {
        if (state == State.VEHICLE) {
            for (int i = 0; i < 3; i++) if (vehicleBtns[i].contains(x, y)) return i;
        } else {
            for (int i = 0; i < 3; i++) if (diffBtns[i].contains(x, y)) return 10 + i;
            if (backBtn.contains(x, y)) return 20;
        }
        return -1;
    }

    private void handleTap(float x, float y) {
        if (state == State.VEHICLE) {
            VehicleType[] types = { VehicleType.COMPACT, VehicleType.SEDAN, VehicleType.TRUCK };
            for (int i = 0; i < 3; i++) {
                if (vehicleBtns[i].contains(x, y)) {
                    selectedVehicle = types[i];
                    state = State.DIFFICULTY;
                    return;
                }
            }
        } else {
            String[] diffs = { "EASY", "MEDIUM", "DIFFICULT" };
            for (int i = 0; i < 3; i++) {
                if (diffBtns[i].contains(x, y)) {
                    startGame(diffs[i]);
                    return;
                }
            }
            if (backBtn.contains(x, y)) backToVehicle();
        }
    }

    private void startGame(String difficulty) {
        game.setScreen(new GameScreen(game, selectedVehicle, difficulty));
        dispose();
    }

    private void backToVehicle() {
        state = State.VEHICLE;
        pressedIndex = -1;
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        titleFont.dispose();
        btnFont.dispose();
        descFont.dispose();
    }
}
