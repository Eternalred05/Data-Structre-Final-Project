package Runner;

import Characters.Hero;
import Logic.Game;
import com.almasb.fxgl.app.GameApplication;
import static com.almasb.fxgl.app.GameApplication.launch;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.Duration;

import com.almasb.fxgl.app.GameApplication;
import static com.almasb.fxgl.app.GameApplication.launch;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.Duration;

public class MainScreen extends GameApplication {

    private Alert a;
    private Hero hero;
    private Game game;

    private final String[] labels = {"Continuar", "Nueva Partida", "Configuración", "Salir"};
    private int selectedIndex = 0;
    private Rectangle cursor;
    private VBox menuBox;

    private final Duration CURSOR_MOVE_DURATION = Duration.millis(160);
    private final Duration BUTTON_PING_DURATION = Duration.millis(180);

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("The Mistery of The Ruins");
        settings.setWidth(800);
        settings.setHeight(600);
    }

    @Override
    protected void initInput() {
        FXGL.onKeyDown(KeyCode.UP, () -> {
            selectedIndex = (selectedIndex - 1 + labels.length) % labels.length;
            updateCursorSmooth();
        });

        FXGL.onKeyDown(KeyCode.DOWN, () -> {
            selectedIndex = (selectedIndex + 1) % labels.length;
            updateCursorSmooth();
        });

        FXGL.onKeyDown(KeyCode.ENTER, this::activateSelected);

        FXGL.onKeyDown(KeyCode.W, () -> {
            selectedIndex = (selectedIndex - 1 + labels.length) % labels.length;
            updateCursorSmooth();
        });
        FXGL.onKeyDown(KeyCode.S, () -> {
            selectedIndex = (selectedIndex + 1) % labels.length;
            updateCursorSmooth();
        });
    }

    @Override
    protected void initGame() {
        game = new Game();
    }

    @Override
    protected void initUI() {
        Image bgImage = new Image(getClass().getResourceAsStream("/Resources/textures/MainScreen.png"));
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

        StackPane root = new StackPane();
        root.getChildren().addAll(bgView, menuBox);
        StackPane.setAlignment(menuBox, Pos.CENTER);
        FXGL.getGameScene().addUINode(root);
        FXGL.getGameScene().addUINode(cursor);

        bgView.fitWidthProperty().bind(root.widthProperty());
        bgView.fitHeightProperty().bind(root.heightProperty());

        root.addEventFilter(MouseEvent.ANY, MouseEvent::consume);
        for (Node n : menuBox.getChildren()) {
            n.addEventFilter(MouseEvent.ANY, MouseEvent::consume);
        }

        Platform.runLater(this::placeCursorImmediate);
        menuBox.boundsInParentProperty().addListener((o, oldB, newB) -> updateCursorSmooth());
        root.widthProperty().addListener((o, oldV, newV) -> updateCursorSmooth());
        root.heightProperty().addListener((o, oldV, newV) -> updateCursorSmooth());
    }

    private void placeCursorImmediate() {
        updateCursorStyles();
        Node target = menuBox.getChildren().get(selectedIndex);
        Bounds btnBounds = target.localToScene(target.getBoundsInLocal());
        double cursorX = btnBounds.getMinX() - 30;
        double cursorY = btnBounds.getMinY() + (btnBounds.getHeight() - cursor.getHeight()) / 2.0;
        cursor.setTranslateX(cursorX);
        cursor.setTranslateY(cursorY);
    }

    private void updateCursorStyles() {
        for (int i = 0; i < menuBox.getChildren().size(); i++) {
            Node n = menuBox.getChildren().get(i);
            if (n instanceof Button) {
                Button b = (Button) n;
                if (i == selectedIndex) {
                    b.setStyle("-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: white; -fx-border-color: yellow; -fx-border-width: 1;");
                } else {
                    b.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white;");
                }
            }
        }
    }

    private void updateCursorSmooth() {
        updateCursorStyles();

        Node target = menuBox.getChildren().get(selectedIndex);
        Bounds btnBounds = target.localToScene(target.getBoundsInLocal());

        double toX = btnBounds.getMinX() - 30;
        double toY = btnBounds.getMinY() + (btnBounds.getHeight() - cursor.getHeight()) / 2.0;

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
            java.util.Optional<String> opt = dlg.showAndWait();

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

    private void activateSelected() {
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
                boolean correct = game.readSaveGame();
                a = new Alert(Alert.AlertType.INFORMATION);
                if (correct) {
                    a.setHeaderText("Partida Iniciada");
                    a.setTitle("Iniciada la partida correctamente");
                    a.setContentText("La partida se ha cargado correctamente: " + game.getHero().getName());
                    a.showAndWait();
                } else {
                    a.setAlertType(Alert.AlertType.ERROR);
                    a.setTitle("No se pudo iniciar la partida");
                    a.setHeaderText("Incorrecto");
                    a.setContentText("Error ");
                    a.showAndWait();
                }
                break;
            case "Nueva Partida":
                initGame();
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
                a = new Alert(Alert.AlertType.INFORMATION);
                a.setTitle("Holis");
                a.setHeaderText("Se supone que deba haber una configuracion xd");
                a.setContentText("Hola, soy la configuracion xdxd");
                a.showAndWait();
                break;
            case "Salir":
                Platform.runLater(() -> {
                    Platform.exit();
                });
                break;
            default:
                break;
        }
    }

    public static void main(String[] args) {

        launch(args);
    }
}
