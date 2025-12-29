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

public class MainScreen extends GameApplication {

    private Alert a;
    private Hero hero;
    private Game game;
    private final String[] labels = {"Continuar", "Nueva Partida", "Configuración", "Salir"};
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

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("The Mistery of The Ruins");
        settings.setWidth(800);
        settings.setHeight(600);
    }

    @Override
    protected void initInput() {
        FXGL.onKeyDown(KeyCode.UP, () -> {
            if (configOpen) {
                return;
            }
            selectedIndex = (selectedIndex - 1 + labels.length) % labels.length;
            updateCursorSmooth();
        });
        FXGL.onKeyDown(KeyCode.DOWN, () -> {
            if (configOpen) {
                return;
            }
            selectedIndex = (selectedIndex + 1) % labels.length;
            updateCursorSmooth();
        });
        FXGL.onKeyDown(KeyCode.ENTER, () -> {
            if (configOpen) {
                return;
            }
            activateSelected();
        });
        FXGL.onKeyDown(KeyCode.W, () -> {
            if (configOpen) {
                return;
            }
            selectedIndex = (selectedIndex - 1 + labels.length) % labels.length;
            updateCursorSmooth();
        });
        FXGL.onKeyDown(KeyCode.S, () -> {
            if (configOpen) {
                return;
            }
            selectedIndex = (selectedIndex + 1) % labels.length;
            updateCursorSmooth();
        });
    }

    @Override
    protected void initGame() {
        game = new Game();
        game.createItems();
        game.createMonsters();
        game.createTasks();

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
        if (total == 0) {
            return;
        }
        if (selectedIndex >= total) {
            selectedIndex = 0;
        }
        if (menuBox.getChildren().get(selectedIndex) instanceof Button
                && ((Button) menuBox.getChildren().get(selectedIndex)).isDisable()) {
            int start = selectedIndex;
            boolean found = false;
            int idx = selectedIndex;
            while (!found) {
                idx = (idx + 1) % total;
                if (idx == start) {
                    break;
                }
                Node n = menuBox.getChildren().get(idx);
                if (n instanceof Button && !((Button) n).isDisable()) {
                    selectedIndex = idx;
                    found = true;
                    break;
                }
            }
        }
        for (int i = 0; i < total; i++) {
            Node n = menuBox.getChildren().get(i);
            if (n instanceof Button) {
                Button b = (Button) n;
                b.setWrapText(true);
                b.setAlignment(Pos.CENTER);
                if (b.isDisable()) {
                    b.setStyle("-fx-background-color: rgba(80,80,80,0.5); -fx-text-fill: rgba(200,200,200,0.7); -fx-background-radius: 6; -fx-padding: 8 12 8 12;");
                    b.setEffect(null);
                    b.setFont(Font.font(b.getFont().getFamily(), 20));
                    continue;
                }
                if (i == selectedIndex) {
                    b.setStyle("-fx-background-color: linear-gradient(#FFD54F, #FFC107); -fx-text-fill: black; -fx-font-weight: bold; -fx-border-color: #FFD700; -fx-border-width: 2; -fx-background-radius: 6; -fx-padding: 8 12 8 12;");
                    b.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.45), 8, 0.3, 0, 2));
                    b.setFont(Font.font(b.getFont().getFamily(), FontWeight.BOLD, 20));
                } else {
                    b.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 8 12 8 12;");
                    b.setEffect(null);
                    b.setFont(Font.font(b.getFont().getFamily(), 20));
                }
            }
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
        dlg.setTitle("Nueva Partida");
        dlg.setHeaderText("Introduce el nombre del jugador");
        dlg.setContentText("Nombre:");
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
                    alert.setTitle("Nombre inválido");
                    alert.setHeaderText("El nombre no puede estar vacío");
                    alert.setContentText("Introduce un nombre válido.");
                    alert.showAndWait();
                    dlg.getEditor().setText("");
                }
            }
        }
        return resultName;
    }

    private void showConfigScreen() {
        Platform.runLater(() -> {
            if (configOpen) {
                return;
            }
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
            Label title = new Label("Configuración");
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
                    for (String name : getters) {
                        try {
                            var gm = cls.getMethod(name);
                            Object val = gm.invoke(audioRoot);
                            if (val instanceof Number) {
                                initialVolume = ((Number) val).doubleValue();
                                break;
                            }
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            Slider volSlider = new Slider(0, 1, initialVolume);
            volSlider.setMajorTickUnit(0.1);
            volSlider.setBlockIncrement(0.05);
            volSlider.setShowTickLabels(true);
            volSlider.setShowTickMarks(true);
            volSlider.setSnapToTicks(false);
            volLabel.setText(String.format("Volumen: %d%%", (int) (volSlider.getValue() * 100)));
            volSlider.valueProperty().addListener((obs, oldV, newV) -> {
                int pct = (int) Math.round(newV.doubleValue() * 100);
                volLabel.setText("Volumen: " + pct + "%");
                applyVolume(newV.doubleValue());
            });
            Button deleteBtn = new Button("Borrar partida");
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
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                if (deleted) {
                    info.setTitle("Partida borrada");
                    info.setHeaderText(null);
                    info.setContentText("El archivo de guardado ha sido eliminado.");
                    for (Node n : menuBox.getChildren()) {
                        if (n instanceof Button) {
                            Button b = (Button) n;
                            if ("Continuar".equals(b.getText())) {
                                b.setDisable(true);
                                b.setStyle("-fx-background-color: rgba(80,80,80,0.5); -fx-text-fill: rgba(200,200,200,0.7);");
                                break;
                            }
                        }
                    }
                } else {
                    info.setTitle("No se borró la partida");
                    info.setHeaderText(null);
                    info.setContentText("No existe ningún archivo de guardado o no se pudo eliminar.");
                }
                info.showAndWait();
            });
            Button closeBtn = new Button("Cerrar");
            closeBtn.setMinWidth(120);
            closeBtn.setOnAction(ev -> {
                FXGL.getGameScene().removeUINode(overlay);
                if (rootPane != null) {
                    rootPane.setCursor(Cursor.NONE);
                }
                for (Node n : menuBox.getChildren()) {
                    if (n instanceof Button) {
                        Button b = (Button) n;
                        boolean saveExists = (game != null && game.getSave() != null && game.getSave().exists());
                        if ("Continuar".equals(b.getText())) {
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
        });
    }

    private void startBackgroundMusic() {
        try {
            if (bgMusic != null) {
                return;
            }
            URL res = getClass().getResource("/Resources/music/mainScreen.mp3");
            if (res == null) {
                return;
            }
            Media media = new Media(res.toExternalForm());
            bgMusic = new MediaPlayer(media);
            bgMusic.setCycleCount(MediaPlayer.INDEFINITE);
            bgMusic.setVolume(volumeSetting);
            bgMusic.play();
        } catch (Throwable ignored) {
        }
    }

    public static double getVolumeSetting() {
        try {
            var app = FXGL.getApp();
            if (app instanceof MainScreen) {
                return ((MainScreen) app).volumeSetting;
            }
        } catch (Throwable ignored) {
        }
        return 0.7;
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
        if (configOpen) {
            return;
        }
        String sel = labels[selectedIndex];
        Node target = menuBox.getChildren().get(selectedIndex);
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
        switch (sel) {
            case "Continuar":
                if (game.getSave().exists()) {
                    boolean correct = game.readSaveGame();
                    a = new Alert(Alert.AlertType.INFORMATION);
                    if (correct) {
                        a.setHeaderText("Partida Iniciada");
                        a.setTitle("Iniciada la partida correctamente");
                        a.setContentText("La partida se ha cargado correctamente: " + game.getHero().getName());
                        a.showAndWait();
                        stopBackgroundMusic();
                        showLoadingThenMap();
                    } else {
                        a.setAlertType(Alert.AlertType.ERROR);
                        a.setTitle("No se pudo iniciar la partida");
                        a.setHeaderText("Incorrecto");
                        a.setContentText("Error ");
                        a.showAndWait();
                    }
                }
                break;
            case "Nueva Partida":
                String name = showNewGameDialog();
                if (name != null) {
                    game.createHero(name);
                    boolean cor = game.createSaveGame();
                    a = new Alert(Alert.AlertType.INFORMATION);
                    if (cor) {
                        a.setHeaderText("Partida Creada");
                        a.setTitle("Creada la partida correctamente");
                        a.setContentText("Creada la partida con nombre: " + name);
                        a.showAndWait();
                        for (Node n : menuBox.getChildren()) {
                            if (n instanceof Button) {
                                Button b = (Button) n;
                                if ("Continuar".equals(b.getText())) {
                                    b.setDisable(false);
                                    b.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 8 12 8 12;");
                                }
                            }
                        }
                        updateCursorSmooth();
                    } else {
                        a.setAlertType(Alert.AlertType.ERROR);
                        a.setTitle("No se pudo crear la partida");
                        a.setHeaderText("Incorrecto");
                        a.setContentText("Error ");
                        a.showAndWait();
                    }
                }
                break;
            case "Configuración":
                showConfigScreen();
                break;
            case "Salir":
                stopBackgroundMusic();
                Platform.runLater(() -> Platform.exit());
                break;
            default:
                break;
        }
    }

    private StackPane newLoadingOverlay() {
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.8);");
        overlay.setPrefSize(800, 600);
        Label label = new Label("Cargando mapa...");
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

    public static void main(String[] args) {
        launch(args);
    }
}
