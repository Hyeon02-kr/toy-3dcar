package com.example.carproject.screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.example.carproject.model.CarPhysics;
import com.example.carproject.model.DifficultyConfig;
import com.example.carproject.model.GameSettings;
import com.example.carproject.model.VehicleType;

public class GameScreen extends ScreenAdapter {

    // ─── 게임 상태 ────────────────────────────────────────────────
    private final Game game;
    private final VehicleType vehicle;
    private final String difficulty;
    private final DifficultyConfig cfg;
    private final CarPhysics physics = new CarPhysics();

    private boolean gameOver = false;
    private boolean gameWon  = false;
    private float   timerValue;
    private float   timerElapsed = 0f;

    // ─── libGDX 3D ────────────────────────────────────────────────
    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
    private Environment environment;

    private final Array<ModelInstance> staticInstances = new Array<>();
    private ModelInstance carInstance;

    // 모델 리소스 (dispose 대상)
    private final Array<Model> allModels = new Array<>();

    // 충돌판정용 2D AABB 목록
    private final Array<Rectangle> walls = new Array<>();

    // ─── 2D HUD 렌더링 ────────────────────────────────────────────
    private SpriteBatch hudBatch;
    private ShapeRenderer shapes;
    private BitmapFont hudFont;
    private BitmapFont overlayFont;

    // ─── 컨트롤러 상태 ────────────────────────────────────────────
    // 0.0 = 입력 없음, 1.0 = 최대 압력 (TAP 모드는 항상 1.0)
    private float gasAmount   = 0f;
    private float brakeAmount = 0f;
    private boolean steeringActive = false;
    private int     steeringPointer = -1;

    // 핸들 회전 상태 (-450° ~ +450°, 900도 풀 로크)
    private float steeringWheelDeg  = 0f;
    private float steeringLastAngle = 0f;  // 이전 터치 각도 (radians)

    // 가스 / 브레이크 터치 추적 (슬라이드 모드용)
    private int   gasPointer   = -1;
    private int   brakePointer = -1;
    private float gasStartY    = 0f;
    private float brakeStartY  = 0f;
    private float gasSlideY    = 0f;
    private float brakeSlideY  = 0f;

    // 카메라 lerp 플래그
    private boolean camInitialized = false;

    // 화면 레이아웃 상수 (비율)
    private float CTRL_Y_RATIO    = 0.32f;  // 컨트롤러 영역 높이 비율
    private float STEER_X_RATIO   = 0.50f;  // 스티어링 영역 x 경계

    // ─── 생성자 ────────────────────────────────────────────────────
    public GameScreen(Game game, VehicleType vehicle, String difficulty) {
        this.game       = game;
        this.vehicle    = vehicle;
        this.difficulty = difficulty;
        this.cfg        = DifficultyConfig.forDifficulty(difficulty);
        this.timerValue = cfg.timerSeconds;
    }

    @Override
    public void show() {
        // 3D 렌더러 초기화
        modelBatch = new ModelBatch();

        camera = new PerspectiveCamera(60f,
                Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.5f;
        camera.far  = 300f;
        camera.update();

        // 조명 환경
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight,
                cfg.ambientColor.r, cfg.ambientColor.g, cfg.ambientColor.b, 1f));
        environment.add(new com.badlogic.gdx.graphics.g3d.environment.DirectionalLight()
                .set(cfg.dirColor, -0.4f, -1f, 0.4f));

        // HUD
        hudBatch    = new SpriteBatch();
        shapes      = new ShapeRenderer();
        hudFont     = new BitmapFont();
        hudFont.getData().setScale(2f);
        hudFont.setColor(Color.WHITE);
        overlayFont = new BitmapFont();
        overlayFont.getData().setScale(3.5f);

        // 맵 & 차량 생성
        buildMap();
        buildCar();

        physics.reset(0f, 10f, MathUtils.PI);

        // 터치 입력 처리기
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int ptr, int btn) {
                float sh  = Gdx.graphics.getHeight();
                float sw  = Gdx.graphics.getWidth();
                float tx  = sx;
                float hudY = sh - sy;

                GameSettings s = GameSettings.get();

                // ── 스티어링 ─────────────────────────────────────
                if (!steeringActive && tx < sw * STEER_X_RATIO) {
                    if (s.steeringMode == GameSettings.SteeringMode.WHEEL) {
                        float cx = getWheelCX(), cy = getWheelCY(), r = getWheelR();
                        float dx = tx - cx, dy = hudY - cy;
                        if (dx * dx + dy * dy <= (r * 1.5f) * (r * 1.5f)) {
                            steeringActive    = true;
                            steeringPointer   = ptr;
                            steeringLastAngle = (float) Math.atan2(dy, dx);
                        }
                    } else if (s.steeringMode == GameSettings.SteeringMode.SLIDE) {
                        steeringActive  = true;
                        steeringPointer = ptr;
                        applyPositionalSteering(tx);
                    }
                    // BUTTONS 모드: handleInput() 폴링으로 처리
                }

                // ── 가스 / 브레이크 ───────────────────────────────
                if (tx >= sw * STEER_X_RATIO) {
                    if (tx < sw * 0.75f) {   // 브레이크 존
                        if (brakePointer < 0) {
                            brakePointer = ptr;
                            brakeStartY = brakeSlideY = hudY;
                        }
                    } else {                  // 가스 존
                        if (gasPointer < 0) {
                            gasPointer = ptr;
                            gasStartY = gasSlideY = hudY;
                        }
                    }
                }
                return true;
            }

            @Override
            public boolean touchDragged(int sx, int sy, int ptr) {
                float sh   = Gdx.graphics.getHeight();
                float hudY = sh - sy;

                GameSettings s = GameSettings.get();

                // 스티어링 드래그
                if (ptr == steeringPointer && steeringActive) {
                    if (s.steeringMode == GameSettings.SteeringMode.WHEEL) {
                        applyRotationalSteering(sx, sy);
                    } else if (s.steeringMode == GameSettings.SteeringMode.SLIDE) {
                        applyPositionalSteering(sx);
                    }
                }

                // 가스 / 브레이크 슬라이드 Y 갱신
                if (ptr == gasPointer)   gasSlideY   = hudY;
                if (ptr == brakePointer) brakeSlideY = hudY;

                return true;
            }

            @Override
            public boolean touchUp(int sx, int sy, int ptr, int btn) {
                if (ptr == steeringPointer) steeringActive = false;
                if (ptr == gasPointer)      gasPointer     = -1;
                if (ptr == brakePointer)    brakePointer   = -1;
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK) { backToMenu(); return true; }
                return false;
            }
        });
    }

    // 핸들 원의 중심·반경 (입력 처리기와 HUD 양쪽에서 공유)
    private float getWheelCX() { return Gdx.graphics.getWidth()  * 0.23f; }
    private float getWheelCY() { return Gdx.graphics.getHeight() * 0.16f; }
    private float getWheelR()  { return Gdx.graphics.getHeight() * 0.10f; }

    // SLIDE 모드: 손가락 X 위치 → 조향각 직접 대입
    private void applyPositionalSteering(float screenX) {
        float halfW = Gdx.graphics.getWidth() * STEER_X_RATIO;
        float norm  = MathUtils.clamp((screenX - halfW * 0.5f) / (halfW * 0.5f), -1f, 1f);
        steeringWheelDeg = -norm * 450f;
        physics.steeringAngle = (steeringWheelDeg / 450f) * CarPhysics.MAX_STEERING;
    }

    // WHEEL 모드: 터치 드래그 → 핸들 회전각 누적 (atan2 델타 방식)
    // 반시계 방향 = 좌회전, 시계 방향 = 우회전
    private void applyRotationalSteering(int screenX, int screenY) {
        float sh = Gdx.graphics.getHeight();
        float dx = screenX - getWheelCX();
        float dy = (sh - screenY) - getWheelCY();   // 스크린 Y → HUD Y
        float newAngle = (float) Math.atan2(dy, dx);

        float delta = newAngle - steeringLastAngle;
        // atan2 불연속 구간(±π 경계) 보정
        if (delta >  MathUtils.PI) delta -= MathUtils.PI2;
        if (delta < -MathUtils.PI) delta += MathUtils.PI2;

        steeringWheelDeg = MathUtils.clamp(
                steeringWheelDeg + MathUtils.radiansToDegrees * delta, -450f, 450f);
        steeringLastAngle = newAngle;
        physics.steeringAngle = (steeringWheelDeg / 450f) * CarPhysics.MAX_STEERING;
    }

    // ═══════════════════════════════════════════════════════════════
    //  맵 생성
    // ═══════════════════════════════════════════════════════════════
    private void buildMap() {
        ModelBuilder mb = new ModelBuilder();
        long attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;

        // 바닥
        Material floorMat = new Material(ColorAttribute.createDiffuse(cfg.floorColor));
        Model floorModel = mb.createBox(200f, 0.2f, 200f, floorMat, attrs);
        allModels.add(floorModel);
        ModelInstance floor = new ModelInstance(floorModel);
        floor.transform.setToTranslation(0f, -0.1f, 0f);
        staticInstances.add(floor);

        // 주차 구역 (반투명 평면)
        Material spotMat = new Material(
                ColorAttribute.createDiffuse(cfg.spotColor),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                        cfg.spotColor.a));
        Model spotModel = mb.createBox(cfg.parkW, 0.05f, cfg.parkL, spotMat, attrs);
        allModels.add(spotModel);
        ModelInstance spot = new ModelInstance(spotModel);
        spot.transform.setToTranslation(cfg.parkX, 0.05f, cfg.parkZ);
        staticInstances.add(spot);

        // 외곽 벽 4면
        addWall(mb, attrs,   0f, -50f, 100f, 2f);
        addWall(mb, attrs,   0f,  50f, 100f, 2f);
        addWall(mb, attrs, -50f,   0f,   2f, 100f);
        addWall(mb, attrs,  50f,   0f,   2f, 100f);

        // 난이도별 내부 장애물
        switch (difficulty) {
            case "EASY":
                addWall(mb, attrs, -10f, -10f,  5f, 20f);
                addWall(mb, attrs,  15f,  10f, 10f,  5f);
                break;
            case "MEDIUM":
                addWall(mb, attrs,   0f, -15f, 40f,  2f);
                addWall(mb, attrs, -15f,  10f,  2f, 30f);
                addWall(mb, attrs,  15f,  15f, 20f,  2f);
                addWall(mb, attrs,  25f, -20f,  2f, 20f);
                addWall(mb, attrs,  15f, -30f,  2f,  8f);
                addWall(mb, attrs,  25f, -30f,  2f,  8f);
                break;
            case "DIFFICULT":
                for (int i = -40; i < 40; i += 10) {
                    if (i != 0)  addWall(mb, attrs, i,   -20f, 4f, 8f);
                    if (i != 40) addWall(mb, attrs, i,   -40f, 4f, 8f);
                }
                addWall(mb, attrs,   0f,  0f, 80f,  2f);
                addWall(mb, attrs, -30f, 20f,  5f, 20f);
                addWall(mb, attrs,  20f, 30f, 30f,  5f);
                break;
        }
    }

    /** 벽 하나 생성 + 충돌 AABB 등록 */
    private void addWall(ModelBuilder mb, long attrs,
                         float cx, float cz, float w, float l) {
        Material mat = new Material(ColorAttribute.createDiffuse(cfg.wallColor));
        Model m = mb.createBox(w, 2f, l, mat, attrs);
        allModels.add(m);
        ModelInstance inst = new ModelInstance(m);
        inst.transform.setToTranslation(cx, 1f, cz);
        staticInstances.add(inst);
        // 2D AABB (x, y = z, width, height = l)
        walls.add(new Rectangle(cx - w / 2f, cz - l / 2f, w, l));
    }

    // ═══════════════════════════════════════════════════════════════
    //  차량 3D 모델 절차적 생성
    // ═══════════════════════════════════════════════════════════════
    private void buildCar() {
        VehicleType v = vehicle;
        ModelBuilder mb = new ModelBuilder();
        long attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;

        Material paintMat = new Material(
                ColorAttribute.createDiffuse(v.color),
                FloatAttribute.createShininess(80f));
        Material glassMat = new Material(
                ColorAttribute.createDiffuse(new Color(0.07f, 0.07f, 0.08f, 0.55f)),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.55f),
                FloatAttribute.createShininess(120f));
        Material darkMat  = new Material(ColorAttribute.createDiffuse(
                new Color(0.1f, 0.1f, 0.1f, 1f)));
        Material chromeMat = new Material(
                ColorAttribute.createDiffuse(new Color(0.8f, 0.8f, 0.8f, 1f)),
                FloatAttribute.createShininess(200f));
        Material lightMat  = new Material(ColorAttribute.createDiffuse(Color.WHITE));
        Material tailMat   = new Material(ColorAttribute.createDiffuse(Color.RED));

        float chassisY = 0.8f;

        mb.begin();

        // 차체 (Chassis)
        addPart(mb, attrs, paintMat,
                0f, chassisY, 0f,
                v.width, v.height * 0.3f, v.length);

        // 범퍼 립
        addPart(mb, attrs, darkMat,
                0f, chassisY - v.height * 0.15f - 0.05f, 0f,
                v.width * 1.05f, 0.1f, v.length * 1.02f);

        // 차종별 캐빈 + 고유 파츠
        buildCabinParts(mb, attrs, v, paintMat, glassMat, chromeMat, darkMat, chassisY);

        // 헤드라이트
        float hw = 0.3f, hh = 0.2f;
        addPart(mb, attrs, lightMat, -v.width/2f+0.3f, chassisY, -v.length/2f-0.05f, hw, hh, 0.1f);
        addPart(mb, attrs, lightMat,  v.width/2f-0.3f, chassisY, -v.length/2f-0.05f, hw, hh, 0.1f);

        // 테일램프
        addPart(mb, attrs, tailMat, -v.width/2f+0.3f, chassisY, v.length/2f+0.05f, hw, hh, 0.1f);
        addPart(mb, attrs, tailMat,  v.width/2f-0.3f, chassisY, v.length/2f+0.05f, hw, hh, 0.1f);

        // 사이드 미러
        float mirrorZ = (v == VehicleType.TRUCK)
                ? -v.length/2f + v.length*0.35f/2f + 0.4f
                : -0.2f;
        addPart(mb, attrs, paintMat, -v.width/2f-0.1f, chassisY+0.3f, mirrorZ, 0.2f, 0.15f, 0.15f);
        addPart(mb, attrs, paintMat,  v.width/2f+0.1f, chassisY+0.3f, mirrorZ, 0.2f, 0.15f, 0.15f);

        // 바퀴 4개 (실린더)
        float wx = v.width / 2f + 0.05f;
        float wz = v.length / 2f - 0.8f;
        addWheel(mb, attrs, chromeMat, darkMat, -wx, wz);
        addWheel(mb, attrs, chromeMat, darkMat,  wx, wz);
        addWheel(mb, attrs, chromeMat, darkMat, -wx, -wz);
        addWheel(mb, attrs, chromeMat, darkMat,  wx, -wz);
        if (v == VehicleType.TRUCK) {
            addWheel(mb, attrs, chromeMat, darkMat, -wx, wz - 1.2f);
            addWheel(mb, attrs, chromeMat, darkMat,  wx, wz - 1.2f);
        }

        Model carModel = mb.end();
        allModels.add(carModel);
        carInstance = new ModelInstance(carModel);
    }

    private void buildCabinParts(ModelBuilder mb, long attrs, VehicleType v,
                                 Material paintMat, Material glassMat,
                                 Material chromeMat, Material darkMat, float chassisY) {
        float cabinW, cabinH, cabinL, cabinY, cabinZ;

        if (v == VehicleType.COMPACT) {
            cabinH = v.height * 0.55f; cabinL = v.length * 0.5f;
            cabinZ = 0.4f;
            cabinW = v.width - 0.2f;
            cabinY = chassisY + v.height * 0.4f + cabinH / 2f;

        } else if (v == VehicleType.SEDAN) {
            cabinH = v.height * 0.45f; cabinL = v.length * 0.4f;
            cabinZ = 0.2f;
            cabinW = v.width - 0.2f;
            cabinY = chassisY + v.height * 0.35f + cabinH / 2f;

            // 스포일러
            addPart(mb, attrs, paintMat,
                    0f, chassisY + v.height * 0.15f + 0.1f, v.length / 2f - 0.1f,
                    v.width * 0.9f, 0.05f, 0.3f);

        } else { // TRUCK
            cabinH = v.height * 0.6f;  cabinL = v.length * 0.35f;
            cabinZ = -v.length / 2f + cabinL / 2f + 0.2f;
            cabinW = v.width;
            cabinY = chassisY + v.height * 0.45f + cabinH / 2f;

            // 크롬 그릴
            addPart(mb, attrs, chromeMat,
                    0f, chassisY + 0.1f, -v.length / 2f - 0.05f,
                    v.width * 0.6f, v.height * 0.4f, 0.1f);

            // 배기통 (박스로 단순화)
            addPart(mb, attrs, chromeMat,
                    -v.width / 2f - 0.1f, chassisY + v.height * 0.5f, cabinZ + cabinL / 2f,
                    0.2f, v.height * 1.2f, 0.2f);
            addPart(mb, attrs, chromeMat,
                    v.width / 2f + 0.1f,  chassisY + v.height * 0.5f, cabinZ + cabinL / 2f,
                    0.2f, v.height * 1.2f, 0.2f);

            // 짐칸 바닥
            addPart(mb, attrs, darkMat,
                    0f, chassisY + v.height * 0.15f, v.length * 0.2f,
                    v.width, 0.1f, v.length * 0.6f);
        }

        // 캐빈 공통
        addPart(mb, attrs, glassMat, 0f, cabinY, cabinZ, cabinW, cabinH, cabinL);

        // 지붕
        addPart(mb, attrs, paintMat,
                0f, cabinY + cabinH / 2f + 0.05f, cabinZ,
                cabinW + 0.05f, 0.1f, cabinL + 0.05f);
    }

    /** MeshPartBuilder 를 이용해 박스 파츠 추가 (setVertexTransform 으로 오프셋 지정) */
    private void addPart(ModelBuilder mb, long attrs, Material mat,
                         float ox, float oy, float oz,
                         float w, float h, float d) {
        MeshPartBuilder mpb = mb.part("p", GL20.GL_TRIANGLES, attrs, mat);
        mpb.setVertexTransform(new Matrix4().setToTranslation(ox, oy, oz));
        BoxShapeBuilder.build(mpb, w, h, d);
    }

    /** 단순 박스 바퀴 (CylinderShapeBuilder 는 회전 필요, 박스로 단순화) */
    private void addWheel(ModelBuilder mb, long attrs,
                          Material chromeMat, Material rubberMat,
                          float wx, float wz) {
        float wy = 0.4f;
        // 타이어 (어두운 박스)
        MeshPartBuilder mpb = mb.part("tire", GL20.GL_TRIANGLES, attrs, rubberMat);
        mpb.setVertexTransform(new Matrix4().setToTranslation(wx, wy, wz));
        BoxShapeBuilder.build(mpb, 0.3f, 0.8f, 0.8f);
        // 휠 (크롬 작은 박스)
        mpb = mb.part("rim", GL20.GL_TRIANGLES, attrs, chromeMat);
        mpb.setVertexTransform(new Matrix4().setToTranslation(wx, wy, wz));
        BoxShapeBuilder.build(mpb, 0.32f, 0.4f, 0.4f);
    }

    // ═══════════════════════════════════════════════════════════════
    //  메인 렌더 루프
    // ═══════════════════════════════════════════════════════════════
    @Override
    public void render(float delta) {
        if (!gameOver) {
            update(delta);
        }
        draw();
    }

    private void update(float delta) {
        handleInput();
        physics.update(vehicle, gasAmount, brakeAmount);
        updateCamera();

        // 타이머 (1초마다 감소)
        timerElapsed += delta;
        if (timerElapsed >= 1f) {
            timerElapsed -= 1f;
            timerValue--;
            if (timerValue <= 0) endGame(false);
        }

        // 충돌 / 클리어 체크
        if (physics.checkCollision(walls, vehicle)) {
            endGame(false);
        } else if (checkWinBySettings()) {
            endGame(true);
        }
    }

    private void handleInput() {
        GameSettings s = GameSettings.get();
        float sh       = Gdx.graphics.getHeight();
        // 화면 높이의 30%를 슬라이드하면 최대 압력(1.0)
        float maxSlide = sh * 0.30f;

        // ── 가스 ───────────────────────────────────────────────────
        gasAmount = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP))
            gasAmount = 1.0f;
        if (gasPointer >= 0) {
            float raw;
            switch (s.throttleMode) {
                case SLIDE_UP:   raw = (gasSlideY - gasStartY)  / maxSlide; break;
                case SLIDE_DOWN: raw = (gasStartY - gasSlideY)  / maxSlide; break;
                default:         raw = 1.0f;  // TAP
            }
            gasAmount = Math.max(gasAmount, MathUtils.clamp(raw, 0f, 1f));
        }

        // ── 브레이크 ───────────────────────────────────────────────
        brakeAmount = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN))
            brakeAmount = 1.0f;
        if (brakePointer >= 0) {
            float raw;
            switch (s.brakeMode) {
                case SLIDE_UP:   raw = (brakeSlideY - brakeStartY) / maxSlide; break;
                case SLIDE_DOWN: raw = (brakeStartY - brakeSlideY) / maxSlide; break;
                default:         raw = 1.0f;  // TAP
            }
            brakeAmount = Math.max(brakeAmount, MathUtils.clamp(raw, 0f, 1f));
        }

        // ── 스티어링 ───────────────────────────────────────────────
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            steeringWheelDeg = MathUtils.clamp(steeringWheelDeg + 5f, -450f, 450f);
            physics.steeringAngle = (steeringWheelDeg / 450f) * CarPhysics.MAX_STEERING;

        } else if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            steeringWheelDeg = MathUtils.clamp(steeringWheelDeg - 5f, -450f, 450f);
            physics.steeringAngle = (steeringWheelDeg / 450f) * CarPhysics.MAX_STEERING;

        } else if (s.steeringMode == GameSettings.SteeringMode.BUTTONS) {
            // 좌우 버튼 모드: 폴링으로 각 버튼 영역 감지
            float sw    = Gdx.graphics.getWidth();
            float splitX = sw * STEER_X_RATIO / 2f;
            boolean leftDown = false, rightDown = false;
            for (int i = 0; i < 5; i++) {
                if (Gdx.input.isTouched(i)) {
                    float tx = Gdx.input.getX(i);
                    float ty = sh - Gdx.input.getY(i);
                    if (ty < sh * CTRL_Y_RATIO && tx < sw * STEER_X_RATIO) {
                        if (tx < splitX) leftDown  = true;
                        else             rightDown = true;
                    }
                }
            }
            if      (leftDown)  steeringWheelDeg = MathUtils.clamp(steeringWheelDeg + 5f, -450f, 450f);
            else if (rightDown) steeringWheelDeg = MathUtils.clamp(steeringWheelDeg - 5f, -450f, 450f);
            else {
                if (steeringWheelDeg > 0) steeringWheelDeg = Math.max(0f, steeringWheelDeg - 3f);
                if (steeringWheelDeg < 0) steeringWheelDeg = Math.min(0f, steeringWheelDeg + 3f);
            }
            physics.steeringAngle = (steeringWheelDeg / 450f) * CarPhysics.MAX_STEERING;

        } else if (!steeringActive) {
            // WHEEL / SLIDE 모드: 손 뗀 후 자동복귀 (3°/frame)
            if (steeringWheelDeg > 0) steeringWheelDeg = Math.max(0f, steeringWheelDeg - 3f);
            if (steeringWheelDeg < 0) steeringWheelDeg = Math.min(0f, steeringWheelDeg + 3f);
            physics.steeringAngle = (steeringWheelDeg / 450f) * CarPhysics.MAX_STEERING;
        }
    }

    private boolean checkWinBySettings() {
        if (GameSettings.get().winCondition == GameSettings.WinCondition.ENTER) {
            // 차량 중심이 주차 구역 사각형 안에 진입하는 즉시 성공
            return Math.abs(physics.x - cfg.parkX) < cfg.parkW / 2f
                && Math.abs(physics.z - cfg.parkZ) < cfg.parkL / 2f;
        }
        // FULL_STOP: 기존 조건 (거리 + 정렬 + 완전멈춤)
        return physics.checkWin(cfg.parkX, cfg.parkZ);
    }

    /** 체이스 카메라 — game.js 와 동일한 행렬 변환 */
    private void updateCamera() {
        float angle = physics.angle;
        // 차량 뒤 14m, 높이 5.5m
        float targetX = physics.x + MathUtils.sin(angle) * 14f;
        float targetY = 5.5f;
        float targetZ = physics.z + MathUtils.cos(angle) * 14f;
        Vector3 targetPos = new Vector3(targetX, targetY, targetZ);

        if (!camInitialized) {
            camera.position.set(targetPos);
            camInitialized = true;
        } else {
            camera.position.lerp(targetPos, 0.15f);
        }

        // 차량 앞 10m 를 응시
        float lookX = physics.x - MathUtils.sin(angle) * 10f;
        float lookZ = physics.z - MathUtils.cos(angle) * 10f;
        camera.lookAt(lookX, 1.5f, lookZ);
        camera.up.set(Vector3.Y);
        camera.update();
    }

    private void draw() {
        Color bg = cfg.bgColor;
        Gdx.gl.glClearColor(bg.r, bg.g, bg.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // 차량 트랜스폼 갱신
        carInstance.transform
                .setToTranslation(physics.x, 0f, physics.z)
                .rotate(Vector3.Y, MathUtils.radiansToDegrees * physics.angle);

        // 3D 렌더링
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        modelBatch.begin(camera);
        modelBatch.render(staticInstances, environment);
        modelBatch.render(carInstance, environment);
        modelBatch.end();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        // HUD 오버레이
        drawHUD();

        if (gameOver) drawOverlay();
    }

    // ─── HUD: 타이머 + 스티어링휠 시각화 + 버튼 + 미니맵 ──────────
    private void drawHUD() {
        float sw = Gdx.graphics.getWidth();
        float sh = Gdx.graphics.getHeight();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // 컨트롤러 배경 (하단 반투명 패널)
        shapes.setColor(0f, 0f, 0f, 0.4f);
        shapes.rect(0, 0, sw, sh * CTRL_Y_RATIO);

        // ── 스티어링 HUD (모드별 분기) ───────────────────────────
        float wheelCX = getWheelCX();
        float wheelCY = getWheelCY();
        float wheelR  = getWheelR();

        GameSettings gs = GameSettings.get();
        if (gs.steeringMode == GameSettings.SteeringMode.WHEEL) {
            // 3-스포크 핸들 회전 시각화
            shapes.setColor(0.28f, 0.28f, 0.28f, 0.90f);
            shapes.circle(wheelCX, wheelCY, wheelR, 48);
            shapes.setColor(0.14f, 0.14f, 0.14f, 0.88f);
            shapes.circle(wheelCX, wheelCY, wheelR * 0.78f, 48);

            float baseRad = MathUtils.degreesToRadians * steeringWheelDeg + MathUtils.PI / 2f;
            for (int s = 0; s < 3; s++) {
                float a   = baseRad + s * MathUtils.PI2 / 3f;
                float cos = MathUtils.cos(a), sin = MathUtils.sin(a);
                if (s == 0) shapes.setColor(0.95f, 0.78f, 0.10f, 1f);
                else        shapes.setColor(0.62f, 0.62f, 0.62f, 1f);
                shapes.rectLine(
                        wheelCX + cos * wheelR * 0.20f, wheelCY + sin * wheelR * 0.20f,
                        wheelCX + cos * wheelR * 0.76f, wheelCY + sin * wheelR * 0.76f, 5f);
            }
            shapes.setColor(0.50f, 0.50f, 0.50f, 1f);
            shapes.circle(wheelCX, wheelCY, wheelR * 0.17f, 20);

        } else if (gs.steeringMode == GameSettings.SteeringMode.SLIDE) {
            // 슬라이더 바 시각화
            float barW = wheelR * 1.8f;
            float barH = wheelR * 0.22f;
            float barX = wheelCX - barW / 2f;
            float barY = wheelCY - barH / 2f;
            shapes.setColor(0.22f, 0.22f, 0.22f, 0.88f);
            shapes.rect(barX, barY, barW, barH);

            float norm    = MathUtils.clamp(steeringWheelDeg / 450f, -1f, 1f);
            float indW    = barW * 0.10f;
            float indX    = wheelCX + norm * (barW / 2f - indW / 2f) - indW / 2f;
            shapes.setColor(0.0f, 0.78f, 1.0f, 1f);
            shapes.rect(indX, barY - barH * 0.3f, indW, barH * 1.6f);

            // 중앙 눈금
            shapes.setColor(0.5f, 0.5f, 0.5f, 0.7f);
            shapes.rectLine(wheelCX, barY, wheelCX, barY + barH, 2f);

        } else {
            // BUTTONS 모드: 좌/우 버튼 시각화
            float bW  = sw * STEER_X_RATIO * 0.44f;
            float bH  = sh * CTRL_Y_RATIO  * 0.52f;
            float bY  = sh * CTRL_Y_RATIO  * 0.24f;
            float lX  = sw * 0.015f;
            float rX  = sw * STEER_X_RATIO / 2f + sw * 0.015f;

            // 폴링으로 눌림 상태 확인
            boolean leftDown = false, rightDown = false;
            float splitX = sw * STEER_X_RATIO / 2f;
            for (int i = 0; i < 5; i++) {
                if (Gdx.input.isTouched(i)) {
                    float tx = Gdx.input.getX(i);
                    float ty = sh - Gdx.input.getY(i);
                    if (ty < sh * CTRL_Y_RATIO && tx < sw * STEER_X_RATIO) {
                        if (tx < splitX) leftDown  = true;
                        else             rightDown = true;
                    }
                }
            }

            shapes.setColor(leftDown  ? 0.18f : 0.10f, leftDown  ? 0.18f : 0.10f,
                            leftDown  ? 0.45f : 0.28f, 0.92f);
            shapes.rect(lX, bY, bW, bH);
            shapes.setColor(rightDown ? 0.18f : 0.10f, rightDown ? 0.18f : 0.10f,
                            rightDown ? 0.45f : 0.28f, 0.92f);
            shapes.rect(rX, bY, bW, bH);

            // 화살표 삼각형
            float ar = bH * 0.28f;
            float lCX = lX + bW / 2f, lCY = bY + bH / 2f;
            float rCX = rX + bW / 2f, rCY = lCY;
            shapes.setColor(Color.WHITE);
            shapes.triangle(lCX - ar, lCY, lCX + ar, lCY + ar * 0.7f, lCX + ar, lCY - ar * 0.7f);
            shapes.triangle(rCX + ar, rCY, rCX - ar, rCY + ar * 0.7f, rCX - ar, rCY - ar * 0.7f);
        }

        // FORWARD / BACKWARD 버튼
        float btnW  = sw * 0.2f;
        float btnH  = sh * 0.09f;
        float btnY  = sh * 0.14f;
        float fwdX  = sw * 0.72f;
        float bwdX  = sw * 0.52f;

        // 브레이크 버튼 (배경 + 압력 채움 바)
        shapes.setColor(0.15f, 0.15f, 0.15f, 0.90f);
        shapes.rect(bwdX, btnY, btnW, btnH);
        if (brakeAmount > 0f) {
            shapes.setColor(0.80f, 0.15f, 0.10f, 0.92f);
            shapes.rect(bwdX, btnY, btnW * brakeAmount, btnH);
        }
        // 가스 버튼 (배경 + 압력 채움 바)
        shapes.setColor(0.15f, 0.15f, 0.15f, 0.90f);
        shapes.rect(fwdX, btnY, btnW, btnH);
        if (gasAmount > 0f) {
            shapes.setColor(0.10f, 0.72f, 0.15f, 0.92f);
            shapes.rect(fwdX, btnY, btnW * gasAmount, btnH);
        }

        // 미니맵 (우측 상단) — shapes.begin() 블록 안에서 호출해야 함
        drawMinimap(sw, sh);

        shapes.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        // HUD 텍스트
        hudBatch.begin();

        // 타이머
        Color timerColor = timerValue <= 10 ? Color.RED : Color.GREEN;
        hudFont.setColor(timerColor);
        hudFont.draw(hudBatch,
                String.format("TIME: %02d", (int) timerValue),
                sw * 0.02f, sh * 0.975f);

        // 속도계
        hudFont.setColor(Color.WHITE);
        int speedKmh = (int)(Math.abs(physics.speed) / vehicle.maxSpeed * 100f);
        hudFont.draw(hudBatch, "SPD: " + speedKmh,
                sw * 0.02f, sh * 0.935f);

        // 버튼 라벨
        hudFont.getData().setScale(1.5f);
        hudFont.setColor(Color.WHITE);
        GlyphLayout bwd = new GlyphLayout(hudFont, "BWD");
        hudFont.draw(hudBatch, bwd,
                bwdX + (btnW - bwd.width) / 2f, btnY + btnH / 2f + bwd.height / 2f);
        GlyphLayout fwd = new GlyphLayout(hudFont, "FWD");
        hudFont.draw(hudBatch, fwd,
                fwdX + (btnW - fwd.width) / 2f, btnY + btnH / 2f + fwd.height / 2f);
        hudFont.getData().setScale(2f);

        hudBatch.end();
    }

    /** 미니맵 렌더링 (ShapeRenderer) — game.js 의 Canvas2D 로직 이식 */
    private void drawMinimap(float sw, float sh) {
        float mmSize  = sh * 0.14f;
        float mmX     = sw - mmSize - sw * 0.02f;
        float mmY     = sh - mmSize - sh * 0.02f;
        float mapExtent = 100f; // 월드 좌표 -50 ~ 50

        // 맵핑 헬퍼: 월드 좌표 → 미니맵 픽셀
        // world val (-50~50) → minimap px (mmX ~ mmX+mmSize)

        shapes.setColor(0f, 0f, 0f, 0.6f);
        shapes.rect(mmX, mmY, mmSize, mmSize);

        // 벽 (회색) — X 반전: rect 오른쪽 끝이 새 왼쪽 기준점
        shapes.setColor(0.65f, 0.65f, 0.65f, 0.6f);
        for (Rectangle w : walls) {
            float rx = mmX + (-w.x - w.width + 50f) / mapExtent * mmSize;
            float ry = mmY + (w.y + 50f) / mapExtent * mmSize;
            float rw = w.width  / mapExtent * mmSize;
            float rl = w.height / mapExtent * mmSize;
            shapes.rect(rx, ry, rw, rl);
        }

        // 주차구역 (녹색)
        shapes.setColor(0f, 1f, 0f, 0.8f);
        float px = mmX + (-cfg.parkX - cfg.parkW / 2f + 50f) / mapExtent * mmSize;
        float py = mmY + (cfg.parkZ - cfg.parkL / 2f + 50f) / mapExtent * mmSize;
        shapes.rect(px, py,
                cfg.parkW / mapExtent * mmSize,
                cfg.parkL / mapExtent * mmSize);

        // 차량 (빨간 삼각형 화살표)
        float cx = mmX + (-physics.x + 50f) / mapExtent * mmSize;
        float cy = mmY + (physics.z + 50f) / mapExtent * mmSize;
        float arrowSize = mmSize * 0.07f;
        float ang = MathUtils.PI - physics.angle;

        shapes.setColor(1f, 0f, 0f, 1f);
        // 삼각형 세 꼭지점
        float ax = cx + MathUtils.sin(ang)           * arrowSize * 1.5f;
        float ay = cy + MathUtils.cos(ang)           * arrowSize * 1.5f;
        float bx = cx + MathUtils.sin(ang + 2.2f)   * arrowSize;
        float by = cy + MathUtils.cos(ang + 2.2f)   * arrowSize;
        float dx = cx + MathUtils.sin(ang - 2.2f)   * arrowSize;
        float dy = cy + MathUtils.cos(ang - 2.2f)   * arrowSize;
        shapes.triangle(ax, ay, bx, by, dx, dy);
    }

    /** 게임 오버 / 성공 오버레이 */
    private void drawOverlay() {
        float sw = Gdx.graphics.getWidth();
        float sh = Gdx.graphics.getHeight();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.65f);
        shapes.rect(0, 0, sw, sh);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        hudBatch.begin();
        overlayFont.setColor(gameWon ? Color.GREEN : Color.RED);
        String msg = gameWon ? "PARKING SUCCESS!" : (timerValue <= 0 ? "TIME OVER!" : "CRASHED!");
        GlyphLayout gl = new GlyphLayout(overlayFont, msg);
        overlayFont.draw(hudBatch, gl, (sw - gl.width) / 2f, sh * 0.6f);

        overlayFont.getData().setScale(2f);
        overlayFont.setColor(Color.WHITE);
        GlyphLayout back = new GlyphLayout(overlayFont, "Tap to return");
        overlayFont.draw(hudBatch, back, (sw - back.width) / 2f, sh * 0.42f);
        overlayFont.getData().setScale(3.5f);
        hudBatch.end();

        // 탭하면 메뉴로
        if (Gdx.input.justTouched()) backToMenu();
    }

    private void endGame(boolean won) {
        gameOver = true;
        gameWon  = won;
        physics.speed = 0f;
    }

    private void backToMenu() {
        game.setScreen(new MenuScreen(game));
        dispose();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth  = width;
        camera.viewportHeight = height;
        camera.update();
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        hudBatch.dispose();
        shapes.dispose();
        hudFont.dispose();
        overlayFont.dispose();
        for (Model m : allModels) m.dispose();
        allModels.clear();
        staticInstances.clear();
    }
}
