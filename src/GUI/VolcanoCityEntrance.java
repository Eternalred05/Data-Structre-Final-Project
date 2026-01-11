package GUI;

import Logic.Game;
import Misc.Task;
import Runner.MainScreen;
import com.almasb.fxgl.dsl.FXGL;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class VolcanoCityEntrance {

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

    private boolean onStartRect = false;
    private Runnable onExitCallback;
    private Rectangle startRect;
    private Rectangle castleRect;
    private final Game game;

    private final List<Rectangle> dungeonTriggerRects = new ArrayList<>();
    private final List<Rectangle> bossTriggerRects = new ArrayList<>();

    private ImageView bossView;

    // Sistema de colisiones
    private final List<Obstacle> obstacles = new ArrayList<>();
    private boolean debugEnabled = false; // puedes activar R para ver cuadros de colisión

    // Inventario (si se abre desde aquí se pasa this)
    private InventoryScreen inventory;

    // Direcciones del héroe (para depuración con tecla P)
    public enum Direction {
        NONE, N, NE, E, SE, S, SW, W, NW
    }

    private Direction currentDirection = Direction.NONE;

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

    private enum ObstacleType {
        BLOCK, PLANT
    }

    public VolcanoCityEntrance(Game game) {
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

            boolean imageOk = loadBackgroundImage("/Resources/textures/volcanoDungeon/cityExterior.png");
            boolean musicOk = startDungeonMusic("/Resources/music/volcanoCity.mp3");

            populateVolcanoObstacles();

            positionHeroAtEntrance();
            createStartRectAtHeroStart();
            createCastleRect();

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
        stopDungeonMusic();
        Platform.runLater(() -> {
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

    // ---------------- internals / UI ----------------
    private StackPane createLoadingOverlay() {
        StackPane overlay = new StackPane();
        overlay.setPickOnBounds(true);
        Rectangle bg = new Rectangle(VIEW_W, VIEW_H);
        bg.setFill(Color.rgb(0, 0, 0, 0.6));
        Text label = new Text("Loading Volcano Zone...");
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
        boolean ret = false;
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
            ret = true;
        } catch (Throwable t) {
            Text err = new Text("No se pudo cargar la imagen del Sky Dungeon.");
            err.setStyle("-fx-font-size: 16px; -fx-fill: #ffdddd;");
            root.getChildren().add(err);
        }
        return ret;
    }

    private boolean startDungeonMusic(String path) {
        boolean started = false;
        try {
            URL res = getClass().getResource(path);
            if (res != null) {
                Media media = new Media(res.toExternalForm());
                stopDungeonMusic();
                // Crear y reproducir en hilo de JavaFX
                javafx.application.Platform.runLater(() -> {
                    try {
                        music = new MediaPlayer(media);
                        music.setCycleCount(MediaPlayer.INDEFINITE);
                        music.setVolume(MainScreen.getVolumeSetting());
                        music.play();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
                started = true;
            } else {
                System.err.println("startDungeonMusic: resource not found -> " + path);
            }
        } catch (Exception e) {
            e.printStackTrace();
            started = false;
        }
        return started;
    }

    private void stopDungeonMusic() {
        try {
            if (music != null) {
                music.stop();
                music.dispose();
                music = null;
            }
        } catch (Throwable ignored) {
        }
    }

    private ImageView createHeroView() {
        Image img;
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

    // ---------------- colisiones ----------------
    private void populateVolcanoObstacles() {
        obstacles.clear();

        double[][] COLLISIONS = new double[][]{
            {911.5357800000015, 63.739134},
            {911.5357800000015, 81.067032},
            {911.5357800000015, 98.434692},
            {911.5357800000015, 118.50013799999999},
            {911.5357800000015, 138.677346},
            {911.5357800000015, 155.950812},
            {911.5357800000015, 176.12152200000003},
            {897.3513120000015, 176.12152200000003},
            {882.9624720000014, 176.12152200000003},
            {868.5894720000015, 176.12152200000003},
            {868.5894720000015, 199.23393600000006},
            {868.5894720000015, 227.99386800000005},
            {868.5894720000015, 256.908222},
            {868.5894720000015, 274.255326},
            {845.5589220000016, 274.255326},
            {825.3666120000017, 274.255326},
            {808.0724820000019, 274.255326},
            {808.0724820000019, 254.30722200000005},
            {845.1976800000019, 254.30722200000005},
            {836.629176000002, 231.22296000000006},
            {790.4944740000019, 231.22296000000006},
            {767.5603860000019, 231.22296000000006},
            {767.5603860000019, 213.9278040000001},
            {767.5603860000019, 202.6208520000001},
            {767.5603860000019, 188.25226200000012},
            {767.5603860000019, 188.25226200000012},
            {767.5603860000019, 170.7884460000001},
            {767.5603860000019, 153.32277600000012},
            {738.5701800000021, 138.9167460000001},
            {758.8291620000023, 138.9167460000001},
            {718.6328940000022, 138.9167460000001},
            {718.6328940000022, 138.9167460000001},
            {718.6328940000022, 118.81249200000012},
            {718.6328940000022, 101.67010200000011},
            {738.7624740000024, 101.67010200000011},
            {756.1538400000023, 101.67010200000011},
            {767.6418180000023, 101.67010200000011},
            {767.6418180000023, 121.77963000000011},
            {767.6418180000023, 84.26048400000013},
            {767.6418180000023, 75.60833400000013},
            {767.6418180000023, 61.30609200000012},
            {767.6418180000023, 38.04780600000012},
            {767.6418180000023, 17.99857800000012},
            {767.6418180000023, 0.0},
            {767.6418180000023, 0.0},
            {793.7684760000022, 0.0},
            {813.8652240000023, 0.0},
            {836.9653800000023, 0.0},
            {859.8856980000023, 0.0},
            {874.3084860000022, 0.0},
            {874.3084860000022, 0.0},
            {724.1813220000022, 247.4301779999999},
            {724.1813220000022, 256.0264739999999},
            {724.1813220000022, 276.16116599999987},
            {724.1813220000022, 296.20078199999995},
            {724.1813220000022, 319.3703639999999},
            {724.1813220000022, 339.41159999999985},
            {724.1813220000022, 356.7224699999998},
            {724.1813220000022, 374.0371199999998},
            {741.8122680000021, 374.0371199999998},
            {759.134964000002, 374.0371199999998},
            {773.3885520000019, 374.0371199999998},
            {698.1268260000018, 374.0371199999998},
            {675.1185600000017, 374.0371199999998},
            {652.1020680000019, 374.0371199999998},
            {631.9776000000019, 374.0371199999998},
            {631.9776000000019, 356.72056199999975},
            {631.9776000000019, 339.4507859999997},
            {631.9776000000019, 319.2485579999998},
            {631.9776000000019, 299.3230439999998},
            {646.381326000002, 299.3230439999998},
            {669.4616820000022, 299.3230439999998},
            {669.4616820000022, 299.3230439999998},
            {686.7955020000022, 299.3230439999998},
            {686.7955020000022, 247.54946399999983},
            {666.5223720000022, 247.54946399999983},
            {666.5223720000022, 227.32806599999984},
            {629.270274000002, 227.32806599999984},
            {626.3077620000021, 227.32806599999984},
            {608.904516000002, 227.32806599999984},
            {589.1823840000019, 227.32806599999984},
            {566.0225940000018, 256.20028199999985},
            {543.083142000002, 276.2921699999998},
            {514.2407340000019, 276.2921699999998},
            {499.9810800000019, 276.2921699999998},
            {485.609268000002, 276.2921699999998},
            {465.44817000000194, 276.2921699999998},
            {448.18296600000184, 276.2921699999998},
            {416.8005420000018, 313.9255799999998},
            {384.89268000000175, 334.0516499999998},
            {384.89268000000175, 334.0516499999998},
            {347.5732260000018, 334.0516499999998},
            {333.18751800000183, 334.0516499999998},
            {310.1879820000018, 334.0516499999998},
            {289.9135020000018, 334.0516499999998},
            {269.71087800000186, 334.0516499999998},
            {246.73855800000186, 334.0516499999998},
            {347.3723820000018, 334.0516499999998},
            {347.3723820000018, 356.8962599999998},
            {347.3723820000018, 365.3363339999998},
            {347.3723820000018, 379.76561999999984},
            {347.3723820000018, 399.4181099999999},
            {347.3723820000018, 434.05464599999993},
            {347.3723820000018, 454.23354599999993},
            {347.3723820000018, 468.64389599999987},
            {333.1547580000018, 474.3324899999999},
            {315.72386400000175, 474.3324899999999},
            {301.60079400000177, 474.3324899999999},
            {286.97217600000175, 474.3324899999999},
            {272.7208380000018, 474.3324899999999},
            {255.34840800000174, 474.3324899999999},
            {586.8238980000013, 425.43025199999994},
            {586.8238980000013, 425.7400859999999},
            {586.8238980000013, 448.58241},
            {650.0478900000012, 457.173684},
            {650.0478900000012, 457.173684},
            {666.9352020000011, 457.173684},
            {678.4597740000012, 457.173684},
            {698.776446000001, 457.173684},
            {713.2304820000008, 457.173684},
            {612.5472300000006, 459.981702},
            {724.9840860000003, 459.981702},
            {724.9840860000003, 480.2259959999999},
            {724.9840860000003, 497.1909959999999},
            {724.9840860000003, 511.6052699999999},
            {696.1561320000003, 531.775656},
            {676.0278660000002, 575.0171640000003},
            {673.2002460000002, 575.0171640000003},
            {632.8928460000001, 575.0171640000003},
            {632.8928460000001, 592.1936820000003},
            {632.8928460000001, 592.1936820000003},
            {609.8752560000001, 609.5716200000003},
            {601.2004260000002, 609.5716200000003},
            {601.2004260000002, 609.5716200000003},
            {601.2004260000002, 623.9074500000003},
            {601.2004260000002, 655.6723380000003},
            {572.3107860000003, 678.7636380000004},
            {566.6029680000004, 678.7636380000004},
            {543.6376320000004, 690.1923060000004},
            {543.6376320000004, 690.1923060000004},
            {543.6376320000004, 716.5550700000005},
            {543.6376320000004, 736.6422780000005},
            {543.6376320000004, 753.9148620000004},
            {489.08953200000053, 767.9246220000003},
            {471.6955740000005, 767.9246220000003},
            {454.1971260000004, 767.9246220000003},
            {439.7698560000004, 767.9246220000003},
            {419.05545600000033, 767.9246220000003},
            {402.0613140000004, 767.9246220000003},
            {384.5036280000004, 767.9246220000003},
            {370.22977200000037, 767.9246220000003},
            {332.88943800000027, 802.5433740000004},
            {327.0671580000003, 802.5433740000004},
            {318.5480280000003, 779.5439460000003},
            {301.0783440000003, 779.5439460000003},
            {393.5452800000003, 779.5439460000003},
            {407.9806320000003, 779.5439460000003},
            {257.97831600000126, 1152.0},
            {257.97831600000126, 1131.953382},
            {257.97831600000126, 1114.8043859999998},
            {257.97831600000126, 1094.5195379999998},
            {257.97831600000126, 1071.4588919999997},
            {257.97831600000126, 1051.2247859999998},
            {257.97831600000126, 1031.1464879999996},
            {257.97831600000126, 1010.9246399999996},
            {257.97831600000126, 988.1877599999995},
            {257.97831600000126, 967.8049379999995},
            {257.97831600000126, 947.8255319999996},
            {257.97831600000126, 930.6820619999996},
            {257.97831600000126, 907.7268959999998},
            {257.97831600000126, 890.4248099999998},
            {257.97831600000126, 872.8352279999998},
            {257.97831600000126, 849.7928519999999},
            {257.97831600000126, 829.6475579999998},
            {257.97831600000126, 821.1891959999997},
            {257.97831600000126, 821.1891959999997},
            {278.17953600000124, 821.1891959999997},
            {278.17953600000124, 838.6144379999997},
            {278.17953600000124, 855.8512019999998},
            {278.17953600000124, 884.7456119999998},
            {278.17953600000124, 896.2797419999999},
            {278.17953600000124, 919.25325},
            {278.17953600000124, 936.571554},
            {309.90361800000125, 956.794752},
            {292.62066600000117, 956.794752},
            {312.7337580000012, 956.794752},
            {327.0354600000012, 956.794752},
            {347.1480840000012, 956.794752},
            {367.1847480000012, 956.794752},
            {375.7140660000012, 956.794752},
            {390.0144360000011, 956.794752},
            {404.2030620000012, 956.794752},
            {421.3420140000012, 956.794752},
            {438.5752680000012, 956.794752},
            {450.2860140000012, 956.794752},
            {464.7182340000012, 956.794752},
            {479.05206600000116, 956.794752},
            {495.71004000000113, 956.794752},
            {512.892912000001, 956.794752},
            {527.3462460000009, 956.794752},
            {545.3094360000008, 956.794752},
            {562.1099160000008, 956.794752},
            {579.2551500000008, 942.438582},
            {579.2551500000008, 942.438582},
            {579.2551500000008, 930.860172},
            {579.2551500000008, 916.3014300000001},
            {579.2551500000008, 904.686984},
            {533.2452060000006, 887.3316540000001},
            {533.2452060000006, 867.1804380000001},
            {533.2452060000006, 861.5225700000001},
            {533.2452060000006, 849.9111840000002},
            {533.2452060000006, 824.0286240000003},
            {553.5818220000008, 806.8509000000003},
            {559.3417140000008, 806.8509000000003},
            {579.3723480000009, 806.8509000000003},
            {605.322336000001, 806.8509000000003},
            {625.552464000001, 806.8509000000003},
            {625.552464000001, 821.7717480000002},
            {625.552464000001, 836.1487440000002},
            {625.552464000001, 876.8814480000002},
            {579.6486480000011, 816.1864740000001},
            {562.4637780000011, 816.1864740000001},
            {545.0893500000011, 816.1864740000001},
            {519.123774000001, 816.1864740000001},
            {435.585576000001, 1014.937308},
            {435.585576000001, 1032.203376},
            {435.585576000001, 1049.5951739999998},
            {668.9449200000009, 1092.8615039999995},
            {668.9449200000009, 1113.0417719999994},
            {668.9449200000009, 1136.2285259999994},
            {668.9449200000009, 1152.0},
            {1054.6719600000001, 1152.0},
            {1054.6719600000001, 1137.474306},
            {1054.6719600000001, 1120.2610499999998},
            {1054.6719600000001, 1108.5855479999998},
            {1097.7379680000001, 958.8203819999999},
            {1097.7379680000001, 941.7513779999999},
            {1097.7379680000001, 938.6113859999999},
            {1097.7379680000001, 927.0864719999998},
            {1097.7379680000001, 912.6653939999999},
            {1296.6913379999999, 912.6653939999999},
            {1296.6913379999999, 929.885688},
            {1296.6913379999999, 947.346246},
            {1296.6913379999999, 964.681218},
            {1484.034222000001, 766.351836},
            {1484.034222000001, 749.057454},
            {1484.034222000001, 720.1359000000001},
            {1484.034222000001, 743.1070140000002},
            {1484.034222000001, 711.7774020000003},
            {1438.4494020000009, 524.5492860000004},
            {1438.4494020000009, 504.24483600000053},
            {1438.4494020000009, 486.9939420000005},
            {1438.4494020000009, 486.9939420000005},
            {1490.3810400000013, 333.9055620000005},
            {1490.3810400000013, 313.8751800000005},
            {1490.3810400000013, 296.7337080000005},
            {1009.792794000002, 325.71984600000053},
            {1009.792794000002, 311.38498800000053},
            {1009.792794000002, 302.72317200000055},
            {1009.792794000002, 334.7489340000005},
            {862.9112100000012, 482.0300280000005},
            {862.9112100000012, 502.1114220000006},
            {862.9112100000012, 519.1550460000005},
            {767.7708240000013, 585.6141420000005},
            {767.7708240000013, 605.9299860000006},
            {767.7708240000013, 623.0749320000007},
            {680.9039940000011, 720.7504740000011},
            {680.9039940000011, 740.781378000001},
            {680.9039940000011, 755.3176560000011},
            {663.6254520000011, 772.5800520000012},
            {663.6254520000011, 758.1771540000012},
            {663.6254520000011, 746.6339700000012},
            {1009.208316000001, 337.55731200000093},
            {1009.208316000001, 314.29306800000097},
            {1009.208316000001, 299.982006000001},
            {1009.208316000001, 288.460188000001},
            {1055.5021020000013, 49.63077000000088},
            {1055.5021020000013, 29.494620000000882},
            {1055.5021020000013, 12.221280000000883},
            {1055.5021020000013, 0.0},
            {1346.8706040000013, 2.756466},
            {1346.8706040000013, 14.254091999999998},
            {1346.8706040000013, 31.5036},
            {1346.8706040000013, 49.04029799999999},
            {1346.8706040000013, 51.827166000000005}
        };

        int idx = 1;
        for (double[] p : COLLISIONS) {
            obstacles.add(new Obstacle(
                    new Rectangle2D(p[0], p[1], 40, 40),
                    ObstacleType.BLOCK,
                    "SkyCollision" + idx
            ));
            idx++;
        }
    }

    // ---------------- movimiento y entradas ----------------
    private void positionHeroAtEntrance() {

        double startX = 868.6131420000022;
        double startY = 1143.106434;
        heroView.setLayoutX(startX);
        heroView.setLayoutY(startY);
        updateCamera();
    }

    private void createStartRectAtHeroStart() {
        if (startRect != null) {
            world.getChildren().remove(startRect);
            startRect = null;
        }
        double rx = heroView.getLayoutX() - 4;
        double ry = heroView.getLayoutY() - 4;
        double rw = HERO_W + 8;
        double rh = HERO_H + 8;
        startRect = new Rectangle(rx, ry, rw, rh);
        startRect.setFill(Color.rgb(0, 120, 255, 0.28));
        startRect.setStroke(Color.rgb(0, 80, 200, 0.9));
        startRect.setMouseTransparent(true);
        if (!world.getChildren().contains(startRect)) {
            world.getChildren().add(startRect);
        }
        startRect.toBack();
        heroView.toFront();
    }

    private void createCastleRect() {
        if (castleRect != null) {
            world.getChildren().remove(castleRect);
            castleRect = null;
        }
        double[] xs = new double[]{
            (1200.2547180000017),
            (1154.1645840000022),
            (1105.3773120000023),
            (1252.301538000002),
            (1298.3531520000017)
        };
        double y = 0;
        double minX = xs[0];
        double maxX = xs[0];
        for (double v : xs) {
            if (v < minX) {
                minX = v;
            }
            if (v > maxX) {
                maxX = v;
            }
        }
        double pad = 4.0;
        double horizontalSpan = (maxX - minX);
        double rw = horizontalSpan + HERO_W + pad * 2;
        double rh = HERO_H + pad * 2;
        double rx = minX - pad;
        double ry = y - pad;

        castleRect = new Rectangle(rx, ry, rw, rh);
        castleRect.setFill(Color.rgb(200, 120, 0, 0.28));
        castleRect.setStroke(Color.rgb(180, 80, 0, 0.9));
        castleRect.setMouseTransparent(true);
        castleRect.getProperties().put("tag", "castle_area");
        if (!world.getChildren().contains(castleRect)) {
            world.getChildren().add(castleRect);
        }
        castleRect.toBack();
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
                System.out.println("(" + heroView.getLayoutX() + ", " + heroView.getLayoutY() + ")");
            }

            if (k == KeyCode.I || k == KeyCode.ADD || k == KeyCode.PLUS) {
                clearInputState();
                openInventory();
            }
            if (k == KeyCode.B) {
                clearInputState();
                openDebugCombat();
            }
            if (k == KeyCode.R) {
                debugEnabled = !debugEnabled;
                if (debugEnabled) {
                    drawDebugObstacles();
                } else {
                    world.getChildren().removeIf(n -> "obstacle_debug".equals(n.getProperties().get("tag")));
                }
                for (Task t : game.getHero().getCompletedTasks()) {
                    System.out.print(t.getName());

                }
            }
            if (k == KeyCode.ENTER) {
                checkCastleTrigger();
                checkExitTrigger();
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

                if (root.getScene() == null || !root.isFocused()) {
                    clearInputState();
                    return;
                }

                updateAndMove(dt);
            }
        };
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

        if (vx == 0 && vy == 0) {
            return;
        }

        moveHero(vx * dt, vy * dt);
    }

    private void moveHero(double dx, double dy) {
        double curX = heroView.getLayoutX();
        double curY = heroView.getLayoutY();

        double proposedX = clamp(curX + dx, 0, Math.max(0, worldW - HERO_W));
        double proposedY = clamp(curY + dy, 0, Math.max(0, worldH - HERO_H));

        Rectangle2D heroRect = new Rectangle2D(proposedX, proposedY, HERO_W, HERO_H);
        boolean collision = false;

        for (Obstacle ob : obstacles) {
            if (heroRect.intersects(ob.collisionRect)) {
                collision = true;
                break;
            }
        }

        if (!collision) {
            heroView.setLayoutX(proposedX);
            heroView.setLayoutY(proposedY);
        } else {
            // Intento separar ejes X/Y para movimiento "slide"
            Rectangle2D heroRectX = new Rectangle2D(proposedX, curY, HERO_W, HERO_H);
            Rectangle2D heroRectY = new Rectangle2D(curX, proposedY, HERO_W, HERO_H);

            boolean canMoveX = true;
            boolean canMoveY = true;

            for (Obstacle ob : obstacles) {
                if (heroRectX.intersects(ob.collisionRect)) {
                    canMoveX = false;
                }
                if (heroRectY.intersects(ob.collisionRect)) {
                    canMoveY = false;
                }
                if (!canMoveX && !canMoveY) {
                    break;
                }
            }

            if (canMoveX) {
                heroView.setLayoutX(proposedX);
            }
            if (canMoveY) {
                heroView.setLayoutY(proposedY);
            }
        }

        updateCamera();
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

        world.setTranslateX(tx);
        world.setTranslateY(ty);
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    private void clearInputState() {
        keys.clear();

    }

    public void startMapMusic() {
        try {
            stopMapMusic();
            URL res = getClass().getResource("/Resources/music/volcanoCity.mp3");
            boolean hasRes = res != null;
            if (hasRes) {
                Media media = new Media(res.toExternalForm());
                music = new MediaPlayer(media);
                music.setCycleCount(MediaPlayer.INDEFINITE);
                music.setVolume(MainScreen.getVolumeSetting());
                music.play();

                AudioManager.register(music);
            }
        } catch (Throwable ignored) {
        }
    }

    public void stopMapMusic() {
        try {
            boolean exists = music != null;
            if (exists) {
                AudioManager.unregister(music);
                music.stop();
                music.dispose();
                music = null;
            }
        } catch (Throwable ignored) {
        }
    }

    private void drawDebugObstacles() {
        world.getChildren().removeIf(n -> "obstacle_debug".equals(n.getProperties().get("tag")));
        for (Obstacle ob : obstacles) {
            Rectangle2D r = ob.collisionRect;
            Rectangle debug = new Rectangle(r.getMinX(), r.getMinY(), r.getWidth(), r.getHeight());
            debug.setFill(Color.color(0, 1, 0, 0.25)); // verde semitransparente
            debug.setStroke(Color.color(0, 1, 0, 0.9));
            debug.setMouseTransparent(true);
            debug.getProperties().put("tag", "obstacle_debug");
            debug.getProperties().put("id", ob.id);
            world.getChildren().add(debug);
        }
        heroView.toFront();
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

    private void openDebugCombat() {
        String bg = "/Resources/textures/Battle/volcanoExterior.png";
        stopMapMusic();

        GUI.CombatScreen cs = new GUI.CombatScreen(game, bg, "Volcano", game.getHero(), false, null);

        cs.setBattleMusicPath("/Resources/music/fieldBattle.mp3");

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
                startDungeonMusic("/Resources/music/volcanoCity.mp3");
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

    // Método que comprueba si el héroe está en el área de salida
    private void checkExitTrigger() {
        Rectangle2D heroRect = new Rectangle2D(
                heroView.getLayoutX(),
                heroView.getLayoutY(),
                HERO_W,
                HERO_H
        );

        if (startRect != null) {
            Rectangle2D startArea = new Rectangle2D(
                    startRect.getX(),
                    startRect.getY(),
                    startRect.getWidth(),
                    startRect.getHeight()
            );

            if (heroRect.intersects(startArea)) {
                clearInputState();
                if (onExitCallback != null) {
                    hide();
                    onExitCallback.run(); // vuelve al mapa anterior
                } else {
                    hide();
                }
            }
        }
    }

    private void checkCastleTrigger() {
        Rectangle2D heroRect = new Rectangle2D(
                heroView.getLayoutX(),
                heroView.getLayoutY(),
                HERO_W,
                HERO_H
        );

        if (castleRect != null) { 
            Rectangle2D castleArea = new Rectangle2D(
                    castleRect.getX(),
                    castleRect.getY(),
                    castleRect.getWidth(),
                    castleRect.getHeight()
            );

            if (heroRect.intersects(castleArea)) {
                clearInputState();
                hide(); // oculta la clase

                VolcanoCastle castle = new VolcanoCastle(game);
                castle.showWithLoading(null, () -> {
                    Platform.runLater(() -> {
                        try {
                            // Volver a añadir la UI de la clase
                            FXGL.getGameScene().addUINode(root);
                        } catch (Throwable ignored) {
                        }

                        // Reiniciar la música 
                        startDungeonMusic("/Resources/music/volcanoCity.mp3");

                        // Reanudar movimiento y foco
                        root.requestFocus();
                        startMover();
                    });
                });

            }
        }
    }
}
