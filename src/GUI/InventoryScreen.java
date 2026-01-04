package GUI;

import Logic.Game;
import Characters.Hero;
import Items.*;
import Misc.Classes;
import Misc.Task;
import Runner.MainScreen;
import Tree.BinaryTreeNode;
import Tree.InBreadthIterator;
import com.almasb.fxgl.dsl.FXGL;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.Duration;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;

public class InventoryScreen {

    private final Game game;
    private final Object mapScreen;
    private StackPane root;
    private final TabPane tabPane;
    private Runnable onClose;
    private boolean isVisible = false;

    private StackPane toastContainer;
    private boolean showingToast = false;

    private EventHandler<KeyEvent> sceneKeyFilter = null;
    private EventHandler<MouseEvent> sceneMouseFilter = null;
    private Parent sceneRootRef = null;

    private HBox selectedRow = null;

    private static final int DURACION_TOAST_MS = 1200;
    private static final int DURACION_CARGA_MS = 600;

    public InventoryScreen(Game game, Object mapScreen) {
        this.game = game;
        this.mapScreen = mapScreen;
        root = new StackPane();
        root.setPrefSize(800, 600);

        Rectangle bg = new Rectangle(800, 600);
        bg.setFill(Color.rgb(0, 0, 0, 0.85));
        root.getChildren().add(bg);

        VBox mainContainer = new VBox();
        mainContainer.setAlignment(Pos.TOP_CENTER);
        mainContainer.setPrefSize(750, 550);
        mainContainer.setStyle("-fx-background-color: rgba(10, 10, 20, 0.95); "
                + "-fx-background-radius: 10; "
                + "-fx-border-color: #2a2a3a; "
                + "-fx-border-width: 2; "
                + "-fx-border-radius: 10;");

        Label title = new Label("INVENTORY");
        title.setFont(Font.font("System Bold", 32));
        title.setTextFill(Color.WHITE);
        title.setPadding(new Insets(15, 0, 15, 0));

        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setPrefSize(730, 450);
        tabPane.setStyle("-fx-background-color: transparent; -fx-border-color: #333344; -fx-border-width: 1;");

        Tab statusTab = createStatusTab();
        Tab weaponsArmorTab = createWeaponsArmorTab();
        Tab waresTab = createWaresTab();
        Tab keyItemsTab = createKeyItemsTab();
        Tab settingsTab = createSettingsTab();
        Tab tasksTab = createTasksTab();

        tabPane.getTabs().addAll(statusTab, weaponsArmorTab, waresTab, keyItemsTab, tasksTab, settingsTab);

        Button closeButton = new Button("Close");
        closeButton.setFont(Font.font("System Bold", 14));
        closeButton.setPrefSize(180, 40);
        closeButton.setStyle("-fx-background-color: linear-gradient(to bottom, #3a7bd5, #00d2ff); "
                + "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand;");
        closeButton.setOnAction(e -> close());

        root.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case I:
                case ADD:
                case PLUS:
                case ESCAPE:
                case SUBTRACT:
                case MINUS:
                    close();
                    break;
                default:
                    break;
            }
        });

        mainContainer.getChildren().addAll(title, tabPane, closeButton);
        VBox.setMargin(title, new Insets(0, 0, 10, 0));
        VBox.setMargin(tabPane, new Insets(0, 0, 20, 0));
        VBox.setMargin(closeButton, new Insets(0, 0, 20, 0));

        root.getChildren().add(mainContainer);
        StackPane.setAlignment(mainContainer, Pos.CENTER);

        toastContainer = new StackPane();
        toastContainer.setMouseTransparent(true);
        toastContainer.setPickOnBounds(false);
        root.getChildren().add(toastContainer);

        root.setFocusTraversable(true);
    }

    // -------------------- STATUS TAB --------------------
    private Tab createStatusTab() {
        Tab tab = new Tab("Status");
        tab.setStyle("-fx-font-weight: bold;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(400);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setHgap(25);
        grid.setVgap(15);
        grid.setAlignment(Pos.TOP_CENTER);

        Hero hero = game.getHero();
        int row = 0;

        ImageView heroIcon = new ImageView("/Resources/sprites/hero/heroFace.png");
        heroIcon.setFitWidth(120);
        heroIcon.setFitHeight(120);
        heroIcon.setStyle("-fx-effect: dropshadow(gaussian, #000000, 10, 0.5, 0, 0);");
        GridPane.setConstraints(heroIcon, 0, row, 1, 3);
        grid.getChildren().add(heroIcon);

        Label nameTitle = createTitleLabel("Name:");
        Label nameValue = createValueLabel(hero.getName());
        GridPane.setConstraints(nameTitle, 1, row);
        GridPane.setConstraints(nameValue, 2, row);
        grid.getChildren().addAll(nameTitle, nameValue);
        row++;

        Label levelTitle = createTitleLabel("Level:");
        Label levelValue = createValueLabel(String.valueOf(hero.getLevel()));
        GridPane.setConstraints(levelTitle, 1, row);
        GridPane.setConstraints(levelValue, 2, row);
        grid.getChildren().addAll(levelTitle, levelValue);
        row++;

        Label hpTitle = createTitleLabel("HP:");
        GridPane.setConstraints(hpTitle, 1, row);
        grid.getChildren().add(hpTitle);
        row++;

        HBox hpContainer = new HBox();
        hpContainer.setAlignment(Pos.CENTER);
        hpContainer.setPrefWidth(250);
        hpContainer.setPrefHeight(30);

        StackPane hpBarContainer = new StackPane();
        hpBarContainer.setPrefWidth(250);
        hpBarContainer.setPrefHeight(30);

        Rectangle hpBg = new Rectangle(250, 25);
        hpBg.setFill(Color.rgb(40, 40, 40));
        hpBg.setArcWidth(10);
        hpBg.setArcHeight(10);

        double hpPercent = (double) hero.getActualLife() / hero.getLife();
        Rectangle hpBar = new Rectangle(250 * hpPercent, 25);
        if (hpPercent < 0.3) {
            hpBar.setFill(Color.rgb(220, 60, 60));
        } else if (hpPercent < 0.6) {
            hpBar.setFill(Color.rgb(220, 180, 60));
        } else {
            hpBar.setFill(Color.rgb(60, 220, 60));
        }
        hpBar.setArcWidth(10);
        hpBar.setArcHeight(10);

        Label hpText = new Label(hero.getActualLife() + " / " + hero.getLife());
        hpText.setFont(Font.font("System Bold", 14));
        hpText.setTextFill(Color.WHITE);
        hpText.setStyle("-fx-effect: dropshadow(gaussian, #000000, 2, 0, 0, 1);");

        hpBarContainer.getChildren().addAll(hpBg, hpBar, hpText);
        hpContainer.getChildren().add(hpBarContainer);
        GridPane.setConstraints(hpContainer, 1, row, 2, 1);
        grid.getChildren().add(hpContainer);
        row++;

        Label attackTitle = createTitleLabel("Attack:");
        int totalAttack = hero.getAttack() + (hero.getActualWeapon() != null ? hero.getActualWeapon().getAttack() : 0);
        Label attackValue = createValueLabel(hero.getAttack() + " + "
                + (hero.getActualWeapon() != null ? hero.getActualWeapon().getAttack() : 0)
                + " = " + totalAttack);
        GridPane.setConstraints(attackTitle, 1, row);
        GridPane.setConstraints(attackValue, 2, row);
        grid.getChildren().addAll(attackTitle, attackValue);
        row++;

        Label defenseTitle = createTitleLabel("Defense:");
        int totalDefense = hero.getDefense() + (hero.getArmor() != null ? hero.getArmor().getDefense() : 0);
        Label defenseValue = createValueLabel(hero.getDefense() + " + "
                + (hero.getArmor() != null ? hero.getArmor().getDefense() : 0)
                + " = " + totalDefense);
        GridPane.setConstraints(defenseTitle, 1, row);
        GridPane.setConstraints(defenseValue, 2, row);
        grid.getChildren().addAll(defenseTitle, defenseValue);
        row++;

        int rightCol = 3;
        int rightRow = 0;

        Label magicTitle = createTitleLabel("Magic:");
        Label magicValue = createValueLabel(String.valueOf(hero.getMagic()));
        GridPane.setConstraints(magicTitle, rightCol, rightRow);
        GridPane.setConstraints(magicValue, rightCol + 1, rightRow);
        grid.getChildren().addAll(magicTitle, magicValue);
        rightRow++;

        Label expTitle = createTitleLabel("Experience:");
        Label expValue = createValueLabel(hero.getExpActual() + " / " + hero.getExpMax());
        GridPane.setConstraints(expTitle, rightCol, rightRow);
        GridPane.setConstraints(expValue, rightCol + 1, rightRow);
        grid.getChildren().addAll(expTitle, expValue);
        rightRow++;

        Label moneyTitle = createTitleLabel("Money:");
        Label moneyValue = createValueLabel(String.valueOf(hero.getMoney()));
        GridPane.setConstraints(moneyTitle, rightCol, rightRow);
        GridPane.setConstraints(moneyValue, rightCol + 1, rightRow);
        grid.getChildren().addAll(moneyTitle, moneyValue);
        rightRow++;

        Label weaponTitle = createTitleLabel("Weapon:");
        Weapon actualWeapon = hero.getActualWeapon();
        String weaponName = actualWeapon != null ? actualWeapon.getName() : "None";
        Label weaponValue = createValueLabel(weaponName);
        weaponValue.setWrapText(true);
        weaponValue.setMaxWidth(250);
        GridPane.setConstraints(weaponTitle, rightCol, rightRow);
        GridPane.setConstraints(weaponValue, rightCol + 1, rightRow);
        grid.getChildren().addAll(weaponTitle, weaponValue);
        rightRow++;

        Label armorTitle = createTitleLabel("Armor:");
        Armor armor = hero.getArmor();
        String armorName = armor != null ? armor.getName() : "None";
        String armorDefense = armor != null ? " (+" + armor.getDefense() + " def)" : "";
        Label armorValue = createValueLabel(armorName + armorDefense);
        GridPane.setConstraints(armorTitle, rightCol, rightRow);
        GridPane.setConstraints(armorValue, rightCol + 1, rightRow);
        grid.getChildren().addAll(armorTitle, armorValue);
        rightRow++;

        Label levelProgressTitle = new Label("LEVEL PROGRESSION");
        levelProgressTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #aaddff;");
        GridPane.setConstraints(levelProgressTitle, 0, 6, 2, 1);
        grid.getChildren().add(levelProgressTitle);

        TextArea levelProgressArea = new TextArea();
        levelProgressArea.setEditable(false);
        levelProgressArea.setPrefRowCount(6);
        levelProgressArea.setPrefColumnCount(50);
        levelProgressArea.setWrapText(true);
        levelProgressArea.setStyle("-fx-control-inner-background: #0a0a14; -fx-text-fill: #aaddff; "
                + "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px; -fx-border-color: #333344;");

        StringBuilder sb = new StringBuilder();
        sb.append("Current Level: ").append(hero.getLevel()).append("\n");
        sb.append("Experience: ").append(hero.getExpActual()).append(" / ").append(hero.getExpMax()).append("\n\n");
        sb.append("Unlocked Classes:\n");

        InBreadthIterator<Classes> it = hero.getUnlockedClasses().inBreadthIterator();
        while (it.hasNext()) {
            BinaryTreeNode<Classes> node = it.nextNode();
            Classes cl = node.getInfo();
            sb.append("- ").append(cl.getId()).append(" : ").append(cl.getDescription()).append("\n");
        }

        levelProgressArea.setText(sb.toString());

        GridPane.setConstraints(levelProgressArea, 0, 7, 4, 2);
        grid.getChildren().add(levelProgressArea);

        scrollPane.setContent(grid);
        tab.setContent(scrollPane);
        return tab;
    }

    // -------------------- WEAPONS / ARMOR TAB --------------------
    private Tab createWeaponsArmorTab() {
        Tab tab = new Tab("Weapons/Armor");
        tab.setStyle("-fx-font-weight: bold;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(400);
        scrollPane.setStyle("-fx-background: transparent;");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: transparent;");

        Hero hero = game.getHero();
        Label weaponsTitle = new Label("AVAILABLE WEAPONS");
        weaponsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffaa44;");
        weaponsTitle.setPadding(new Insets(0, 0, 10, 0));

        VBox weaponsList = new VBox(5);
        if (game.getHeroWeapons().isEmpty()) {
            Label noWeapons = new Label("No weapons available.");
            noWeapons.setStyle("-fx-text-fill: #888888; -fx-font-style: italic;");
            weaponsList.getChildren().add(noWeapons);
        } else {
            for (Weapon w : game.getHeroWeapons()) {
                HBox weaponRow = createItemRow(w.getName(),
                        "Attack: " + w.getAttack()
                        + (w == hero.getActualWeapon() ? " (EQUIPPED)" : ""),
                        w == hero.getActualWeapon());
                Button equipButton = new Button("Equip");
                equipButton.setOnAction(ev -> handleEquipWeapon(w));
                weaponRow.getChildren().add(equipButton);
                weaponsList.getChildren().add(weaponRow);
            }
        }

        Label armorEquippedTitle = new Label("ARMOR (Equipped)");
        armorEquippedTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #44aaff;");
        armorEquippedTitle.setPadding(new Insets(20, 0, 10, 0));

        VBox armorEquippedBox = new VBox(5);
        Armor equippedArmor = hero.getArmor();
        if (equippedArmor != null) {
            HBox equippedRow = createItemRow(equippedArmor.getName(),
                    "Defense: " + equippedArmor.getDefense() + " | Info: " + equippedArmor.getEffect(),
                    true);
            equippedRow.setStyle("-fx-background-color: rgba(68, 170, 255, 0.1); -fx-background-radius: 5;");
            armorEquippedBox.getChildren().add(equippedRow);
        } else {
            Label noEquipped = new Label("No armor equipped.");
            noEquipped.setStyle("-fx-text-fill: #888888; -fx-font-style: italic;");
            armorEquippedBox.getChildren().add(noEquipped);
        }

        Label armorInvTitle = new Label("ARMOR (Inventory)");
        armorInvTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #44aaff;");
        armorInvTitle.setPadding(new Insets(12, 0, 6, 0));

        VBox armorInvList = new VBox(5);
        for (Armor a : game.getHeroArmors()) {
            if (equippedArmor != null) {

            }
            HBox armorRow = createItemRow(a.getName(),
                    "Defense: " + a.getDefense() + " | Info: " + a.getEffect(),
                    false);
            Button equipButton = new Button("Equip");
            equipButton.setOnAction(ev -> handleEquipArmor(a));
            armorRow.getChildren().add(equipButton);
            armorInvList.getChildren().add(armorRow);
        }

        if (armorInvList.getChildren().isEmpty()) {
            Label noArmorInv = new Label("No armor in inventory.");
            noArmorInv.setStyle("-fx-text-fill: #888888; -fx-font-style: italic;");
            armorInvList.getChildren().add(noArmorInv);
        }
        content.getChildren().addAll(weaponsTitle, weaponsList, armorEquippedTitle, armorEquippedBox, armorInvTitle, armorInvList);

        scrollPane.setContent(content);
        tab.setContent(scrollPane);
        return tab;
    }

    // -------------------- WARES TAB --------------------
    private Tab createWaresTab() {
        Tab tab = new Tab("Consumables");
        tab.setStyle("-fx-font-weight: bold;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(400);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: transparent;");

        Hero hero = game.getHero();

        Label waresTitle = new Label("HEALING ITEMS");
        waresTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #44ff44;");
        waresTitle.setPadding(new Insets(0, 0, 10, 0));

        VBox waresList = new VBox(5);
        waresList.setStyle("-fx-background-color: transparent;");

        int healingItems = 0;
        LinkedList<Item> allItems = hero.getItems();
        for (Item item : allItems) {
            if (item instanceof Wares ware) {
                healingItems++;
                HBox wareRow = createItemRow(ware.getName(),
                        "Healing: " + ware.getHealing() + " | ID: " + ware.getId(),
                        false);
                wareRow.setStyle("-fx-background-color: rgba(68, 255, 68, 0.1); -fx-background-radius: 5;");

                Button useButton = new Button("Use");
                useButton.setOnAction(ev -> handleUseWare(ware));
                wareRow.getChildren().add(useButton);

                waresList.getChildren().add(wareRow);
            }
        }

        if (healingItems == 0) {
            Label noWares = new Label("No healing items available.");
            noWares.setStyle("-fx-text-fill: #888888; -fx-font-style: italic;");
            waresList.getChildren().add(noWares);
        }

        content.getChildren().addAll(waresTitle, waresList);
        scrollPane.setContent(content);
        tab.setContent(scrollPane);
        return tab;
    }

    //---------------------Task Tab----------------------------
    private HBox createTaskRow(String title, String details) {
        HBox row = new HBox(15);
        row.setPadding(new Insets(8, 15, 8, 15));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: rgba(255, 255, 255, 0.05); -fx-background-radius: 5; -fx-border-color: rgba(255, 255, 255, 0.1); -fx-border-radius: 5;");

        Label nameLabel = new Label(title);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: white;");
        nameLabel.setMinWidth(220);

        Label detailsLabel = new Label(details);
        detailsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #aaaaaa;");
        detailsLabel.setWrapText(true);
        detailsLabel.setMaxWidth(380);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(nameLabel, detailsLabel, spacer);
        return row;
    }

    private Tab createTasksTab() {
        Tab tab = new Tab("Tasks");
        tab.setStyle("-fx-font-weight: bold;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(400);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: transparent;");

        Hero hero = game.getHero();

        Label pendingTitle = new Label("PENDING TASKS");
        pendingTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffaa44;");
        pendingTitle.setPadding(new Insets(0, 0, 6, 0));

        VBox pendingList = new VBox(6);
        if (hero.getTasks().isEmpty()) {
            Label none = new Label("No pending tasks.");
            none.setStyle("-fx-text-fill: #888888; -fx-font-style: italic;");
            pendingList.getChildren().add(none);
        } else {
            Queue<Task> aux = new ArrayDeque<>();
            while (!hero.getTasks().isEmpty()) {
                Task t = hero.getTasks().poll();
                String name = t.getName();
                String info = t.getInfo();
                HBox row = createTaskRow(name, info);
                pendingList.getChildren().add(row);
                aux.offer(t);
            }
            while (!aux.isEmpty()) {
                hero.getTasks().offer(aux.poll());
            }
        }

        Label completedTitle = new Label("COMPLETED TASKS");
        completedTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #44aaff;");
        completedTitle.setPadding(new Insets(12, 0, 6, 0));

        VBox completedList = new VBox(6);
        if (hero == null || hero.getCompletedTasks() == null || hero.getCompletedTasks().isEmpty()) {
            Label none = new Label("No completed tasks.");
            none.setStyle("-fx-text-fill: #888888; -fx-font-style: italic;");
            completedList.getChildren().add(none);
        } else {
            Deque<Task> aux = new ArrayDeque<>();
            while (!hero.getCompletedTasks().isEmpty()) {
                Task t = hero.getCompletedTasks().pop();
                String name = t.getName();
                String info = t.getInfo();
                HBox row = createTaskRow(name, info);
                row.setStyle("-fx-background-color: rgba(68, 170, 255, 0.06); -fx-background-radius: 5; -fx-border-color: rgba(255,255,255,0.04);");
                completedList.getChildren().add(row);
                aux.push(t);
            }
            while (!aux.isEmpty()) {
                hero.getCompletedTasks().push(aux.pop());
            }
        }

        content.getChildren().addAll(pendingTitle, pendingList, completedTitle, completedList);
        scrollPane.setContent(content);
        tab.setContent(scrollPane);
        return tab;
    }

    // -------------------- KEY ITEMS TAB --------------------
    private Tab createKeyItemsTab() {
        Tab tab = new Tab("Key Items");
        tab.setStyle("-fx-font-weight: bold;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(400);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: transparent;");

        Label keyTitle = new Label("KEY ITEMS");
        keyTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ff44ff;");
        keyTitle.setPadding(new Insets(0, 0, 10, 0));

        VBox keyList = new VBox(5);
        keyList.setStyle("-fx-background-color: transparent;");

        Hero hero = game.getHero();
        for (KeyItem k : game.getHeroKeyItems()) {
            HBox row = createItemRow(k.getName(), "ID: " + k.getId() + " | " + k.getInfo(), false);
            keyList.getChildren().add(row);
        }

        if (keyList.getChildren().isEmpty()) {
            Label noKeys = new Label("No key items at the moment.");
            noKeys.setStyle("-fx-text-fill: #888888; -fx-font-style: italic;");
            keyList.getChildren().add(noKeys);
        }

        content.getChildren().addAll(keyTitle, keyList);
        scrollPane.setContent(content);
        tab.setContent(scrollPane);
        return tab;
    }

    // -------------------- SETTINGS TAB --------------------
    private Tab createSettingsTab() {
        Tab tab = new Tab("Settings");
        tab.setStyle("-fx-font-weight: bold;");

        VBox content = new VBox(25);
        content.setPadding(new Insets(40));
        content.setAlignment(Pos.TOP_CENTER);
        content.setStyle("-fx-background-color: transparent;");

        VBox saveSection = new VBox(10);
        saveSection.setAlignment(Pos.CENTER);
        Label saveLabel = new Label("Save Game");
        saveLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffff44;");

        Button saveButton = new Button("💾 SAVE");
        saveButton.setFont(Font.font("System Bold", 16));
        saveButton.setPrefWidth(250);
        saveButton.setPrefHeight(50);
        saveButton.setStyle("-fx-background-color: linear-gradient(to bottom, #ffd54f, #ffb300); -fx-text-fill: black; -fx-font-weight: bold; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, #333, 5, 0, 0, 2); -fx-cursor: hand;");
        saveButton.setOnAction(e -> {
            try {
                Point2D pos = tryGetHeroTopLeftFromProvider();

                if (pos != null) {
                    Hero h = game.getHero();
                    String clsName = mapScreen != null ? mapScreen.getClass().getSimpleName() : "";

                    switch (clsName) {
                        case "FieldVillage" -> {
                            h.setLastPosX(670.5704519999998);
                            h.setLastPosY(864.0);
                            h.setLastLocation(Hero.Location.FIELD_VILLAGE);
                        }
                        case "JVInn" -> {
                            h.setLastPosX(670.5704519999998);
                            h.setLastPosY(864.0);
                            h.setLastLocation(Hero.Location.FIELD_VILLAGE);
                        }
                        case "JVMayor" -> {
                            h.setLastPosX(670.5704519999998);
                            h.setLastPosY(864.0);
                            h.setLastLocation(Hero.Location.FIELD_VILLAGE);
                        }
                        case "JVStore" -> {
                            h.setLastPosX(670.5704519999998);
                            h.setLastPosY(864.0);
                            h.setLastLocation(Hero.Location.FIELD_VILLAGE);
                        }
                        case "GameMapScreen" -> {
                            h.setLastPosX(682.4067800000003);
                            h.setLastPosY(400.5119300000001);
                            h.setLastLocation(Hero.Location.MAP);
                        }
                        case "ForestHouse" -> {
                            h.setLastPosX(384.0);
                            h.setLastPosY(576.0);
                            h.setLastLocation(Hero.Location.FOREST_HOUSE);
                        }
                        case "Swamp" -> {
                            h.setLastPosX(384.0);
                            h.setLastPosY(607.059);
                            h.setLastLocation(Hero.Location.SWAMP);

                        }
                        case "SwampDungeon" -> {
                            h.setLastPosX(500.1253860000012);
                            h.setLastPosY(1200.0);
                            h.setLastLocation(Hero.Location.SWAMP_DUNGEON);
                        }
                        default ->
                            h.setLastLocation(Hero.Location.MAP);
                    }
                }

                boolean saved = game.createSaveGame();

                if (saved) {
                    showToast("Game saved successfully!", DURACION_TOAST_MS);
                } else {
                    showToast("Error saving game.", DURACION_TOAST_MS);
                }

                PauseTransition pt = new PauseTransition(Duration.millis(350));
                pt.setOnFinished(ev -> {
                    try {
                        close();
                    } catch (Throwable ex) {
                        try {
                            FXGL.getGameScene().removeUINode(root);
                        } catch (Throwable ignored) {
                        }
                        if (onClose != null) {
                            try {
                                onClose.run();
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                });
                pt.play();

            } catch (Throwable ex) {
                try {
                    showToast("Error saving game.", DURACION_TOAST_MS);
                } catch (Throwable ignored) {
                }
            }
        });

        saveSection.getChildren().addAll(saveLabel, saveButton);

        VBox volumeSection = new VBox(10);
        volumeSection.setAlignment(Pos.CENTER);
        Label volumeLabel = new Label("VOLUME");
        volumeLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #44aaff;");

        HBox volumeControls = new HBox(15);
        volumeControls.setAlignment(Pos.CENTER);

        Slider volumeSlider = new Slider(0, 100, Math.round(MainScreen.getVolumeSetting() * 100));
        volumeSlider.setPrefWidth(200);
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            // MainScreen.setVolumeSetting(newVal.doubleValue() / 100.0);
        });

        Label volumeValue = new Label(Math.round(volumeSlider.getValue()) + "%");
        volumeValue.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            volumeValue.setText(Math.round(newVal.doubleValue()) + "%");
        });

        volumeControls.getChildren().addAll(volumeSlider, volumeValue);
        volumeSection.getChildren().addAll(volumeLabel, volumeControls);

        VBox exitSection = new VBox(10);
        exitSection.setAlignment(Pos.CENTER);
        Label exitLabel = new Label("Exit to Main Menu");
        exitLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ff6b6b;");

        Button exitButton = new Button("🚪 EXIT TO MAIN MENU");
        exitButton.setFont(Font.font("System Bold", 16));
        exitButton.setPrefWidth(250);
        exitButton.setPrefHeight(50);
        exitButton.setStyle("-fx-background-color: linear-gradient(to bottom, #ff6b6b, #c44569); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, #333, 5, 0, 0, 2); -fx-cursor: hand;");
        exitButton.setOnAction(e -> {
            try {
                Parent currentRoot = FXGL.getGameScene().getRoot();
                if (sceneRootRef != null) {
                    try {
                        sceneRootRef.removeEventFilter(KeyEvent.ANY, sceneKeyFilter);
                    } catch (Throwable ignored) {
                    }
                    try {
                        sceneRootRef.removeEventFilter(MouseEvent.ANY, sceneMouseFilter);
                    } catch (Throwable ignored) {
                    }
                }
                if (currentRoot != null && currentRoot != sceneRootRef) {
                    try {
                        currentRoot.removeEventFilter(KeyEvent.ANY, sceneKeyFilter);
                    } catch (Throwable ignored) {
                    }
                    try {
                        currentRoot.removeEventFilter(MouseEvent.ANY, sceneMouseFilter);
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }

            try {
                FXGL.getGameScene().removeUINode(root);
            } catch (Throwable ignored) {
            }

            try {
                if (mapScreen != null) {
                    try {
                        Method m = mapScreen.getClass().getMethod("stopMapMusic");
                        m.invoke(mapScreen);
                    } catch (NoSuchMethodException nsme) {
                        try {
                            Method mv = mapScreen.getClass().getMethod("stopVillageMusic");
                            mv.invoke(mapScreen);
                        } catch (Throwable ignored2) {
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }

            try {
                AudioManager.stopAll();
            } catch (Throwable ignored) {
            }
            try {
                FXGL.getGameScene().clearUINodes();
            } catch (Throwable ignored) {
            }
            close();
            MainScreen.restoreMenuAndMusic();
        });

        exitSection.getChildren().addAll(exitLabel, exitButton);

        content.getChildren().addAll(saveSection, volumeSection, exitSection);
        tab.setContent(content);
        return tab;
    }

    // -------------------- ROW CREATION & SELECTION --------------------
    private HBox createItemRow(String name, String details, boolean equipped) {
        HBox row = new HBox(15);
        row.setPadding(new Insets(8, 15, 8, 15));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: rgba(255, 255, 255, 0.05); -fx-background-radius: 5; -fx-border-color: rgba(255, 255, 255, 0.1); -fx-border-radius: 5;");

        row.getProperties().put("baseStyle", row.getStyle());

        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; " + (equipped ? "-fx-text-fill: #ffff44;" : "-fx-text-fill: white;"));
        nameLabel.setMinWidth(150);

        Label detailsLabel = new Label(details);
        detailsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #aaaaaa;");
        detailsLabel.setWrapText(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (equipped) {
            Label equippedLabel = new Label("EQUIPPED");
            equippedLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #ffff44;");
            row.getChildren().addAll(nameLabel, detailsLabel, spacer, equippedLabel);
        } else {
            row.getChildren().addAll(nameLabel, detailsLabel, spacer);
        }

        row.setOnMouseClicked(ev -> selectRow(row));
        return row;
    }

    private void selectRow(HBox row) {
        if (selectedRow != null) {
            Object basePrev = selectedRow.getProperties().get("baseStyle");
            selectedRow.setStyle(basePrev instanceof String ? (String) basePrev : "");
        }
        selectedRow = row;
        String base = (String) row.getProperties().getOrDefault("baseStyle", "");
        row.setStyle(base + " -fx-border-color: rgba(255,255,255,0.7); -fx-border-width: 2; -fx-border-radius: 5;");
    }

    // -------------------- TOAST --------------------
    private void showToast(String message, int durationMs) {
        if (showingToast) {
            toastContainer.getChildren().clear();
        }

        showingToast = true;

        Label toastLabel = new Label(message);
        toastLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.85); -fx-text-fill: white; -fx-padding: 12 20 12 20; -fx-background-radius: 6; -fx-font-size: 13px; -fx-font-weight: bold;");

        StackPane toastPane = new StackPane(toastLabel);
        toastPane.setMouseTransparent(true);
        StackPane.setAlignment(toastLabel, Pos.BOTTOM_CENTER);
        toastLabel.setTranslateY(-50);

        toastContainer.getChildren().add(toastPane);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), toastPane);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        PauseTransition pause = new PauseTransition(Duration.millis(durationMs));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), toastPane);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            toastContainer.getChildren().remove(toastPane);
            showingToast = false;
        });

        SequentialTransition sequence = new SequentialTransition(fadeIn, pause, fadeOut);
        sequence.play();
    }

    // -------------------- SHOW / CLOSE / HELPERS --------------------
    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    // Helper: comprueba si 'node' está dentro del árbol de 'ancestor'
    private boolean isDescendant(Node node, Parent ancestor) {
        boolean result = false;
        Node cur = node;
        while (cur != null) {
            if (cur == ancestor) {
                result = true;
                break;
            }
            cur = cur.getParent();
        }
        return result;
    }

    public void show() {
        Platform.runLater(() -> {
            MainScreen.setModalOpen(true);

            try {
                sceneRootRef = FXGL.getGameScene().getRoot();

                sceneKeyFilter = (EventHandler<KeyEvent>) ev -> {
                    Node targetNode = (ev.getTarget() instanceof Node) ? (Node) ev.getTarget() : null;
                    boolean insideInventory = false;
                    if (targetNode != null) {
                        insideInventory = isDescendant(targetNode, root);
                    }
                    if (!insideInventory) {
                        ev.consume();
                    }
                };

                sceneMouseFilter = (EventHandler<MouseEvent>) ev -> {
                    Node targetNode = (ev.getTarget() instanceof Node) ? (Node) ev.getTarget() : null;
                    boolean insideInventory = false;
                    if (targetNode != null) {
                        insideInventory = isDescendant(targetNode, root);
                    }
                    if (!insideInventory) {
                        ev.consume();
                    }
                };

                if (sceneRootRef != null) {
                    try {
                        sceneRootRef.addEventFilter(KeyEvent.ANY, sceneKeyFilter);
                    } catch (Throwable ignored) {
                    }
                    try {
                        sceneRootRef.addEventFilter(MouseEvent.ANY, sceneMouseFilter);
                    } catch (Throwable ignored) {
                    }
                }

                try {
                    FXGL.getGameScene().addUINode(root);
                } catch (Throwable ignored) {
                }

                root.setFocusTraversable(true);
                root.requestFocus();
                isVisible = true;

                root.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                    switch (ev.getCode()) {
                        case ESCAPE:
                            ev.consume();
                            close();
                            break;
                        default:
                            break;
                    }
                });

            } catch (Throwable ignored) {
                MainScreen.setModalOpen(false);
            }
        });
    }

    public void close() {
        Platform.runLater(() -> {
            try {
                // Intentar quitar el UI del inventario
                try {
                    FXGL.getGameScene().removeUINode(root);
                } catch (Throwable ignored) {
                }

                // Intentar quitar filtros desde la raíz donde se añadieron
                if (sceneRootRef != null) {
                    try {
                        sceneRootRef.removeEventFilter(KeyEvent.ANY, sceneKeyFilter);
                    } catch (Throwable ignored) {
                    }
                    try {
                        sceneRootRef.removeEventFilter(MouseEvent.ANY, sceneMouseFilter);
                    } catch (Throwable ignored) {
                    }
                }

                Parent currentRoot = null;
                try {
                    currentRoot = FXGL.getGameScene().getRoot();
                } catch (Throwable ignored) {
                }
                if (currentRoot != null && currentRoot != sceneRootRef) {
                    try {
                        currentRoot.removeEventFilter(KeyEvent.ANY, sceneKeyFilter);
                    } catch (Throwable ignored) {
                    }
                    try {
                        currentRoot.removeEventFilter(MouseEvent.ANY, sceneMouseFilter);
                    } catch (Throwable ignored) {
                    }
                }

                sceneKeyFilter = null;
                sceneMouseFilter = null;
                sceneRootRef = null;

            } catch (Throwable ignored) {
            } finally {
                // Asegurar que el flag se desactive y ejecutar onClose
                MainScreen.setModalOpen(false);
                isVisible = false;
                if (onClose != null) {
                    try {
                        onClose.run();
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
    }

    public void toggle() {
        if (isVisible) {
            close();
        } else {
            show();
        }
    }

    public boolean isVisible() {
        return isVisible;
    }

    public StackPane getRoot() {
        return root;
    }

    private Label createTitleLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #aaddff;");
        label.setMinWidth(100);
        return label;
    }

    private Label createValueLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: white;");
        label.setMinWidth(150);
        return label;
    }

    private String getSkillTreeAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Placeholder");
        return sb.toString();
    }

    private Point2D tryGetHeroTopLeftFromProvider() {
        Point2D result = null;

        if (mapScreen != null) {
            try {
                Method m = mapScreen.getClass().getMethod("getHeroMapTopLeft");
                Object res = m.invoke(mapScreen);
                if (res instanceof Point2D) {
                    result = (Point2D) res;
                }
            } catch (Throwable ignored) {
            }
        }

        return result;
    }

    private boolean isNodeDescendantOfRoot(Node node) {
        boolean result = false;
        Node cur = node;

        while (cur != null && !result) {
            if (cur == root) {
                result = true;
            }
            cur = cur.getParent();
        }

        return result;
    }

    private void handleUseWare(Wares ware) {
        boolean healed = game.heal(ware);
        if (healed) {
            game.getHero().getItems().remove(ware);
            showToast("Healed successfully!", DURACION_TOAST_MS);
        } else {
            showToast("Healing was not possible", DURACION_TOAST_MS);
        }
        refreshTabsIfNeeded();
    }

    private void handleEquipWeapon(Weapon w) {
        boolean equipped = game.equipWeapon(w);
        if (equipped) {
            showToast("Weapon equipped: " + w.getName(), DURACION_TOAST_MS);
        } else {
            showToast("Cannot equip weapon: " + w.getName(), DURACION_TOAST_MS);
        }
        refreshTabsIfNeeded();
    }

    private void handleEquipArmor(Armor a) {
        boolean equipped = game.equipArmor(a);
        if (equipped) {
            showToast("Armor equipped: " + a.getName(), DURACION_TOAST_MS);
        } else {
            showToast("Cannot equip armor: " + a.getName(), DURACION_TOAST_MS);
        }
        refreshTabsIfNeeded();
    }

    private void refreshTabsIfNeeded() {
        Platform.runLater(() -> {
            int selectedIndex = tabPane.getSelectionModel().getSelectedIndex();

            Tab status = createStatusTab();
            Tab weaponsArmor = createWeaponsArmorTab();
            Tab wares = createWaresTab();
            Tab keyItems = createKeyItemsTab();
            Tab tasks = createTasksTab();
            Tab settings = createSettingsTab();

            tabPane.getTabs().setAll(status, weaponsArmor, wares, keyItems, tasks, settings);

            if (selectedIndex >= 0 && selectedIndex < tabPane.getTabs().size()) {
                tabPane.getSelectionModel().select(selectedIndex);
            } else {
                tabPane.getSelectionModel().select(0);
            }

            Platform.runLater(() -> root.requestFocus());
        });
    }

}
