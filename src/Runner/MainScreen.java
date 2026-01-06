package Runner;

import Characters.Hero;
import static Characters.Hero.Location.SWAMP;
import Logic.Game;
import GUI.*;
import com.almasb.fxgl.app.GameApplication;
import static com.almasb.fxgl.app.GameApplication.launch;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.Duration;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.BlurType;
import javafx.scene.text.FontWeight;
import java.io.File;
import java.net.URL;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;

public class MainScreen extends GameApplication {

    private Alert a;
    private Hero hero;
    private Game game;
    private final String[] labels = {"Continue", "New Game", "Settings", "Quit"};
    private int selectedIndex = 0;
    private static Rectangle cursor;
    private VBox menuBox;
    private static StackPane rootPane;
    private final Duration CURSOR_MOVE_DURATION = Duration.millis(160);
    private final Duration BUTTON_PING_DURATION = Duration.millis(180);
    private double volumeSetting = 0.7;
    private MediaPlayer bgMusic;
    private boolean configOpen = false;
    private static final double CURSOR_UP_OFFSET = 8.0;
    private GameMapScreen currentMapScreen;
    private static final int DURACION_CARGA_MS = 600;
    public static volatile boolean modalOpen = false;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("The Mistery of The Ruins");
        settings.setWidth(800);
        settings.setHeight(600);
        settings.setVersion("0.1dev");
    }

    @Override
    protected void initInput() {
        FXGL.onKeyDown(KeyCode.UP, () -> {
            boolean proceed = !MainScreen.isModalOpen() && !configOpen;
            if (proceed) {
                selectedIndex = (selectedIndex - 1 + labels.length) % labels.length;
                updateCursorSmooth();
            }
        });

        FXGL.onKeyDown(KeyCode.DOWN, () -> {
            boolean proceed = !MainScreen.isModalOpen() && !configOpen;
            if (proceed) {
                selectedIndex = (selectedIndex + 1) % labels.length;
                updateCursorSmooth();
            }
        });

        FXGL.onKeyDown(KeyCode.ENTER, () -> {
            boolean proceed = !MainScreen.isModalOpen() && !configOpen;
            if (proceed) {
                activateSelected();
            }
        });

        FXGL.onKeyDown(KeyCode.W, () -> {
            boolean proceed = !MainScreen.isModalOpen() && !configOpen;
            if (proceed) {
                selectedIndex = (selectedIndex - 1 + labels.length) % labels.length;
                updateCursorSmooth();
            }
        });

        FXGL.onKeyDown(KeyCode.S, () -> {
            boolean proceed = !MainScreen.isModalOpen() && !configOpen;
            if (proceed) {
                selectedIndex = (selectedIndex + 1) % labels.length;
                updateCursorSmooth();
            }
        });

    }

    @Override
    protected void initGame() {
        game = new Game();
        game.createItems();
        game.createMonsters();
        game.createTasks();
        game.createVillagers();

    }

    @Override
    protected void initUI() {
        Image bgImage = new Image(getClass().getResourceAsStream("/Resources/textures/Main/MainScreen.png"));
        ImageView bgView = new ImageView(bgImage);
        bgView.setPreserveRatio(false);
        menuBox = new VBox(12);
        menuBox.setAlignment(Pos.CENTER);
        for (String text : labels) {
            Button b = new Button(text);
            b.setFocusTraversable(false);
            b.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white;");
            b.setFont(Font.font(20));
            b.setMinWidth(240);
            b.setMinHeight(44);
            menuBox.getChildren().add(b);
        }
        cursor = new Rectangle(12, 32, Color.YELLOW);
        cursor.setArcWidth(4);
        cursor.setArcHeight(4);
        rootPane = new StackPane();
        rootPane.getChildren().addAll(bgView, menuBox);
        StackPane.setAlignment(menuBox, Pos.CENTER);
        FXGL.getGameScene().addUINode(rootPane);
        FXGL.getGameScene().addUINode(cursor);
        bgView.fitWidthProperty().bind(rootPane.widthProperty());
        bgView.fitHeightProperty().bind(rootPane.heightProperty());
        rootPane.addEventFilter(MouseEvent.ANY, MouseEvent::consume);
        for (Node n : menuBox.getChildren()) {
            n.addEventFilter(MouseEvent.ANY, MouseEvent::consume);
        }
        rootPane.setCursor(Cursor.NONE);
        for (Node n : menuBox.getChildren()) {
            if (n instanceof Button) {
                Button b = (Button) n;
                if ("Continuar".equals(b.getText())) {
                    boolean saveExists = (game != null && game.getSave() != null && game.getSave().exists());
                    b.setDisable(!saveExists);
                    if (b.isDisable()) {
                        b.setStyle("-fx-background-color: rgba(80,80,80,0.5); -fx-text-fill: rgba(200,200,200,0.7);");
                    }
                }
            }
        }
        Platform.runLater(() -> {
            placeCursorImmediate();
            startBackgroundMusic();
        });
        menuBox.boundsInParentProperty().addListener((o, oldB, newB) -> updateCursorSmooth());
        rootPane.widthProperty().addListener((o, oldV, newV) -> updateCursorSmooth());
        rootPane.heightProperty().addListener((o, oldV, newV) -> updateCursorSmooth());
    }

    private void placeCursorImmediate() {
        updateCursorStyles();
        Node target = menuBox.getChildren().get(selectedIndex);
        Bounds btnBounds = target.localToScene(target.getBoundsInLocal());
        double cursorX = btnBounds.getMinX() - 30;
        double cursorY = btnBounds.getMinY() + (btnBounds.getHeight() - cursor.getHeight()) / 2.0 - CURSOR_UP_OFFSET;
        cursor.setTranslateX(cursorX);
        cursor.setTranslateY(cursorY);
    }

    private void updateCursorStyles() {
        int total = menuBox.getChildren().size();
        boolean hasItems = (total > 0);

        if (hasItems) {
            if (selectedIndex >= total) {
                selectedIndex = 0;
            }

            Node selNode = menuBox.getChildren().get(selectedIndex);
            boolean selIsDisabled = (selNode instanceof Button) && ((Button) selNode).isDisable();

            if (selIsDisabled) {
                int start = selectedIndex;
                boolean found = false;
                int idx = selectedIndex;
                int attempts = 0;

                while (!found && attempts < total) {
                    idx = (idx + 1) % total;
                    attempts++;
                    Node n = menuBox.getChildren().get(idx);
                    if (n instanceof Button && !((Button) n).isDisable()) {
                        selectedIndex = idx;
                        found = true;
                    }
                }
            }

            for (int i = 0; i < total; i++) {
                Node n = menuBox.getChildren().get(i);
                boolean isButton = (n instanceof Button);

                if (isButton) {
                    Button b = (Button) n;
                    b.setWrapText(true);
                    b.setAlignment(Pos.CENTER);

                    if (b.isDisable()) {
                        b.setStyle("-fx-background-color: rgba(80,80,80,0.5); -fx-text-fill: rgba(200,200,200,0.7); -fx-background-radius: 6; -fx-padding: 8 12 8 12;");
                        b.setEffect(null);
                        b.setFont(Font.font(b.getFont().getFamily(), 20));
                    } else if (i == selectedIndex) {
                        b.setStyle("-fx-background-color: linear-gradient(#FFD54F, #FFC107); -fx-text-fill: black; -fx-font-weight: bold; -fx-border-color: #FFD700; -fx-border-width: 2; -fx-background-radius: 6; -fx-padding: 8 12 8 12;");
                        b.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.45), 8, 0.3, 0, 2));
                        b.setFont(Font.font(b.getFont().getFamily(), FontWeight.BOLD, 20));
                    } else {
                        b.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 8 12 8 12;");
                        b.setEffect(null);
                        b.setFont(Font.font(b.getFont().getFamily(), 20));
                    }
                } else {

                }
            }
        } else {
            selectedIndex = 0;
        }
    }

    private void updateCursorSmooth() {
        updateCursorStyles();
        Node target = menuBox.getChildren().get(selectedIndex);
        Bounds btnBounds = target.localToScene(target.getBoundsInLocal());
        double toX = btnBounds.getMinX() - 30;
        double toY = btnBounds.getMinY() + (btnBounds.getHeight() - cursor.getHeight()) / 2.0 - CURSOR_UP_OFFSET;
        TranslateTransition tt = new TranslateTransition(CURSOR_MOVE_DURATION, cursor);
        tt.setToX(toX);
        tt.setToY(toY);
        tt.setInterpolator(Interpolator.EASE_BOTH);
        tt.play();
    }

    private String showNewGameDialog() {
        String resultName = null;
        boolean finished = false;
        javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog();
        dlg.setTitle("New Game");
        dlg.setHeaderText("Enter your name:");
        dlg.setContentText("Name:");
        while (!finished) {
            Optional<String> opt = dlg.showAndWait();
            if (!opt.isPresent()) {
                finished = true;
            } else {
                String name = opt.get().trim();
                if (!name.isEmpty()) {
                    resultName = name;
                    finished = true;
                } else {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
                    alert.setTitle("Invalid Name");
                    alert.setHeaderText("Name cannot be empty");
                    alert.setContentText("Introduce a valid name for the hero.");
                    alert.showAndWait();
                    dlg.getEditor().setText("");
                }
            }
        }
        return resultName;
    }

    private void showConfigScreen() {
        Platform.runLater(() -> {
            boolean shouldOpen = !configOpen;

            if (shouldOpen) {
                configOpen = true;

                for (Node n : menuBox.getChildren()) {
                    if (n instanceof Button) {
                        ((Button) n).setDisable(true);
                    }
                }

                StackPane overlay = new StackPane();
                overlay.setStyle("-fx-background-color: rgba(0,0,0,0.6);");
                overlay.setPrefSize(800, 600);
                overlay.setCursor(Cursor.DEFAULT);
                overlay.setPickOnBounds(true);

                VBox content = new VBox(16);
                content.setAlignment(Pos.CENTER);
                content.setStyle("-fx-background-color: rgba(30,30,30,0.95); -fx-padding: 20; -fx-background-radius: 8;");
                content.setMaxWidth(420);

                Label title = new Label("Game Settings");
                title.setFont(Font.font(22));
                title.setTextFill(Color.WHITE);

                Label volLabel = new Label();
                volLabel.setTextFill(Color.WHITE);
                volLabel.setFont(Font.font(14));

                double initialVolume = volumeSetting;
                try {
                    Object audioRoot = null;
                    try {
                        var m = FXGL.class.getMethod("getAudioPlayer");
                        audioRoot = m.invoke(null);
                    } catch (NoSuchMethodException ignored) {
                    }

                    if (audioRoot == null) {
                        try {
                            var m2 = FXGL.class.getMethod("getAudio");
                            audioRoot = m2.invoke(null);
                        } catch (NoSuchMethodException ignored) {
                        }
                    }

                    if (audioRoot != null) {
                        Class<?> cls = audioRoot.getClass();
                        String[] getters = {"getMusicVolume", "getSoundVolume", "getGlobalVolume", "getVolume"};
                        boolean found = false;
                        for (int i = 0; i < getters.length && !found; i++) {
                            String name = getters[i];
                            try {
                                var gm = cls.getMethod(name);
                                Object val = gm.invoke(audioRoot);
                                if (val instanceof Number) {
                                    initialVolume = ((Number) val).doubleValue();
                                    found = true;
                                }
                            } catch (NoSuchMethodException ignored) {
                            }
                        }
                    }
                } catch (Exception ignored) {
                }

                Slider volSlider = new Slider(0, 1, initialVolume);
                volSlider.setMajorTickUnit(0.1);
                volSlider.setBlockIncrement(0.05);
                volSlider.setShowTickLabels(true);
                volSlider.setShowTickMarks(true);
                volSlider.setSnapToTicks(false);
                volLabel.setText(String.format("Volume: %d%%", (int) (volSlider.getValue() * 100)));
                volSlider.valueProperty().addListener((obs, oldV, newV) -> {
                    int pct = (int) Math.round(newV.doubleValue() * 100);
                    volLabel.setText("Volume: " + pct + "%");
                    applyVolume(newV.doubleValue());
                });

                Button deleteBtn = new Button("Erase savefile");
                deleteBtn.setMinWidth(200);
                deleteBtn.setStyle("-fx-background-color: linear-gradient(#E57373,#EF5350); -fx-text-fill: white; -fx-font-weight: bold;");
                deleteBtn.setOnAction(e -> {
                    boolean deleted = false;
                    if (game != null && game.getSave() != null) {
                        File saveFile = game.getSave();
                        if (saveFile.exists()) {
                            deleted = saveFile.delete();
                        }
                    }
                    if (!deleted && game != null && game.getArchives() != null) {
                        File arch = game.getArchives();
                        if (arch.exists()) {
                            deleted = arch.delete();
                        }
                    }

                    if (deleted) {
                        showBlackModal("Savefile Deleted Correctly", "Save File has been erased correctly", null);

                        for (Node n : menuBox.getChildren()) {
                            if (n instanceof Button) {
                                Button b = (Button) n;
                                if ("Continue".equals(b.getText())) {
                                    b.setDisable(true);
                                    b.setStyle("-fx-background-color: rgba(80,80,80,0.5); -fx-text-fill: rgba(200,200,200,0.7);");

                                }
                            }
                        }
                    } else {
                        showBlackModal("Save could't be deleted.", "Savefile doesn't exists or couldn't be eliminated.", null);

                    }

                });

                Button closeBtn = new Button("Close");
                closeBtn.setMinWidth(120);
                closeBtn.setOnAction(ev -> {
                    FXGL.getGameScene().removeUINode(overlay);
                    if (rootPane != null) {
                        rootPane.setCursor(Cursor.NONE);
                    }

                    boolean saveExists = (game != null && game.getSave() != null && game.getSave().exists());
                    for (Node n : menuBox.getChildren()) {
                        if (n instanceof Button) {
                            Button b = (Button) n;
                            if ("Continue".equals(b.getText())) {
                                b.setDisable(!saveExists);
                                if (b.isDisable()) {
                                    b.setStyle("-fx-background-color: rgba(80,80,80,0.5); -fx-text-fill: rgba(200,200,200,0.7);");
                                } else {
                                    b.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 8 12 8 12;");
                                }
                            } else {
                                b.setDisable(false);
                            }
                        }
                    }

                    configOpen = false;
                    Platform.runLater(this::updateCursorSmooth);
                });

                javafx.scene.layout.HBox foot = new javafx.scene.layout.HBox(12, deleteBtn, closeBtn);
                foot.setAlignment(Pos.CENTER);

                content.getChildren().addAll(title, volLabel, volSlider, foot);
                overlay.getChildren().add(content);
                StackPane.setAlignment(content, Pos.CENTER);

                overlay.addEventFilter(MouseEvent.MOUSE_PRESSED, ev -> {
                    if (ev.getTarget() == overlay) {
                        ev.consume();
                    }
                });

                FXGL.getGameScene().addUINode(overlay);
                overlay.requestFocus();
            }
        });
    }

    private void startBackgroundMusic() {
        boolean shouldStart = false;
        URL res = null;

        try {
            shouldStart = (bgMusic == null);
            if (shouldStart) {
                res = getClass().getResource("/Resources/music/mainScreen.mp3");
                shouldStart = (res != null);
            }

            if (shouldStart) {
                Media media = new Media(res.toExternalForm());
                MediaPlayer player = new MediaPlayer(media);
                player.setCycleCount(MediaPlayer.INDEFINITE);
                player.setVolume(volumeSetting);
                player.play();
                bgMusic = player;
            }
        } catch (Exception ignored) {
        }
    }

    public static double getVolumeSetting() {
        double volume = 0.7;

        try {
            Object app = FXGL.getApp();
            if (app instanceof MainScreen) {
                volume = ((MainScreen) app).volumeSetting;
            }
        } catch (Exception ignored) {
        }

        return volume;
    }

    private void stopBackgroundMusic() {
        try {
            if (bgMusic != null) {
                bgMusic.stop();
                bgMusic.dispose();
                bgMusic = null;
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyVolume(double vol) {
        this.volumeSetting = vol;
        try {
            if (bgMusic != null) {
                bgMusic.setVolume(vol);
            }
        } catch (Throwable ignored) {
        }
        try {
            Object audioRoot = null;
            try {
                var m = FXGL.class.getMethod("getAudioPlayer");
                audioRoot = m.invoke(null);
            } catch (NoSuchMethodException ignored) {
            }
            if (audioRoot == null) {
                try {
                    var m2 = FXGL.class.getMethod("getAudio");
                    audioRoot = m2.invoke(null);
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (audioRoot != null) {
                Class<?> cls = audioRoot.getClass();
                String[] candidates = {"setMusicVolume", "setSoundVolume", "setGlobalVolume", "setVolume", "setMusicGain"};
                for (String name : candidates) {
                    try {
                        try {
                            var mm = cls.getMethod(name, double.class);
                            mm.invoke(audioRoot, vol);
                        } catch (NoSuchMethodException e1) {
                            var mm2 = cls.getMethod(name, float.class);
                            mm2.invoke(audioRoot, (float) vol);
                        }
                    } catch (Throwable ignored) {
                    }
                }
                try {
                    var sm = cls.getMethod("setMusicVolume", double.class);
                    sm.invoke(audioRoot, vol);
                } catch (Throwable ignored) {
                }
                try {
                    var ss = cls.getMethod("setSoundVolume", double.class);
                    ss.invoke(audioRoot, vol);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void activateSelected() {
        boolean proceed = true;

        // Comprobar estado de configuración
        if (configOpen) {
            proceed = false;
        }

        // Comprobar índices y estructuras antes de ejecutar
        if (proceed) {
            if (labels == null || selectedIndex < 0 || selectedIndex >= labels.length) {
                proceed = false;
            }
        }
        if (proceed) {
            if (menuBox == null || menuBox.getChildren().size() <= selectedIndex) {
                proceed = false;
            }
        }

        if (proceed) {
            String sel = labels[selectedIndex];
            Node target = menuBox.getChildren().get(selectedIndex);

            // Animación de "ping"
            ScaleTransition st = new ScaleTransition(BUTTON_PING_DURATION, target);
            st.setFromX(1.0);
            st.setFromY(1.0);
            st.setToX(1.12);
            st.setToY(1.12);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.setOnFinished(evt -> {
                ScaleTransition stBack = new ScaleTransition(Duration.millis(120), target);
                stBack.setFromX(1.12);
                stBack.setFromY(1.12);
                stBack.setToX(1.0);
                stBack.setToY(1.0);
                stBack.setInterpolator(Interpolator.EASE_IN);
                stBack.play();
            });
            st.play();

            // Lógica de selección
            switch (sel) {
                case "Continue":
                    if (game != null && game.getSave() != null && game.getSave().exists()) {
                        boolean correct = game.readSaveGame();

                        if (correct) {
                            String heroName = game.getHero().getName();
                            showBlackModal("The game has started Correctly", "Your save file has loaded correctly: " + heroName, () -> {
                                stopBackgroundMusic();
                                showLoadingThenMap();
                            });
                        } else {
                            showBlackModal("Loading Error", "An unexpected Error has occured please create a new save file.", null);
                        }
                    }
                    break;

                case "New Game":
                    String name = showNewGameDialog();
                    if (name != null) {
                        if (game != null) {
                            game.createHero(name);
                            boolean cor = game.createSaveGame();

                            if (cor) {
                                showBlackModal("Savefile created!", "The savefile has been created. You can start the game! " + "Enjoy, " + name, null);

                                for (Node n : menuBox.getChildren()) {
                                    if (n instanceof Button) {
                                        Button b = (Button) n;
                                        if ("Continue".equals(b.getText())) {
                                            b.setDisable(false);
                                            b.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 8 12 8 12;");
                                        }
                                    }
                                }
                                updateCursorSmooth();
                            } else {
                                showBlackModal("Savefile was not created!", "The savefile could not be created", null);
                               
                            }
                        }
                    }
                    break;

                case "Settings":
                    showConfigScreen();
                    break;

                case "Quit":
                    stopBackgroundMusic();
                    Platform.runLater(Platform::exit);
                    break;

                default:
                    break;
            }
        }
    }

    private StackPane newLoadingOverlay() {
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.8);");
        overlay.setPrefSize(800, 600);
        Label label = new Label("Loading Game..");
        label.setTextFill(Color.WHITE);
        label.setFont(Font.font(18));
        javafx.scene.shape.Rectangle progBg = new javafx.scene.shape.Rectangle(320, 12, Color.rgb(255, 255, 255, 0.12));
        progBg.setArcWidth(6);
        progBg.setArcHeight(6);
        javafx.scene.shape.Rectangle progFill = new javafx.scene.shape.Rectangle(0, 12, Color.web("#FFD54F"));
        progFill.setArcWidth(6);
        progFill.setArcHeight(6);
        StackPane bar = new StackPane(progBg, progFill);
        bar.setMaxWidth(320);
        VBox box = new VBox(12, label, bar);
        box.setAlignment(Pos.CENTER);
        overlay.getChildren().add(box);
        StackPane.setAlignment(box, Pos.CENTER);
        javafx.animation.Timeline t = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.ZERO, new javafx.animation.KeyValue(progFill.widthProperty(), 0)),
                new javafx.animation.KeyFrame(Duration.millis(DURACION_CARGA_MS), new javafx.animation.KeyValue(progFill.widthProperty(), progBg.getWidth()))
        );
        t.setCycleCount(1);
        t.play();
        return overlay;
    }

    private void showLoadingThenMap() {
        StackPane overlay = newLoadingOverlay();
        overlay.setOpacity(0);
        FXGL.getGameScene().addUINode(overlay);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), overlay);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(220), overlay);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setInterpolator(Interpolator.EASE_IN);

        fadeIn.setOnFinished(e -> {
            PauseTransition pause = new PauseTransition(Duration.millis(DURACION_CARGA_MS));
            pause.setOnFinished(ev -> {
                try {
                    FXGL.getGameScene().removeUINode(rootPane);
                } catch (Throwable ignored) {
                }
                try {
                    FXGL.getGameScene().removeUINode(cursor);
                } catch (Throwable ignored) {
                }

                currentMapScreen = new GameMapScreen(game);

                Hero h = game.getHero();
                if (h != null) {
                    Hero.Location loc = h.getLastLocation();
                    double lx = h.getLastPosX();
                    double ly = h.getLastPosY();

                    switch (loc) {
                        case FIELD_VILLAGE -> {
                            double x = 665.5536599999996;
                            double y = 864.0;
                            FieldVillage field = new FieldVillage(game);
                            field.showWithLoading(() -> {
                                Platform.runLater(() -> field.setHeroPosition(lx, ly));
                            }, () -> {
                                Platform.runLater(() -> {
                                    currentMapScreen.show();
                                    if (h.getLastLocation() == Hero.Location.MAP) {
                                        currentMapScreen.setHeroPosition(h.getLastPosX(), h.getLastPosY());
                                    } else {
                                        currentMapScreen.resetHeroToCenter();
                                    }
                                    currentMapScreen.drawDebugObstacles();
                                });
                            });
                        }
                        case FOREST_HOUSE -> {
                            ForestHouse fh = new ForestHouse(game);
                            double startX = 384.0;
                            double startY = 576.0;
                            fh.showWithLoading(() -> {
                                Platform.runLater(() -> fh.setHeroPosition(lx, ly));
                            }, () -> {
                                Platform.runLater(() -> {
                                    currentMapScreen.show();
                                    if (h.getLastLocation() == Hero.Location.MAP) {
                                        currentMapScreen.setHeroPosition(startX, startY);
                                    } else {
                                        currentMapScreen.resetHeroToCenter();
                                    }
                                    currentMapScreen.drawDebugObstacles();
                                });
                            });
                        }
                        case MAP -> {
                            currentMapScreen.setHeroPosition(lx, ly);
                            currentMapScreen.show();
                        }
                        case SWAMP -> {
                            Swamp swamp = new Swamp(game);
                            double startX = 2352.0;
                            double startY = 607.059;
                            swamp.showWithLoading(() -> {
                                Platform.runLater(() -> swamp.setHeroPosition(startX, startY));
                            }, () -> {
                                Platform.runLater(() -> {
                                    currentMapScreen.show();
                                    if (h.getLastLocation() == Hero.Location.MAP) {
                                        currentMapScreen.setHeroPosition(h.getLastPosX(), h.getLastPosY());
                                    } else {
                                        currentMapScreen.resetHeroToCenter();
                                    }
                                    currentMapScreen.drawDebugObstacles();
                                });
                            });
                        }
                        case SWAMP_DUNGEON -> {
                            SwampDungeon swamp = new SwampDungeon(game);
                            double startX = 500.1253860000012;
                            double startY = 1200.0;
                            swamp.showWithLoading(() -> {
                                Platform.runLater(() -> swamp.setHeroPosition(startX, startY));
                            }, () -> {
                                Platform.runLater(() -> {
                                    currentMapScreen.show();
                                    if (h.getLastLocation() == Hero.Location.MAP) {
                                        currentMapScreen.setHeroPosition(h.getLastPosX(), h.getLastPosY());
                                    } else {
                                        currentMapScreen.resetHeroToCenter();
                                    }
                                    currentMapScreen.drawDebugObstacles();
                                });
                            });
                        }

                        default -> {
                            currentMapScreen.resetHeroToCenter();
                            currentMapScreen.show();
                        }
                    }
                } else {
                    currentMapScreen.resetHeroToCenter();
                    currentMapScreen.show();
                }

                fadeOut.play();
            });
            pause.play();
        });

        fadeOut.setOnFinished(e2 -> {
            try {
                FXGL.getGameScene().removeUINode(overlay);
            } catch (Throwable ignored) {
            }
        });

        fadeIn.play();
    }

    public static void hideMenu() {
        Platform.runLater(() -> {
            try {
                if (rootPane != null) {
                    FXGL.getGameScene().removeUINode(rootPane);
                }
                if (cursor != null) {
                    FXGL.getGameScene().removeUINode(cursor);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    public static void restoreMenuAndMusic() {
        Platform.runLater(() -> {
            try {
                if (rootPane != null) {
                    FXGL.getGameScene().addUINode(rootPane);
                }
                if (cursor != null) {
                    FXGL.getGameScene().addUINode(cursor);
                }
            } catch (Throwable ignored) {
            }
            Platform.runLater(() -> {
                try {
                    GameApplication app = (GameApplication) FXGL.getApp();
                    if (app instanceof MainScreen) {
                        ((MainScreen) app).startBackgroundMusic();
                        ((MainScreen) app).updateCursorSmooth();
                    }
                } catch (Throwable ignored) {
                }
            });
        });
    }

    public static void setModalOpen(boolean open) {
        modalOpen = open;
    }

    public static boolean isModalOpen() {
        return modalOpen;
    }

    private void showBlackModal(String titleText, String messageText, Runnable onConfirm) {
        Platform.runLater(() -> {
            if (modalOpen) {
                return;
            }
            modalOpen = true;

            StackPane overlay = new StackPane();
            overlay.setStyle("-fx-background-color: rgba(0,0,0,0.75);");
            overlay.setPrefSize(800, 600);
            overlay.setCursor(Cursor.DEFAULT);

            // Contenedor central (cartel)
            VBox card = new VBox(14);
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(18));
            card.setMaxWidth(420);
            card.setStyle("-fx-background-color: #0b0b0b; -fx-background-radius: 10; -fx-border-color: #FFD54F; -fx-border-radius: 10; -fx-border-width: 2;");

            Label title = new Label(titleText);
            title.setFont(Font.font("System", FontWeight.BOLD, 20));
            title.setTextFill(Color.web("#FFD54F"));

            Text message = new Text(messageText);
            message.setFill(Color.WHITE);
            message.setWrappingWidth(360);
            message.setFont(Font.font(14));

            Button ok = new Button("OK");
            ok.setMinWidth(120);
            ok.setStyle("-fx-background-color: linear-gradient(#FFD54F,#FFC107); -fx-text-fill: black; -fx-font-weight: bold; -fx-background-radius: 6;");
            ok.setOnAction(e -> {
                // animación de salida
                FadeTransition ft = new FadeTransition(Duration.millis(180), overlay);
                ft.setFromValue(1);
                ft.setToValue(0);
                ft.setOnFinished(ev -> {
                    FXGL.getGameScene().removeUINode(overlay);
                    modalOpen = false;
                    if (rootPane != null) {
                        rootPane.setCursor(Cursor.NONE);
                    }
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                });
                ft.play();
            });

            card.getChildren().addAll(title, message, ok);
            overlay.getChildren().add(card);
            StackPane.setAlignment(card, Pos.CENTER);

            overlay.setOnKeyPressed((KeyEvent ke) -> {
                if (ke.getCode() == KeyCode.ENTER) {
                    ok.fire();
                    ke.consume();
                }
            });

            overlay.addEventFilter(MouseEvent.MOUSE_PRESSED, ev -> {
                if (ev.getTarget() == overlay) {
                    ev.consume();
                }
            });

            overlay.setOpacity(0);
            FXGL.getGameScene().addUINode(overlay);
            overlay.requestFocus();

            FadeTransition fadeIn = new FadeTransition(Duration.millis(180), overlay);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
