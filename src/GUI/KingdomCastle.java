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
import java.util.Random;
import java.util.Set;
import javafx.animation.TranslateTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class KingdomCastle {

    private final StackPane root;
    private final Pane world;
    private final StackPane loadingOverlay;
    private ImageView backgroundView;
    private MediaPlayer music;

    private final ImageView heroView;
    private Random rnd;
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

    private final List<Rectangle> transitionRects = new ArrayList<>();

    private Runnable onExitCallback;
    private final Game game;

    private boolean entranceCastle = false;
    private boolean entrance2floor = false;

    // Sistema de colisiones
    private final List<Obstacle> obstacles = new ArrayList<>();
    private boolean debugEnabled = true;

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
        CASTLE, TREE, FENCE, BUSH, BLOCK, PLANT, DECORATION, NPC
    }

    // Clase interna para obstáculos
    private static class Obstacle {

        final Rectangle2D collisionRect;
        final KingdomCastle.ObstacleType type;
        final String id;

        Obstacle(Rectangle2D collision, KingdomCastle.ObstacleType type, String id) {
            this.collisionRect = collision;
            this.type = type;
            this.id = id;
        }
    }

    public KingdomCastle(Game game) {
        this.game = game;
        this.rnd = new Random();
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

            boolean imageOk = loadBackgroundImage("/Resources/textures/KingdomCastle/Castle exterior.png");
            boolean musicOk = startVillageMusic("/Resources/music/Castle.mp3");

            populateKingdomCastleObstacles();

            // Cargar NPCs para el exterior
            addNpcsForExterior();
            renderNpcs();

            // Luego posicionar al héroe
            positionHeroAtEntrance();
            createStartRectAtHeroStart();
            createTransitionRects();
            setHeroPosition(480.87223200000005, 768.0);

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

        Text label = new Text("Loading Map..");
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
        boolean loaded = false;
        try {
            java.io.InputStream is = getClass().getResourceAsStream(path);
            if (is != null) {
                Image img = new Image(is);
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
                loaded = true;
            } else {
                Text err = new Text("No se encontró la imagen de la aldea: " + path);
                err.setStyle("-fx-font-size: 16px; -fx-fill: #ffdddd;");
                root.getChildren().add(err);
            }
        } catch (Throwable t) {
            Text err = new Text("No se pudo cargar la imagen de la aldea.");
            err.setStyle("-fx-font-size: 16px; -fx-fill: #ffdddd;");
            root.getChildren().add(err);
        }
        return loaded;
    }

    private boolean startVillageMusic(String path) {
        boolean started = false;
        try {
            URL res = getClass().getResource(path);
            if (res != null) {
                Media media = new Media(res.toExternalForm());
                stopVillageMusic();
                music = new MediaPlayer(media);
                music.setCycleCount(MediaPlayer.INDEFINITE);
                music.setVolume(MainScreen.getVolumeSetting());
                music.play();
                started = true;
            } else {
                System.err.println("No se encontró el recurso de música: " + path);
            }
        } catch (Throwable t) {
            System.err.println("Error al iniciar música: " + t.getMessage());
            started = false;
        }
        return started;
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

    // ---------------- metodos de colisiones  ----------------
    private void populateKingdomCastleObstacles() {
        obstacles.clear();
        obstacles.add(new Obstacle(
                new Rectangle2D(200, 0, 750, 550),
                ObstacleType.BLOCK,
                "castillo"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(100, 50, 80, 560),
                ObstacleType.BLOCK,
                "castillo1"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(55, 340, 70, 270),
                ObstacleType.BLOCK,
                "castillo2"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(830, 550, 115, 60),
                ObstacleType.BLOCK,
                "castillo3"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(395, 550, 30, 60),
                ObstacleType.BLOCK,
                "estatua"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(587, 550, 30, 60),
                ObstacleType.BLOCK,
                "estatua1"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(1053.6648806200026, 426.5433549000001, 40, 40),
                ObstacleType.BUSH,
                "arbusto1"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(1102.773074620003, 374.7810249000001, 40, 40),
                ObstacleType.BUSH,
                "arbusto2"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(1010.4206006200029, 380.6170389000001, 40, 40),
                ObstacleType.BUSH,
                "arbusto3"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(961.390652620003, 423.83383290000006, 40, 40),
                ObstacleType.BUSH,
                "arbusto4"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(1152.0, 423.83383290000006, 30, 30),
                ObstacleType.BUSH,
                "agua1"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(1152.0, 465.4908299999995, 30, 30),
                ObstacleType.BUSH,
                "agua2"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(1152.0, 508.65202199999953, 30, 30),
                ObstacleType.BUSH,
                "agua3"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(1152.0, 554.8717919999995, 30, 30),
                ObstacleType.BUSH,
                "agua4"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(1152.0, 592.3379099999994, 30, 30),
                ObstacleType.BUSH,
                "agua5"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(1152.0, 629.8739579999992, 30, 30),
                ObstacleType.BUSH,
                "agua6"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(1152.0, 661.5840359999993, 30, 30),
                ObstacleType.BUSH,
                "agua7"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(1152.0, 693.2625059999992, 30, 30),
                ObstacleType.BUSH,
                "agua8"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(1152.0, 719.0955299999993, 30, 30),
                ObstacleType.BUSH,
                "agua9"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(1152.0, 792.0, 30, 30),
                ObstacleType.BUSH,
                "agua10"
        ));

    }

    private void colissionInSide() {
        obstacles.add(new Obstacle(
                new Rectangle2D(0, 535, 390, 70),
                ObstacleType.BLOCK,
                "muro"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(485, 535, 390, 70),
                ObstacleType.BLOCK,
                "muro1"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(0.0, 335, 30, 140),
                ObstacleType.BLOCK,
                "columna"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(55, 385, 30, 70),
                ObstacleType.BLOCK,
                "estatua"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(445, 450, 20, 10),
                ObstacleType.BLOCK,
                "banco"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(590, 450, 20, 10),
                ObstacleType.BLOCK,
                "banco1"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(490, 430, 70, 30),
                ObstacleType.BLOCK,
                "mesa"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(370, 350, 310, 60),
                ObstacleType.BLOCK,
                "sofa"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(240, 0, 90, 70),
                ObstacleType.BLOCK,
                "reloj"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(345, 0, 20, 70),
                ObstacleType.BLOCK,
                "pintura"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(390, 0, 50, 70),
                ObstacleType.BLOCK,
                "espejo"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(440, 0, 230, 90),
                ObstacleType.BLOCK,
                "muebles"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(690, 0, 150, 70),
                ObstacleType.BLOCK,
                "espejo1"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(0, 0, 45, 70),
                ObstacleType.BLOCK,
                "cortina"
        ));

    }

    private void colisicions2Floor() {

        obstacles.clear();
        obstacles.add(new Obstacle(
                new Rectangle2D(150, 535, 700, 70),
                ObstacleType.BLOCK,
                "muro"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(0, 535, 45, 70),
                ObstacleType.BLOCK,
                "cortina"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(0, 190, 40, 180),
                ObstacleType.BLOCK,
                "columna"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(55, 295, 30, 70),
                ObstacleType.BLOCK,
                "estatua"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(730, 190, 40, 180),
                ObstacleType.BLOCK,
                "columna1"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(680, 295, 30, 70),
                ObstacleType.BLOCK,
                "estatua1"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(0, 0, 900, 70),
                ObstacleType.BLOCK,
                "pared"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(160, 250, 30, 30),
                ObstacleType.BLOCK,
                "bloque"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(210, 200, 20, 70),
                ObstacleType.BLOCK,
                "espada"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(590, 200, 20, 70),
                ObstacleType.BLOCK,
                "espada1"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(630, 250, 30, 30),
                ObstacleType.BLOCK,
                "bloque1"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(300, 90, 75, 30),
                ObstacleType.BLOCK,
                "silla"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(440, 90, 70, 30),
                ObstacleType.BLOCK,
                "silla1"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(800, 95, 30, 800),
                ObstacleType.BLOCK,
                "columna2"
        ));
        obstacles.add(new Obstacle(
                new Rectangle2D(400, 110, 30, 40),
                ObstacleType.NPC,
                "Mayor"
        ));
    }

    // ---------------- movimiento , y entradas ----------------
    private void positionHeroAtEntrance() {
        double startX = (worldW - HERO_W) / 2.0;
        double startY = worldH - HERO_H - 8.0;

        startX = clamp(startX, 0, Math.max(0, worldW - HERO_W));
        startY = clamp(startY, 0, Math.max(0, worldH - HERO_H));

        Rectangle2D heroRect = new Rectangle2D(startX, startY, HERO_W, HERO_H);
        for (KingdomCastle.Obstacle ob : obstacles) {
            if (heroRect.intersects(ob.collisionRect)) {
                startY = ob.collisionRect.getMinY() - HERO_H - 5;
                break;
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
        double rx = 495.32676984;
        double ry = 768.0;
        double rw = 30;
        double rh = 30;

        startRect = new Rectangle(rx, ry, rw, rh);
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

            if (k == KeyCode.R) {
                debugEnabled = !debugEnabled;
                if (debugEnabled) {
                    drawDebugObstacles();
                } else {
                    world.getChildren().removeIf(n -> "obstacle_debug".equals(n.getProperties().get("tag")));
                }
            }

            if (k == KeyCode.I || k == KeyCode.ADD || k == KeyCode.PLUS) {
                clearInputState();
                openInventory();
            }

            if (k == KeyCode.ENTER) {
                String foundTag = null;

                // 1. Primero verificar si está en el área de salida
                if (onStartRect) {
                    clearInputState();
                    try {
                        if (game != null && game.getHero() != null) {
                            Hero h = game.getHero();
                            h.setLastLocation(Hero.Location.KINGDOM_CASTLE);
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
                } else {
                    Villager v = findNearbyVillager();
                    if (v != null) {
                        try {
                            String name = v.getName();
                            String message = null;
                            if ("Maya".equals(name)) {
                                int randomIndex = rnd.nextInt(3);
                                message = v.getMessageFromList(randomIndex);
                            } else if ("Mayor".equals(name)) {
                                message = v.getMessageFromList(0);
                            } else {
                                // Otros NPCs: primer mensaje
                                message = v.getMessageFromList(0);
                            }

                            // Mostrar el diálogo
                            showBottomDialogRPG(name, message, null);

                        } catch (Exception e) {
                            System.err.println("Error en diálogo: " + e.getMessage());
                            showBottomDialogRPG("Error", "No se pudo mostrar el diálogo", null);
                        }
                    } else {
                        boolean tagFound = false;

                        for (Rectangle r : transitionRects) {
                            if (!tagFound && heroView.getBoundsInParent().intersects(r.getBoundsInParent())) {
                                foundTag = (String) r.getProperties().get("tag");
                                tagFound = true;
                            }
                        }

                        if (foundTag != null) {
                            if ("Castle_entrance".equals(foundTag)) {
                                entranceCastle = true;
                                intoCastle(entranceCastle);
                            } else if ("Castle_exit".equals(foundTag)) {
                                exitCastle();
                            } else if ("floor2_entrance".equals(foundTag)) {
                                entrance2floor = true;
                                floor2Into(entrance2floor);
                            } else if ("floor2_exit".equals(foundTag)) {
                                entranceCastle = false;
                                intoCastle(entranceCastle);
                            }
                        }
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

        try {
            if (music != null) {
                music.pause();
            }
        } catch (Throwable ignored) {
        }

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

        KingdomCastle.Direction newDir = (vx != 0 || vy != 0)
                ? directionFromVector(vx, vy)
                : KingdomCastle.Direction.NONE;
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

        for (KingdomCastle.Obstacle ob : obstacles) {
            if (!collision && heroRect.intersects(ob.collisionRect)) {
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

            for (KingdomCastle.Obstacle ob : obstacles) {
                if (heroRectX.intersects(ob.collisionRect)) {
                    canMoveX = false;
                }
                if (heroRectY.intersects(ob.collisionRect)) {
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

        checkStartIntersection();
        updateCamera();
    }

    private KingdomCastle.Direction directionFromVector(double vx, double vy) {
        KingdomCastle.Direction result = KingdomCastle.Direction.NONE;

        if (!(vx == 0 && vy == 0)) {
            double angle = Math.toDegrees(Math.atan2(-vy, vx));
            if (angle < 0) {
                angle += 360.0;
            }

            if (angle >= 337.5 || angle < 22.5) {
                result = KingdomCastle.Direction.E;
            } else if (angle < 67.5) {
                result = KingdomCastle.Direction.NE;
            } else if (angle < 112.5) {
                result = KingdomCastle.Direction.N;
            } else if (angle < 157.5) {
                result = KingdomCastle.Direction.NW;
            } else if (angle < 202.5) {
                result = KingdomCastle.Direction.W;
            } else if (angle < 247.5) {
                result = KingdomCastle.Direction.SW;
            } else if (angle < 292.5) {
                result = KingdomCastle.Direction.S;
            } else if (angle < 337.5) {
                result = KingdomCastle.Direction.SE;
            }
        }

        return result;
    }

    private void setDirectionIfChanged(KingdomCastle.Direction newDir) {
        if (newDir == null) {
            newDir = KingdomCastle.Direction.NONE;
        }
        currentDirection = newDir;
    }

    public KingdomCastle.Direction getHeroDirection() {
        return currentDirection;
    }

    private void checkStartIntersection() {
        boolean intersects = false;

        if (startRect != null) {
            intersects = heroView.getBoundsInParent().intersects(startRect.getBoundsInParent());
            startRect.setFill(intersects
                    ? Color.rgb(0, 120, 255, 0.42)
                    : Color.rgb(0, 120, 255, 0.28));
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

    private void intoCastle(boolean entranceCastle) {
        transitionRects.clear();
        obstacles.clear();
        clearNpcs(); // Limpiar NPCs anteriores
        colissionInSide();
        startRect = null;

        loadBackgroundImage("/Resources/textures/KingdomCastle/castleInterior.png");

        stopVillageMusic();

        startVillageMusic("/Resources/music/interiorOST.mp3");
        createTransitionRects();

        // Restaurar NPCs del interior
        // addNpcsForInterior();
        // renderNpcs();
        if (entranceCastle) {
            setHeroPosition(420, 600.0);
        } else {
            setHeroPosition(105.72477324000008, 0.0);;
        }
    }

    private void exitCastle() {
        transitionRects.clear();
        obstacles.clear();
        populateKingdomCastleObstacles();
        createStartRectAtHeroStart();

        loadBackgroundImage("/Resources/textures/KingdomCastle/Castle exterior.png");

        stopVillageMusic();

        startVillageMusic("/Resources/music/Castle.mp3");
        createTransitionRects();

        // Restaurar NPCs del exterior
        addNpcsForExterior();
        renderNpcs();

        setHeroPosition(483.5743526200002, 552.9294368999995);

    }

    private void floor2Into(boolean entrance2floor) {
        transitionRects.clear();
        obstacles.clear();
        clearNpcs(); // Limpiar NPCs anteriores
        colisicions2Floor();
        String rect = "Castle_exit";
        int pos;

        loadBackgroundImage("/Resources/textures/KingdomCastle/throne.png");
        createTransitionRects();

        // Agregar NPCs para la segunda planta
        addNpcsForSecondFloor();
        renderNpcs();

        pos = PostInArray(rect);
        transitionRects.remove(pos);

        setHeroPosition(71.67917739999993, 576.0);

    }

    private void createTransitionRects() {
        transitionRects.clear();

        // Entrada a la casa desde el exterior
        Rectangle CastleEntrance = new Rectangle(504.69517342000023, 574.0562225399997, 50, 50);
        CastleEntrance.getProperties().put("tag", "Castle_entrance");
        transitionRects.add(CastleEntrance);

        // Salida de la casa hacia el exterior
        Rectangle CastleExit = new Rectangle(414, 600.0, 50, 50);
        CastleExit.getProperties().put("tag", "Castle_exit");
        transitionRects.add(CastleExit);

        // Entrada al segundo piso
        Rectangle floor2Entrance = new Rectangle(69.21376752000009, 0.0, 300, 50);
        floor2Entrance.getProperties().put("tag", "floor2_entrance");
        transitionRects.add(floor2Entrance);

        // Salida del segundo piso hacia la planta baja
        Rectangle floor2Exit = new Rectangle(66.26601953999977, 576.0, 50, 50);
        floor2Exit.getProperties().put("tag", "floor2_exit");
        transitionRects.add(floor2Exit);

        for (Rectangle r : transitionRects) {
            r.setFill(Color.color(0, 0, 0, 0.0));
            r.setStroke(null);
            r.setMouseTransparent(true);
            world.getChildren().add(r);
            r.toBack();
        }
        heroView.toFront();
    }

    private int PostInArray(String rect) {
        int pos = -1;
        int i = 0;
        boolean found = false;
        while (!found && i < transitionRects.size()) {
            Rectangle r = transitionRects.get(i);
            String tagFound = (String) r.getProperties().get("tag");
            if (tagFound.equals(rect)) {
                found = true;
                pos = i;
            }
            i++;
        }
        return pos;
    }

    private void drawDebugObstacles() {
        world.getChildren().removeIf(n -> "obstacle_debug".equals(n.getProperties().get("tag")));

        for (KingdomCastle.Obstacle ob : obstacles) {
            Rectangle2D r = ob.collisionRect;
            Rectangle debug = new Rectangle(r.getMinX(), r.getMinY(), r.getWidth(), r.getHeight());
            debug.setFill(Color.color(1, 0, 0, 0.25));      // rojo semitransparente
            debug.setStroke(Color.color(1, 0, 0, 0.9));
            debug.setMouseTransparent(true);
            debug.getProperties().put("tag", "obstacle_debug");
            debug.getProperties().put("id", ob.id);
            world.getChildren().add(debug);
        }
        heroView.toFront();
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

    private void addNpcsForExterior() {
        addNpc(game.getCharacters().get(31), 301.3052878000003, 660.4730311199996);
        double x = 308.1560760000001;
        double y = 667.2754919999998;
        obstacles.add(new Obstacle(
                new Rectangle2D(x, y, 24, 24),
                ObstacleType.NPC,
                "Maya"
        ));
    }

    /*private void addNpcsForInterior() {
  
    addNpc(game.getCharacters().get(31), 365.72677572000015, 121.91383662);
}*/
    private void addNpcsForSecondFloor() {
        // Mayor en la segunda planta 
        addNpc(game.getCharacters().get(29), 400, 110);
    }

    private void clearNpcs() {
        Platform.runLater(() -> {
            // Remover visualmente los NPCs
            for (ImageView iv : npcNodes) {
                world.getChildren().remove(iv);
            }
        });

        // Limpiar las listas
        npcs.clear();
        npcNodes.clear();
        npcCollisionRects.clear();
    }

    // Para los Dialogos
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
