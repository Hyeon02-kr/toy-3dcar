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
import com.example.carproject.model.GameSettings;

public class SettingsScreen extends ScreenAdapter {

    private final Game game;
    private final GameSettings cfg = GameSettings.get();

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont titleFont, labelFont, optFont;

    // btn[0..2]  = 핸들 조작 (3종)
    // btn[3..5]  = 엑셀 조작 (3종)
    // btn[6..8]  = 브레이크 조작 (3종)
    // btn[9..10] = 주차성공 조건 (2종)
    // btn[11]    = 저장 & 돌아가기
    private final Rectangle[] btns = new Rectangle[12];
    private int pressedIdx = -1;

    private static final Color COL_SEL_BG   = new Color(0.06f, 0.24f, 0.50f, 1f);
    private static final Color COL_NORM_BG  = new Color(0.08f, 0.08f, 0.18f, 1f);
    private static final Color COL_PRESS_BG = new Color(0.15f, 0.15f, 0.32f, 1f);
    private static final Color COL_ACCENT   = new Color(0.00f, 0.78f, 1.00f, 1f);
    private static final Color COL_SEL_BDR  = new Color(0.10f, 1.00f, 0.55f, 1f);
    private static final Color COL_LABEL    = new Color(0.70f, 0.82f, 0.92f, 1f);

    public SettingsScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        batch  = new SpriteBatch();
        shapes = new ShapeRenderer();

        titleFont = new BitmapFont();
        titleFont.getData().setScale(3.0f);
        titleFont.setColor(Color.CYAN);

        labelFont = new BitmapFont();
        labelFont.getData().setScale(1.7f);
        labelFont.setColor(COL_LABEL);

        optFont = new BitmapFont();
        optFont.getData().setScale(1.3f);

        layoutButtons();

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int ptr, int btn) {
                float tx = sx, ty = Gdx.graphics.getHeight() - sy;
                pressedIdx = hitTest(tx, ty);
                return true;
            }
            @Override
            public boolean touchUp(int sx, int sy, int ptr, int btn) {
                float tx = sx, ty = Gdx.graphics.getHeight() - sy;
                handleTap(tx, ty);
                pressedIdx = -1;
                return true;
            }
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK) goBack();
                return true;
            }
        });
    }

    @Override
    public void resize(int w, int h) {
        layoutButtons();
    }

    private void layoutButtons() {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        float margin = w * 0.04f;
        float usable = w - 2 * margin;
        float gap    = w * 0.022f;
        float bh     = h * 0.072f;

        float bw3 = (usable - 2 * gap) / 3f;
        float bw2 = (usable - gap)     / 2f;

        // 각 행의 버튼 Y (아래쪽이 기준)
        float[] rowY = { h * 0.730f, h * 0.540f, h * 0.350f, h * 0.178f };

        for (int i = 0; i < 3; i++) btns[i]     = new Rectangle(margin + i * (bw3 + gap), rowY[0], bw3, bh);
        for (int i = 0; i < 3; i++) btns[3 + i] = new Rectangle(margin + i * (bw3 + gap), rowY[1], bw3, bh);
        for (int i = 0; i < 3; i++) btns[6 + i] = new Rectangle(margin + i * (bw3 + gap), rowY[2], bw3, bh);

        btns[9]  = new Rectangle(margin,              rowY[3], bw2, bh);
        btns[10] = new Rectangle(margin + bw2 + gap,  rowY[3], bw2, bh);

        float backW = w * 0.55f;
        btns[11] = new Rectangle((w - backW) / 2f, h * 0.040f, backW, bh);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.04f, 0.04f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        // 선택 상태 배열
        boolean[] steeSel = {
            cfg.steeringMode == GameSettings.SteeringMode.WHEEL,
            cfg.steeringMode == GameSettings.SteeringMode.SLIDE,
            cfg.steeringMode == GameSettings.SteeringMode.BUTTONS
        };
        boolean[] thrSel = {
            cfg.throttleMode == GameSettings.ThrottleMode.TAP,
            cfg.throttleMode == GameSettings.ThrottleMode.SLIDE_UP,
            cfg.throttleMode == GameSettings.ThrottleMode.SLIDE_DOWN
        };
        boolean[] brkSel = {
            cfg.brakeMode == GameSettings.BrakeMode.TAP,
            cfg.brakeMode == GameSettings.BrakeMode.SLIDE_UP,
            cfg.brakeMode == GameSettings.BrakeMode.SLIDE_DOWN
        };
        boolean[] winSel = {
            cfg.winCondition == GameSettings.WinCondition.ENTER,
            cfg.winCondition == GameSettings.WinCondition.FULL_STOP
        };

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // ── 버튼 배경 (전체 한 번에) ─────────────────────────────
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawBGs(0, 3, steeSel);
        drawBGs(3, 3, thrSel);
        drawBGs(6, 3, brkSel);
        drawBGs(9, 2, winSel);
        shapes.setColor(pressedIdx == 11 ? COL_PRESS_BG : COL_NORM_BG);
        shapes.rect(btns[11].x, btns[11].y, btns[11].width, btns[11].height);
        shapes.end();

        // ── 버튼 테두리 (전체 한 번에) ──────────────────────────
        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawBorders(0, 3, steeSel);
        drawBorders(3, 3, thrSel);
        drawBorders(6, 3, brkSel);
        drawBorders(9, 2, winSel);
        shapes.setColor(COL_ACCENT);
        shapes.rect(btns[11].x, btns[11].y, btns[11].width, btns[11].height);
        shapes.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ── 텍스트 ────────────────────────────────────────────────
        batch.begin();

        // 타이틀
        GlyphLayout tGL = new GlyphLayout(titleFont, "SETTINGS");
        titleFont.draw(batch, tGL, (w - tGL.width) / 2f, h * 0.950f);

        // 섹션 라벨
        String[] sectionLabels = { "핸들 조작", "엑셀 조작", "브레이크 조작", "주차성공 조건" };
        float[]  labelYs       = { h * 0.838f,  h * 0.648f,  h * 0.458f,      h * 0.278f      };
        for (int i = 0; i < 4; i++) {
            GlyphLayout gl = new GlyphLayout(labelFont, sectionLabels[i]);
            labelFont.draw(batch, gl, (w - gl.width) / 2f, labelYs[i]);
        }

        // 옵션 버튼 텍스트
        String[] steeLabels = { "핸들 회전", "슬라이드", "좌우 버튼" };
        String[] thrLabels  = { "단순 터치", "위로 스와이프", "아래로 스와이프" };
        String[] winLabels  = { "진입 즉시", "완전 멈춤" };

        drawOptTexts(0, steeLabels, steeSel);
        drawOptTexts(3, thrLabels,  thrSel);
        drawOptTexts(6, thrLabels,  brkSel);
        drawOptTexts(9, winLabels,  winSel);

        // 뒤로 버튼
        optFont.setColor(Color.WHITE);
        GlyphLayout bGL = new GlyphLayout(optFont, "저장 & 돌아가기");
        Rectangle b = btns[11];
        optFont.draw(batch, bGL, b.x + (b.width - bGL.width) / 2f, b.y + b.height / 2f + bGL.height / 2f);

        batch.end();
    }

    // ── 내부 렌더링 헬퍼 ────────────────────────────────────────────

    private void drawBGs(int offset, int count, boolean[] sel) {
        for (int i = 0; i < count; i++) {
            Rectangle r = btns[offset + i];
            boolean pressed = (pressedIdx == offset + i);
            shapes.setColor(sel[i] ? COL_SEL_BG : pressed ? COL_PRESS_BG : COL_NORM_BG);
            shapes.rect(r.x, r.y, r.width, r.height);
        }
    }

    private void drawBorders(int offset, int count, boolean[] sel) {
        for (int i = 0; i < count; i++) {
            Rectangle r = btns[offset + i];
            shapes.setColor(sel[i] ? COL_SEL_BDR : COL_ACCENT);
            shapes.rect(r.x, r.y, r.width, r.height);
        }
    }

    private void drawOptTexts(int offset, String[] labels, boolean[] sel) {
        for (int i = 0; i < labels.length; i++) {
            Rectangle r = btns[offset + i];
            optFont.setColor(sel[i] ? Color.WHITE : new Color(0.62f, 0.72f, 0.82f, 1f));
            GlyphLayout gl = new GlyphLayout(optFont, labels[i]);
            optFont.draw(batch, gl,
                    r.x + (r.width - gl.width) / 2f,
                    r.y + r.height / 2f + gl.height / 2f);
        }
    }

    // ── 입력 처리 ────────────────────────────────────────────────────

    private int hitTest(float x, float y) {
        for (int i = 0; i < btns.length; i++)
            if (btns[i] != null && btns[i].contains(x, y)) return i;
        return -1;
    }

    private void handleTap(float x, float y) {
        int idx = hitTest(x, y);
        if (idx < 0)  return;
        if      (idx < 3)  cfg.steeringMode = GameSettings.SteeringMode.values()[idx];
        else if (idx < 6)  cfg.throttleMode = GameSettings.ThrottleMode.values()[idx - 3];
        else if (idx < 9)  cfg.brakeMode    = GameSettings.BrakeMode.values()[idx - 6];
        else if (idx < 11) cfg.winCondition = GameSettings.WinCondition.values()[idx - 9];
        else if (idx == 11) goBack();
    }

    private void goBack() {
        cfg.save();
        game.setScreen(new MenuScreen(game));
        dispose();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        titleFont.dispose();
        labelFont.dispose();
        optFont.dispose();
    }
}
