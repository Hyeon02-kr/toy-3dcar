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
    private boolean gasPressed   = false;
    private boolean brakePressed = false;
    private boolean steeringActive = false;
    private int     steeringPointer = -1;
    private float   steeringStartX  = 0f;
    private float   steeringStartAngle = 0f;

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
                float tx = sx;
                float ty = Gdx.graphics.getHeight() - sy;
                // 왼쪽 상단 절반 = 스티어링 드래그 영역
                if (tx < Gdx.graphics.getWidth() * STEER_X_RATIO
                        && ty > Gdx.graphics.getHeight() * CTRL_Y_RATIO
                        && !steeringActive) {
                    steeringActive     = true;
                    steeringPointer    = ptr;
                    steeringStartX     = tx;
                    steeringStartAngle = physics.steeringAngle;
                }
                return true;
            }
            @Override
            public boolean touchDragged(int sx, int sy, int ptr) {
                if (ptr == steeringPointer && steeringActive) {
                    float deltaX = sx - steeringStartX;
                    // 1px = 0.8도, 최대 120도
                    float deg = steeringStartAngle
                            * (120f / CarPhysics.MAX_STEERING)
                            - deltaX * 0.8f;
                    deg = MathUtils.clamp(deg, -120f, 120f);
                    physics.steeringAngle = -(deg / 120f) * CarPhysics.MAX_STEERING;
                }
                return true;
            }
            @Override
            public boolean touchUp(int sx, int sy, int ptr, int btn) {
                if (ptr == steeringPointer) {
                    steeringActive = false;
                    physics.steeringAngle = 0f;
                }
                return true;
            }
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK) {
                    backToMenu(); return true;
                }
                return false;
            }
        });
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
        physics.update(vehicle, gasPressed, brakePressed);
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
        } else if (physics.checkWin(cfg.parkX, cfg.parkZ)) {
            endGame(true);
        }
    }

    private void handleInput() {
        // 키보드 지원 (에뮬레이터 / PC 테스트용)
        gasPressed   = Gdx.input.isKeyPressed(Input.Keys.W)
                    || Gdx.input.isKeyPressed(Input.Keys.UP);
        brakePressed = Gdx.input.isKeyPressed(Input.Keys.S)
                    || Gdx.input.isKeyPressed(Input.Keys.DOWN);

        if (!steeringActive) {
            if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
                physics.steeringAngle = Math.min(physics.steeringAngle + 0.03f, CarPhysics.MAX_STEERING);
            } else if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
                physics.steeringAngle = Math.max(physics.steeringAngle - 0.03f, -CarPhysics.MAX_STEERING);
            } else {
                if (physics.steeringAngle > 0) physics.steeringAngle = Math.max(0f, physics.steeringAngle - 0.05f);
                if (physics.steeringAngle < 0) physics.steeringAngle = Math.min(0f, physics.steeringAngle + 0.05f);
            }
        }

        // 멀티 터치 — 오른쪽 하단 영역에서 gas/brake 버튼
        float sw = Gdx.graphics.getWidth();
        float sh = Gdx.graphics.getHeight();
        float btnY  = sh * CTRL_Y_RATIO;
        float btnMid = sw * 0.75f;

        for (int i = 0; i < 5; i++) {
            if (Gdx.input.isTouched(i)) {
                float tx = Gdx.input.getX(i);
                float ty = sh - Gdx.input.getY(i);
                if (tx >= sw * STEER_X_RATIO && ty < btnY) {
                    if (tx < btnMid) brakePressed = true;
                    else             gasPressed   = true;
                }
            }
        }
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

        // 스티어링 휠 원 (왼쪽)
        float wheelCX = sw * 0.23f;
        float wheelCY = sh * 0.16f;
        float wheelR  = sh * 0.1f;
        shapes.setColor(0.3f, 0.3f, 0.3f, 0.85f);
        shapes.circle(wheelCX, wheelCY, wheelR, 32);

        // 조향 방향 표시 막대
        float indicatorAngle = -physics.steeringAngle * (120f / CarPhysics.MAX_STEERING);
        float cos = MathUtils.cosDeg(indicatorAngle);
        float sin = MathUtils.sinDeg(indicatorAngle);
        shapes.setColor(0f, 0.9f, 1f, 1f);
        shapes.rectLine(
                wheelCX - cos * wheelR * 0.7f,
                wheelCY - sin * wheelR * 0.7f,
                wheelCX + cos * wheelR * 0.7f,
                wheelCY + sin * wheelR * 0.7f, 6f);

        // FORWARD / BACKWARD 버튼
        float btnW  = sw * 0.2f;
        float btnH  = sh * 0.09f;
        float btnY  = sh * 0.14f;
        float fwdX  = sw * 0.72f;
        float bwdX  = sw * 0.52f;

        shapes.setColor(brakePressed ? 0.7f : 0.25f, 0.25f, 0.25f, 0.9f);
        shapes.rect(bwdX, btnY, btnW, btnH);
        shapes.setColor(gasPressed ? 0.1f : 0.25f, gasPressed ? 0.7f : 0.25f, 0.25f, 0.9f);
        shapes.rect(fwdX, btnY, btnW, btnH);

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

        // 벽 (회색)
        shapes.setColor(0.65f, 0.65f, 0.65f, 0.6f);
        for (Rectangle w : walls) {
            float rx = mmX + (w.x + 50f) / mapExtent * mmSize;
            float ry = mmY + (w.y + 50f) / mapExtent * mmSize;
            float rw = w.width  / mapExtent * mmSize;
            float rl = w.height / mapExtent * mmSize;
            shapes.rect(rx, ry, rw, rl);
        }

        // 주차구역 (녹색)
        shapes.setColor(0f, 1f, 0f, 0.8f);
        float px = mmX + (cfg.parkX - cfg.parkW / 2f + 50f) / mapExtent * mmSize;
        float py = mmY + (cfg.parkZ - cfg.parkL / 2f + 50f) / mapExtent * mmSize;
        shapes.rect(px, py,
                cfg.parkW / mapExtent * mmSize,
                cfg.parkL / mapExtent * mmSize);

        // 차량 (빨간 삼각형 화살표)
        float cx = mmX + (physics.x + 50f) / mapExtent * mmSize;
        float cy = mmY + (physics.z + 50f) / mapExtent * mmSize;
        float arrowSize = mmSize * 0.07f;
        float ang = -physics.angle; // libGDX 캔버스 Y+ = 위 방향이므로 부호 유지

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
