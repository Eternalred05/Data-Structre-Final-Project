package GUI;

import Runner.MainScreen;
import Characters.Hero;
import Characters.NPC;
import Characters.Villager;
import Logic.Game;
import com.almasb.fxgl.dsl.FXGL;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.animation.TranslateTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class JVInn {

    private final StackPane root;
    private final Pane world;
    private final StackPane loadingOverlay;
    private ImageView backgroundView;
    private MediaPlayer music;

    private final ImageView heroView;
    private final double HERO_W = 48;
    private final double HERO_H = 48;
    private final double HERO_SPEED = 180.0;
    private final Set<KeyCode> keys = new HashSet<>();
    private AnimationTimer mover;

    private final double VIEW_W = 800;
    private final double VIEW_H = 600;
    private double worldW = VIEW_W;
    private double worldH = VIEW_H;

    private Rectangle startRect;
    private boolean onStartRect = false;

    private Runnable onExitCallback;
    private final Game game;

    // Sistema de colisiones
    private final List<Obstacle> obstacles = new ArrayList<>();
    private boolean debugEnabled = false;
    
    // para los NPC
    private final List<NPC> npcs = new ArrayList<>();
    private final List<ImageView> npcNodes = new ArrayList<>();
    private final List<Rectangle2D> npcCollisionRects = new ArrayList<>();

    // Inventario (si se abre desde aquí se pasa this)
    private InventoryScreen inventory;

    // Direcciones del héroe (para depuración con tecla P)
    public enum Direction {
        NONE, N, NE, E, SE, S, SW, W, NW
    }
    private Direction currentDirection = Direction.NONE;

    // Tipos de obstáculos para la aldea
    private enum ObstacleType {
        HOUSE, TREE, WELL, FENCE, BUSH, EXIT, BLOCK, NPC
    }

    // Clase interna para obstáculos
    private static class Obstacle {

        final Rectangle2D collisionRect;
        final ObstacleType type;
        final String id;

        Obstacle(Rectangle2D collision, ObstacleType type, String id) {
            this.collisionRect = collision;
            this.type = type;
            this.id = id;
        }
    }

    public JVInn(Game game) {
        this.game = game;
        root = new StackPane();
        root.setPrefSize(VIEW_W, VIEW_H);

        world = new Pane();
        world.setPrefSize(VIEW_W, VIEW_H);

        loadingOverlay = createLoadingOverlay();

        root.getChildren().addAll(world, loadingOverlay);

        heroView = createHeroView();

        installInputHandlers();
        createMover();

        root.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                clearInputState();
            }
        });
    }

    public StackPane getRoot() {
        return root;
    }

    public Point2D getHeroMapTopLeft() {
        return new Point2D(heroView.getLayoutX(), heroView.getLayoutY());
    }

    public void showWithLoading(Runnable onLoaded, Runnable onExit) {
        this.onExitCallback = onExit;

        Platform.runLater(() -> {
            FXGL.getGameScene().addUINode(root);
            showLoading(true);

            boolean imageOk = loadBackgroundImage("/Resources/textures/fieldVillage/FVInn.png");
            boolean musicOk = startVillageMusic("/Resources/music/interiorOST.mp3");

            // Primero poblar colisiones
            populateVillageObstacles();
            
            //
             // Cargar NPC
            addVillagerToList();
            renderNpcs();

            // Luego posicionar al héroe
            positionHeroAtEntrance();
            createStartRectAtHeroStart();

            // Dibujar obstáculos en modo debug
            if (debugEnabled) {
                drawDebugObstacles();
            }

            PauseTransition wait = new PauseTransition(Duration.millis(600));
            wait.setOnFinished(e -> {
                showLoading(false);
                fadeInContent();
                startMover();
                if (onLoaded != null) {
                    onLoaded.run();
                }
            });
            wait.play();
        });
    }

    public void hide() {
        Platform.runLater(() -> {
            stopVillageMusic();
            stopMover();
            try {
                FXGL.getGameScene().removeUINode(root);
            } catch (Throwable ignored) {
            }
        });
    }

    public void setHeroPosition(double x, double y) {
        double nx = clamp(x, 0, Math.max(0, worldW - HERO_W));
        double ny = clamp(y, 0, Math.max(0, worldH - HERO_H));
        heroView.setLayoutX(nx);
        heroView.setLayoutY(ny);
        updateCamera();
    }

    public void startMover() {
        if (mover != null) {
            mover.start();
        }
    }

    public void stopMover() {
        if (mover != null) {
            mover.stop();
        }
    }

    public void stopVillageMusic() {
        try {
            if (music != null) {
                music.stop();
                music.dispose();
                music = null;
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------------- internals / UI ----------------
    private StackPane createLoadingOverlay() {
        StackPane overlay = new StackPane();
        overlay.setPickOnBounds(true);

        Rectangle bg = new Rectangle(VIEW_W, VIEW_H);
        bg.setFill(Color.rgb(0, 0, 0, 0.6));

        Text label = new Text("Cargando aldea...");
        label.setStyle("-fx-font-size: 24px; -fx-fill: #e0d090;");

        overlay.getChildren().addAll(bg, label);
        StackPane.setAlignment(label, Pos.CENTER);
        overlay.setVisible(false);
        return overlay;
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisible(show);
        if (show) {
            loadingOverlay.toFront();
        } else {
            loadingOverlay.toBack();
        }
    }

    private void fadeInContent() {
        FadeTransition ft = new FadeTransition(Duration.millis(400), root);
        ft.setFromValue(0.2);
        ft.setToValue(1.0);
        ft.play();
    }

    private boolean loadBackgroundImage(String path) {
        boolean load = false;
        try {
            Image img = new Image(getClass().getResourceAsStream(path));
            backgroundView = new ImageView(img);
            backgroundView.setPreserveRatio(false);
            backgroundView.setSmooth(true);

            worldW = img.getWidth() > 0 ? img.getWidth() : VIEW_W;
            worldH = img.getHeight() > 0 ? img.getHeight() : VIEW_H;

            backgroundView.setFitWidth(worldW);
            backgroundView.setFitHeight(worldH);

            world.setPrefSize(worldW, worldH);
            world.getChildren().clear();
            world.getChildren().add(backgroundView);

            if (!world.getChildren().contains(heroView)) {
                world.getChildren().add(heroView);
            } else {
                heroView.toFront();
            }
            load = true;
        } catch (Throwable t) {
            Text err = new Text("No se pudo cargar la imagen de la aldea.");
            err.setStyle("-fx-font-size: 16px; -fx-fill: #ffdddd;");
            root.getChildren().add(err);

        }
        return load;
    }

    private boolean startVillageMusic(String path) {
        boolean result = false;
        try {
            URL res = getClass().getResource(path);
            if (res != null) {
                Media media = new Media(res.toExternalForm());
                stopVillageMusic();
                music = new MediaPlayer(media);
                music.setCycleCount(MediaPlayer.INDEFINITE);
                music.setVolume(MainScreen.getVolumeSetting());
                music.play();
                result = true;
            }
        } catch (Throwable t) {
            result = false;
        }
        return result;
    }

    private ImageView createHeroView() {
        Image img = null;
        try {
            img = new Image(getClass().getResourceAsStream(game.getHero().getSpritePath()));
        } catch (Throwable ignored) {
            img = null;
        }
        ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true);
        iv.setFitWidth(HERO_W);
        iv.setFitHeight(HERO_H);
        iv.setMouseTransparent(true);
        return iv;
    }

    // ---------------- colisiones (restauradas) ----------------
    private void populateVillageObstacles() {
        obstacles.clear();

        // Agrega al menos un área de salida (puerta)
        obstacles.add(new Obstacle(
                new Rectangle2D(330, 570, 100, 50),
                ObstacleType.EXIT,
                "puertaSalida"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(0, 0, 820, 95),
                ObstacleType.BLOCK,
                "parteSuperior"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(55, 340, 175, 85),
                ObstacleType.BLOCK,
                "mesaIzquierda"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(580, 340, 175, 90),
                ObstacleType.BLOCK,
                "mesaDerecha"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(0, 100, 180, 80),
                ObstacleType.BLOCK,
                "camas"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(338, 100, 40, 90),
                ObstacleType.BLOCK,
                "estanteLargo"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(338, 195, 500, 40),
                ObstacleType.BLOCK,
                "estanteAncho"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(255, 105, 20, 25),
                ObstacleType.BLOCK,
                "maceta1"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(400, 105, 20, 25),
                ObstacleType.BLOCK,
                "maceta2"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(440, 110, 20, 25),
                ObstacleType.BLOCK,
                "maceta3"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(510, 95, 80, 40),
                ObstacleType.BLOCK,
                "piano"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(630, 95, 170, 40),
                ObstacleType.BLOCK,
                "mueble"
        ));
    }

    private void drawDebugObstacles() {
        world.getChildren().removeIf(n -> "debug_obstacle".equals(n.getProperties().get("tag")));

        if (!debugEnabled) {
            return;
        }

        for (JVInn.Obstacle ob : obstacles) {
            Rectangle rect = new Rectangle(
                    ob.collisionRect.getMinX(),
                    ob.collisionRect.getMinY(),
                    ob.collisionRect.getWidth(),
                    ob.collisionRect.getHeight()
            );

            switch (ob.type) {
                case HOUSE:
                    rect.setFill(Color.rgb(139, 69, 19, 0.4));
                    rect.setStroke(Color.rgb(101, 50, 14, 0.8));
                    break;
                case TREE:
                    rect.setFill(Color.rgb(34, 139, 34, 0.4));
                    rect.setStroke(Color.rgb(0, 100, 0, 0.8));
                    break;
                case WELL:
                    rect.setFill(Color.rgb(105, 105, 105, 0.4));
                    rect.setStroke(Color.rgb(64, 64, 64, 0.8));
                    break;
                case FENCE:
                    rect.setFill(Color.rgb(160, 82, 45, 0.4));
                    rect.setStroke(Color.rgb(101, 50, 14, 0.8));
                    break;
                case BUSH:
                    rect.setFill(Color.rgb(0, 128, 0, 0.4));
                    rect.setStroke(Color.rgb(0, 64, 0, 0.8));
                    break;
                default:
                    rect.setFill(Color.rgb(255, 0, 0, 0.3));
                    rect.setStroke(Color.RED);
            }

            rect.getProperties().put("tag", "debug_obstacle");
            rect.setMouseTransparent(true);
            world.getChildren().add(rect);
        }
    }

    // ---------------- movimiento y entradas ----------------
    private void positionHeroAtEntrance() {
        double startX = (worldW - HERO_W) / 2.0;
        double startY = worldH - HERO_H - 8.0;

        startX = clamp(startX, 0, Math.max(0, worldW - HERO_W));
        startY = clamp(startY, 0, Math.max(0, worldH - HERO_H));

        Rectangle2D heroRect = new Rectangle2D(startX, startY, HERO_W, HERO_H);

        boolean collisionFound = false;
        for (int i = 0; i < obstacles.size() && !collisionFound; i++) {
            JVInn.Obstacle ob = obstacles.get(i);
            if (heroRect.intersects(ob.collisionRect)) {
                startY = ob.collisionRect.getMinY() - HERO_H - 5;
                collisionFound = true;
            }
        }

        heroView.setLayoutX(startX);
        heroView.setLayoutY(startY);
        updateCamera();
    }

    private void createStartRectAtHeroStart() {
        if (startRect != null) {
            world.getChildren().remove(startRect);
            startRect = null;
        }
        double rx = heroView.getLayoutX();
        double ry = heroView.getLayoutY();
        double rw = HERO_W + 8;
        double rh = HERO_H + 8;

        startRect = new Rectangle(rx - 4, ry - 4, rw, rh);
        startRect.setFill(Color.rgb(0, 120, 255, 0.28));
        startRect.setStroke(Color.rgb(0, 80, 200, 0.9));
        startRect.setMouseTransparent(true);
        startRect.getProperties().put("tag", "exit_area");

        if (!world.getChildren().contains(startRect)) {
            world.getChildren().add(startRect);
        }
        startRect.toBack();
        heroView.toFront();
    }

    private void installInputHandlers() {
        root.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            KeyCode k = ev.getCode();

            if (k == KeyCode.W || k == KeyCode.UP) {
                keys.add(KeyCode.W);
                heroView.setImage(game.getHero().getSpriteForDirection("Up"));
            }
            if (k == KeyCode.S || k == KeyCode.DOWN) {
                keys.add(KeyCode.S);
                heroView.setImage(game.getHero().getSpriteForDirection("Down"));
            }
            if (k == KeyCode.A || k == KeyCode.LEFT) {
                keys.add(KeyCode.A);
                heroView.setImage(game.getHero().getSpriteForDirection("Left"));
            }
            if (k == KeyCode.D || k == KeyCode.RIGHT) {
                keys.add(KeyCode.D);
                heroView.setImage(game.getHero().getSpriteForDirection("Right"));
            }

            if (k == KeyCode.P) {
                System.out.println("Hero position (aldea): (" + heroView.getLayoutX() + ", " + heroView.getLayoutY() + ")");
                System.out.println("Hero world center (aldea): (" + (heroView.getLayoutX() + HERO_W / 2) + ", " + (heroView.getLayoutY() + HERO_H / 2) + ")");
                System.out.println("Hero direction: " + getHeroDirection().name());
            }

            if (k == KeyCode.I || k == KeyCode.ADD || k == KeyCode.PLUS) {
                clearInputState();
                openInventory();
            }

            if (k == KeyCode.ENTER) {
                if (onStartRect) {
                    clearInputState();
                    try {
                        if (game != null && game.getHero() != null) {
                            Hero h = game.getHero();
                            h.setLastLocation(Hero.Location.FIELD_VILLAGE);
                            h.setLastPosX(heroView.getLayoutX());
                            h.setLastPosY(heroView.getLayoutY());
                            try {
                                game.createSaveGame();
                            } catch (Throwable ignored) {
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    if (onExitCallback != null) {
                        hide();
                        onExitCallback.run();
                    } else {
                        hide();
                    }
                }
            }

            ev.consume();
        });

        root.addEventFilter(KeyEvent.KEY_RELEASED, ev -> {
            KeyCode k = ev.getCode();
            if (k == KeyCode.W || k == KeyCode.UP) {
                keys.remove(KeyCode.W);
            }
            if (k == KeyCode.S || k == KeyCode.DOWN) {
                keys.remove(KeyCode.S);
            }
            if (k == KeyCode.A || k == KeyCode.LEFT) {
                keys.remove(KeyCode.A);
            }
            if (k == KeyCode.D || k == KeyCode.RIGHT) {
                keys.remove(KeyCode.D);
            }
            ev.consume();
        });

        root.setFocusTraversable(true);
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(root::requestFocus);
            } else {
                clearInputState();
            }
        });
    }

    private void openInventory() {
        stopMover();

        // Pausar música localmente
        try {
            if (music != null) {
                music.pause();
            }
        } catch (Throwable ignored) {
        }

        // Pasar referencia para que InventoryScreen pueda guardar la posición y reanudar foco
        inventory = new InventoryScreen(game, this);

        inventory.setOnClose(() -> {
            Platform.runLater(() -> {
                try {
                    FXGL.getGameScene().removeUINode(inventory.getRoot());
                } catch (Throwable ignored) {
                }
                startMover();
                try {
                    if (music != null) {
                        music.play();
                    }
                } catch (Throwable ignored) {
                }
                root.requestFocus();
            });
        });

        inventory.show();
        Platform.runLater(() -> {
            try {
                inventory.getRoot().requestFocus();
            } catch (Throwable ignored) {
            }
        });
    }

    private void createMover() {
        mover = new AnimationTimer() {
            private long last = -1;

            @Override
            public void handle(long now) {
                if (last < 0) {
                    last = now;
                }
                double dt = (now - last) / 1e9;
                last = now;

                boolean shouldProcess = true;
                if (root.getScene() == null || !root.isFocused()) {
                    clearInputState();
                    shouldProcess = false;
                }

                if (shouldProcess) {
                    updateAndMove(dt);
                }
            }
        };
    }

    private void updateAndMove(double dt) {
        double vx = 0;
        double vy = 0;
        if (keys.contains(KeyCode.A)) {
            vx -= HERO_SPEED;
        }
        if (keys.contains(KeyCode.D)) {
            vx += HERO_SPEED;
        }
        if (keys.contains(KeyCode.W)) {
            vy -= HERO_SPEED;
        }
        if (keys.contains(KeyCode.S)) {
            vy += HERO_SPEED;
        }

        JVInn.Direction newDir = (vx != 0 || vy != 0) ? directionFromVector(vx, vy) : JVInn.Direction.NONE;
        setDirectionIfChanged(newDir);

        boolean isIdle = (vx == 0 && vy == 0);
        if (isIdle) {
            checkStartIntersection();
        } else {
            moveHero(vx * dt, vy * dt);
        }
    }

    private void moveHero(double dx, double dy) {
        double curX = heroView.getLayoutX();
        double curY = heroView.getLayoutY();

        double proposedX = clamp(curX + dx, 0, Math.max(0, worldW - HERO_W));
        double proposedY = clamp(curY + dy, 0, Math.max(0, worldH - HERO_H));

        Rectangle2D heroRect = new Rectangle2D(proposedX, proposedY, HERO_W, HERO_H);
        boolean collision = false;
        for (int i = 0; i < obstacles.size() && !collision; i++) {
            JVInn.Obstacle ob = obstacles.get(i);
            if (heroRect.intersects(ob.collisionRect)) {
                collision = true;
            }
        }

        if (!collision) {
            heroView.setLayoutX(proposedX);
            heroView.setLayoutY(proposedY);
        } else {
            Rectangle2D heroRectX = new Rectangle2D(proposedX, curY, HERO_W, HERO_H);
            Rectangle2D heroRectY = new Rectangle2D(curX, proposedY, HERO_W, HERO_H);

            boolean canMoveX = true;
            boolean canMoveY = true;

            for (int i = 0; i < obstacles.size() && (canMoveX || canMoveY); i++) {
                JVInn.Obstacle ob = obstacles.get(i);
                if (canMoveX && heroRectX.intersects(ob.collisionRect)) {
                    canMoveX = false;
                }
                if (canMoveY && heroRectY.intersects(ob.collisionRect)) {
                    canMoveY = false;
                }
            }

            if (canMoveX) {
                heroView.setLayoutX(proposedX);
            }
            if (canMoveY) {
                heroView.setLayoutY(proposedY);
            }
        }

        checkExitArea();
        checkStartIntersection();
        updateCamera();
    }

    private JVInn.Direction directionFromVector(double vx, double vy) {
        JVInn.Direction result = JVInn.Direction.NONE;

        if (!(vx == 0 && vy == 0)) {
            double angle = Math.toDegrees(Math.atan2(-vy, vx));
            if (angle < 0) {
                angle += 360.0;
            }

            if (angle >= 337.5 || angle < 22.5) {
                result = JVInn.Direction.E;
            } else if (angle < 67.5) {
                result = JVInn.Direction.NE;
            } else if (angle < 112.5) {
                result = JVInn.Direction.N;
            } else if (angle < 157.5) {
                result = JVInn.Direction.NW;
            } else if (angle < 202.5) {
                result = JVInn.Direction.W;
            } else if (angle < 247.5) {
                result = JVInn.Direction.SW;
            } else if (angle < 292.5) {
                result = JVInn.Direction.S;
            } else if (angle < 337.5) {
                result = JVInn.Direction.SE;
            }
        }

        return result;
    }

    private void setDirectionIfChanged(JVInn.Direction newDir) {
        if (newDir == null) {
            newDir = JVInn.Direction.NONE;
        }
        currentDirection = newDir;
    }

    public JVInn.Direction getHeroDirection() {
        return currentDirection;
    }

    private void checkStartIntersection() {
        boolean intersects = false;

        if (startRect != null) {
            intersects = heroView.getBoundsInParent().intersects(startRect.getBoundsInParent());
            startRect.setFill(intersects ? Color.rgb(0, 120, 255, 0.42) : Color.rgb(0, 120, 255, 0.28));
        } else {
            intersects = false;
        }

        onStartRect = intersects;
    }

    private void updateCamera() {
        double heroCenterX = heroView.getLayoutX() + HERO_W / 2.0;
        double heroCenterY = heroView.getLayoutY() + HERO_H / 2.0;

        double targetTx = VIEW_W / 2.0 - heroCenterX;
        double targetTy = VIEW_H / 2.0 - heroCenterY;

        double minTx = Math.min(0, VIEW_W - worldW);
        double maxTx = 0;
        double minTy = Math.min(0, VIEW_H - worldH);
        double maxTy = 0;

        double tx = clamp(targetTx, minTx, maxTx);
        double ty = clamp(targetTy, minTy, maxTy);

        double lowerZone = worldH * 0.75;
        if (heroCenterY > lowerZone) {
            double factor = 0.45;
            ty = ty * factor + (VIEW_H / 2.0 - heroCenterY) * (1 - factor);
            ty = clamp(ty, minTy, maxTy);
        }

        world.setTranslateX(tx);
        world.setTranslateY(ty);
    }

    private static double clamp(double v, double lo, double hi) {
        double result = v;
        if (result < lo) {
            result = lo;
        } else if (result > hi) {
            result = hi;
        }
        return result;
    }

    private void clearInputState() {
        keys.clear();
    }

    private boolean onExitArea = false;

    private void checkExitArea() {
        onExitArea = false;
        if (startRect != null) {
            onExitArea = heroView.getBoundsInParent().intersects(startRect.getBoundsInParent());
            startRect.setFill(onExitArea
                    ? Color.rgb(255, 120, 0, 0.42)
                    : Color.rgb(0, 120, 255, 0.28));
        }
    }

    // Para los Dialogos
    private void addVillagerToList() {
        double x;
        double y;
        x = 480.6044819999995;
        y = 564.4142460000003;
        addNpc(game.getCharacters().get(26), 463.021721999999, 547.2334619999993);// Morty 1
        obstacles.add(new JVInn.Obstacle(
                new Rectangle2D(x, y, 24, 24),
                JVInn.ObstacleType.NPC,
                "Morty"
        ));
        addNpc(game.getCharacters().get(28), 860.430275999998939, 226.9060919999997);// Cat 1
        x = 874.659689999999;
        y = 250.58789999999973;
        obstacles.add(new JVInn.Obstacle(
                new Rectangle2D(x, y, 24, 24),
                JVInn.ObstacleType.NPC,
                "Cat1"
        ));
        addNpc(game.getCharacters().get(31), 141.29985599999966, 141.5015559999997);// Maya
        x = 144.11312999999961;
        y = 150.28780599999916;
        obstacles.add(new JVInn.Obstacle(
                new Rectangle2D(x, y, 24, 24),
                JVInn.ObstacleType.NPC,
                "Maya"
        ));
        addNpc(game.getCharacters().get(33), 1328.3121959999992, 691.752466);// Dog
        x = 1339.178634;
        y = 688.9790080000007;
        obstacles.add(new JVInn.Obstacle(
                new Rectangle2D(x, y, 24, 24),
                JVInn.ObstacleType.NPC,
                "Dog"
        ));
        addNpc(game.getCharacters().get(34), 691.6607640000009, 70.4151640000008);// Mural

    }

    public void addNpc(NPC npc, double x, double y) {
        boolean shouldAdd = npc != null;

        ImageView iv = null;
        Rectangle2D rect = null;

        if (shouldAdd) {

            npcs.add(npc);
            iv = new ImageView(npc.getSpritePath());
            iv.setPreserveRatio(true);
            iv.setFitWidth(60);
            iv.setFitHeight(60);
            iv.setMouseTransparent(true);
            iv.setLayoutX(x);
            iv.setLayoutY(y);

            rect = new Rectangle2D(x, y, iv.getFitWidth(), iv.getFitHeight());

            npcNodes.add(iv);
            npcCollisionRects.add(rect);
        }

        if (shouldAdd) {
            ImageView finalIv = iv;
            Platform.runLater(() -> {
                if (finalIv != null && !world.getChildren().contains(finalIv)) {
                    world.getChildren().add(finalIv);
                }
                if (finalIv != null) {
                    finalIv.toFront();
                }
                if (heroView != null) {
                    heroView.toFront();
                }
            });
        }
    }

    public Villager findNearbyVillager() {
        Villager found = null;
        Rectangle2D heroRect = new Rectangle2D(heroView.getLayoutX(), heroView.getLayoutY(), HERO_W, HERO_H);
        for (int i = 0; i < npcCollisionRects.size(); i++) {
            Rectangle2D nr = npcCollisionRects.get(i);
            boolean intersects = heroRect.intersects(nr);
            if (intersects) {
                if (i >= 0 && i < npcs.size() && npcs.get(i) instanceof Villager) {
                    found = (Villager) npcs.get(i);
                }
            }

        }

        return found;
    }

    public void renderNpcs() {
        Platform.runLater(() -> {
            // eliminar nodos antiguos si world fue limpiado
            world.getChildren().removeIf(n -> npcNodes.contains(n));
            for (int i = 0; i < npcNodes.size(); i++) {
                ImageView iv = npcNodes.get(i);
                Rectangle2D r = npcCollisionRects.get(i);
                iv.setLayoutX(r.getMinX());
                iv.setLayoutY(r.getMinY());
                if (!world.getChildren().contains(iv)) {
                    world.getChildren().add(iv);
                }
                iv.toFront();
            }
            heroView.toFront();
        });
    }

    private void showBottomDialogRPG(String title, String message, String iconResourcePath) {
        Platform.runLater(() -> {
            boolean foundExisting = false;
            StackPane existingOverlay = null;
            Button existingOkBtn = null;

            for (Node child : root.getChildren()) {
                Object flag = child.getProperties().get("rpgDialog");
                if (Boolean.TRUE.equals(flag) && child instanceof StackPane) {
                    existingOverlay = (StackPane) child;
                    Node db = existingOverlay.getChildren().isEmpty() ? null : existingOverlay.getChildren().get(0);
                    if (db instanceof HBox) {
                        HBox dialogBox = (HBox) db;
                        for (Node n : dialogBox.getChildren()) {
                            if (n instanceof VBox) {
                                VBox texts = (VBox) n;
                                if (texts.getChildren().size() >= 2 && texts.getChildren().get(1) instanceof Text) {
                                    Text tMsg = (Text) texts.getChildren().get(1);
                                    tMsg.setText(message);
                                }
                                if (texts.getChildren().size() >= 1 && texts.getChildren().get(0) instanceof Text) {
                                    Text tTitle = (Text) texts.getChildren().get(0);
                                    tTitle.setText(title);
                                }
                            }
                            if (n instanceof Button) {
                                existingOkBtn = (Button) n;
                            }
                        }
                    }
                    foundExisting = true;
                }
            }

            if (foundExisting && existingOverlay != null) {
                StackPane overlayRef = existingOverlay;
                Button okRef = existingOkBtn;
                Platform.runLater(() -> {
                    overlayRef.requestFocus();
                    if (okRef != null) {
                        okRef.requestFocus();
                    }
                });
            } else {
                stopMover();
                root.getProperties().put("dialogOpen", true);

                StackPane modalOverlay = new StackPane();
                modalOverlay.getProperties().put("rpgDialog", true);
                modalOverlay.setPrefSize(VIEW_W, VIEW_H);
                modalOverlay.setStyle("-fx-background-color: transparent;");
                modalOverlay.setPickOnBounds(true);
                modalOverlay.setFocusTraversable(true);

                HBox dialogBox = new HBox(10);
                dialogBox.setMinHeight(72);
                dialogBox.setMaxHeight(140);
                dialogBox.setMaxWidth(420);
                dialogBox.setPrefWidth(420);
                dialogBox.setStyle(
                        "-fx-background-color: rgba(0,0,0,0.88);"
                        + "-fx-padding: 10 12 10 12;"
                        + "-fx-background-radius: 6;"
                        + "-fx-border-radius: 6;"
                        + "-fx-border-color: rgba(255,255,255,0.06);"
                        + "-fx-border-width: 1;"
                );
                dialogBox.setEffect(new DropShadow(6, Color.rgb(0, 0, 0, 0.7)));
                dialogBox.setAlignment(Pos.CENTER_LEFT);

                ImageView iconView = null;
                if (iconResourcePath != null) {
                    try {
                        Image icon = new Image(getClass().getResourceAsStream(iconResourcePath));
                        iconView = new ImageView(icon);
                        iconView.setFitWidth(44);
                        iconView.setFitHeight(44);
                        iconView.setPreserveRatio(true);
                    } catch (Throwable ignored) {
                        iconView = null;
                    }
                }

                VBox texts = new VBox(3);
                Text tTitle = new Text(title);
                tTitle.setStyle("-fx-font-size: 13px; -fx-fill: #f5f5f5; -fx-font-weight: 700;");
                Text tMsg = new Text(message);
                tMsg.setWrappingWidth(420 - 140);
                tMsg.setStyle("-fx-font-size: 12px; -fx-fill: #e6e6e6;");
                texts.getChildren().addAll(tTitle, tMsg);

                Button okBtn = new Button("Ok");
                okBtn.setDefaultButton(true);
                okBtn.setStyle(
                        "-fx-background-color: linear-gradient(#444444, #222222);"
                        + "-fx-text-fill: #ffffff;"
                        + "-fx-font-weight: 600;"
                        + "-fx-background-radius: 6;"
                        + "-fx-padding: 6 10 6 10;"
                );
                okBtn.setOnAction(e -> fadeOutAndRemove(modalOverlay));

                if (iconView != null) {
                    dialogBox.getChildren().addAll(iconView, texts, okBtn);
                } else {
                    dialogBox.getChildren().addAll(texts, okBtn);
                }

                StackPane.setAlignment(dialogBox, Pos.BOTTOM_CENTER);
                StackPane.setMargin(dialogBox, new Insets(0, 20, 12, 20));
                modalOverlay.getChildren().add(dialogBox);

                root.getChildren().add(modalOverlay);

                modalOverlay.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, ev -> {
                    Bounds b = dialogBox.localToScene(dialogBox.getBoundsInLocal());
                    if (!b.contains(ev.getSceneX(), ev.getSceneY())) {
                        ev.consume();
                    }
                });

                TranslateTransition tt = new TranslateTransition(Duration.millis(220), dialogBox);
                tt.setFromY(28);
                tt.setToY(0);
                tt.play();

                FadeTransition ftIn = new FadeTransition(Duration.millis(160), dialogBox);
                ftIn.setFromValue(0.0);
                ftIn.setToValue(1.0);
                ftIn.play();

                Platform.runLater(() -> {
                    modalOverlay.requestFocus();
                    okBtn.requestFocus();
                });

                Platform.runLater(() -> {
                    javafx.scene.Scene scene = root.getScene();
                    if (scene != null) {
                        javafx.event.EventHandler<KeyEvent> sceneHandler = ev -> {
                            if (Boolean.TRUE.equals(root.getProperties().get("dialogOpen"))) {
                                if (ev.getCode() == KeyCode.ENTER || ev.getCode() == KeyCode.ESCAPE) {
                                    ev.consume();
                                    Platform.runLater(() -> {
                                        try {
                                            okBtn.fire();
                                        } catch (Throwable ignored) {
                                        }
                                    });
                                } else {
                                    ev.consume();
                                }
                            }
                        };
                        modalOverlay.getProperties().put("sceneKeyHandler", sceneHandler);
                        scene.addEventFilter(KeyEvent.KEY_PRESSED, sceneHandler);
                    }
                });

                modalOverlay.getProperties().put("onRemoved", (Runnable) () -> {
                    startMover();
                    root.getProperties().put("dialogOpen", false);
                });
            }
        });
    }

    private void fadeOutAndRemove(StackPane modalOverlay) {
        final Runnable[] resumeArr = new Runnable[1];
        if (modalOverlay != null) {
            Node dialogBox = modalOverlay.getChildren().isEmpty() ? null : modalOverlay.getChildren().get(0);
            try {
                Object o = modalOverlay.getProperties().get("onRemoved");
                if (o instanceof Runnable) {
                    resumeArr[0] = (Runnable) o;
                }
            } catch (Throwable ignored) {
            }

            try {
                Object handlerObj = modalOverlay.getProperties().remove("sceneKeyHandler");
                if (handlerObj instanceof javafx.event.EventHandler) {
                    javafx.scene.Scene scene = root.getScene();
                    if (scene != null) {
                        @SuppressWarnings("unchecked")
                        javafx.event.EventHandler<KeyEvent> h = (javafx.event.EventHandler<KeyEvent>) handlerObj;
                        scene.removeEventFilter(KeyEvent.KEY_PRESSED, h);
                    }
                }
            } catch (Throwable ignored) {
            }

            if (dialogBox != null) {
                FadeTransition ftOut = new FadeTransition(Duration.millis(140), dialogBox);
                ftOut.setFromValue(1.0);
                ftOut.setToValue(0.0);
                ftOut.setOnFinished(ev -> {
                    root.getChildren().remove(modalOverlay);
                    if (resumeArr[0] != null) {
                        resumeArr[0].run();
                    }
                });
                ftOut.play();
            } else {
                root.getChildren().remove(modalOverlay);
                if (resumeArr[0] != null) {
                    resumeArr[0].run();
                }
            }
        } else {
            try {
                Object o = root.getProperties().get("onRemovedFallback");
                if (o instanceof Runnable) {
                    ((Runnable) o).run();
                }
            } catch (Throwable ignored) {
            }
        }
    }

}
