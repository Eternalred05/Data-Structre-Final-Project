package GUI;

import Characters.Hero;
import Logic.Game;
import Misc.Task;
import Runner.MainScreen;
import com.almasb.fxgl.dsl.FXGL;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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

public class CastleSecondFloor {

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

    private boolean beforeDungeon = true;
    private ImageView bossView;

    // Sistema de colisiones
    private final List<Obstacle> obstacles = new ArrayList<>();
    private boolean debugEnabled = false; // R para ver/ocultar áreas de trigger

    private InventoryScreen inventory;

    // Direcciones del héroe (si quieres usarlas para depuración)
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

    public CastleSecondFloor(Game game) {
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
            root.requestFocus();
            showLoading(true);

            boolean imageOk = loadBackgroundImage("/Resources/textures/skyDungeon/2ndFloor007.png");
            startMapMusic();

            populateCastleObstacles();
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
        Platform.runLater(() -> {
            stopMapMusic();
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
        Text label = new Text("Loading Castle Second Floor...");
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
            Text err = new Text("No se pudo cargar la imagen del Castle First Floor.");
            err.setStyle("-fx-font-size: 16px; -fx-fill: #ffdddd;");
            root.getChildren().add(err);
        }
        return ret;
    }

    private boolean startDungeonMusic(String path) {
        try {
            URL res = getClass().getResource(path);
            if (res == null) {
                return false;
            }
            Media media = new Media(res.toExternalForm());
            stopDungeonMusic();
            music = new MediaPlayer(media);
            music.setCycleCount(MediaPlayer.INDEFINITE);
            music.setVolume(MainScreen.getVolumeSetting());
            music.play();
            return true;
        } catch (Throwable t) {
            return false;
        }
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
    private void populateCastleObstacles() {
        obstacles.clear();

        double[][] COLLISIONS = new double[][]{
            {528.7748980000002, 1152.0},
            {528.7748980000002, 1106.727066},
            {528.7748980000002, 1059.0852600000003},
            {528.7748980000002, 1005.2101440000006},
            {572.2835080000002, 1005.2101440000006},
            {572.2835080000002, 954.8881200000005},
            {572.2835080000002, 914.1593940000008},
            {572.2835080000002, 870.9669180000009},
            {526.2145600000005, 870.9669180000009},
            {526.2145600000005, 920.0396700000009},
            {526.2145600000005, 963.223614000001},
            {526.2145600000005, 819.7187580000011},
            {572.1641680000004, 819.7187580000011},
            {815.3662360000004, 1152.0},
            {815.3662360000004, 1093.7578679999997},
            {819.2417080000004, 1152.0},
            {819.2417080000004, 1104.1595279999997},
            {777.1495000000003, 1104.1595279999997},
            {822.4952980000002, 1057.9764059999998},
            {814.2710979999998, 1011.9004200000004},
            {774.0233679999994, 1003.1525100000005},
            {774.0233679999994, 962.9092800000001},
            {774.0233679999994, 908.6024880000002},
            {774.0233679999994, 860.8388940000003},
            {774.0233679999994, 823.4215560000005},
            {825.4016859999996, 823.4215560000005},
            {869.5189119999993, 823.4215560000005},
            {918.5433339999994, 823.4215560000005},
            {969.6642519999992, 823.4215560000005},
            {1018.2975159999994, 823.4215560000005},
            {1047.121761999999, 823.4215560000005},
            {1076.1462399999994, 826.1591220000003},
            {1099.1857179999993, 826.1591220000003},
            {1099.1857179999993, 857.041236},
            {1099.1857179999993, 917.5716719999997},
            {1099.1857179999993, 963.5905799999996},
            {1099.1857179999993, 999.7434899999996},
            {1061.5512279999996, 999.7434899999996},
            {1061.5512279999996, 962.1945359999997},
            {1061.5512279999996, 922.1656679999996},
            {1061.5512279999996, 876.1440959999999},
            {1015.4132319999999, 907.9997219999999},
            {974.9924499999998, 907.9997219999999},
            {946.2245079999999, 907.9997219999999},
            {909.9331059999998, 907.9997219999999},
            {869.5588179999995, 907.9997219999999},
            {909.0977619999994, 953.9536500000002},
            {909.0977619999994, 916.4601000000004},
            {960.0627999999992, 916.4601000000004},
            {960.0627999999992, 955.6985340000003},
            {814.8487720000006, 863.558118000001},
            {814.8487720000006, 906.8616180000009},
            {814.8487720000006, 963.1254420000008},
            {860.1165939999989, 1152.0},
            {909.1823799999988, 1152.0},
            {962.9369139999988, 1152.0},
            {1020.6026319999988, 1152.0},
            {1053.5982879999983, 1152.0},
            {1097.4097659999977, 1152.0},
            {1137.8318439999978, 1152.0},
            {1176.1896639999977, 1152.0},
            {1029.6882579999979, 1152.0},
            {1205.386203999998, 1152.0},
            {1248.545055999998, 1152.0},
            {1280.389593999998, 1152.0},
            {1306.4164959999978, 1152.0},
            {1334.4697299999975, 1152.0},
            {1369.0983999999976, 1152.0},
            {1392.0, 1152.0},
            {1392.0, 1105.9221060000004},
            {1392.0, 1049.7821760000002},
            {1359.329622, 1049.7821760000002},
            {1359.329622, 1009.4280840000004},
            {1392.0, 1009.4280840000004},
            {1392.0, 954.6609060000002},
            {1392.0, 917.2513800000006},
            {1392.0, 872.5802400000006},
            {1392.0, 840.8352420000007},
            {1392.0, 797.6606580000005},
            {1392.0, 774.5782680000004},
            {1392.0, 757.2127500000005},
            {1392.0, 736.8834780000004},
            {1392.0, 711.8265420000005},
            {1392.0, 677.4319260000003},
            {1392.0, 645.7318380000005},
            {1392.0, 612.0315900000004},
            {1298.2176059999997, 625.4591220000004},
            {1392.0, 584.8423380000004},
            {1392.0, 553.1918220000003},
            {1392.0, 512.8214400000002},
            {1392.0, 469.5030720000002},
            {1392.0, 433.4822280000002},
            {1392.0, 410.40192600000006},
            {1392.0, 386.03457000000003},
            {1392.0, 344.36525400000005},
            {1392.0, 312.53117399999996},
            {1392.0, 273.8995200000001},
            {1392.0, 242.10048600000007},
            {1392.0, 211.70777400000006},
            {1392.0, 181.61064000000005},
            {1392.0, 159.4094940000001},
            {1392.0, 131.99920200000014},
            {1392.0, 91.04590800000013},
            {1346.8123140000002, 91.04590800000013},
            {1300.6901040000002, 91.04590800000013},
            {1248.957150000001, 91.04590800000013},
            {1199.5927440000014, 91.04590800000013},
            {1163.2156800000012, 91.04590800000013},
            {1131.7456320000015, 91.04590800000013},
            {1107.0204720000015, 91.04590800000013},
            {1072.1914980000013, 91.04590800000013},
            {1043.3501880000013, 91.04590800000013},
            {1014.5537160000013, 91.04590800000013},
            {991.6780740000014, 91.04590800000013},
            {952.0936440000014, 91.04590800000013},
            {911.6115000000013, 91.04590800000013},
            {911.6115000000013, 119.72809800000016},
            {911.6115000000013, 148.69227600000025},
            {911.6115000000013, 184.4557380000003},
            {895.5245940000015, 184.4557380000003},
            {895.5245940000015, 210.57885000000033},
            {895.5245940000015, 230.7441780000004},
            {863.3161860000015, 230.7441780000004},
            {840.2616960000015, 230.7441780000004},
            {840.2616960000015, 207.72592200000042},
            {840.2616960000015, 181.81940400000042},
            {820.2359220000018, 184.80051000000043},
            {820.2359220000018, 153.10312200000047},
            {820.2359220000018, 106.86457800000049},
            {776.9377860000019, 92.6819100000005},
            {776.9377860000019, 47.83962600000055},
            {820.240188000002, 47.83962600000055},
            {820.240188000002, 0.0},
            {571.2864900000013, 47.01924},
            {575.067606000001, 97.63352999999996},
            {575.067606000001, 138.09985199999997},
            {575.067606000001, 183.87277199999994},
            {524.3401320000007, 183.87277199999994},
            {487.13362800000095, 183.87277199999994},
            {480.88793400000077, 213.81974999999994},
            {480.88793400000077, 261.79005599999994},
            {480.88793400000077, 314.5480740000001},
            {480.88793400000077, 355.253382},
            {480.88793400000077, 411.719436},
            {480.88793400000077, 457.51994999999994},
            {480.88793400000077, 506.4093539999999},
            {480.88793400000077, 526.5717479999998},
            {480.88793400000077, 560.0140019999998},
            {480.88793400000077, 588.8515679999998},
            {480.88793400000077, 614.9338379999999},
            {528.6679260000006, 614.9338379999999},
            {528.6679260000006, 584.4305520000004},
            {528.6679260000006, 532.6219260000003},
            {579.4414980000006, 532.6219260000003},
            {579.4414980000006, 584.4023640000007},
            {579.4414980000006, 625.1512320000007},
            {579.4414980000006, 662.1408180000012},
            {630.3024420000004, 662.1408180000012},
            {630.3024420000004, 630.4701420000011},
            {630.3024420000004, 598.8526200000013},
            {630.3024420000004, 570.0876660000011},
            {630.3024420000004, 530.2229040000008},
            {674.2377780000005, 530.2229040000008},
            {674.2377780000005, 576.2810520000012},
            {674.2377780000005, 619.4374560000011},
            {674.2377780000005, 656.4732480000007},
            {710.6523180000006, 656.4732480000007},
            {710.6523180000006, 627.6854700000007},
            {710.6523180000006, 591.6798720000008},
            {710.6523180000006, 562.7603520000007},
            {710.6523180000006, 537.4519200000008},
            {758.365530000001, 536.0513760000008},
            {758.365530000001, 582.391818000001},
            {758.365530000001, 614.2009320000011},
            {758.365530000001, 640.1734380000009},
            {758.365530000001, 668.927970000001},
            {820.1286420000009, 620.4126420000011},
            {862.215540000001, 620.4126420000011},
            {824.7989220000011, 565.8686100000011},
            {824.7989220000011, 525.4371900000012},
            {858.8575860000013, 525.4371900000012},
            {910.5166860000013, 525.4371900000012},
            {962.5097220000013, 525.4371900000012},
            {1014.3767040000013, 525.4371900000012},
            {1058.618850000001, 525.4371900000012},
            {1101.3221040000017, 525.4371900000012},
            {1101.3221040000017, 580.0235040000014},
            {1101.3221040000017, 612.3714840000014},
            {1062.2592240000013, 612.3714840000014},
            {1021.9100280000009, 612.3714840000014},
            {975.9265440000008, 612.3714840000014},
            {935.5341840000008, 612.3714840000014},
            {909.5463240000007, 612.3714840000014},
            {1105.578330000002, 522.4462020000018},
            {1148.6755320000022, 523.9170540000023},
            {1148.6755320000022, 477.8886960000022},
            {1148.6755320000022, 428.96527200000213},
            {1107.3725520000023, 425.95977600000214},
            {1107.3725520000023, 477.8975880000021},
            {1107.3725520000023, 377.8538580000021},
            {1107.3725520000023, 336.25508400000206},
            {1107.3725520000023, 282.969396000002},
            {1107.3725520000023, 239.70582000000206},
            {1254.284952000003, 184.84907400000208},
            {1298.6915100000024, 184.84907400000208},
            {961.7265780000021, 380.4573960000021},
            {961.7265780000021, 347.0754960000021},
            {771.1048320000014, 347.0754960000021},
            {771.1048320000014, 378.94525200000203},
            {575.2859640000012, 417.1570200000018},
            {531.854790000001, 417.1570200000018},
            {531.854790000001, 368.0981640000018},
            {562.8797160000003, 368.0981640000018},
            {562.8797160000003, 323.0216100000017},
            {525.4179900000006, 326.29924800000157},
            {525.4179900000006, 369.2829240000015},
            {1145.6785479999949, 877.7558880000001},
            {1145.6785479999949, 949.6578420000001},
            {1199.7948459999948, 909.2782980000003},
            {477.0347079999944, 819.4881240000022},
            {431.7482559999942, 819.4881240000022},
            {379.75309599999423, 819.4881240000022},
            {340.9653459999941, 819.4881240000022},
            {340.9653459999941, 774.1996560000023},
            {285.2998419999941, 816.0541380000024},
            {245.41605399999418, 816.0541380000024},
            {245.41605399999418, 857.3076360000022},
            {245.41605399999418, 900.3789000000019},
            {285.773907999994, 900.3789000000019},
            {331.8202839999939, 900.3789000000019},
            {385.12076799999386, 900.3789000000019},
            {434.1880299999936, 900.3789000000019},
            {477.80087799999365, 900.3789000000019},
            {477.80087799999365, 865.6728300000018},
            {440.41507599999363, 865.6728300000018},
            {399.69839199999376, 865.6728300000018},
            {350.6594439999938, 865.6728300000018},
            {330.4461819999938, 865.6728300000018},
            {477.94800999999404, 1009.1005200000022},
            {480.76968999999406, 1007.4781800000017},
            {480.76968999999406, 1052.2168560000016},
            {480.76968999999406, 1096.102044000002},
            {480.76968999999406, 1152.0},
            {429.0712779999939, 1152.0},
            {391.2052539999939, 1152.0},
            {342.0336459999937, 1152.0},
            {287.54688999999377, 1152.0},
            {230.1744459999937, 1152.0},
            {192.89243199999373, 1152.0},
            {153.5676699999937, 1152.0},
            {90.03481599999371, 1152.0},
            {49.55911599999374, 1152.0},
            {49.55911599999374, 1105.8283800000006},
            {49.55911599999374, 1052.896392000001},
            {0.0, 1052.896392000001},
            {0.0, 1102.2041340000014},
            {0.0, 1152.0},
            {0.0, 1007.6496120000003},
            {0.0, 958.6600380000002},
            {0.0, 907.4540520000006},
            {0.0, 864.0404280000006},
            {0.0, 812.3748660000003},
            {0.0, 761.6747699999999},
            {40.426883999999994, 758.940714},
            {40.426883999999994, 708.8453280000002},
            {40.426883999999994, 662.7391920000005},
            {0.0, 662.7391920000005},
            {0.0, 613.6370460000003},
            {0.0, 584.8697880000001},
            {0.0, 553.7688120000003},
            {0.0, 527.8933260000001},
            {0.0, 487.53295200000014},
            {0.0, 427.154814},
            {0.0, 378.4378499999999},
            {0.0, 329.7666960000001},
            {0.0, 289.39555800000005},
            {37.461743999999996, 289.39555800000005},
            {0.0, 242.52667200000005},
            {0.0, 194.77395},
            {0.0, 134.9757719999999},
            {0.0, 80.34044399999986},
            {0.0, 35.881811999999904},
            {44.39208600000001, 92.81280599999987},
            {87.62275799999999, 92.81280599999987},
            {135.31467600000002, 92.81280599999987},
            {166.194036, 92.81280599999987},
            {206.41663800000003, 92.81280599999987},
            {243.80796600000005, 92.81280599999987},
            {275.49180000000007, 92.81280599999987},
            {301.38678000000004, 92.81280599999987},
            {336.1960980000001, 92.81280599999987},
            {261.46549799999997, 137.1186359999999},
            {217.50381000000007, 137.1186359999999},
            {188.64757800000007, 137.1186359999999},
            {162.7046640000001, 137.1186359999999},
            {122.56482600000012, 137.1186359999999},
            {145.3679100000001, 185.8051079999998},
            {194.38277400000004, 185.8051079999998},
            {243.6251580000001, 185.8051079999998},
            {243.6251580000001, 230.71897799999974},
            {194.66010000000023, 230.71897799999974},
            {146.63475000000022, 230.71897799999974},
            {374.929794, 0.0},
            {300.8343240000002, 92.292498},
            {329.93940600000013, 92.292498},
            {384.37052400000005, 92.292498},
            {384.37052400000005, 135.661068},
            {384.37052400000005, 194.78030400000003},
            {384.37052400000005, 238.537368},
            {384.37052400000005, 277.97617800000006},
            {384.37052400000005, 321.1080120000002},
            {431.783064, 321.1080120000002},
            {431.783064, 277.6600080000003},
            {431.783064, 236.93095800000023},
            {431.783064, 180.69127200000023},
            {339.50449799999996, 606.8423520000002},
            {339.8707980000001, 583.7993460000002},
            {339.8707980000001, 537.757506},
            {339.8707980000001, 475.0535879999998},
            {339.8707980000001, 434.616786},
            {287.83414800000014, 434.616786},
            {234.75517200000004, 434.616786},
            {194.32106999999996, 434.616786},
            {140.31273600000006, 434.616786},
            {140.31273600000006, 482.369256},
            {140.31273600000006, 541.5129359999999},
            {140.31273600000006, 572.310018},
            {198.25686000000024, 514.8263340000003},
            {244.18776600000012, 514.8263340000003},
            {287.6225220000002, 514.8263340000003},
            {148.28664600000022, 1052.1821880000011},
            {189.00050400000023, 1052.1821880000011},
            {336.39229800000044, 1052.1821880000011},
            {380.59257600000046, 1052.1821880000011},
            {380.59257600000046, 998.9927640000011},
            {331.7295420000004, 998.9927640000011},
            {179.3936340000005, 998.9927640000011},
            {148.40080200000048, 998.9927640000011},
            {339.50449799999996, 606.8423520000002},
            {339.8707980000001, 583.7993460000002},
            {339.8707980000001, 537.757506},
            {339.8707980000001, 475.0535879999998},
            {339.8707980000001, 434.616786},
            {287.83414800000014, 434.616786},
            {234.75517200000004, 434.616786},
            {194.32106999999996, 434.616786},
            {140.31273600000006, 434.616786},
            {140.31273600000006, 482.369256},
            {140.31273600000006, 541.5129359999999},
            {140.31273600000006, 572.310018},
            {198.25686000000024, 514.8263340000003},
            {244.18776600000012, 514.8263340000003},
            {287.6225220000002, 514.8263340000003},
            {148.28664600000022, 1052.1821880000011},
            {189.00050400000023, 1052.1821880000011},
            {336.39229800000044, 1052.1821880000011},
            {380.59257600000046, 1052.1821880000011},
            {380.59257600000046, 998.9927640000011},
            {331.7295420000004, 998.9927640000011},
            {179.3936340000005, 998.9927640000011},};

        int idx = 1;
        for (double[] p : COLLISIONS) {
            obstacles.add(new Obstacle(
                    new Rectangle2D(p[0], p[1], 32, 32),
                    ObstacleType.BLOCK,
                    "SkyCollision" + idx
            ));
            idx++;
        }
    }

    // ---------------- movimiento y entradas ----------------
    private void positionHeroAtEntrance() {
        // Ajusta estas coordenadas al punto de entrada real del primer piso
        double startX = 676.7317480000008;
        double startY = 1152.0;
        heroView.setLayoutX(startX);
        heroView.setLayoutY(startY);
        updateCamera();
    }

    private void createStartRectAtHeroStart() {

        if (startRect != null) {
            world.getChildren().remove(startRect);
            startRect = null;
        }

        // Coordenadas iniciales del héroe
        double[] xs = {676.7317480000008, 739.1579980000006, 619.6053460000007};
        double[] ys = {1152.0, 1152.0, 1152.0};

        // Calcular límites mínimos y máximos
        double minX = Arrays.stream(xs).min().getAsDouble();
        double maxX = Arrays.stream(xs).max().getAsDouble();
        double minY = Arrays.stream(ys).min().getAsDouble();
        double maxY = Arrays.stream(ys).max().getAsDouble();

        // Definir rectángulo que cubra toda la zona inicial
        double rx = minX;
        double ry = minY - HERO_H; // un poco hacia arriba para cubrir al héroe
        double rw = (maxX - minX) + HERO_W;
        double rh = HERO_H + 20;   // altura suficiente

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

    private void createCastleRect() {

        if (castleRect != null) {
            world.getChildren().remove(castleRect);
            castleRect = null;
        }

        // Coordenadas de avance (entrada al segundo piso)
        double[] xs = {746.5826560000006, 702.1915240000005, 663.7390240000004};
        double[] ys = {0.0, 0.0, 0.0};

        // Calcular límites mínimos y máximos
        double minX = Arrays.stream(xs).min().getAsDouble();
        double maxX = Arrays.stream(xs).max().getAsDouble();
        double minY = Arrays.stream(ys).min().getAsDouble();
        double maxY = Arrays.stream(ys).max().getAsDouble();

        // Definir rectángulo que cubra toda la zona de avance
        double rx = minX;
        double ry = minY;
        double rw = (maxX - minX) + HERO_W; // ancho cubriendo todo el rango
        double rh = HERO_H + 20;            // altura suficiente para detectar al héroe

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
                System.out.println("Hero position (CastleFirstFloor): (" + heroView.getLayoutX() + ", " + heroView.getLayoutY() + ")");
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
                // Salida (volver al mapa anterior)
                checkExitTrigger();
                // Avance (ir al siguiente piso del castillo)
                checkCastleTrigger();
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
            URL res = getClass().getResource("/Resources/music/skyFinalDungeon.mp3");
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

        if (startRect != null) {
            startRect.setFill(debugEnabled ? Color.rgb(0, 120, 255, 0.42) : Color.rgb(0, 120, 255, 0.28));
        }
        if (castleRect != null) {
            castleRect.setFill(debugEnabled ? Color.rgb(200, 120, 0, 0.42) : Color.rgb(200, 120, 0, 0.28));
        }
        heroView.toFront();
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

    private void openDebugCombat() {
        String bg = "/Resources/textures/Battle/castleBattle.png";
        stopMapMusic();

        GUI.CombatScreen cs = new GUI.CombatScreen(game, bg, "Sky", game.getHero(), false, null);
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
                root.requestFocus();
                startMapMusic();
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

    // Trigger de salida: volver al mapa anterior
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
                try {
                    if (game != null && game.getHero() != null) {
                        Hero h = game.getHero();
                        h.setLastLocation(Hero.Location.SKY_DUNGEON);
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
                    onExitCallback.run(); // vuelve al mapa anterior
                } else {
                    hide();
                }
            }
        }
    }

    //Trigger de avance: entrar a la siguiente zona (por ejemplo, CastleSecondFloor)
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
                hide();

                // Avanza a la siguiente pantalla del castillo
                CastleLastFloor next = new CastleLastFloor(game);
                next.showWithLoading(null, () -> {
                    Platform.runLater(() -> {
                        FXGL.getGameScene().addUINode(root);
                        startDungeonMusic("/Resources/music/skyFinalDungeon.mp3");
                        root.requestFocus();
                        startMover();
                    });
                });
            }
        }
    }
}
