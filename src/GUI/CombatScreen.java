package GUI;

import Logic.Game;
import Characters.*;
import Items.Item;
import Items.Wares;
import com.almasb.fxgl.dsl.FXGL;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import Runner.MainScreen;

import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ScrollPane;

public class CombatScreen {

    public final StackPane root;
    private final Pane content;
    private final ImageView backgroundView;

    // Layout regions
    private final Pane leftPane;
    private final Pane centerPane;
    private final Pane rightPane;
    private final HBox monstersBox;
    private final HBox actionButtons;

    private final Game game;
    private final List<Monster> monsters = new ArrayList<>();
    private final List<ImageView> monsterViews = new ArrayList<>();
    private Boss boss;
    private final Random rnd = new Random();
    private final int origDefense;
// Botones y selección por teclado
    private final List<Button> buttons = new ArrayList<>();
    private int selectedButtonIndex = 0;

    private Runnable onExit; // callback opcional al cerrar combate

    // Game Over UI
    private StackPane gameOverOverlay = null;
    private MediaPlayer gameOverPlayer = null;

    // Música de combate
    private MediaPlayer battleMusic = null;

    // Ruta configurable de la música de combate (puede cambiarse desde fuera)
    private String battleMusicPath = "/Resources/music/fieldBattle.mp3";

    // Flag que indica que estamos en estado Game Over (bloquea inputs salvo Start)
    private volatile boolean gameOverActive = false;

    // Cola de toasts (mensajes temporales no modales, uno a la vez)
    private final ToastQueue toastQueue = new ToastQueue();

    // Label para mostrar vida del héroe (actual / total)
    private final Label heroHpLabel = new Label();

    private StackPane inventoryOverlay = null;
    private boolean inventoryOpen = false;

    public CombatScreen(Game game, String bgPath, String encounter, Hero heroForIcon, boolean bossFight, Boss monster) {
        this.game = game;
        origDefense = game.getHero().getDefense();

        if (bossFight) {
            boss = monster;
        }

        root = new StackPane();
        root.setPrefSize(800, 600);
        root.setFocusTraversable(true);

        // Fondo
        Image bg = null;
        try {
            bg = new Image(getClass().getResourceAsStream(bgPath));
        } catch (Throwable ignored) {
        }
        backgroundView = new ImageView(bg);
        backgroundView.setPreserveRatio(false);
        backgroundView.setFitWidth(800);
        backgroundView.setFitHeight(600);

        content = new Pane();
        content.setPrefSize(800, 600);

        leftPane = new Pane();
        leftPane.setPrefSize(220, 600);
        leftPane.setLayoutX(0);
        leftPane.setLayoutY(0);

        centerPane = new Pane();
        centerPane.setPrefSize(360, 600);
        centerPane.setLayoutX(220);
        centerPane.setLayoutY(0);

        rightPane = new Pane();
        rightPane.setPrefSize(220, 600);
        rightPane.setLayoutX(520);
        rightPane.setLayoutY(0);

        monstersBox = new HBox(8);
        monstersBox.setAlignment(Pos.CENTER);
        monstersBox.setPrefWidth(200);
        monstersBox.setLayoutX(10);
        monstersBox.setLayoutY(120);
        rightPane.getChildren().add(monstersBox);

        Rectangle bottomPanel = new Rectangle(800, 96, Color.rgb(10, 10, 10, 0.86));
        bottomPanel.setLayoutX(0);
        bottomPanel.setLayoutY(504);

        actionButtons = new HBox(12);
        actionButtons.setAlignment(Pos.CENTER);
        actionButtons.setPadding(new Insets(12));
        actionButtons.setLayoutX(0);
        actionButtons.setLayoutY(520);
        actionButtons.setPrefWidth(800);

        content.getChildren().addAll(backgroundView, leftPane, centerPane, rightPane, bottomPanel, actionButtons);
        root.getChildren().add(content);

        createHeroIcon(heroForIcon);

        // Hero HP label: esquina superior derecha
        setupHeroHpLabel();
        if (bossFight) {
            Monster m = createBoss(boss);
            monsters.add(m);
            ImageView mv = createMonsterView(m);
            monsterViews.add(mv);
            VBox wrapper = new VBox(6);
            wrapper.setAlignment(Pos.CENTER);
            Text name = new Text(m.getName());
            name.setFill(Color.WHITE);
            name.setFont(Font.font(12));
            wrapper.getChildren().addAll(mv, name);
            wrapper.setMouseTransparent(true);
            monstersBox.getChildren().add(wrapper);

        } else {
            // Generar entre 1 y 3 monstruos y colocarlos en monstersBox
            int count = 1 + rnd.nextInt(3);
            int i = 0;
            while (i < count) {
                Monster m = foundMonster(encounter);
                monsters.add(m);
                ImageView mv = createMonsterView(m);
                monsterViews.add(mv);
                VBox wrapper = new VBox(6);
                wrapper.setAlignment(Pos.CENTER);
                Text name = new Text(m.getName());
                name.setFill(Color.WHITE);
                name.setFont(Font.font(12));
                wrapper.getChildren().addAll(mv, name);
                wrapper.setMouseTransparent(true);
                monstersBox.getChildren().add(wrapper);
                i = i + 1;
            }
        }
        // Crear botones y lógica de selección
        createActionButtons();

        // Instalar manejadores de teclado para controlar botones
        installKeyHandlers();

        // Reproducir música de combate cuando la escena se añade
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                stopBattleMusic();
                playBattleMusic();
                Platform.runLater(root::requestFocus);
            } else {
                stopBattleMusic();
            }
        });

        // Inicializar visual de HP
        updateHeroHpDisplay();

        root.setCursor(Cursor.DEFAULT);
        Platform.runLater(() -> root.requestFocus());
    }

    public void setBattleMusicPath(String path) {
        if (path != null && !path.isBlank()) {
            this.battleMusicPath = path;
        }
    }

    private void setupHeroHpLabel() {
        heroHpLabel.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-padding: 6 10 6 10; -fx-background-radius: 6;");
        heroHpLabel.setFont(Font.font(13));
        heroHpLabel.setMouseTransparent(true);
        StackPane.setAlignment(heroHpLabel, Pos.TOP_RIGHT);
        heroHpLabel.setTranslateX(-12);
        heroHpLabel.setTranslateY(12);
        root.getChildren().add(heroHpLabel);
    }

    private void updateHeroHpDisplay() {
        Platform.runLater(() -> {
            try {
                int actual = game.getHero().getActualLife();
                int max = game.getHero().getLife();
                heroHpLabel.setText("HP: " + actual + " / " + max);

            } catch (Throwable ignored) {
                heroHpLabel.setText("HP: - / -");
            }
        });
    }

    private Monster foundMonster(String encounter) {
        Monster m = null;
        boolean found = false;
        while (!found) {
            for (int i = 0; i < game.getCharacters().size() && !found; i++) {
                NPC n = game.getCharacters().get(i);
                if (n instanceof Monster) {
                    if (((Monster) n).getEncounter().equalsIgnoreCase(encounter)) {
                        if (rnd.nextInt(0, 10) == 7) {
                            Monster t = (Monster) n;
                            m = new Monster(t.getActualWeapon(), t.getAttack(), t.getDefense(), t.getName(),
                                    t.getSpritePath(), t.getLife(), t.getActualLife(), t.getExp(), t.getMoney(), t.getEncounter());
                            found = true;
                        }
                    }
                }
            }
        }
        return m;
    }

    private Monster createBoss(Boss t) {
        Monster m = new Monster(t.getActualWeapon(), t.getAttack(), t.getDefense(), t.getName(),
                t.getSpritePath(), t.getLife(), t.getActualLife(), t.getExp(), t.getMoney(), t.getEncounter());;

        return m;
    }

    private ImageView createMonsterView(Monster m) {
        Image img = null;
        try {
            img = m.getFxImage();
        } catch (Throwable ignored) {
        }
        ImageView iv = new ImageView(img);
        if (boss == null) {
            iv.setPreserveRatio(true);
            iv.setFitWidth(95);
            iv.setFitHeight(95);
            iv.setSmooth(true);
        } else {
            iv.setPreserveRatio(true);
            iv.setFitWidth(200);
            iv.setFitHeight(200);
            iv.setSmooth(true);
        }

        return iv;
    }

    private void createHeroIcon(Hero heroForIcon) {
        Image heroImg = null;

        heroImg = new Image(getClass().getResourceAsStream("/Resources/sprites/hero/heroCombat.png"));

        ImageView heroIv = new ImageView(heroImg);
        heroIv.setPreserveRatio(true);
        heroIv.setFitWidth(120);
        heroIv.setFitHeight(120);
        heroIv.setSmooth(true);

        double leftMargin = 40;
        double paneHeight = leftPane.getPrefHeight();
        double ivHeight = 120;
        double layoutY = (paneHeight - ivHeight) / 2.0;
        heroIv.setLayoutX(leftMargin);
        heroIv.setLayoutY(layoutY);

        Text name = new Text((game != null && game.getHero() != null) ? game.getHero().getName() : (heroForIcon != null ? heroForIcon.getName() : "Heroe"));
        name.setFill(Color.WHITE);
        name.setFont(Font.font(14));
        name.setLayoutX(leftMargin);
        name.setLayoutY(layoutY + ivHeight + 18);

        leftPane.getChildren().addAll(heroIv, name);
    }

    private void createActionButtons() {
        Button bBattle = styledButton("Battle");
        Button bItem = styledButton("Item");
        Button bDefend = styledButton("Defend");
        Button bEscape = styledButton("Escape");

        buttons.add(bBattle);
        buttons.add(bItem);
        buttons.add(bDefend);
        buttons.add(bEscape);

        if (boss != null) {
            bEscape.setDisable(true);
        }

        bBattle.setOnAction(e -> {
            if (!gameOverActive) {
                game.getHero().setDefense(origDefense);
                onBattle();
            }
        });
        bItem.setOnAction(e -> {
            if (!gameOverActive && !inventoryOpen) {
                game.getHero().setDefense(origDefense);
                openInventoryDuringCombat();
            }
        });
        bDefend.setOnAction(e -> {
            if (!gameOverActive) {
                game.getHero().setDefense(origDefense);
                Hero h = game.getHero();
                h.setDefense(h.getDefense() + rnd.nextInt(0, 10));
                toastQueue.enqueue("The Defense has augmented in this turn. Now it has a value of: " + String.valueOf(h.getDefense()));
                monstersAttackAfterHeroAction();
            }
        });
        bEscape.setOnAction(e -> {
            if (!gameOverActive) {
                game.getHero().setDefense(origDefense);
                closeCombatAndReturnToMap();
            }
        });

        actionButtons.getChildren().addAll(bBattle, bItem, bDefend, bEscape);
        updateButtonSelection();
    }

    private Button styledButton(String text) {
        Button b = new Button(text);
        b.setMinWidth(140);
        b.setMinHeight(44);
        b.setStyle("-fx-background-color: linear-gradient(#3a7bd5,#00d2ff); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6;");
        b.setFont(Font.font(14));
        return b;
    }

    private void installKeyHandlers() {
        root.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            KeyCode code = ev.getCode();

            boolean shouldProcess = true;

            // Si el inventario está abierto, manejar teclas aquí (sin return)
            if (inventoryOpen) {
                if (code == KeyCode.ESCAPE) {
                    closeCombatInventory();
                }
                ev.consume(); // consumir siempre cuando el inventario está abierto
                shouldProcess = false;
            }

            // Si game over está activo, bloquear inputs (sin return)
            if (shouldProcess && gameOverActive) {
                ev.consume();
                shouldProcess = false;
            }

            if (shouldProcess) {
                boolean handled = false;

                if (code == KeyCode.LEFT) {
                    selectedButtonIndex = Math.max(0, selectedButtonIndex - 1);
                    updateButtonSelection();
                    handled = true;
                } else if (code == KeyCode.RIGHT) {
                    selectedButtonIndex = Math.min(buttons.size() - 1, selectedButtonIndex + 1);
                    updateButtonSelection();
                    handled = true;
                } else if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
                    if (!buttons.isEmpty()) {
                        Button sel = buttons.get(selectedButtonIndex);
                        if (sel != null) {
                            sel.fire();
                        }
                    }
                    handled = true;
                } else if (code == KeyCode.DIGIT1 || code == KeyCode.NUMPAD1) {
                    if (!buttons.isEmpty()) {
                        buttons.get(0).fire();
                    }
                    handled = true;
                } else if (code == KeyCode.DIGIT2 || code == KeyCode.NUMPAD2) {
                    if (buttons.size() > 1) {
                        buttons.get(1).fire();
                    }
                    handled = true;
                } else if (code == KeyCode.DIGIT3 || code == KeyCode.NUMPAD3) {
                    if (buttons.size() > 2) {
                        buttons.get(2).fire();
                    }
                    handled = true;
                } else if (code == KeyCode.DIGIT4 || code == KeyCode.NUMPAD4) {
                    if (buttons.size() > 3) {
                        buttons.get(3).fire();
                    }
                    handled = true;
                }

                if (handled) {
                    ev.consume();
                }
            }
        });
    }

    private void updateButtonSelection() {
        int i = 0;
        while (i < buttons.size()) {
            Button b = buttons.get(i);
            if (i == selectedButtonIndex) {
                b.setStyle("-fx-background-color: linear-gradient(#ffd54f,#ffb300); -fx-text-fill: black; -fx-font-weight: bold; -fx-background-radius: 6; -fx-border-color: #ffffff55; -fx-border-width: 2;");
            } else {
                b.setStyle("-fx-background-color: linear-gradient(#3a7bd5,#00d2ff); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6;");
            }
            i = i + 1;
        }
    }

    // acciones de combate  
    private void onBattle() {
        boolean hadTarget = false;
        Monster target = null;
        int tIndex = 0;
        int monstersCount = monsters.size();

        while (tIndex < monstersCount && !hadTarget) {
            Monster m = monsters.get(tIndex);
            if (m.getActualLife() > 0) {
                target = m;
                hadTarget = true;
            }
            tIndex = tIndex + 1;
        }

        boolean endCombatNow = false;
        if (!hadTarget) {
            endCombatNow = true;
        }

        boolean heroDidDamage = false;
        if (!endCombatNow && target != null) {
            heroDidDamage = game.heroCombat(target);
            final Monster finalTarget = target;
            final boolean finalHeroDidDamage = heroDidDamage;
            String heroMsg = finalHeroDidDamage
                    ? ("You attacked " + finalTarget.getName() + ". Monster's remaining life: " + finalTarget.getActualLife())
                    : "Your attack didn't damaged any monster.";
            toastQueue.enqueue(heroMsg);
            updateHeroHpDisplay();
        }

        boolean removedOne = false;
        if (!endCombatNow && target != null && game.checkGameOver(target.getActualLife())) {
            game.getHero().sumExp(target.getExp());
            game.getHero().setMoney(game.getHero().getMoney() + target.getMoney());
            game.getHero().growDefeatedMonsters();
            removeMonster(target);
            removedOne = true;
        }

        if (!endCombatNow) {
            if (monsters.isEmpty()) {
                endCombatNow = true;
            }
        }

        if (endCombatNow) {
            boolean leveled = game.levelUp();
            if (leveled) {
                String alert = "You have leveled up! Now You Are level" + String.valueOf(game.getHero().getLevel());
                toastQueue.enqueue(alert);
            }
            endCombatAndReturnToMap();
        }
        if (!endCombatNow) {
            monstersAttackAfterHeroAction();
        }
    }

    private void monstersAttackAfterHeroAction() {
        int idx = 0;
        int total = monsters.size();
        boolean heroDied = false;

        while (idx < total && !heroDied) {
            Monster m = monsters.get(idx);
            boolean alive = m.getActualLife() > 0;
            if (alive) {
                boolean monsterDidDamage = game.combat(m);
                int heroHp = game.getHero().getActualLife();
                final String msg = monsterDidDamage
                        ? (m.getName() + " Attacked, your life has decreased!")
                        : (m.getName() + " Attacked but didn't damaged!");
                toastQueue.enqueue(msg);
                updateHeroHpDisplay();

                if (game.checkGameOver(heroHp)) {
                    heroDied = true;
                }
            }
            idx = idx + 1;
        }

        if (heroDied) {
            showGameOver();
        }
    }

    private void showGameOver() {
        Platform.runLater(() -> {
            if (gameOverOverlay == null) {
                gameOverActive = true;

                stopBattleMusic();

                StackPane overlay = new StackPane();
                overlay.setPrefSize(800, 600);
                overlay.setStyle("-fx-background-color: rgba(0,0,0,0.85);");

                VBox vbox = new VBox(18);
                vbox.setAlignment(Pos.CENTER);

                Image goImg = null;
                try {
                    goImg = new Image(getClass().getResourceAsStream("/Resources/textures/Main/gameOver.png"));
                } catch (Throwable ignored) {
                }
                ImageView goView = new ImageView(goImg);
                goView.setPreserveRatio(true);
                goView.setFitWidth(600);
                goView.setFitHeight(400);

                Button startBtn = new Button("Start");
                startBtn.setMinWidth(160);
                startBtn.setMinHeight(44);
                startBtn.setStyle("-fx-background-color: linear-gradient(#ff5f6d,#ffc371); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6;");
                startBtn.setFont(Font.font(16));

                startBtn.setOnAction(ev -> {
                    stopGameOverMusic();
                    stopBattleMusic();
                    try {
                        FXGL.getGameScene().removeUINode(root);
                    } catch (Throwable ignored) {
                    }
                    try {
                        FXGL.getGameScene().removeUINode(overlay);
                    } catch (Throwable ignored) {
                    }
                    MainScreen.restoreMenuAndMusic();
                });

                vbox.getChildren().addAll(goView, startBtn);
                overlay.getChildren().add(vbox);

                gameOverOverlay = overlay;

                try {
                    FXGL.getGameScene().addUINode(overlay);
                } catch (Throwable ignored) {
                }

                playGameOverMusic();
            }
        });
    }

    private void removeMonster(Monster m) {
        int idx = monsters.indexOf(m);
        if (idx >= 0) {
            monsters.remove(idx);
            if (idx < monstersBox.getChildren().size()) {
                final int removeIdx = idx;
                Platform.runLater(() -> monstersBox.getChildren().remove(removeIdx));
            }
        }
    }

    private void endCombatAndReturnToMap() {
        stopBattleMusic();
        Platform.runLater(() -> {
            try {
                FXGL.getGameScene().removeUINode(root);
            } catch (Throwable ignored) {
            }
            if (onExit != null) {
                onExit.run();
            }
        });
    }

    private void closeCombatAndReturnToMap() {
        endCombatAndReturnToMap();
    }

    // Game Over
    private void playGameOverMusic() {
        try {
            stopGameOverMusic();
            URL res = getClass().getResource("/Resources/music/gameOver.mp3");
            if (res != null) {
                Media media = new Media(res.toExternalForm());
                gameOverPlayer = new MediaPlayer(media);
                gameOverPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                gameOverPlayer.setVolume(MainScreen.getVolumeSetting());
                gameOverPlayer.play();
            }
        } catch (Throwable ignored) {
        }
    }

    private void stopGameOverMusic() {
        try {
            if (gameOverPlayer != null) {
                gameOverPlayer.stop();
                gameOverPlayer.dispose();
                gameOverPlayer = null;
            }
        } catch (Throwable ignored) {
        }
    }

// Musica
    private void playBattleMusic() {
        try {
            stopBattleMusic();
            URL res = getClass().getResource(battleMusicPath);
            if (res != null) {
                Media media = new Media(res.toExternalForm());
                battleMusic = new MediaPlayer(media);
                battleMusic.setCycleCount(MediaPlayer.INDEFINITE);
                battleMusic.setVolume(MainScreen.getVolumeSetting());
                battleMusic.play();
            } else {

                try {
                    Media media = new Media(battleMusicPath);
                    battleMusic = new MediaPlayer(media);
                    battleMusic.setCycleCount(MediaPlayer.INDEFINITE);
                    battleMusic.setVolume(MainScreen.getVolumeSetting());
                    battleMusic.play();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void stopBattleMusic() {
        try {
            if (battleMusic != null) {
                battleMusic.stop();
                battleMusic.dispose();
                battleMusic = null;
            }
        } catch (Throwable ignored) {
        }
    }

    public void setOnExit(Runnable onExit) {
        this.onExit = onExit;
    }

    public void show() {
        Platform.runLater(() -> {
            try {
                FXGL.getGameScene().addUINode(root);
            } catch (Throwable ignored) {
            }
            root.requestFocus();
        });
    }

    private class ToastQueue {

        private final Queue<String> q = new ArrayDeque<>();
        private boolean showing = false;
        private final double DURATION_SECONDS = 1.2;
        private final double FADE_SECONDS = 0.22;

        public synchronized void enqueue(String msg) {
            q.offer(msg == null ? "" : msg);
            if (!showing) {
                showing = true;
                Platform.runLater(this::showNext);
            }
        }

        private void showNext() {
            String msg = q.poll();
            if (msg == null) {
                showing = false;
                return;
            }

            Label lbl = new Label(msg);
            lbl.setStyle("-fx-background-color: rgba(0,0,0,0.75); -fx-text-fill: white; -fx-padding: 10 16 10 16; -fx-background-radius: 8; -fx-font-size: 13;");
            lbl.setOpacity(0.0);

            StackPane container = new StackPane(lbl);
            container.setPickOnBounds(false);
            container.setMouseTransparent(true);
            container.setPrefSize(800, 600);

            StackPane.setAlignment(lbl, Pos.BOTTOM_CENTER);
            lbl.setTranslateY(-72);

            try {
                if (root != null) {
                    root.getChildren().add(container);
                }
            } catch (Throwable ignored) {
            }

            FadeTransition fadeIn = new FadeTransition(javafx.util.Duration.seconds(FADE_SECONDS), lbl);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);

            PauseTransition pause = new PauseTransition(javafx.util.Duration.seconds(DURATION_SECONDS));

            FadeTransition fadeOut = new FadeTransition(javafx.util.Duration.seconds(FADE_SECONDS), lbl);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);

            fadeOut.setOnFinished(ev -> {
                try {
                    root.getChildren().remove(container);
                } catch (Throwable ignored) {
                }
                Platform.runLater(this::showNext);
            });

            SequentialTransition seq = new SequentialTransition(fadeIn, pause, fadeOut);
            seq.play();
        }

    }
    // Para el Inventario

    private void openInventoryDuringCombat() {
        boolean canOpen = !(inventoryOpen || gameOverActive);

        if (canOpen) {
            inventoryOpen = true;

            // Crear overlay oscuro
            inventoryOverlay = new StackPane();
            inventoryOverlay.setPrefSize(800, 600);
            inventoryOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.85);");
            inventoryOverlay.setPickOnBounds(true);

            // Crear contenedor principal
            VBox mainContainer = new VBox(15);
            mainContainer.setAlignment(Pos.TOP_CENTER);
            mainContainer.setPrefSize(600, 450);
            mainContainer.setStyle("-fx-background-color: rgba(20, 20, 35, 0.95); "
                    + "-fx-background-radius: 10; "
                    + "-fx-border-color: #2a2a3a; "
                    + "-fx-border-width: 2; "
                    + "-fx-border-radius: 10;");
            mainContainer.setPadding(new Insets(20));

            // Título
            Label title = new Label("Healing Items");
            title.setFont(Font.font("System Bold", 24));
            title.setTextFill(Color.WHITE);
            title.setPadding(new Insets(0, 0, 15, 0));

            // ScrollPane para items
            ScrollPane scrollPane = new ScrollPane();
            scrollPane.setFitToWidth(true);
            scrollPane.setPrefViewportHeight(300);
            scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

            VBox itemsContainer = new VBox(8);
            itemsContainer.setPadding(new Insets(10));
            itemsContainer.setStyle("-fx-background-color: transparent;");

            Hero hero = game.getHero();
            LinkedList<Item> allItems = hero != null && hero.getItems() != null ? hero.getItems() : new LinkedList<>();
            List<Wares> healingItems = new ArrayList<>();

            for (Item item : allItems) {
                if (item instanceof Wares ware) {
                    healingItems.add(ware);
                }
            }

            if (healingItems.isEmpty()) {
                Label noItems = new Label("There are no items at the moment.");
                noItems.setStyle("-fx-font-size: 16px; -fx-text-fill: #888888; -fx-font-style: italic;");
                noItems.setPadding(new Insets(20));
                itemsContainer.getChildren().add(noItems);
            } else {
                for (Wares ware : healingItems) {
                    HBox itemRow = createHealingItemRow(ware);
                    itemsContainer.getChildren().add(itemRow);
                }
            }

            scrollPane.setContent(itemsContainer);

            // Botón de cerrar
            Button closeButton = new Button("Close");
            closeButton.setFont(Font.font("System Bold", 16));
            closeButton.setPrefSize(180, 40);
            closeButton.setStyle("-fx-background-color: linear-gradient(to bottom, #3a7bd5, #00d2ff); "
                    + "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; "
                    + "-fx-cursor: hand;");
            closeButton.setOnAction(e -> closeCombatInventory());

            HBox buttonBox = new HBox(15, closeButton);
            buttonBox.setAlignment(Pos.CENTER);

            mainContainer.getChildren().addAll(title, scrollPane, buttonBox);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            inventoryOverlay.getChildren().add(mainContainer);
            StackPane.setAlignment(mainContainer, Pos.CENTER);

            Platform.runLater(() -> {
                if (root != null && !root.getChildren().contains(inventoryOverlay)) {
                    root.getChildren().add(inventoryOverlay);
                }
                inventoryOverlay.toFront();

                inventoryOverlay.requestFocus();
                inventoryOverlay.setFocusTraversable(true);
            });
        }
    }

    private HBox createHealingItemRow(Wares ware) {
        HBox row = new HBox(15);
        row.setPadding(new Insets(10, 15, 10, 15));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: rgba(255, 255, 255, 0.08); "
                + "-fx-background-radius: 6; "
                + "-fx-border-color: rgba(255, 255, 255, 0.1); "
                + "-fx-border-radius: 6;");

        ImageView icon = new ImageView();
        icon.setFitWidth(32);
        icon.setFitHeight(32);
        icon.setStyle("-fx-effect: dropshadow(gaussian, #000000, 3, 0.5, 0, 0);");

        try {
            Image potionImg = new Image(getClass().getResourceAsStream("/Resources/sprites/items/potion.png"));
            icon.setImage(potionImg);
        } catch (Exception ignored) {
            Rectangle placeholder = new Rectangle(32, 32, Color.rgb(68, 255, 68, 0.5));
            placeholder.setArcWidth(8);
            placeholder.setArcHeight(8);
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            icon.setImage(placeholder.snapshot(params, null));
        }

        VBox infoBox = new VBox(3);

        Label nameLabel = new Label(ware.getName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: white;");

        Label healLabel = new Label("Heals: " + ware.getHealing() + " HP");
        healLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #44ff44;");

        Label descLabel = new Label("ID: " + ware.getId());
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #aaaaaa;");

        infoBox.getChildren().addAll(nameLabel, healLabel, descLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button useButton = new Button("Use");
        useButton.setFont(Font.font("System Bold", 14));
        useButton.setMinWidth(100);
        useButton.setMinHeight(35);
        useButton.setStyle("-fx-background-color: linear-gradient(to bottom, #2a7d2a, #1b5e20); "
                + "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; "
                + "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, #000000, 3, 0.5, 0, 1);");

        useButton.setOnAction(e -> {
            handleUseWareInCombat(ware);
            closeCombatInventory();
        });

        row.getChildren().addAll(icon, infoBox, spacer, useButton);

        row.setOnMouseEntered(ev -> row.setStyle("-fx-background-color: rgba(255, 255, 255, 0.15); "
                + "-fx-background-radius: 6; -fx-border-color: rgba(255, 255, 255, 0.3); -fx-border-radius: 6;"));

        row.setOnMouseExited(ev -> row.setStyle("-fx-background-color: rgba(255, 255, 255, 0.08); "
                + "-fx-background-radius: 6; -fx-border-color: rgba(255, 255, 255, 0.1); -fx-border-radius: 6;"));

        return row;
    }

    private void handleUseWareInCombat(Wares ware) {
        boolean healed = false;

        try {
            if (game != null) {
                healed = game.heal(ware);
            }
        } catch (Exception ignored) {
        }

        if (healed) {
            if (game != null && game.getHero() != null) {
                game.getHero().getItems().remove(ware);
            }
            if (toastQueue != null) {
                toastQueue.enqueue("You used" + ware.getName() + "! +" + ware.getHealing() + " HP");
            }
            updateHeroHpDisplay();
            monstersAttackAfterHeroAction();
        } else {
            if (toastQueue != null) {
                toastQueue.enqueue("Your HP is already at max!");
            }
        }
    }

    private void closeCombatInventory() {
        Platform.runLater(() -> {
            if (inventoryOverlay != null && root != null) {
                root.getChildren().remove(inventoryOverlay);
                inventoryOverlay = null;
            }

            inventoryOpen = false;

            if (root != null) {
                root.setDisable(false);
            }

            if (root != null) {
                root.requestFocus();
            }

            updateButtonSelection();
        });
    }

}
