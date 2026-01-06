package GUI;

import Characters.Boss;
import Characters.Hero;
import Logic.Game;
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
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class SwampDungeon {

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

    private Rectangle startRect;
    private boolean onStartRect = false;

    private Runnable onExitCallback;
    private final Game game;
    // Para cambiar de mapa en el mismo pantano
    private final List<Rectangle> dungeonTriggerRects = new ArrayList<>();
    private final List<Rectangle> bossTriggerRects = new ArrayList<>();
    private boolean beforeDungeon = true;
    private ImageView bossView;

    // Sistema de colisiones
    private final List<Obstacle> obstacles = new ArrayList<>();
    private boolean debugEnabled = true;

    // Inventario (si se abre desde aquí se pasa this)
    private InventoryScreen inventory;

    // Direcciones del héroe (para depuración con tecla P)
    public enum Direction {
        NONE, N, NE, E, SE, S, SW, W, NW
    }
    private Direction currentDirection = Direction.NONE;

    // Tipos de obstáculos para la aldea
    private enum ObstacleType {
        BLOCK, PLANT
    }

    // Clase interna para obstáculos
    private static class Obstacle {

        final Rectangle2D collisionRect;
        final SwampDungeon.ObstacleType type;
        final String id;

        Obstacle(Rectangle2D collision, SwampDungeon.ObstacleType type, String id) {
            this.collisionRect = collision;
            this.type = type;
            this.id = id;
        }
    }

    public SwampDungeon(Game game) {
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

            boolean imageOk = loadBackgroundImage("/Resources/textures/SwampDungeon/SwampDungeon01.png");
            boolean musicOk = startVillageMusic("/Resources/music/swampDungeonInside.mp3");

            populateSwampObstacles();
            createDungeonTriggerRects();

            positionHeroAtEntrance();
            createStartRectAtHeroStart();

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

        Text label = new Text("Loading Terrain...");
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
            Text err = new Text("No se pudo cargar la imagen de la Zona.");
            err.setStyle("-fx-font-size: 16px; -fx-fill: #ffdddd;");
            root.getChildren().add(err);
        }
        return ret;
    }

    private boolean startVillageMusic(String path) {
        boolean startMusic = true;
        try {
            URL res = getClass().getResource(path);
            if (res == null) {
                startMusic = false;
            }
            Media media = new Media(res.toExternalForm());
            stopVillageMusic();
            music = new MediaPlayer(media);
            music.setCycleCount(MediaPlayer.INDEFINITE);
            music.setVolume(MainScreen.getVolumeSetting());
            music.play();
        } catch (Throwable t) {
            startMusic = false;
        }
        return startMusic;
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

    // ---------------- colisiones  ----------------
    private void populateSwampObstacles() {
        obstacles.clear();
        double[][] COLLISIONS = new double[][]{
            {49.139664000001254, 1110.518184},
            {146.93136000000126, 966.5263379999997},
            {431.90332800000135, 920.4938939999997},
            {763.2390840000014, 957.9141479999997},
            {815.0621460000015, 957.9141479999997},
            {815.0621460000015, 1000.9677899999997},
            {763.0831680000016, 1000.9677899999997},
            {1010.4921420000016, 911.8932959999999},
            {912.5751660000017, 816.4395660000001},
            {912.5751660000017, 767.442414},
            {950.2297440000017, 767.442414},
            {953.2874220000017, 810.5581379999999},
            {1102.524432000001, 773.1911819999999},
            {768.4029600000013, 525.4438980000007},
            {725.411022000001, 525.4438980000007},
            {725.411022000001, 485.02831800000064},
            {768.739128000001, 485.02831800000064},
            {616.1591400000007, 568.4234520000008},
            {575.8203300000007, 568.4234520000008},
            {575.8203300000007, 617.1976740000007},
            {619.1033640000005, 617.1976740000007},
            {627.7590240000005, 674.5256040000007},
            {478.20363000000066, 625.4348340000009},
            {285.16390800000073, 671.548278000001},
            {244.83022800000074, 573.544344000001},
            {334.1022720000008, 383.6362080000006},
            {480.70141800000084, 288.71891400000055},
            {575.6773200000007, 245.5071960000006},
            {575.6773200000007, 213.90571200000062},
            {575.6773200000007, 190.91346600000065},
            {532.5949320000005, 190.91346600000065},
            {244.50131400000078, 46.796322000000686},
            {97.63782000000076, 46.796322000000686},
            {48.74677800000076, 147.63700200000073},
            {143.8707120000008, 147.63700200000073},
            {1002.2717220000002, 395.49662400000074},
            {970.5837120000002, 395.49662400000074},
            {970.5837120000002, 424.3187280000007},
            {993.7003920000002, 424.3187280000007},
            {993.7003920000002, 461.70168600000073},
            {964.9230180000002, 461.70168600000073},
            {964.9230180000002, 487.78698000000065},
            {930.1903800000001, 487.78698000000065},
            {930.1903800000001, 510.7706220000007},
            {901.5219600000001, 522.0524100000007},
            {869.7931800000002, 542.1593100000007},
            {849.6249720000002, 570.9673740000007},
            {817.8044460000002, 570.9673740000007},
            {791.9370420000001, 570.9673740000007},
            {763.2020220000002, 611.4815400000007},
            {728.5985880000002, 611.4815400000007},
            {728.5985880000002, 582.7604160000008},
            {699.7104780000003, 574.0363020000007},
            {670.8203160000004, 574.0363020000007},
            {670.8203160000004, 539.3984520000008},
            {642.0922080000004, 527.7777060000009},
            {622.1341860000003, 490.3320000000007},
            {593.1140100000003, 490.3320000000007},
            {601.5730560000004, 516.3146580000007},
            {555.4353480000005, 527.8548720000007},
            {532.4122860000007, 527.8548720000007},
            {532.4122860000007, 568.3570320000008},
            {503.52187200000066, 568.3570320000008},
            {489.02839800000066, 568.3570320000008},
            {489.02839800000066, 536.7763740000008},
            {506.46444000000065, 536.7763740000008},
            {469.0179600000007, 536.7763740000008},
            {445.97139000000067, 528.0612240000008},
            {414.1850460000006, 528.0612240000008},
            {385.43863200000055, 533.8836840000009},
            {385.43863200000055, 565.661568000001},
            {385.43863200000055, 600.306744000001},
            {365.42419800000056, 571.2463740000014},
            {333.8019420000007, 571.2463740000014},
            {305.0013840000007, 571.2463740000014},
            {290.5832760000007, 571.2463740000014},
            {290.5832760000007, 545.3994900000014},
            {290.5832760000007, 525.1568340000013},
            {258.7655940000007, 525.1568340000013},
            {229.85559600000067, 525.1568340000013},
            {206.93659200000064, 525.1568340000013},
            {186.96604200000064, 525.1568340000013},
            {186.96604200000064, 559.4842740000015},
            {186.96604200000064, 588.2554020000014},
            {186.96604200000064, 608.6108100000015},
            {143.77923600000065, 608.6108100000015},
            {143.77923600000065, 649.0860240000014},
            {112.15138200000062, 649.0860240000014},
            {88.92834000000062, 703.9673040000015},
            {42.7952400000006, 703.9673040000015},
            {42.7952400000006, 749.9937360000015},
            {42.7952400000006, 778.7991360000015},
            {42.7952400000006, 807.6373320000015},
            {42.7952400000006, 839.2943820000014},
            {16.782702000000604, 810.5476440000014},
            {0.0, 810.5476440000014},
            {333.9199080000002, 525.5337540000015},
            {333.9199080000002, 494.0778180000015},
            {305.2209060000001, 494.0778180000015},
            {273.5072280000001, 494.0778180000015},
            {241.87818600000008, 494.0778180000015},
            {213.2597340000001, 494.07781800000015},
            {184.58861400000012, 488.28442800000147},
            {184.58861400000012, 456.6460620000015},
            {184.58861400000012, 425.0367840000016},
            {184.58861400000012, 396.2904240000016},
            {184.58861400000012, 364.5998760000017},
            {184.58861400000012, 350.1735780000018},
            {153.03691800000013, 350.1735780000018},
            {135.84097800000012, 350.1735780000018},
            {135.84097800000012, 315.4051560000018},
            {135.84097800000012, 295.23545400000177},
            {95.55370200000011, 295.23545400000177},
            {95.55370200000011, 266.42033400000173},
            {95.55370200000011, 234.87693600000173},
            {95.55370200000011, 206.14497600000178},
            {66.69757800000012, 295.5132840000018},
            {35.02765800000012, 295.5132840000018},
            {0.0, 295.5132840000018},
            {382.17887400000114, 897.422609999999},
            {382.17887400000114, 868.6215479999989},
            {410.9827800000011, 859.8474119999988},
            {442.6114080000011, 859.8474119999988},
            {477.09616800000106, 859.8474119999988},
            {477.09616800000106, 836.8840199999987},
            {477.09616800000106, 811.0364699999988},
            {508.68087600000115, 811.0364699999988},
            {540.4439460000013, 811.0364699999988},
            {574.9913820000014, 811.0364699999988},
            {609.4460640000013, 811.0364699999988},
            {644.0267820000015, 811.0364699999988},
            {675.7345560000016, 811.0364699999988},
            {710.2114500000016, 811.0364699999988},
            {744.6573480000018, 811.0364699999988},
            {767.6896260000017, 811.0364699999988},
            {767.6896260000017, 785.0744939999988},
            {767.6896260000017, 767.7887339999988},
            {796.5252480000015, 767.7887339999988},
            {819.4438020000016, 767.7887339999988},
            {819.4438020000016, 744.8044619999988},
            {819.4438020000016, 713.1608039999987},
            {851.2811040000016, 713.1608039999987},
            {923.3671800000018, 661.3659479999986},
            {955.0173000000018, 661.3659479999986},
            {955.0173000000018, 629.8775579999987},
            {955.0173000000018, 606.7529399999985},
            {955.0173000000018, 572.1653099999985},
            {955.0173000000018, 551.9751599999985},
            {813.9875700000023, 336.17445599999854},
            {283.99828800000273, 339.0155759999984},
            {243.65784000000264, 339.0155759999984},
            {243.65784000000264, 295.9825079999985},
            {286.96924200000257, 295.9825079999985}
        };

        int idx = 1;
        for (double[] p : COLLISIONS) {
            double x = p[0];
            double y = p[1];
            obstacles.add(new Obstacle(
                    new Rectangle2D(x, y, 40, 40),
                    ObstacleType.PLANT,
                    "Collision" + idx
            ));
            idx++;
        }
    }

    private void populateSwamp2Obstacles() {
        obstacles.clear();

        double[][] COLLISIONS = new double[][]{
            {656.3989799999999, 905.06637},
            {627.6247919999998, 905.06637},
            {627.6247919999998, 930.923064},
            {627.6247919999998, 953.9284499999999},
            {596.0412899999997, 953.9284499999999},
            {567.3174299999997, 953.9284499999999},
            {538.4732939999997, 953.9284499999999},
            {535.6040399999997, 927.9966959999998},
            {526.8586499999997, 893.547036},
            {526.8586499999997, 870.564132},
            {495.1861199999996, 870.564132},
            {477.8411219999996, 856.130976},
            {477.8411219999996, 827.267112},
            {454.7230199999996, 812.849616},
            {425.89481399999966, 812.849616},
            {391.31225999999975, 812.849616},
            {379.6471079999997, 812.849616},
            {379.6471079999997, 781.3179720000003},
            {350.85351599999973, 781.3179720000003},
            {350.85351599999973, 807.1537320000002},
            {339.3613079999997, 830.2494780000002},
            {339.3613079999997, 867.7279980000003},
            {339.3613079999997, 899.4173220000005},
            {313.54226999999975, 899.4173220000005},
            {293.3960939999997, 899.4173220000005},
            {273.4288199999997, 861.9273540000002},
            {244.69176599999963, 861.9273540000002},
            {207.35980199999963, 861.9273540000002},
            {181.43065799999965, 861.9273540000002},
            {155.40609599999965, 861.9273540000002},
            {143.90931599999965, 861.9273540000002},
            {143.90931599999965, 887.8536900000001},
            {143.90931599999965, 913.7227320000001},
            {143.90931599999965, 936.861156},
            {143.90931599999965, 962.862012},
            {143.90931599999965, 971.5134240000001},
            {175.52548799999965, 971.5134240000001},
            {184.24904399999963, 991.615986},
            {184.24904399999963, 1023.1094519999999},
            {187.2444959999996, 1043.3443679999998},
            {187.2444959999996, 1069.1061839999998},
            {187.2444959999996, 1092.2214419999998},
            {187.2444959999996, 1115.3167559999997},
            {187.2444959999996, 1138.2716159999998},
            {187.2444959999996, 1152.0},
            {652.0069259999992, 656.4327839999997},
            {652.0069259999992, 630.5648039999998},
            {652.0069259999992, 604.6956719999997},
            {652.0069259999992, 578.8154699999996},
            {652.0069259999992, 550.0256219999995},
            {652.0069259999992, 526.7906279999996},
            {652.0069259999992, 495.15233399999954},
            {652.0069259999992, 472.11809399999953},
            {652.0069259999992, 449.06263199999955},
            {652.0069259999992, 405.9384299999995},
            {652.0069259999992, 391.7095199999995},
            {496.52517599999914, 394.7001299999996},
            {496.52517599999914, 417.75438599999967},
            {496.52517599999914, 437.8368239999998},
            {496.52517599999914, 463.86896399999983},
            {496.52517599999914, 486.8095319999999},
            {496.52517599999914, 506.76722999999987},
            {496.52517599999914, 532.6752599999999},
            {496.52517599999914, 561.5333999999999},
            {496.52517599999914, 581.6932559999999},
            {496.52517599999914, 607.6982700000001},
            {496.52517599999914, 627.7670640000002},
            {496.52517599999914, 650.7622800000003},
            {1014.6657239999995, 1152.0},
            {1014.6657239999995, 1128.922182},
            {1014.6657239999995, 1105.9105859999997},
            {1014.6657239999995, 1082.9691},
            {1014.6657239999995, 1054.085706},
            {1014.6657239999995, 1025.1682200000002},
            {1014.6657239999995, 1002.1324320000004},
            {1014.6657239999995, 976.1879160000004},
            {1014.6657239999995, 956.0218500000004},
            {1014.6657239999995, 930.1273380000002},
            {1014.6657239999995, 901.3396680000002},
            {1014.6657239999995, 875.2908420000001},
            {1014.6657239999995, 846.476802},
            {1014.6657239999995, 820.8045539999999},
            {980.2350719999995, 815.1305579999998},
            {948.6506159999996, 815.1305579999998},
            {919.9367999999996, 815.1305579999998},
            {899.8250939999994, 815.1305579999998},
            {871.0806059999993, 815.1305579999998},
            {853.9485119999993, 841.0691519999999},
            {825.1379819999994, 841.0691519999999},
            {825.1379819999994, 815.2840799999999},
            {825.1379819999994, 792.3006719999998},
            {825.1379819999994, 769.2348239999998},
            {825.1379819999994, 726.0245639999998},
            {825.1379819999994, 705.9421979999998},
            {784.9085039999994, 705.9421979999998},
            {784.9085039999994, 680.0734799999999},
            {750.3421679999993, 657.2270699999999},
            {724.5294479999993, 657.2270699999999},
            {704.3691779999995, 657.2270699999999},
            {661.2798419999995, 657.2270699999999},
            {676.0069259999992, 680.4327839999997},
            {448.276284, 660.8758140000008},
            {425.2744619999999, 660.8758140000008},
            {399.48586199999994, 660.8758140000008},
            {370.72949399999993, 660.8758140000008},
            {344.74019399999986, 660.8758140000008},
            {315.7191539999999, 660.8758140000008},
            {284.01541199999997, 660.8758140000008},
            {246.52159200000008, 660.8758140000008},
            {246.52159200000008, 689.6686500000008},
            {226.2497400000001, 712.7668620000007},
            {194.66204400000012, 712.7668620000007},
            {160.13023200000012, 712.7668620000007},
            {145.8329580000001, 747.2450880000008},
            {145.8329580000001, 779.0675220000007},
            {145.8329580000001, 807.8868180000007},
            {189.05589000000006, 807.8868180000007},
            {229.3698240000001, 807.8868180000007},
            {291.03591600000004, 813.0853620000007},
            {440.56434600000006, 381.0209940000017},
            {405.844506, 381.0209940000017},
            {371.27367000000004, 381.0209940000017},
            {342.52205399999986, 381.0209940000017},
            {313.81732799999986, 381.0209940000017},
            {288.0092519999999, 381.0209940000017},
            {288.0092519999999, 349.45547400000174},
            {288.0092519999999, 314.9203140000017},
            {288.0092519999999, 283.5026460000017},
            {288.0092519999999, 260.4657240000017},
            {288.0092519999999, 231.66480600000168},
            {313.8278759999999, 223.13028600000166},
            {342.554958, 223.13028600000166},
            {374.1927659999999, 223.13028600000166},
            {400.13121599999994, 223.13028600000166},
            {426.04065, 223.13028600000166},
            {426.04065, 194.29335000000168},
            {426.04065, 168.32824200000167},
            {426.04065, 139.3743060000017},
            {426.04065, 113.42611800000168},
            {734.0435819999999, 127.86012000000169},
            {734.0435819999999, 148.02291000000167},
            {734.0435819999999, 176.8179240000017},
            {734.0435819999999, 208.39824000000175},
            {734.0435819999999, 222.72015600000174},
            {759.9385439999998, 222.6058380000017},
            {794.6012879999998, 222.6058380000017},
            {829.1007179999998, 222.6058380000017},
            {866.3562179999999, 222.6058380000017},
            {866.3562179999999, 254.03814000000173},
            {866.3562179999999, 291.4713360000017},
            {866.3562179999999, 325.9464300000017},
            {866.3562179999999, 354.74976000000174},
            {866.3562179999999, 377.79910200000165},
            {846.3380399999999, 386.6209560000017},
            {814.5857339999999, 386.6209560000017},
            {777.0871259999999, 386.6209560000017},
            {748.3040819999998, 386.6209560000017},
            {719.5161959999997, 386.6209560000017},
            {696.5477819999999, 386.6209560000017},
            {682.2633599999998, 386.6209560000017},
            {771.4475819999998, 285.6364740000017},
            {647.7662700000002, 196.6238460000017},
            {647.7662700000002, 162.11214000000172},
            {503.67486600000046, 193.75720200000174},
            {503.67486600000046, 176.49450000000175},
            {503.67486600000046, 144.88522200000173},
            {575.6474520000003, 192.44205000000167},
            {685.4711759999998, 187.88725800000054}
        };

        int idx = 1;
        for (double[] p : COLLISIONS) {
            double x = p[0];
            double y = p[1];
            obstacles.add(new Obstacle(
                    new Rectangle2D(x, y, 30, 30),
                    ObstacleType.PLANT,
                    "Colision" + idx
            ));
            idx++;
        }
        if (!game.getHero().existsCompletedTask(game.getTasks().get(3))) {
            double[][] COLLISIONS2 = new double[][]{
                {645.0170220000001, 332.0898660000009},
                {607.6962720000002, 332.0898660000009},
                {581.6617560000003, 332.0898660000009},
                {550.1667780000002, 332.0898660000009},
                {524.3238360000001, 332.0898660000009},
                {501.2223300000002, 332.0898660000009}
            };

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
        double startX = 500.1253860000012;
        double startY = 1200.0;

        heroView.setLayoutX(startX);
        heroView.setLayoutY(startY);
        updateCamera();
    }

    private void createStartRectAtHeroStart() {
        if (startRect != null) {
            world.getChildren().remove(startRect);
            startRect = null;
        }

        double[] xs = new double[]{
            387.79590000000115,
            430.8981060000012,
            473.9741220000012,
            528.7377900000012,
            577.8503940000012,
            615.4039560000012
        };
        double y = 1200.0;

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
            battleAgainstBoss((Boss) game.getCharacters().get(12));
        }
    }

    private void battleAgainstBoss(Boss boss) {
        String bg = "/Resources/textures/Battle/swampBattle.png";
        stopMapMusic();

        CombatScreen cs = new GUI.CombatScreen(game, bg, "Swamp", game.getHero(), true, boss);

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
        game.completeMainM003();
        redrawRoomAfterBoss();

    }

    private void installInputHandlers() {
        root.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            KeyCode k = ev.getCode();

            if (k == KeyCode.W || k == KeyCode.UP) {
                keys.add(KeyCode.W);
            }
            if (k == KeyCode.S || k == KeyCode.DOWN) {
                keys.add(KeyCode.S);
            }
            if (k == KeyCode.A || k == KeyCode.LEFT) {
                keys.add(KeyCode.A);
            }
            if (k == KeyCode.D || k == KeyCode.RIGHT) {
                keys.add(KeyCode.D);
            }

            if (k == KeyCode.P) {
                System.out.println("Hero position (Zona): (" + heroView.getLayoutX() + ", " + heroView.getLayoutY() + ")");
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
                if (checkOrbTrigger()) {
                    game.completeMainM001();
                    collectOrb();
                    showBottomDialogRPG("Item Obtained", "You have received the Toxic Orb", null);
                }

                if (beforeDungeon) {
                    checkDungeonTriggers();

                    if (onStartRect) {
                        clearInputState();
                        try {
                            if (game != null && game.getHero() != null) {
                                Hero h = game.getHero();
                                h.setLastLocation(Hero.Location.SWAMP);
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
                } else {
                    checkReturnToPreviousZone();

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

        SwampDungeon.Direction newDir = (vx != 0 || vy != 0)
                ? directionFromVector(vx, vy) : SwampDungeon.Direction.NONE;
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

        for (SwampDungeon.Obstacle ob : obstacles) {
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

            for (SwampDungeon.Obstacle ob : obstacles) {
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

    private SwampDungeon.Direction directionFromVector(double vx, double vy) {
        SwampDungeon.Direction result = SwampDungeon.Direction.NONE;

        if (!(vx == 0 && vy == 0)) {
            double angle = Math.toDegrees(Math.atan2(-vy, vx));
            if (angle < 0) {
                angle += 360.0;
            }

            if (angle >= 337.5 || angle < 22.5) {
                result = SwampDungeon.Direction.E;
            } else if (angle < 67.5) {
                result = SwampDungeon.Direction.NE;
            } else if (angle < 112.5) {
                result = SwampDungeon.Direction.N;
            } else if (angle < 157.5) {
                result = SwampDungeon.Direction.NW;
            } else if (angle < 202.5) {
                result = SwampDungeon.Direction.W;
            } else if (angle < 247.5) {
                result = SwampDungeon.Direction.SW;
            } else if (angle < 292.5) {
                result = SwampDungeon.Direction.S;
            } else if (angle < 337.5) {
                result = SwampDungeon.Direction.SE;
            }
        }

        return result;
    }

    private void setDirectionIfChanged(SwampDungeon.Direction newDir) {
        if (newDir == null) {
            newDir = SwampDungeon.Direction.NONE;
        }
        currentDirection = newDir;
    }

    public SwampDungeon.Direction getHeroDirection() {
        return currentDirection;
    }

    private void checkStartIntersection() {
        boolean intersects = false;

        if (startRect != null) {
            intersects = heroView.getBoundsInParent().intersects(startRect.getBoundsInParent());
            startRect.setFill(intersects ? Color.rgb(0, 120, 255, 0.42) : Color.rgb(0, 120, 255, 0.28));
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

    public void startMapMusic() {
        try {
            stopMapMusic();
            URL res = getClass().getResource("/Resources/music/swampDungeonInside.mp3");
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

    private void openDebugCombat() {
        String bg = "/Resources/textures/Battle/swampBattle.png";
        stopMapMusic();

        GUI.CombatScreen cs = new GUI.CombatScreen(game, bg, "Swamp", game.getHero(), false, null);

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

    private void drawDebugObstacles() {
        world.getChildren().removeIf(n -> "obstacle_debug".equals(n.getProperties().get("tag")));

        for (SwampDungeon.Obstacle ob : obstacles) {
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

    public void createOrbTrigger() {
        if (game.getHero().existsCompletedTask(game.searchTask("M001"))) {
            if (orbNode != null) {
                try {
                    world.getChildren().remove(orbNode);
                } catch (Throwable ignored) {
                }
                orbNode = null;
            }
            orbTrigger = null;
        } else {
            double x = 573.1055639999997;
            double y = 222.56532000000004;
            orbTrigger = new Rectangle2D(x - 4, y - 4, HERO_W + 8, HERO_H + 8);
            if (orbNode == null) {
                Rectangle r = new Rectangle(orbTrigger.getWidth(), orbTrigger.getHeight());
                r.setLayoutX(orbTrigger.getMinX());
                r.setLayoutY(orbTrigger.getMinY());
                r.setFill(Color.TRANSPARENT);
                r.setStroke(null);
                r.setMouseTransparent(true);
                orbNode = r;
                if (!world.getChildren().contains(orbNode)) {
                    world.getChildren().add(orbNode);
                }
                orbNode.toFront();
                heroView.toFront();

            } else {
                orbNode.setLayoutX(orbTrigger.getMinX());
                orbNode.setLayoutY(orbTrigger.getMinY());
            }
        }
    }

    public boolean checkOrbTrigger() {
        boolean intersects = false;
        if (orbTrigger != null) {
            Rectangle2D heroRect = new Rectangle2D(heroView.getLayoutX(), heroView.getLayoutY(), HERO_W, HERO_H);
            intersects = heroRect.intersects(orbTrigger);
            if (intersects) {
                if (orbHintText == null) {
                    orbHintText = new Text("Press ENTER to pick up");
                    orbHintText.setStyle("-fx-font-size: 12px; -fx-fill: #fffacd; -fx-stroke: #00000055;");
                    orbHintText.setMouseTransparent(true);
                    world.getChildren().add(orbHintText);
                }
                orbHintText.setLayoutX(heroView.getLayoutX());
                orbHintText.setLayoutY(heroView.getLayoutY() - 10);
                if (!world.getChildren().contains(orbHintText)) {
                    world.getChildren().add(orbHintText);
                }
                orbHintText.toFront();
                heroView.toFront();
            } else {
                if (orbHintText != null) {
                    try {
                        world.getChildren().remove(orbHintText);
                    } catch (Throwable ignored) {
                    }
                    orbHintText = null;
                }
            }
        } else {
            if (orbHintText != null) {
                try {
                    world.getChildren().remove(orbHintText);
                } catch (Throwable ignored) {
                }
                orbHintText = null;
            }
        }
        return intersects;
    }

    public void collectOrb() {
        if (orbNode != null) {
            try {
                world.getChildren().remove(orbNode);
            } catch (Throwable ignored) {
            }
            orbNode = null;
        }
        orbTrigger = null;
        if (orbHintText != null) {
            try {
                world.getChildren().remove(orbHintText);
            } catch (Throwable ignored) {
            }
            orbHintText = null;
        }
    }

    private void createDungeonTriggerRects() {  // Este metodo para crear las colisiones para avanzar hacia la otra habitacion en la primera sala
        for (Rectangle r : dungeonTriggerRects) {
            try {
                world.getChildren().remove(r);
            } catch (Throwable ignored) {
            }
        }
        dungeonTriggerRects.clear();

        double[][] TRIGGERS = new double[][]{
            {241.05108000000024, 97.08609000000065}
        };

        for (int i = 0; i < TRIGGERS.length; i++) {
            double x = TRIGGERS[i][0];
            double y = TRIGGERS[i][1];
            double w = HERO_W + 8;
            double h = HERO_H + 8;
            Rectangle r = new Rectangle(x - 4, y - 4, w, h);
            r.setFill(Color.color(0, 0, 0, 0.0));
            r.setStroke(null);
            r.setMouseTransparent(true);
            r.getProperties().put("tag", "dungeon_trigger");
            r.getProperties().put("id", "dungeonTrigger" + (i + 1));
            dungeonTriggerRects.add(r);
            if (!world.getChildren().contains(r)) {
                world.getChildren().add(r);
            }
        }
        heroView.toFront();
    }

    private void checkDungeonTriggers() {
        boolean shouldSwitch = false;

        if (beforeDungeon) {
            double hx = heroView.getLayoutX();
            double hy = heroView.getLayoutY();
            Rectangle2D heroRect = new Rectangle2D(hx, hy, HERO_W, HERO_H);

            for (Rectangle trigger : dungeonTriggerRects) {
                Rectangle2D tr = new Rectangle2D(trigger.getX(), trigger.getY(), trigger.getWidth(), trigger.getHeight());
                if (heroRect.intersects(tr)) {
                    shouldSwitch = true;
                }
            }
        }

        if (shouldSwitch) {
            switchToDungeon();
        }
    }

    private void switchToDungeon() {
        if (beforeDungeon) {
            if (!game.getHero().existsPendingTask(game.getTasks().get(3)) && !game.getHero().existsCompletedTask(game.getTasks().get(3))) {
                game.getHero().addTasks(game.getTasks().get(3));
            }
            beforeDungeon = false;
            populateSwamp2Obstacles();
            createReturnTriggerRect();

            try {
                world.getChildren().removeIf(n -> {
                    Object tag = n.getProperties().get("tag");
                    return "obstacle_debug".equals(tag) || "dungeon_trigger".equals(tag) || "exit_area".equals(tag);
                });
            } catch (Throwable ignored) {
            }

            boolean bgOk = loadBackgroundImage("/Resources/textures/SwampDungeon/SwampDungeon02.png");
            createOrbTrigger();
            drawBossDungeon();
            setHeroPosition(382.9433579999997, 1126.7086680000002);

            if (!world.getChildren().contains(heroView)) {
                world.getChildren().add(heroView);
            }
            heroView.toFront();

            createBossTriggerRects();

            updateCamera();
        }
    }

    private void createReturnTriggerRect() {

        double x = 385.0208099999996;
        double y1 = 1120.3398179999992;
        double y2 = 1091.4255359999995;
        double y3 = 1059.8180759999998;

        double minY = Math.min(y1, Math.min(y2, y3));
        double maxY = Math.max(y1, Math.max(y2, y3));

        double pad = 4.0;

        double rx = x - pad;
        double rw = HERO_W + pad * 2;

        double ry = minY - pad;
        double rh = (maxY - minY) + HERO_H + pad * 2;

        Rectangle r = new Rectangle(rx, ry, rw, rh);
        r.setFill(Color.color(0, 0, 0, 0.0));
        r.setStroke(null);
        r.setMouseTransparent(true);
        r.getProperties().put("tag", "return_trigger");
        r.getProperties().put("id", "dungeonReturn");

        dungeonTriggerRects.add(r);
        if (!world.getChildren().contains(r)) {
            world.getChildren().add(r);
        }

        heroView.toFront();
    }

    private void returnToPreviousZone() {
        boolean imageOk = loadBackgroundImage("/Resources/textures/SwampDungeon/SwampDungeon01.png");
        if (imageOk) {

            beforeDungeon = true;

            setHeroPosition(241.05108000000024, 97.08609000000065);

            obstacles.clear();
            populateSwampObstacles();
            createDungeonTriggerRects();
            orbTrigger = null;
            orbNode = null;
            createStartRectAtHeroStart();

            heroView.toFront();
            updateCamera();
        }
    }

    private void checkReturnToPreviousZone() {
        double hx = heroView.getLayoutX();
        double hy = heroView.getLayoutY();
        Rectangle2D heroRect = new Rectangle2D(hx, hy, HERO_W, HERO_H);

        boolean found = false;
        Rectangle foundTrigger = null;

        for (Rectangle trigger : dungeonTriggerRects) {
            Object tag = trigger.getProperties().get("tag");
            if (!"return_trigger".equals(tag)) {
            } else {
                Rectangle2D tr = new Rectangle2D(trigger.getX(), trigger.getY(), trigger.getWidth(), trigger.getHeight());
                if (heroRect.intersects(tr)) {
                    found = true;
                    foundTrigger = trigger;
                }
            }

        }

        if (found && foundTrigger != null) {
            returnToPreviousZone();
        }
    }

    public void drawBossDungeon() {
        boolean shouldShowBoss = true;
        try {
            shouldShowBoss = !game.getHero().existsCompletedTask(game.getTasks().get(3));
        } catch (Throwable ignored) {
            shouldShowBoss = true;
        }

        if (shouldShowBoss) {
            if (bossView == null) {
                try {
                    java.io.InputStream is = getClass().getResourceAsStream("/Resources/sprites/Monsters/swampBoss01.png");
                    if (is != null) {
                        Image img = new Image(is);
                        bossView = new ImageView(img);
                        bossView.setPreserveRatio(true);
                        bossView.setFitWidth(200);
                        bossView.setFitHeight(200);
                        bossView.setMouseTransparent(true);
                        bossView.setLayoutX(512.5474079999998);
                        bossView.setLayoutY(243.2034720000001);
                        bossView.getProperties().put("tag", "swamp_boss");
                    } else {
                        System.err.println("Imagen del boss no encontrada: /Resources/sprites/Monsters/swampBoss01.png");
                    }
                } catch (Throwable t) {
                    System.err.println("No se pudo cargar la imagen del boss: " + t.getMessage());
                }
            }

            if (bossView != null && !world.getChildren().contains(bossView)) {
                world.getChildren().add(bossView);
            }
            if (bossView != null) {
                bossView.toFront();
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
                {527.8152960000003, 364.2732719999998},
                {559.3640940000006, 364.2732719999998},
                {593.9372700000007, 364.2732719999998},
                {602.5033080000007, 364.2732719999998}
            };

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
                populateSwamp2Obstacles();
                if (!world.getChildren().contains(heroView)) {
                    world.getChildren().add(heroView);
                }
                heroView.toFront();

                createReturnTriggerRect();

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
