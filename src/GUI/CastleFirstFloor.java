package GUI;

import Characters.Boss;
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

public class CastleFirstFloor {

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

    private final boolean onStartRect = false;
    private Runnable onExitCallback;
    private Rectangle startRect;
    private Rectangle castleRect;
    private final Game game;

    private ImageView bossView;

    // Sistema de colisiones
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final List<Rectangle> bossTriggerRects = new ArrayList<>();
    private boolean debugEnabled = false; // R para ver/ocultar áreas de trigger

    private InventoryScreen inventory;

    // Direcciones del héroe (si quieres usarlas para depuración)
    public enum Direction {
        NONE, N, NE, E, SE, S, SW, W, NW
    }
    private final Direction currentDirection = Direction.NONE;

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

    public CastleFirstFloor(Game game) {
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

            boolean imageOk = loadBackgroundImage("/Resources/textures/skyDungeon/1stFloor.png");
            boolean musicOk = startDungeonMusic("/Resources/music/skyFinalDungeon.mp3");
            if (!game.getHero().existsCompletedTask(game.getTasks().get(8)) && !game.getHero().existsPendingTask(game.getTasks().get(8))) {
                game.getHero().addTasks(game.searchTask("M010"));
            }
            populateCastleObstacles();
            positionHeroAtEntrance();
            createStartRectAtHeroStart();
            createCastleRect();
            drawBossDungeon();

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
        Text label = new Text("Loading Castle First Floor...");
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
            {296.2478380000003, 1152.0},
            {296.2478380000003, 1129.3322940000005},
            {296.2478380000003, 1085.2612740000002},
            {296.2478380000003, 1060.9124580000002},
            {250.7997820000003, 1048.5231119999994},
            {250.7997820000003, 1012.5364859999995},
            {250.7997820000003, 960.5428379999993},
            {250.7997820000003, 921.1491179999991},
            {293.2565980000002, 921.1491179999991},
            {345.15365800000023, 921.1491179999991},
            {37.48712799999992, 967.7208599999991},
            {37.48712799999992, 1013.6539079999991},
            {37.48712799999992, 1047.2750639999983},
            {0.0, 1047.2750639999983},
            {0.0, 1099.0416779999987},
            {0.0, 1152.0},
            {0.0, 912.8201580000007},
            {0.0, 886.7218500000005},
            {0.0, 841.5833760000004},
            {0.0, 786.8494080000003},
            {38.17996200000001, 811.1827620000004},
            {38.17996200000001, 782.3740860000005},
            {38.17996200000001, 667.9219320000009},
            {0.0, 716.9204160000008},
            {0.0, 591.0488280000009},
            {39.510756000000015, 565.0211880000008},
            {0.0, 517.7887740000009},
            {43.059960000000004, 473.69228400000077},
            {43.059960000000004, 442.8566100000007},
            {0.0, 377.82981000000063},
            {42.261174000000025, 327.00153600000044},
            {95.69016000000002, 327.00153600000044},
            {289.32471000000004, 327.00153600000044},
            {340.2280439999999, 327.00153600000044},
            {383.54459399999996, 327.00153600000044},
            {383.54459399999996, 280.7732880000005},
            {383.54459399999996, 240.35688000000047},
            {340.20871199999993, 240.35688000000047},
            {386.5580819999998, 323.00872200000066},
            {386.5580819999998, 380.6918460000006},
            {386.5580819999998, 438.4896300000008},
            {349.9346519999998, 487.7949420000007},
            {349.9346519999998, 531.0185040000006},
            {348.55491599999993, 555.014826000001},
            {376.1124479999999, 579.8563740000012},
            {340.16022, 617.1570900000011},
            {389.361096, 617.1570900000011},
            {389.361096, 666.0357120000009},
            {349.7865479999999, 723.8565720000009},
            {384.74899199999993, 757.5118740000012},
            {384.74899199999993, 819.4915440000011},
            {384.74899199999993, 857.0035980000008},
            {85.25852999999998, 278.9600580000006},
            {85.25852999999998, 249.99654600000054},
            {40.58922599999997, 249.99654600000054},
            {0.0, 249.99654600000054},
            {0.0, 186.2794440000005},
            {0.0, 126.9418320000005},
            {0.0, 92.44296000000048},
            {56.749283999999996, 92.44296000000048},
            {101.53485000000002, 118.21105800000043},
            {153.63458999999997, 118.21105800000043},
            {179.36258399999997, 118.21105800000043},
            {210.97069199999996, 118.21105800000043},
            {233.86030199999996, 118.21105800000043},
            {259.926678, 118.21105800000043},
            {291.5924039999999, 118.21105800000043},
            {346.19882399999995, 95.17960800000043},
            {392.38533, 95.17960800000043},
            {417.735558, 95.17960800000043},
            {441.13355999999993, 95.17960800000043},
            {469.2464279999999, 95.17960800000043},
            {491.1102719999999, 95.17960800000043},
            {533.8002059999999, 126.83737800000046},
            {580.4071019999998, 126.83737800000046},
            {626.4014219999999, 126.83737800000046},
            {675.8506439999994, 89.32642200000046},
            {724.8145139999996, 89.32642200000046},
            {767.8927799999997, 89.32642200000046},
            {767.8927799999997, 129.93741000000045},
            {767.8927799999997, 176.48713800000044},
            {767.8927799999997, 215.79067800000055},
            {767.8927799999997, 274.0139820000005},
            {767.8927799999997, 313.1825940000005},
            {681.5256660000001, 317.2183020000005},
            {724.9390200000003, 317.2183020000005},
            {698.9149800000005, 291.2920020000006},
            {675.9730260000007, 384.3714960000005},
            {675.9730260000007, 423.3678660000005},
            {675.9730260000007, 480.16798200000045},
            {675.9730260000007, 521.5745700000004},
            {730.5831720000009, 568.1326140000002},
            {775.5928560000008, 568.1326140000002},
            {775.5928560000008, 634.4678879999998},
            {775.5928560000008, 675.6373979999998},
            {775.5928560000008, 712.2538979999999},
            {775.5928560000008, 750.6111060000001},
            {729.6144840000006, 753.3997740000001},
            {729.6144840000006, 802.392912},
            {729.6144840000006, 851.3323379999996},
            {682.4266740000006, 851.3323379999996},
            {682.4266740000006, 803.2733279999997},
            {472.415976000001, 803.2733279999997},
            {472.415976000001, 860.8925879999997},
            {435.629610000001, 860.8925879999997},
            {435.629610000001, 821.3915879999998},
            {435.629610000001, 766.0423079999998},
            {425.063664000001, 708.4700459999998},
            {474.2681760000008, 708.4700459999998},
            {474.2681760000008, 658.3527719999995},
            {474.2681760000008, 618.0709499999998},
            {474.2681760000008, 583.629966},
            {429.5799180000008, 583.629966},
            {429.5799180000008, 623.9324880000001},
            {429.5799180000008, 670.1124780000001},
            {429.5799180000008, 525.72564},
            {390.3332220000009, 479.7060840000001},
            {385.23351600000075, 915.2130600000008},
            {375.1654680000009, 984.5677800000009},
            {375.1654680000009, 1048.171446000001},
            {424.6002720000009, 1054.7432280000012},
            {372.7118700000009, 1108.3981860000015},
            {439.07680800000094, 1152.0},
            {496.4789160000009, 1152.0},
            {536.846364000001, 1152.0},
            {571.3107120000008, 1152.0},
            {597.0879180000007, 1152.0},
            {652.052754000001, 1152.0},
            {678.0187260000008, 1152.0},
            {707.0038020000007, 1152.0},
            {719.0317260000008, 1117.3958279999995},
            {778.4247780000012, 1117.3958279999995},
            {292.06703200000015, 247.94679600000114},
            {426.59181400000176, 280.21458600000045},
            {426.59181400000176, 332.2148940000003},
            {577.6412080000028, 1006.2766260000009},
            {778.6651540000025, 891.4767300000018},
            {810.4050220000024, 891.4767300000018},
            {881.2985620000029, 891.4767300000018},
            {913.045540000003, 891.4767300000018},
            {952.7206180000034, 891.4767300000018},
            {818.5869040000036, 1152.0},
            {852.0385180000039, 1152.0},
            {901.0315480000038, 1152.0},
            {947.1718660000037, 1152.0},
            {993.3383200000035, 1152.0},
            {1041.2895640000033, 1152.0},
            {1091.2720360000033, 1152.0},
            {1143.1867180000038, 1152.0},
            {1194.995524000004, 1152.0},
            {1238.3371840000034, 1152.0},
            {1284.6275140000037, 1152.0},
            {1333.7169340000037, 1152.0},
            {1379.9257060000034, 1152.0},
            {1392.0, 1092.578202},
            {1345.884701999999, 1046.6130419999995},
            {1348.839167999999, 1010.2970879999997},
            {1383.4986899999988, 953.2900619999999},
            {1383.4986899999988, 901.4326379999998},
            {1337.4966839999988, 901.4326379999998},
            {1337.4966839999988, 856.3498379999997},
            {1337.4966839999988, 819.8474399999998},
            {1288.7381939999989, 819.8474399999998},
            {1248.5951159999988, 819.8474399999998},
            {1209.3301139999987, 819.8474399999998},
            {1209.3301139999987, 866.2177799999998},
            {1209.3301139999987, 909.508338},
            {1258.4242139999985, 909.508338},
            {1304.4730379999983, 909.508338},
            {950.1551879999994, 821.7536579999999},
            {901.1545259999989, 821.7536579999999},
            {861.5347439999991, 821.7536579999999},
            {806.8360559999992, 821.7536579999999},
            {770.6786819999991, 821.7536579999999},
            {808.8018880000042, 179.53318799999997},
            {864.5168920000042, 179.53318799999997},
            {921.297010000004, 179.53318799999997},
            {961.6191160000038, 179.53318799999997},
            {961.6191160000038, 142.14473999999996},
            {961.6191160000038, 93.63970799999994},
            {961.6191160000038, 53.73709199999995},
            {961.6191160000038, 13.236335999999948},
            {961.6191160000038, 0.0},
            {1198.8770440000053, 0.0},
            {1198.8770440000053, 30.925512},
            {1198.8770440000053, 74.00312999999998},
            {1198.8770440000053, 123.26499},
            {1198.8770440000053, 174.3044580000001},
            {1198.8770440000053, 184.06798200000017},
            {1240.9021660000046, 184.06798200000017},
            {1286.9842180000048, 184.06798200000017},
            {1313.077270000005, 184.06798200000017},
            {1392.0, 184.06798200000017},
            {1392.0, 227.15193600000015},
            {1392.0, 270.2687040000001},
            {1392.0, 316.4139180000001},
            {1392.0, 362.54800800000015},
            {1392.0, 427.3276680000002},
            {1392.0, 480.4902000000003},
            {1392.0, 552.5992800000001},
            {1392.0, 616.5798659999999},
            {1392.0, 685.805508},
            {1392.0, 754.7146199999997},
            {1392.0, 800.8220699999998},
            {1392.0, 852.8006699999997},
            //{1249.1151899999993, 659.6071919999999},
            //{1249.1151899999993, 323.5194539999998},
            //{912.1870499999998, 323.5194539999998},
            //{912.1870499999998, 653.7848219999997},
            //{962.1329639999994, 609.5000339999996},
            //{962.1329639999994, 569.3782139999993},
            //{962.1329639999994, 520.2160199999993},
            //{962.1329639999994, 465.3123479999991},
            //{962.1329639999994, 413.6303519999991},
            //{962.1329639999994, 391.5928979999992},
            //{1010.9568659999999, 387.1636559999991},
            //{1060.0051739999994, 387.1636559999991},
            //{1106.128697999999, 387.1636559999991},
            //{1159.9962719999992, 387.1636559999991},
            //{1206.1673879999996, 387.1636559999991},
            //{1198.7255220000004, 424.617533999999},
            // {1198.7255220000004, 467.8558199999991},
            //{1198.7255220000004, 511.2582839999991},
            //{1198.7255220000004, 573.4482659999995},
            //{1198.7255220000004, 619.9343099999995},
            //{1156.738614, 619.9343099999995},
            //{1122.017262, 619.9343099999995},
            //{1074.0203699999997, 619.9343099999995},
            //{1025.9108700000006, 619.9343099999995},
            //{999.9884400000005, 619.9343099999995},
            {761.504349999997, 520.045613999999},
            {761.504349999997, 475.04381399999903},
            {761.504349999997, 421.45439399999907},
            {761.504349999997, 382.54289399999897},
            {1343.2107839999999, 191.70138600000038},
            {1202.6482240000016, 216.0994499999999},
            {958.8813520000008, 216.0994499999999},
            {1009.5462400000008, 343.05357600000036},
            {1009.5462400000008, 380.2682340000003},
            {963.2136640000007, 388.9835820000005},
            {963.2136640000007, 428.74131600000067},
            {963.2136640000007, 479.0877120000007},
            {963.2136640000007, 513.7083180000005},
            {963.2136640000007, 558.7683480000003},
            {963.2136640000007, 575.52354},
            {1003.6565860000007, 575.52354},
            {1003.6565860000007, 615.7521},
            {1003.6565860000007, 663.950268},
            {966.6391180000011, 663.950268},
            {917.7773080000013, 663.950268},
            {871.1948920000015, 663.950268},
            {821.681590000001, 663.950268},
            {818.6232640000013, 335.88198},
            {870.535786000001, 335.88198},
            {897.0597760000009, 335.88198},
            {937.4012500000009, 335.88198},
            {979.3791400000007, 335.88198},
            {997.9825000000009, 335.88198},
            {1154.3612260000023, 335.1906720000003},
            {1154.3612260000023, 376.08125400000034},
            {1195.7872900000023, 376.08125400000034},
            {1206.9642640000034, 413.5596300000003},
            {1206.9642640000034, 462.54229200000026},
            {1206.9642640000034, 499.91767200000015},
            {1206.9642640000034, 539.3621340000003},
            {1206.9642640000034, 585.4395780000002},
            {1158.3943600000025, 585.4395780000002},
            {1158.3943600000025, 625.9600800000002},
            {1158.3943600000025, 660.4306740000002},
            {1198.7835880000023, 660.4306740000002},
            {1281.2065240000004, 664.2438480000014},
            {1304.2612120000003, 664.2438480000014},
            {1343.7435999999998, 664.2438480000014},
            {1343.7435999999998, 339.73151400000177},
            {1302.8223280000004, 339.73151400000177},
            {1282.2655540000012, 339.73151400000177},
            {1155.414226000003, 343.03762800000226},
            {1198.4818180000036, 343.03762800000226},
            {1232.2579720000033, 668.709216000001},
            {1234.1169220000038, 328.656096000001}

        };

        int idx = 1;
        for (double[] p : COLLISIONS) {
            obstacles.add(new Obstacle(
                    new Rectangle2D(p[0], p[1], 32, 32),
                    ObstacleType.BLOCK,
                    "SkyCollision" + idx
            ));
            idx++;
        }

        if (!game.getHero().existsCompletedTask(game.getTasks().get(8))) {
            double[][] COLLISIONS2 = new double[][]{
                {995.834253999998, 516.9851820000019},
                {1027.9402959999986, 516.9851820000019},
                {1068.6698859999985, 516.9851820000019},
                {1108.673337999998, 516.9851820000019},
                {1151.8351959999975, 516.9851820000019}};

            for (double[] p : COLLISIONS2) {
                double x = p[0];
                double y = p[1];
                obstacles.add(new Obstacle(
                        new Rectangle2D(x, y, 30, 30),
                        ObstacleType.PLANT,
                        "Colision" + idx
                ));
                idx++;
            }
        }
    }

    // ---------------- movimiento y entradas ----------------
    private void positionHeroAtEntrance() {
        // Ajusta estas coordenadas al punto de entrada real del primer piso
        double startX = 144.83416000000022;
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
        double[] xs = {193.69503400000008, 144.8692240000001, 87.42013600000007, 52.84427800000007, 224.80792600000004};
        double[] ys = {1152.0, 1152.0, 1152.0, 1152.0, 1152.0};

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
        double[] xs = {1083.136233999999, 1042.9659039999992, 1135.0466499999986};
        double minX = Arrays.stream(xs).min().getAsDouble();
        double maxX = Arrays.stream(xs).max().getAsDouble();
        double minY = 0.0;
        double maxY = 0.0;

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
                if (bossView != null) {
                    checkBossTriggers();
                }
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

        boolean shouldMove = !(vx == 0 && vy == 0);
        if (shouldMove) {
            moveHero(vx * dt, vy * dt);
        }
    }

    private void moveHero(double dx, double dy) {
        boolean proceed = true;
        if (heroView == null) {
            proceed = false;
        }

        double curX = 0;
        double curY = 0;
        if (proceed) {
            curX = heroView.getLayoutX();
            curY = heroView.getLayoutY();
        }

        double proposedX = curX;
        double proposedY = curY;
        if (proceed) {
            proposedX = clamp(curX + dx, 0, Math.max(0, worldW - HERO_W));
            proposedY = clamp(curY + dy, 0, Math.max(0, worldH - HERO_H));
        }

        boolean collision = false;
        boolean canMoveX = true;
        boolean canMoveY = true;

        if (proceed && obstacles != null) {
            Rectangle2D heroRect = new Rectangle2D(proposedX, proposedY, HERO_W, HERO_H);
            Rectangle2D heroRectX = new Rectangle2D(proposedX, curY, HERO_W, HERO_H);
            Rectangle2D heroRectY = new Rectangle2D(curX, proposedY, HERO_W, HERO_H);

            for (Obstacle ob : obstacles) {
                boolean valid = (ob != null && ob.collisionRect != null);
                if (valid) {
                    if (!collision) {
                        if (heroRect.intersects(ob.collisionRect)) {
                            collision = true;
                        }
                    }
                    if (canMoveX) {
                        if (heroRectX.intersects(ob.collisionRect)) {
                            canMoveX = false;
                        }
                    }
                    if (canMoveY) {
                        if (heroRectY.intersects(ob.collisionRect)) {
                            canMoveY = false;
                        }
                    }
                }
            }
        }

        if (proceed) {
            if (!collision) {
                heroView.setLayoutX(proposedX);
                heroView.setLayoutY(proposedY);
            } else {
                if (canMoveX) {
                    heroView.setLayoutX(proposedX);
                }
                if (canMoveY) {
                    heroView.setLayoutY(proposedY);
                }
            }
            updateCamera();
        }
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
        // Aquí no hay obstáculos; resaltamos triggers si debugEnabled
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
                startMapMusic();
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
                CastleSecondFloor next = new CastleSecondFloor(game);
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

    //TODO LO RELACIONADO AL BOSS
    public void drawBossDungeon() {
        if (!game.getHero().existsCompletedTask(game.getTasks().get(8))) {
            createBossTriggerRects();
            if (bossView != null) {
                if (!world.getChildren().contains(bossView)) {
                    world.getChildren().add(bossView);
                }
                bossView.toFront();
                return;
            }

            try {
                Image img = new Image(getClass().getResourceAsStream("/Resources/sprites/Monsters/skyBoss01.png"));
                bossView = new ImageView(img);

                bossView.setPreserveRatio(true);
                bossView.setFitWidth(200);
                bossView.setFitHeight(200);
                bossView.setMouseTransparent(true);

                bossView.setLayoutX(1000.0846699999993);
                bossView.setLayoutY(400.98027399999916);

                bossView.getProperties().put("tag", "sky_boss");

                if (!world.getChildren().contains(bossView)) {
                    world.getChildren().add(bossView);
                }
                bossView.toFront();

            } catch (Throwable t) {
                System.err.println("No se pudo cargar la imagen del boss: " + t.getMessage());
            }

        } else {
            if (bossView != null) {
                try {
                    world.getChildren().remove(bossView);
                } catch (Throwable ignored) {
                }
                bossView = null;
            }
        }
    }

    private void createBossTriggerRects() {
        if (!game.getHero().existsCompletedTask(game.getTasks().get(3))) {
            for (Rectangle r : bossTriggerRects) {
                try {
                    world.getChildren().remove(r);
                } catch (Throwable ignored) {
                }
            }
            bossTriggerRects.clear();

            double[][] TRIGGERS = new double[][]{
                {995.834253999998, 516.9851820000019},
                {1027.9402959999986, 516.9851820000019},
                {1068.6698859999985, 516.9851820000019},
                {1108.673337999998, 516.9851820000019},
                {1151.8351959999975, 516.9851820000019},};

            for (int i = 0; i < TRIGGERS.length; i++) {
                double x = TRIGGERS[i][0];
                double y = TRIGGERS[i][1];
                double w = HERO_W + 8;
                double h = HERO_H + 8;
                Rectangle r = new Rectangle(x - 4, y - 4, w, h);
                r.setFill(Color.color(0, 0, 0, 0.0));
                r.setStroke(null);
                r.setMouseTransparent(true);
                r.getProperties().put("tag", "boss_trigger");
                r.getProperties().put("id", "bossTRigger" + (i + 1));
                bossTriggerRects.add(r);
                if (!world.getChildren().contains(r)) {
                    world.getChildren().add(r);
                }
            }
            heroView.toFront();
        }
    }

    public void redrawRoomAfterBoss() {
        Platform.runLater(() -> {
            try {
                if (bossView != null) {
                    try {
                        world.getChildren().remove(bossView);
                    } catch (Throwable ignored) {
                    }
                    bossView = null;
                }

                try {
                    for (Rectangle r : bossTriggerRects) {
                        world.getChildren().remove(r);
                    }
                    bossTriggerRects.clear();
                } catch (Throwable ignored) {
                }

                obstacles.clear();
                populateCastleObstacles();
                if (!world.getChildren().contains(heroView)) {
                    world.getChildren().add(heroView);
                }
                heroView.toFront();

                checkExitTrigger();

                updateCamera();

                FadeTransition ft = new FadeTransition(Duration.millis(260), root);
                ft.setFromValue(0.95);
                ft.setToValue(1.0);
                ft.play();

            } catch (Throwable t) {
                System.err.println("Error al redibujar la sala tras boss: " + t.getMessage());
            }
        });
    }

    private void checkBossTriggers() {
        boolean combat = false;

        if (bossView != null) {
            double hx = heroView.getLayoutX();
            double hy = heroView.getLayoutY();
            Rectangle2D heroRect = new Rectangle2D(hx, hy, HERO_W, HERO_H);

            for (Rectangle trigger : bossTriggerRects) {
                Rectangle2D tr = new Rectangle2D(trigger.getX(), trigger.getY(), trigger.getWidth(), trigger.getHeight());
                if (heroRect.intersects(tr)) {
                    combat = true;
                }
            }
        }

        if (combat) {
            battleAgainstBoss((Boss) game.getCharacters().get(24));
        }
    }
//cambiar fondo

    private void battleAgainstBoss(Boss boss) {
        String bg = "/Resources/textures/Battle/castleBattle.png";
        stopMapMusic();

        CombatScreen cs = new GUI.CombatScreen(game, bg, "Sky", game.getHero(), true, boss);

        cs.setBattleMusicPath("/Resources/music/bossBattle1.mp3");

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
        game.completeMainM010();
        redrawRoomAfterBoss();

    }
}
