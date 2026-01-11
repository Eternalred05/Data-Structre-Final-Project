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

public class CastleLastFloor {

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
    private Rectangle orbNode = null;
    private Rectangle2D orbTrigger = null;
    private Text orbHintText = null;

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

    private final List<Rectangle> dungeonTriggerRects = new ArrayList<>();
    private final List<Rectangle> bossTriggerRects = new ArrayList<>();

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

    public CastleLastFloor(Game game) {
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

            boolean imageOk = loadBackgroundImage("/Resources/textures/skyDungeon/lastFloor.png");
            startMapMusic();
            if (!game.getHero().existsCompletedTask(game.getTasks().get(0)) && !game.getHero().existsPendingTask(game.getTasks().get(0))) {
                game.getHero().addTasks(game.searchTask("M000"));
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
        Text label = new Text("Loading Castle Third Floor...");
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
        boolean started = false;
        try {
            URL res = getClass().getResource(path);
            if (res != null) {
                Media media = new Media(res.toExternalForm());
                stopDungeonMusic();
                music = new MediaPlayer(media);
                music.setCycleCount(MediaPlayer.INDEFINITE);
                music.setVolume(MainScreen.getVolumeSetting());
                music.play();
                started = true;
            }
        } catch (Exception t) {
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
    private void populateCastleObstacles() {
        obstacles.clear();

        double[][] COLLISIONS = new double[][]{
            {0.0, 0.0},
            {0.0, 55.063224000000005},
            {0.0, 90.51667199999996},
            {0.0, 154.20774599999996},
            {0.0, 205.6764599999999},
            {0.0, 250.7237099999999},
            {96.98551199999999, 239.28992999999994},
            {146.06571599999998, 239.28992999999994},
            {0.0, 286.7909039999999},
            {0.0, 333.5569199999996},
            {41.83441200000002, 333.5569199999996},
            {0.0, 387.8647019999995},
            {0.0, 422.41854599999954},
            {0.0, 479.87389799999943},
            {50.026428, 479.87389799999943},
            {0.0, 528.2907119999996},
            {42.686567999999994, 528.2907119999996},
            {0.0, 580.4907839999996},
            {0.0, 626.6652479999996},
            {0.0, 678.9279599999994},
            {0.0, 715.5293579999992},
            {0.0, 758.3698799999991},
            {0.0, 818.7288659999987},
            {0.0, 856.0164239999986},
            {0.0, 909.6303959999985},
            {0.0, 957.3166259999986},
            {0.0, 1014.0090119999985},
            {0.0, 1057.2651899999987},
            {0.0, 1109.689397999999},
            {0.0, 1152.0},
            {55.60106399999997, 1152.0},
            {48.21443999999998, 814.8999419999991},
            {48.21443999999998, 774.5608259999991},
            {87.55669799999997, 1152.0},
            {135.86507999999995, 1152.0},
            {170.524134, 1013.6678580000003},
            {170.524134, 1152.0},
            {195.842088, 1152.0},
            {195.842088, 668.9745719999997},
            {195.842088, 625.635648},
            {243.8953559999999, 0.0},
            {243.8953559999999, 42.867054},
            {243.8953559999999, 84.69574200000004},
            {243.65442599999983, 331.91954999999973},
            {243.65442599999983, 379.07825399999984},
            {243.65442599999983, 425.14500599999985},
            {243.65442599999983, 482.59618199999994},
            {243.65442599999983, 522.6386219999999},
            {243.65442599999983, 577.0704779999999},
            {243.65442599999983, 613.15542},
            {243.65442599999983, 662.1037020000001},
            {243.65442599999983, 718.4441160000001},
            {243.65442599999983, 767.3402519999999},
            {243.65442599999983, 819.7603739999997},
            {243.65442599999983, 863.0290799999993},
            {243.65442599999983, 1152.0},
            {289.46408399999984, 1152.0},
            {289.46408399999984, 766.9259819999996},
            {289.46408399999984, 720.9482399999994},
            {289.46408399999984, 668.9190239999996},
            {289.46408399999984, 432.45419400000014},
            {289.46408399999984, 383.1878880000001},
            {289.46408399999984, 341.2199160000002},
            {289.46408399999984, 0.0},
            {289.46408399999984, 57.642371999999995},
            {289.46408399999984, 91.63483200000003},
            {333.4540139999998, 91.85308200000006},
            {382.46149799999966, 91.85308200000006},
            {420.5364299999997, 91.85308200000006},
            {458.10986399999956, 91.85308200000006},
            {502.50038399999937, 91.85308200000006},
            {540.3173219999994, 91.85308200000006},
            {566.1093419999996, 91.85308200000006},
            {595.008198, 91.85308200000006},
            {642.7034280000001, 91.85308200000006},
            {668.598156, 91.85308200000006},
            {668.4238620000001, 91.85308200000006},
            {722.9389859999999, 91.85308200000006},
            {625.0447260000001, 129.30768000000006},
            {547.7926499999999, 129.30768000000006},
            {507.36738599999984, 129.30768000000006},
            {472.7671559999997, 129.30768000000006},
            {452.5966079999998, 129.30768000000006},
            {435.3420419999997, 129.30768000000006},
            {412.3027439999997, 129.30768000000006},
            {334.8961199999996, 129.30768000000006},
            {436.0264919999994, 187.734114},
            {482.1295499999993, 187.734114},
            {528.5157479999995, 187.734114},
            {576.0794159999994, 233.99909999999997},
            {384.0405839999993, 233.99909999999997},
            {320.0656540000015, 1152.0},
            {366.20750200000145, 1152.0},
            {404.12416000000144, 1152.0},
            {458.72330800000145, 1152.0},
            {497.7951340000014, 1152.0},
            {526.9755280000012, 1152.0},
            {526.9755280000012, 1099.4496839999997},
            {526.9755280000012, 1051.645266},
            {526.9755280000012, 1003.78656},
            {526.9755280000012, 958.05828},
            {526.9755280000012, 910.5250319999998},
            {526.9755280000012, 861.969564},
            {526.9755280000012, 820.1014020000001},
            {526.9755280000012, 770.508954},
            {526.9755280000012, 727.3074060000002},
            {526.9755280000012, 682.4024820000005},
            {578.0089300000013, 682.4024820000005},
            {578.0089300000013, 725.7191400000005},
            {578.0089300000013, 760.0096800000001},
            {776.6954320000017, 763.0014420000003},
            {771.0241720000016, 717.2559360000009},
            {771.0241720000016, 673.8889140000007},
            {819.7233340000015, 673.8889140000007},
            {866.1350200000016, 673.8889140000007},
            {919.2608860000016, 673.8889140000007},
            {961.7804500000017, 673.8889140000007},
            {822.2916640000017, 673.8889140000007},
            {817.7912320000019, 623.4244020000006},
            {817.7912320000019, 576.2355660000009},
            {817.7912320000019, 523.959192000001},
            {817.7912320000019, 477.78561000000093},
            {817.7912320000019, 431.4694500000011},
            {817.7912320000019, 378.23306400000126},
            {817.7912320000019, 320.5831860000013},
            {817.7912320000019, 271.5173460000015},
            {817.7912320000019, 231.21027000000143},
            {817.7912320000019, 176.86332000000138},
            {817.7912320000019, 110.69625600000141},
            {817.7912320000019, 55.7306640000014},
            {817.7912320000019, 32.666472000001406},
            {817.7912320000019, 0.0},
            {863.7080260000017, 0.0},
            {914.6407720000021, 0.0},
            {954.7409920000024, 0.0},
            {857.4439360000019, 91.89412200000001},
            {894.7687900000018, 91.89412200000001},
            {932.2872160000014, 91.89412200000001},
            {969.8638900000013, 91.89412200000001},
            {1006.1327020000012, 91.89412200000001},
            {1043.6888380000016, 91.89412200000001},
            {1066.8797500000014, 91.89412200000001},
            {1095.0524860000014, 91.89412200000001},
            {1121.023624000001, 91.89412200000001},
            {1170.0421780000008, 91.89412200000001},
            {1198.819768000001, 91.89412200000001},
            {1229.732320000001, 91.89412200000001},
            {1258.6088920000002, 91.89412200000001},
            {1284.5712099999998, 91.89412200000001},
            {1310.6561619999993, 91.89412200000001},
            {1345.5207579999988, 91.89412200000001},
            {1392.0, 91.89412200000001},
            {1392.0, 138.12811200000004},
            {1392.0, 187.18898399999998},
            {1392.0, 244.502622},
            {1392.0, 281.81512800000013},
            {1392.0, 324.8656200000002},
            {1392.0, 388.04236200000025},
            {1392.0, 428.01895800000034},
            {1392.0, 461.6288460000004},
            {1392.0, 490.25471400000043},
            {1392.0, 519.2242920000006},
            {1392.0, 550.2857940000005},
            {1392.0, 579.146346},
            {1392.0, 608.0502419999998},
            {1392.0, 636.0547319999997},
            {1392.0, 666.6199739999996},
            {1339.2950999999998, 666.6199739999996},
            {1284.241775999999, 666.6199739999996},
            {1251.1786739999986, 666.6199739999996},
            {1251.1786739999986, 764.2171619999999},
            {1268.5062839999987, 764.2171619999999},
            {1268.5062839999987, 807.8828040000003},
            {1291.466021999998, 807.8828040000003},
            {1323.1976459999976, 807.8828040000003},
            {1323.1976459999976, 760.126644},
            {1360.7913479999972, 760.126644},
            {1392.0, 760.126644},
            {1392.0, 806.4419580000001},
            {1392.0, 849.8471940000001},
            {1392.0, 898.628292},
            {1392.0, 927.222174},
            {1392.0, 956.0258279999998},
            {1392.0, 1010.4357779999998},
            {1392.0, 1024.8721019999998},
            {1392.0, 1060.965972},
            {1392.0, 1086.668838},
            {1392.0, 1121.2867800000001},
            {1392.0, 1147.2891300000001},
            {1343.1792840000005, 1152.0},
            {1285.2179880000006, 1152.0},
            {1259.2960440000006, 1152.0},
            {1224.7344960000005, 1152.0},
            {1193.8364700000004, 1152.0},
            {1162.029444000001, 1152.0},
            {1110.2356860000011, 1152.0},
            {1078.518858000001, 1152.0},
            {1052.8227780000013, 1152.0},
            {1016.3038380000012, 1152.0},
            {978.9139140000015, 1152.0},
            {950.0834220000016, 1152.0},
            {915.8376840000016, 1152.0},
            {884.0272920000015, 1152.0},
            {853.4348700000016, 1152.0},
            {806.7628860000018, 1152.0},
            {783.3018120000015, 1152.0},
            {745.8596520000015, 1152.0},
            {722.7062520000014, 1152.0},
            {680.8721820000015, 1152.0},
            {652.0134480000016, 1152.0},
            {603.2013720000016, 1152.0},
            {578.9096160000016, 1152.0},
            {1010.1293700000006, 142.65541799999883},
            {1105.1476620000017, 131.14110599999884},
            {1202.805726000001, 134.26174799999882},
            {1298.284692, 234.07225199999874},
            {1298.284692, 288.89287199999876},
            {1298.284692, 341.1860579999986},
            {1298.284692, 374.2124039999986},
            {1348.73916, 374.2124039999986},
            {1348.73916, 330.78250799999864},
            {1348.73916, 275.8118759999986},
            {1348.73916, 239.5074239999987},
            {914.5324860000021, 475.41995999999824},
            {861.4063500000022, 521.577161999998},
            {959.1068580000025, 521.577161999998},
            {959.1068580000025, 423.58811399999826},
            {858.4355220000027, 423.58811399999826},
            {1006.0110420000035, 767.4210719999984},
            {1006.0110420000035, 731.4732179999986},
            {1006.0110420000035, 682.2058139999988},
            {1207.7730240000037, 672.9439499999987},
            {1207.7730240000037, 721.6110179999988},
            {1207.7730240000037, 764.2247579999986},
            {955.3513560000044, 764.2247579999986},
            {903.4369260000047, 764.2247579999986},
            {890.1914460000048, 810.1876679999988},
            {867.3427500000047, 810.1876679999988},
            {838.391640000005, 810.1876679999988},
            {838.391640000005, 771.6127319999983},
            {812.701716000005, 763.1557559999983},
            {769.7450400000048, 763.1557559999983},
            {769.7450400000048, 962.4180959999983},
            {769.7450400000048, 997.7866199999982},
            {814.998138000005, 997.7866199999982},
            {814.998138000005, 966.1026059999981},
            {614.4576540000055, 966.1026059999981},
            {574.0927800000054, 966.1026059999981},
            {574.0927800000054, 1003.5048239999982},
            {620.3074380000055, 1003.5048239999982},
            {487.8144960000056, 1003.5048239999982},
            {487.8144960000056, 966.1213619999983},
            {487.8144960000056, 1044.3906359999978},
            {319.93067400000484, 426.44201399999884},
            {348.8597700000049, 426.44201399999884},
            {374.7888240000049, 426.44201399999884},
            {406.3160580000049, 426.44201399999884},
            {429.23572800000494, 426.44201399999884},
            {452.26856400000486, 426.44201399999884},
            {484.02933000000473, 426.44201399999884},
            {521.2087440000047, 426.44201399999884},
            {558.6952380000046, 426.44201399999884},
            {590.2707480000046, 426.44201399999884},
            {624.5248740000045, 426.44201399999884},
            {653.4267180000046, 426.44201399999884},
            {690.3622500000046, 426.44201399999884},
            {730.7864160000045, 426.44201399999884},
            {775.5295920000046, 426.44201399999884},
            {720.647250000005, 475.25741999999894},
            {563.8925940000053, 475.25741999999894},
            {589.9542540000054, 475.25741999999894},
            {572.5944780000053, 516.976109999999},
            {387.89773800000523, 516.976109999999},
            {387.89773800000523, 478.01433599999893},
            {411.0068040000053, 478.01433599999893},
            {355.5354480000053, 478.01433599999893},
            {355.5354480000053, 451.919375999999},
            {411.88903800000514, 449.27888399999875},
            {1346.9967600000082, 957.7532339999968},
            {1346.9967600000082, 1000.7296199999967},
            {1346.9967600000082, 1052.9885519999968},
            {1070.3575140000096, 992.3736239999971},
            {1110.5356740000095, 992.3736239999971},
            {1137.6620700000096, 992.3736239999971},
            {727.0475080000017, 116.97074999999998},
            {727.0475080000017, 162.04735799999997},
            {727.0475080000017, 216.73463399999997},
            {727.0475080000017, 260.72451000000007},
            {727.0475080000017, 311.82985799999994},
            {314.48623000000157, 335.39972399999937},
            {362.55060400000156, 335.39972399999937},
            {362.55060400000156, 335.39972399999937},
            {410.77103800000145, 335.39972399999937},
            {456.24731800000154, 335.39972399999937},
            {508.1016640000017, 335.39972399999937},
            {547.2378580000018, 335.39972399999937},
            {603.0706900000017, 335.39972399999937},
            {659.7535180000018, 335.39972399999937},
            {701.8164580000022, 335.39972399999937},
            {769.3796740000028, 570.6548459999996},
            {722.0644060000024, 570.6548459999996},
            {722.0644060000024, 622.7003699999993},
            {768.7457500000024, 622.7003699999993},};

        int idx = 1;
        for (double[] p : COLLISIONS) {
            obstacles.add(new Obstacle(
                    new Rectangle2D(p[0], p[1], 25, 25),
                    ObstacleType.BLOCK,
                    "SkyCollision" + idx
            ));
            idx++;
        }

        if (!game.getHero().existsCompletedTask(game.getTasks().get(0))) {
            double[][] COLLISIONS2 = new double[][]{
                {1250.0244400000004, 345.49560000000133},
                {1192.1942379999996, 345.49560000000133},
                {1124.338071999999, 345.49560000000133},
                {1069.9219839999992, 345.49560000000133},
                {1015.4302239999992, 345.49560000000133},
                {966.936135999999, 345.49560000000133},
                {912.9653319999989, 345.49560000000133},
                {858.2888739999987, 345.49560000000133},};

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
        double startX = 121.40199400000095;
        double startY = 0.0;
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
        double[] xs = {121.40199400000095, 69.70930600000092, 175.1709820000011};
        double[] ys = {0.0, 0.0, 0.0};

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

            }

            if (k == KeyCode.ENTER) {

                if (bossView != null) {
                    checkBossTriggers();
                }
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

                boolean proceed = true;
                if (root.getScene() == null || !root.isFocused()) {
                    clearInputState();
                    proceed = false;
                }

                if (proceed) {
                    updateAndMove(dt);
                }
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

        double curX = 0, curY = 0;
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

        Rectangle2D heroRect = new Rectangle2D(proposedX, proposedY, HERO_W, HERO_H);
        boolean collision = false;

        if (proceed && obstacles != null) {
            for (Obstacle ob : obstacles) {
                if (ob == null || ob.collisionRect == null) {
                    continue;
                }
                if (heroRect.intersects(ob.collisionRect)) {
                    collision = true;
                    break;
                }
            }
        }

        if (proceed && !collision) {
            heroView.setLayoutX(proposedX);
            heroView.setLayoutY(proposedY);
        } else if (proceed) {

            Rectangle2D heroRectX = new Rectangle2D(proposedX, curY, HERO_W, HERO_H);
            Rectangle2D heroRectY = new Rectangle2D(curX, proposedY, HERO_W, HERO_H);

            boolean canMoveX = true;
            boolean canMoveY = true;

            if (obstacles != null) {
                for (Obstacle ob : obstacles) {
                    if (ob == null || ob.collisionRect == null) {
                        continue;
                    }
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
            }

            if (canMoveX) {
                heroView.setLayoutX(proposedX);
            }
            if (canMoveY) {
                heroView.setLayoutY(proposedY);
            }
        }

        if (proceed) {
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
        double result = v;
        if (v < lo) {
            result = lo;
        } else if (v > hi) {
            result = hi;
        }
        return result;
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

    //TODO LO RELACIONADO AL MANEJO DEL BOSS
    public void drawBossDungeon() {
        if (!game.getHero().existsCompletedTask(game.getTasks().get(0))) {
            createBossTriggerRects();

            boolean skipCreate = false;
            if (bossView != null) {
                if (!world.getChildren().contains(bossView)) {
                    world.getChildren().add(bossView);
                }
                bossView.toFront();
                skipCreate = true;
            }

            if (!skipCreate) {
                try {
                    Image img = new Image(getClass().getResourceAsStream("/Resources/sprites/Monsters/finalBoss.png"));
                    bossView = new ImageView(img);

                    bossView.setPreserveRatio(true);
                    bossView.setFitWidth(200);
                    bossView.setFitHeight(200);
                    bossView.setMouseTransparent(true);

                    bossView.setLayoutX(1031.8274860000024);
                    bossView.setLayoutY(157.35265200000003);

                    bossView.getProperties().put("tag", "sky_boss");

                    if (!world.getChildren().contains(bossView)) {
                        world.getChildren().add(bossView);
                    }
                    bossView.toFront();

                } catch (Exception t) {
                    System.err.println("No se pudo cargar la imagen del boss: " + t.getMessage());
                }
            }

        } else {
            if (bossView != null) {
                try {
                    world.getChildren().remove(bossView);
                } catch (Exception ignored) {
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
                {1176.5769699999987, 348.5237580000012},
                {1117.6297599999987, 348.5237580000012},
                {1083.1165059999987, 348.5237580000012},
                {1051.7367999999985, 348.5237580000012},
                {1017.1041339999991, 348.5237580000012},};

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
            battleAgainstBoss((Boss) game.getCharacters().get(25));

        }
    }

    private void battleAgainstBoss(Boss boss) {
        String bg = "/Resources/textures/Battle/finalBattle.png";
        stopMapMusic();

        CombatScreen cs = new GUI.CombatScreen(game, bg, "Sky", game.getHero(), true, boss);

        cs.setBattleMusicPath("/Resources/music/finalBossFight.mp3");

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
        game.completeMainM000();
        redrawRoomAfterBoss();

    }
}
