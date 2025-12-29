package GUI;

import Runner.MainScreen;
import Characters.Hero;
import GUI.Swamp;
import Logic.Game;
import Misc.Classes;
import Tree.InBreadthIterator;
import com.almasb.fxgl.dsl.FXGL;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.transform.Scale;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class GameMapScreen {

    public final StackPane root;
    private final Pane container;
    private final ImageView mapView;
    public final ImageView heroView;
    private final Scale containerScale;

    private double lastMouseX, lastMouseY;
    private boolean draggingMap = false;
    private boolean up, down, left, right;
    private final AnimationTimer mover;
    private double vx = 0, vy = 0;
    private final double SPEED = 180.0;

    private final double mapW;
    private final double mapH;

    private MediaPlayer mapMusic;

    private final List<Obstacle> obstacles = new ArrayList<>();
    private Obstacle currentInteractable = null;

    private final Game game;

    // Ruta configurable para la música de combate
    private String combatMusicPath = "/Resources/music/fieldBattle.mp3";

    // Inventario
    private InventoryScreen inventory;

    public enum Direction {
        NONE, N, NE, E, SE, S, SW, W, NW
    }
    private Direction currentDirection = Direction.NONE;

    private static final double VILLAGE_COLLISION_SCALE = 0.22;

    // Offset específico para FIELD_VILLAGE
    private static final double FV_COLLISION_OFFSET_Y = -18.0;
    private static final Double FV_COLLISION_W = null;
    private static final Double FV_COLLISION_H = null;

    private enum ObstacleType {
        VILLAGE, BLOCK
    }

    private static class Obstacle {

        final Rectangle2D visualRect;
        final Rectangle2D collisionRect;
        final ObstacleType type;
        final String id;

        Obstacle(Rectangle2D visual, Rectangle2D collision, ObstacleType type, String id) {
            this.visualRect = visual;
            this.collisionRect = collision;
            this.type = type;
            this.id = id;
        }
    }

    // Indica si la posición del héroe ya fue inicializada desde fuera (MainScreen)
    private boolean heroPositionInitialized = false;

    // Flag para mostrar/ocultar rectángulos de colisión (debug)
    private boolean debugEnabled = false;

    private final Random rnd = new Random();

    public GameMapScreen(Game game) {
        this.game = game;
        Hero hero = game.getHero();

        Image mapImg;
        try {
            mapImg = new Image(getClass().getResourceAsStream("/Resources/textures/Main/map.png"));
        } catch (Throwable t) {
            mapImg = null;
        }

        if (mapImg == null) {
            mapW = 800;
            mapH = 600;
        } else {
            mapW = mapImg.getWidth() > 0 ? mapImg.getWidth() : 800;
            mapH = mapImg.getHeight() > 0 ? mapImg.getHeight() : 600;
        }

        mapView = new ImageView(mapImg);
        mapView.setPreserveRatio(false);
        mapView.setSmooth(true);
        mapView.setFitWidth(mapW);
        mapView.setFitHeight(mapH);

        container = new Pane();
        container.setPrefSize(mapW, mapH);
        container.getChildren().add(mapView);

        heroView = createHeroView(hero);
        container.getChildren().add(heroView);

        containerScale = new Scale(1.0, 1.0, 0, 0);
        container.getTransforms().add(containerScale);

        root = new StackPane();
        root.setPrefSize(800, 600);
        root.getChildren().add(container);

        root.addEventFilter(MouseEvent.ANY, MouseEvent::consume);

        populateVillagesFromList();
        populateExtraBlocks();

        installControls();
        installEscHandler();

        mover = new AnimationTimer() {
            private long last = -1;

            @Override
            public void handle(long now) {
                if (last < 0) {
                    last = now;
                }
                double dt = (now - last) / 1e9;
                last = now;

                if (root.getScene() == null || !root.isFocused()) {
                    clearInputState();
                    return;
                }

                updateVelocity();
                boolean moving = (vx != 0 || vy != 0);
                if (moving) {
                    double dx = vx * dt;
                    double dy = vy * dt;
                    moveHeroWithCollision(dx, dy);
                }
            }
        };

        // Si la escena se asigna, posicionamos el héroe solo si no se ha inicializado externamente
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            boolean sceneReady = newScene != null;
            if (sceneReady) {
                Platform.runLater(() -> {
                    if (!heroPositionInitialized) {
                        positionHeroCenter();
                    }
                    root.requestFocus();
                    mover.start();
                });
            } else {
                mover.stop();
                clearInputState();
            }
        });

        // Limpia inputs al perder foco
        root.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                clearInputState();
            }
        });
    }

    public void setCombatMusicPath(String path) {
        if (path != null && !path.isBlank()) {
            this.combatMusicPath = path;
        }
    }

    private ImageView createHeroView(Hero hero) {
        Image img = null;
        if (hero != null) {
            try {
                img = hero.getImage();
            } catch (Throwable ignored) {
            }
        }
        if (img == null) {
            try {
                img = new Image(getClass().getResourceAsStream("/Resources/sprites/hero.png"));
            } catch (Throwable ignored) {
            }
        }
        ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true);
        iv.setFitWidth(48);
        iv.setFitHeight(48);
        iv.setMouseTransparent(true);
        return iv;
    }

    private void positionHeroCenter() {
        double hw = heroView.getBoundsInLocal().getWidth();
        double hh = heroView.getBoundsInLocal().getHeight();
        heroView.setLayoutX((mapW - hw) / 2.0);
        heroView.setLayoutY((mapH - hh) / 2.0);
        heroPositionInitialized = true;
    }

    private void installControls() {
        root.addEventFilter(ScrollEvent.SCROLL, ev -> {
            boolean ctrl = ev.isControlDown();
            if (ctrl) {
                double delta = ev.getDeltaY() > 0 ? 1.1 : 0.9;
                double newScale = clamp(containerScale.getX() * delta, 0.4, 3.5);
                containerScale.setX(newScale);
                containerScale.setY(newScale);
            }
            ev.consume();
        });

        mapView.setOnMousePressed(e -> {
            boolean primary = e.getButton() == MouseButton.PRIMARY;
            if (primary) {
                lastMouseX = e.getSceneX();
                lastMouseY = e.getSceneY();
                draggingMap = true;
                root.setCursor(Cursor.CLOSED_HAND);
            }
            e.consume();
        });

        mapView.setOnMouseDragged(e -> {
            if (draggingMap) {
                double dx = e.getSceneX() - lastMouseX;
                double dy = e.getSceneY() - lastMouseY;
                container.setTranslateX(container.getTranslateX() + dx);
                container.setTranslateY(container.getTranslateY() + dy);
                lastMouseX = e.getSceneX();
                lastMouseY = e.getSceneY();
            }
            e.consume();
        });

        mapView.setOnMouseReleased(e -> {
            draggingMap = false;
            root.setCursor(Cursor.DEFAULT);
            e.consume();
        });

        mapView.setOnMouseClicked(e -> {
            Point2D mapPoint = sceneToMap(e.getSceneX(), e.getSceneY());
            System.out.println("Click map coords: " + mapPoint);
            e.consume();
        });

        root.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            KeyCode k = ev.getCode();
            boolean handled = false;

            if (k == KeyCode.ENTER) {
                handled = true;
                boolean readyToEnter = currentInteractable != null && currentInteractable.type == ObstacleType.VILLAGE;
                if (readyToEnter) {
                    clearInputState();
                    enterVillage(currentInteractable);
                }
            } else if (k == KeyCode.P) {
                handled = true;
                System.out.println("Hero map top-left: " + getHeroMapTopLeft());
                System.out.println("Hero map center: " + getHeroMapCenter());
                System.out.println("Hero scene center: " + getHeroSceneCenter());
                System.out.println("Hero direction: " + getHeroDirection().name());
            } else if (k == KeyCode.B) {
                // Debug: abrir pantalla de combate
                handled = true;
                clearInputState();
                openDebugCombat();
            } else if (k == KeyCode.I || k == KeyCode.ADD || k == KeyCode.PLUS) {
                handled = true;
                clearInputState();
                openInventory();
            } else if (k == KeyCode.W || k == KeyCode.UP) {
                up = true;
            } else if (k == KeyCode.S || k == KeyCode.DOWN) {
                down = true;
            } else if (k == KeyCode.A || k == KeyCode.LEFT) {
                left = true;
            } else if (k == KeyCode.D || k == KeyCode.RIGHT) {
                right = true;
            } else if (k == KeyCode.L) {
                enterDebugSwamp();
            }
            else if (k == KeyCode.Q) {
                InBreadthIterator<Classes> it = game.getHero().getUnlockedClasses().inBreadthIterator();
                while(it.hasNext()){
                    System.out.println(it.nextNode().getInfo().getClass().getSimpleName());
                    
                }
            }

            if (handled) {
                ev.consume();
            }
        });

        root.addEventFilter(KeyEvent.KEY_RELEASED, ev -> {
            KeyCode k = ev.getCode();
            if (k == KeyCode.W || k == KeyCode.UP) {
                up = false;
            }
            if (k == KeyCode.S || k == KeyCode.DOWN) {
                down = false;
            }
            if (k == KeyCode.A || k == KeyCode.LEFT) {
                left = false;
            }
            if (k == KeyCode.D || k == KeyCode.RIGHT) {
                right = false;
            }
        });

        root.setFocusTraversable(true);
    }

    private void openInventory() {
        mover.stop();

        InventoryScreen inventory = new InventoryScreen(game, this);

        inventory.setOnClose(() -> {
            Platform.runLater(() -> {
                try {
                    FXGL.getGameScene().removeUINode(inventory.getRoot());
                } catch (Throwable ignored) {
                }

                mover.start();
                root.requestFocus();
            });
        });

        // Mostrar el inventario
        inventory.show();
    }

    private void installEscHandler() {
        root.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                ev.consume();
                confirmReturnToMenu();
            }
        });
    }

    private void confirmReturnToMenu() {
        clearInputState();

        Alert dlg = new Alert(Alert.AlertType.CONFIRMATION);
        dlg.setTitle("Volver al menú");
        dlg.setHeaderText("¿Quieres volver al menú principal?");
        dlg.setContentText("Si vuelves al menú, la partida seguirá guardada en disco.");

        try {
            if (root.getScene() != null && root.getScene().getWindow() != null) {
                dlg.initOwner(root.getScene().getWindow());
            }
        } catch (Throwable ignored) {
        }

        dlg.setOnHidden(ev -> {
            clearInputState();
            Platform.runLater(root::requestFocus);
        });

        Optional<ButtonType> opt = dlg.showAndWait();
        boolean ok = opt.isPresent() && opt.get() == ButtonType.OK;
        if (ok) {
            try {
                if (game != null && game.getHero() != null) {
                    Hero h = game.getHero();
                    h.setLastLocation(Hero.Location.MAP);
                    h.setLastPosX(heroView.getLayoutX());
                    h.setLastPosY(heroView.getLayoutY());
                    try {
                        game.createSaveGame();
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }

            stopMapMusic();
            try {
                FXGL.getGameScene().removeUINode(root);
            } catch (Throwable ignored) {
            }
            MainScreen.restoreMenuAndMusic();
        } else {
            clearInputState();
            Platform.runLater(root::requestFocus);
        }
    }

    private void updateVelocity() {
        vx = 0;
        vy = 0;
        if (left) {
            vx -= SPEED;
        }
        if (right) {
            vx += SPEED;
        }
        if (up) {
            vy -= SPEED;
        }
        if (down) {
            vy += SPEED;
        }
        Direction newDir = (vx != 0 || vy != 0) ? directionFromVector(vx, vy) : Direction.NONE;
        setDirectionIfChanged(newDir);
    }

    private void moveHeroWithCollision(double dx, double dy) {
        currentInteractable = null;

        double hw = heroView.getBoundsInLocal().getWidth();
        double hh = heroView.getBoundsInLocal().getHeight();

        double proposedX = clamp(heroView.getLayoutX() + dx, 0, mapW - hw);
        double proposedY = clamp(heroView.getLayoutY() + dy, 0, mapH - hh);

        Rectangle2D heroRect = new Rectangle2D(proposedX, proposedY, hw, hh);

        boolean blocked = false;
        for (Obstacle ob : obstacles) {
            boolean hit = heroRect.intersects(ob.collisionRect);
            if (hit && ob.type == ObstacleType.VILLAGE) {
                currentInteractable = ob;
            }
            blocked = blocked || hit;
        }

        if (blocked) {
            setDirectionIfChanged(Direction.NONE);
        } else {
            heroView.setLayoutX(proposedX);
            heroView.setLayoutY(proposedY);
        }
    }

    public void show() {
        Platform.runLater(() -> {
            MainScreen.hideMenu();
            startMapMusic();
            FXGL.getGameScene().addUINode(root);
            root.requestFocus();
            if (debugEnabled) {
                drawDebugObstacles();
            }
        });
    }

    public void startMapMusic() {
        try {
            stopMapMusic();
            URL res = getClass().getResource("/Resources/music/gameMapScreen.mp3");
            boolean hasRes = res != null;
            if (hasRes) {
                Media media = new Media(res.toExternalForm());
                mapMusic = new MediaPlayer(media);
                mapMusic.setCycleCount(MediaPlayer.INDEFINITE);
                mapMusic.setVolume(MainScreen.getVolumeSetting());
                mapMusic.play();

                AudioManager.register(mapMusic);
            }
        } catch (Throwable ignored) {
        }
    }

    public void stopMapMusic() {
        try {
            boolean exists = mapMusic != null;
            if (exists) {
                AudioManager.unregister(mapMusic);
                mapMusic.stop();
                mapMusic.dispose();
                mapMusic = null;
            }
        } catch (Throwable ignored) {
        }
    }

    private static double clamp(double v, double lo, double hi) {
        double out = v;
        if (out < lo) {
            out = lo;
        } else if (out > hi) {
            out = hi;
        }
        return out;
    }

    private Direction directionFromVector(double vx, double vy) {
        if (vx == 0 && vy == 0) {
            return Direction.NONE;
        }
        double angle = Math.toDegrees(Math.atan2(-vy, vx));
        if (angle < 0) {
            angle += 360.0;
        }

        if (angle >= 337.5 || angle < 22.5) {
            return Direction.E;
        }
        if (angle < 67.5) {
            return Direction.NE;
        }
        if (angle < 112.5) {
            return Direction.N;
        }
        if (angle < 157.5) {
            return Direction.NW;
        }
        if (angle < 202.5) {
            return Direction.W;
        }
        if (angle < 247.5) {
            return Direction.SW;
        }
        if (angle < 292.5) {
            return Direction.S;
        }
        if (angle < 337.5) {
            return Direction.SE;
        }
        return Direction.NONE;
    }

    private void setDirectionIfChanged(Direction newDir) {
        if (newDir == null) {
            newDir = Direction.NONE;
        }
        currentDirection = newDir;
    }

    public Direction getHeroDirection() {
        return currentDirection;
    }

    public Point2D getHeroMapTopLeft() {
        return new Point2D(heroView.getLayoutX(), heroView.getLayoutY());
    }

    public Point2D getHeroMapCenter() {
        double cx = heroView.getLayoutX() + heroView.getBoundsInLocal().getWidth() / 2.0;
        double cy = heroView.getLayoutY() + heroView.getBoundsInLocal().getHeight() / 2.0;
        return new Point2D(cx, cy);
    }

    public Point2D getHeroSceneCenter() {
        double localCx = heroView.getBoundsInLocal().getWidth() / 2.0;
        double localCy = heroView.getBoundsInLocal().getHeight() / 2.0;
        return heroView.localToScene(localCx, localCy);
    }

    public Point2D sceneToMap(double sceneX, double sceneY) {
        return container.sceneToLocal(sceneX, sceneY);
    }

    public Point2D mapToScene(double mapX, double mapY) {
        return container.localToScene(mapX, mapY);
    }

    private void addVillageAtCenter(Point2D center, double visualW, double visualH, String id) {
        double vxVisual = center.getX() - visualW / 2.0;
        double vyVisual = center.getY() - visualH / 2.0;
        Rectangle2D visual = new Rectangle2D(vxVisual, vyVisual, visualW, visualH);

        double cw = visualW * VILLAGE_COLLISION_SCALE;
        double ch = visualH * VILLAGE_COLLISION_SCALE;

        double finalW = cw;
        double finalH = ch;
        double offsetY = 0.0;

        if ("FIELD_VILLAGE".equals(id)) {
            if (FV_COLLISION_W != null) {
                finalW = FV_COLLISION_W;
            }
            if (FV_COLLISION_H != null) {
                finalH = FV_COLLISION_H;
            }
            offsetY = FV_COLLISION_OFFSET_Y;
        }

        double cx = center.getX() - finalW / 2.0;
        double cy = center.getY() - finalH / 2.0 + offsetY;
        Rectangle2D collision = new Rectangle2D(cx, cy, finalW, finalH);

        obstacles.add(new Obstacle(visual, collision, ObstacleType.VILLAGE, id));
    }

    private void addBlockAtCenter(Point2D center, double width, double height) {
        double x = center.getX() - width / 2.0;
        double y = center.getY() - height / 2.0;
        Rectangle2D collision = new Rectangle2D(x, y, width, height);
        obstacles.add(new Obstacle(null, collision, ObstacleType.BLOCK, null));
    }

    private void populateVillagesFromList() {
        obstacles.clear();

        double visualW = 64;
        double visualH = 48;

        Point2D[] villageCenters = new Point2D[]{
            new Point2D(407.0, 358.5165319999998),
            new Point2D(435.8005579999999, 358.5165319999998),
            new Point2D(458.8777639999999, 358.5165319999998),
            new Point2D(192.526778, 152.8527799999999),
            new Point2D(169.48557200000005, 152.8527799999999),
            new Point2D(218.316764, 152.8527799999999),
            new Point2D(51.34460599999994, 121.2158539999999),
            new Point2D(25.328647999999937, 121.2158539999999),
            new Point2D(77.27695399999993, 121.2158539999999),
            new Point2D(405.6877819999999, 121.1205079999999),
            new Point2D(431.47821799999986, 121.1205079999999),
            new Point2D(466.0058359999998, 121.1205079999999),
            new Point2D(399.6368480000001, 78.16393999999985),
            new Point2D(693.5160080000004, 262.65198799999985),
            new Point2D(722.2856060000005, 262.65198799999985),
            new Point2D(745.1885540000004, 262.65198799999985)
        };

        for (int i = 0; i < villageCenters.length; i++) {
            addVillageAtCenter(villageCenters[i], visualW, visualH, "VILLA_" + i);
        }

        addVillageAtCenter(new Point2D(432.9572420000002, 387.930548), visualW, visualH, "FIELD_VILLAGE");
        addVillageAtCenter(new Point2D(187.2432599999999, 160.468792), visualW, visualH, "FORESTHOUSE_Village");
    }

    private void populateExtraBlocks() {
        double blockW = 20;
        double blockH = 20;

        Point2D[] blockCenters = new Point2D[]{
            new Point2D(265.78503199999994, 351.7745599999999),
            new Point2D(265.78503199999994, 299.99973800000004),
            new Point2D(265.78503199999994, 285.5473580000001),
            new Point2D(265.78503199999994, 271.2116360000001),
            new Point2D(72.98749999999998, 210.93302),
            new Point2D(72.98749999999998, 179.40848599999995),
            new Point2D(44.17545799999998, 167.86434799999992),
            new Point2D(27.035443999999977, 167.86434799999992),
            new Point2D(27.035443999999977, 190.76171599999995),
            new Point2D(27.035443999999977, 208.00933399999997),
            new Point2D(47.15656399999998, 208.00933399999997),
            new Point2D(122.23544599999997, 147.39627799999997),
            new Point2D(122.23544599999997, 124.40786599999996),
            new Point2D(122.23544599999997, 104.41818199999996),
            new Point2D(122.23544599999997, 78.54111199999997),
            new Point2D(122.23544599999997, 61.30780399999996),
            new Point2D(151.08154399999995, 61.30780399999996),
            new Point2D(174.22122799999997, 61.30780399999996),
            new Point2D(197.22717199999994, 61.30780399999996),
            new Point2D(211.68604999999997, 61.30780399999996),
            new Point2D(234.550514, 61.30780399999996),
            new Point2D(251.63372, 61.30780399999996),
            new Point2D(263.00768600000004, 78.60920599999997),
            new Point2D(263.00768600000004, 92.95437799999998),
            new Point2D(263.00768600000004, 113.14857799999997),
            new Point2D(263.00768600000004, 127.56612799999996),
            new Point2D(263.00768600000004, 144.86959999999996),
            new Point2D(294.74773400000015, 24.0),
            new Point2D(311.8714400000002, 24.0),
            new Point2D(328.99233800000013, 24.0),
            new Point2D(349.16330000000016, 24.0),
            new Point2D(366.2651720000002, 24.0),
            new Point2D(383.5229960000001, 24.0),
            new Point2D(400.72500200000013, 24.0),
            new Point2D(417.97476200000006, 24.0),
            new Point2D(438.3064820000001, 24.0),
            new Point2D(464.1087440000001, 24.0),
            new Point2D(515.7755119999999, 107.50795800000002),
            new Point2D(541.8374960000001, 107.50795800000002),
            new Point2D(784.1642380000001, 510.55332),
            new Point2D(752.342776, 510.55332),
            new Point2D(729.152494, 510.55332),
            new Point2D(706.0279119999999, 510.55332),
            new Point2D(680.1019719999999, 510.55332),
            new Point2D(656.943568, 510.55332),
            new Point2D(631.096882, 510.55332),
            new Point2D(613.700332, 510.55332),
            new Point2D(584.8805860000001, 510.55332),
            new Point2D(558.8708200000001, 510.55332),
            new Point2D(535.716592, 510.55332),
            new Point2D(512.851408, 510.55332),
            new Point2D(489.63903999999997, 510.55332),
            new Point2D(466.47024999999996, 510.55332),
            new Point2D(446.34977799999996, 510.55332),
            new Point2D(423.34511200000003, 510.55332),
            new Point2D(403.232542, 510.55332),
            new Point2D(380.092408, 510.55332),
            new Point2D(357.00064, 510.55332),
            new Point2D(328.1868159999999, 510.55332),
            new Point2D(302.4719259999998, 510.55332),
            new Point2D(273.7200939999998, 510.55332),
            new Point2D(247.8484419999998, 510.55332),
            new Point2D(216.03842799999975, 510.55332),
            new Point2D(190.10647599999976, 510.55332),
            new Point2D(161.36760399999974, 510.55332),
            new Point2D(135.45129399999976, 510.55332),
            new Point2D(106.71216999999976, 510.55332),
            new Point2D(80.79465399999975, 510.55332),
            new Point2D(54.852405999999746, 510.55332),
            new Point2D(34.80735399999975, 510.55332),
            new Point2D(24.0, 510.55332)
        };

        for (Point2D c : blockCenters) {
            addBlockAtCenter(c, blockW, blockH);
        }
    }

    public void drawDebugObstacles() {
        container.getChildren().removeIf(n -> "debug".equals(n.getProperties().get("tag")));

        if (!debugEnabled) {
            return;
        }

        for (Obstacle ob : obstacles) {
            if (ob.visualRect != null) {
                Rectangle rv = new Rectangle(
                        ob.visualRect.getMinX(), ob.visualRect.getMinY(),
                        ob.visualRect.getWidth(), ob.visualRect.getHeight());
                rv.setFill(Color.rgb(0, 0, 255, 0.12));
                rv.setStroke(Color.rgb(0, 0, 120, 0.5));
                rv.getProperties().put("tag", "debug");
                rv.setMouseTransparent(true);
                container.getChildren().add(rv);
            }

            Rectangle rc = new Rectangle(
                    ob.collisionRect.getMinX(), ob.collisionRect.getMinY(),
                    ob.collisionRect.getWidth(), ob.collisionRect.getHeight());
            if (ob.type == ObstacleType.VILLAGE) {
                rc.setFill(Color.rgb(255, 0, 0, 0.22));
                rc.setStroke(Color.rgb(120, 0, 0, 0.6));
            } else {
                rc.setFill(Color.rgb(160, 0, 200, 0.28));
                rc.setStroke(Color.rgb(120, 0, 120, 0.6));
            }
            rc.getProperties().put("tag", "debug");
            rc.setMouseTransparent(true);
            container.getChildren().add(rc);
        }
    }

    private void enterVillage(Obstacle village) {
        boolean isFieldVillage = village != null && "FIELD_VILLAGE".equals(village.id);
        boolean isForestHouse = village != null && "FORESTHOUSE_Village".equals(village.id);

        if (isFieldVillage) {
            final Point2D savedHeroTopLeft = getHeroMapTopLeft();

            clearInputState();

            stopMapMusic();
            try {
                FXGL.getGameScene().removeUINode(root);
            } catch (Throwable ignored) {
            }

            FieldVillage field = new FieldVillage(game);
            field.showWithLoading(null, () -> {
                Platform.runLater(() -> {
                    MainScreen.hideMenu();
                    startMapMusic();
                    try {
                        FXGL.getGameScene().addUINode(root);
                    } catch (Throwable ignored) {
                    }
                    heroView.setLayoutX(savedHeroTopLeft.getX());
                    heroView.setLayoutY(savedHeroTopLeft.getY());
                    if (debugEnabled) {
                        drawDebugObstacles();
                    }
                    root.requestFocus();
                    clearInputState();
                    mover.start();
                });
            });
        } else if (isForestHouse) {
            final Point2D savedHeroTopLeft = getHeroMapTopLeft();

            clearInputState();

            stopMapMusic();
            try {
                FXGL.getGameScene().removeUINode(root);
            } catch (Throwable ignored) {
            }

            ForestHouse field = new ForestHouse(game);
            field.showWithLoading(null, () -> {
                Platform.runLater(() -> {
                    MainScreen.hideMenu();
                    startMapMusic();
                    try {
                        FXGL.getGameScene().addUINode(root);
                    } catch (Throwable ignored) {
                    }
                    heroView.setLayoutX(savedHeroTopLeft.getX());
                    heroView.setLayoutY(savedHeroTopLeft.getY());
                    if (debugEnabled) {
                        drawDebugObstacles();
                    }
                    root.requestFocus();
                    clearInputState();
                    mover.start();
                });
            });
        } else {

            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Villa");
            a.setHeaderText(null);
            a.setContentText("Has interactuado con una villa.");
            try {
                if (root.getScene() != null && root.getScene().getWindow() != null) {
                    a.initOwner(root.getScene().getWindow());
                }
            } catch (Throwable ignored) {
            }
            a.setOnHidden(ev -> {
                clearInputState();
                Platform.runLater(root::requestFocus);
            });
            a.showAndWait();
        }
    }

    private void enterDebugSwamp() {
        final Point2D savedHeroTopLeft = getHeroMapTopLeft();

        clearInputState();

        stopMapMusic();
        try {
            FXGL.getGameScene().removeUINode(root);
        } catch (Throwable ignored) {
        }

        Swamp swamp = new Swamp(game);
        swamp.showWithLoading(null, () -> {
            Platform.runLater(() -> {
                MainScreen.hideMenu();
                startMapMusic();
                try {
                    FXGL.getGameScene().addUINode(root);
                } catch (Throwable ignored) {
                }
                heroView.setLayoutX(savedHeroTopLeft.getX());
                heroView.setLayoutY(savedHeroTopLeft.getY());
                if (debugEnabled) {
                    drawDebugObstacles();
                }
                root.requestFocus();
                clearInputState();
                mover.start();
            });
        });

    }

    public void setHeroPosition(double x, double y) {
        if (heroView != null) {
            double hw = heroView.getBoundsInLocal().getWidth();
            double hh = heroView.getBoundsInLocal().getHeight();
            heroView.setLayoutX(clamp(x, 0, mapW - hw));
            heroView.setLayoutY(clamp(y, 0, mapH - hh));
            heroPositionInitialized = true;
        }
    }

    public void resetHeroToCenter() {
        positionHeroCenter();
    }

    private void clearInputState() {
        up = down = left = right = false;
        draggingMap = false;
    }

    private void openDebugCombat() {
        String bg = "/Resources/textures/Battle/fieldBattle.png";
        stopMapMusic();

        GUI.CombatScreen cs = new GUI.CombatScreen(game, bg, "Overworld", game.getHero(), false, null);

        cs.setBattleMusicPath(combatMusicPath);
        //cs.setBattleMusicPath("/Resources/music/bossBattle2.mp3");

        cs.setOnExit(() -> {
            Platform.runLater(() -> {
                try {
                    FXGL.getGameScene().removeUINode(cs.root);
                } catch (Throwable ignored) {
                }
                try {
                    FXGL.getGameScene().addUINode(root);
                } catch (Throwable ignored) {
                }
                startMapMusic();
                root.requestFocus();
            });
        });

        Platform.runLater(() -> {
            try {
                FXGL.getGameScene().removeUINode(root);
            } catch (Throwable ignored) {
            }
            cs.show();
        });
    }
}
