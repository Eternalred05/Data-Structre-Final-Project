package GUI;

import Runner.MainScreen;
import Characters.Hero;
import Logic.Game;
import com.almasb.fxgl.dsl.FXGL;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ForestHouse {

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

    private Rectangle startRect;
    private boolean onStartRect = false;
    private Rectangle houseEntranceRect;
    private boolean onHouseEntranceRect = false;
    private Rectangle houseExitRect;
    private boolean onHouseExit = false;
    private Rectangle floor2EntranceRect;
    private boolean onFloor2Entrance = false;
    private Rectangle floor2ExitRect;
    private boolean onFloor2Exit = false;
    
    private Runnable onExitCallback;
    private final Game game;

    // Sistema de colisiones
    private final List<Obstacle> obstacles = new ArrayList<>();
    private boolean debugEnabled = false;

    // Inventario (si se abre desde aquí se pasa this)
    private InventoryScreen inventory;

    // Direcciones del héroe (para depuración con tecla P)
    public enum Direction {
        NONE, N, NE, E, SE, S, SW, W, NW
    }
    private Direction currentDirection = Direction.NONE;

    // Tipos de obstáculos para la aldea
    private enum ObstacleType {
        HOUSE, TREE, FENCE, BUSH, BLOCK, PLANT, DECORATION
    }

    // Clase interna para obstáculos
    private static class Obstacle {

        final Rectangle2D collisionRect;
        final ForestHouse.ObstacleType type;
        final String id;

        Obstacle(Rectangle2D collision, ForestHouse.ObstacleType type, String id) {
            this.collisionRect = collision;
            this.type = type;
            this.id = id;
        }
    }

    public ForestHouse(Game game) {
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

            boolean imageOk = loadBackgroundImage("/Resources/textures/forestHouse/forestHouseOutside2.png");
            boolean musicOk = startVillageMusic("/Resources/music/forestHouse.mp3");

            populateForestHouseObstacles();

            // Luego posicionar al héroe
            positionHeroAtEntrance();
            createStartRectAtHeroStart();
            createHouseEntranceRect();  
            
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

        Text label = new Text("Cargando aldea...");
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
            return true;
        } catch (Throwable t) {
            Text err = new Text("No se pudo cargar la imagen de la aldea.");
            err.setStyle("-fx-font-size: 16px; -fx-fill: #ffdddd;");
            root.getChildren().add(err);
            return false;
        }
    }

    private boolean startVillageMusic(String path) {
        try {
            URL res = getClass().getResource(path);
            if (res == null) {
                return false;
            }
            Media media = new Media(res.toExternalForm());
            stopVillageMusic();
            music = new MediaPlayer(media);
            music.setCycleCount(MediaPlayer.INDEFINITE);
            music.setVolume(MainScreen.getVolumeSetting());
            music.play();
            return true;
        } catch (Throwable t) {
            return false;
        }
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

    // ---------------- metodos de colisiones  ----------------
    private void populateForestHouseObstacles() {
        obstacles.clear();
        //-----------------BordesYCentro-----
        double heroTopLeftX = 37;
        double heroTopLeftY = 57;
        obstacles.add(new Obstacle(
                new Rectangle2D(heroTopLeftX, heroTopLeftY, 48, 48),
                ObstacleType.BLOCK,
                "Bloque1"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(205.0, 92.46, 402, 305),
                ObstacleType.HOUSE,
                "Mansion"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(560.64, 535.70, 450, 150),
                ObstacleType.TREE,
                "IleraArbolesDerechaH"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(785.24, 64.61, 40, 450),
                ObstacleType.TREE,
                "IleraArbolesDerechaV"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(40.64, 535.70, 245, 150),
                ObstacleType.TREE,
                "IleraArbolesIzquierdaH"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(0, 64.61, 40, 450),
                ObstacleType.TREE,
                "IleraArbolesIzquierdaV"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(0, 0, 800, 70),
                ObstacleType.TREE,
                "IleraArbolesFinal"
        ));

        //-----------------Adornos-----
        obstacles.add(new Obstacle(
                new Rectangle2D(502.0, 590.0, 35, 10),
                ObstacleType.PLANT,
                "Planta1Derecha"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(268.0, 590.0, 48, 48),
                ObstacleType.PLANT,
                "Planta1Izquierda"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(502.0, 541.0, 48, 48),
                ObstacleType.PLANT,
                "Planta2Derecha"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(268.0, 541.0, 48, 48),
                ObstacleType.PLANT,
                "Planta2Izquierda"
        ));

        obstacles.add(new Obstacle( //mejorar
                new Rectangle2D(508.27, 438.0, 5, 5),
                ObstacleType.DECORATION,
                "AdornoDerecha"
        ));

        obstacles.add(new Obstacle( //mejorar
                new Rectangle2D(303.0, 438.0, 5, 5),
                ObstacleType.DECORATION,
                "AdornoIzquierda"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(608.64, 75.61, 170, 30),
                ObstacleType.DECORATION,
                "IleraAdornosFondo"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(608.7, 350., 45, 8),
                ObstacleType.DECORATION,
                "CuboAgua"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(167.5, 390., 45, 10),
                ObstacleType.DECORATION,
                "Jarron"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(42.18, 118.12, 90, 50),
                ObstacleType.TREE,
                "ArbolFondo"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(41.19, 170.50, 40, 50),
                ObstacleType.DECORATION,
                "Tronco1"
        ));

        obstacles.add(new Obstacle(
                new Rectangle2D(133.81, 69.76, 40, 100),
                ObstacleType.DECORATION,
                "Chimenea"
        ));
    }

    private  void colisicions2Floor(){
          double heroTopLeftX = 0;
        double heroTopLeftY =0 ;
        
        obstacles.add(new Obstacle(
                new Rectangle2D(heroTopLeftX, heroTopLeftY, 5, 5),
                ObstacleType.DECORATION,
                "Bloque1"
        ));
        
         obstacles.add(new Obstacle(
                new Rectangle2D(0, 0, 800,71),
                ObstacleType.DECORATION,
                "CortinasFondo"
        ));
         
          obstacles.add(new Obstacle(
                new Rectangle2D(61.01,77,68,77),
                ObstacleType.DECORATION,
                "Cama"
        ));
          
           obstacles.add(new Obstacle(
                new Rectangle2D(0,70,230,39),
                ObstacleType.DECORATION,
                "ComodasIzquirdaSupe"
        ));
           
            obstacles.add(new Obstacle(
                new Rectangle2D(0,295,83,15),
                ObstacleType.DECORATION,
                "Mesaizq"
        ));
            
          obstacles.add(new Obstacle(
                new Rectangle2D(253,582.0,72,15),
                ObstacleType.DECORATION,
                "MesaInferior"
        )); 
           
          obstacles.add(new Obstacle(
                new Rectangle2D(490.297, 90.72,73,20),
                ObstacleType.DECORATION,
                "Mesasuperior"
        ));
            
          obstacles.add(new Obstacle(
                new Rectangle2D(640,90.72,150,20),
                ObstacleType.DECORATION,
                "EstantesDercha"
        ));
            
          obstacles.add(new Obstacle(
                new Rectangle2D(740,395.0,15,55),
                ObstacleType.DECORATION,
                "Silla"
        ));
            
           obstacles.add(new Obstacle(
                new Rectangle2D(588,391.0,117,109),
                ObstacleType.DECORATION,
                "MesaMapas"
        ));
           
     }
    
    private void colissionInSide(){
        double heroTopLeftX = 0;
        double heroTopLeftY =580 ;
        
        obstacles.add(new Obstacle(
                new Rectangle2D(heroTopLeftX, heroTopLeftY, 5, 5),
                ObstacleType.DECORATION,
                "Bloque1"
        ));
        
       obstacles.add(new Obstacle(
                new Rectangle2D(0, 580, 285,10),
                ObstacleType.BLOCK,
                "DifenPisoIzquierda"
        ));
       
       obstacles.add(new Obstacle(
                new Rectangle2D(582, 580, 285,10),
                ObstacleType.DECORATION,
                "DifenPisoIDerecha"
        ));
       
       obstacles.add(new Obstacle(
                new Rectangle2D(0,488.06,35,100),
                ObstacleType.DECORATION,
                "EsferaYMesaIzquierda"
        ));
 
       obstacles.add(new Obstacle(
                new Rectangle2D(0,340,35,70),
                ObstacleType.BLOCK,
                "CuboYJarronIzquierda"
        ));
       
       obstacles.add(new Obstacle(
                new Rectangle2D(110,535,70,30),
                ObstacleType.DECORATION,
                "MesaIzqInferior"
        ));
       
       
       obstacles.add(new Obstacle(
                new Rectangle2D(680.21,537,70,20),
                ObstacleType.DECORATION,
                "MesasDerechInferior"
        ));
      
       obstacles.add(new Obstacle(
                new Rectangle2D(785,480,20,70),
                ObstacleType.DECORATION,
                "SacosDerecha"
        ));
        //aquiii
       obstacles.add(new Obstacle(
                new Rectangle2D(785,400,70,30),
                ObstacleType.DECORATION,
                "BarrilesDerecha"
        ));
       
       obstacles.add(new Obstacle(
                new Rectangle2D(735,350,70,20),
                ObstacleType.DECORATION,
                "JarronesDerecha"
        ));
       
       obstacles.add(new Obstacle(
                new Rectangle2D(785,150,30,65),
                ObstacleType.DECORATION,
                "SillaDerecha"
        ));
       
        obstacles.add(new Obstacle(
                new Rectangle2D(680,200,74,30),
                ObstacleType.DECORATION,
                "MesaComida"
        ));
        
        obstacles.add(new Obstacle(
                new Rectangle2D(155,0,70,70),
                ObstacleType.DECORATION,
                "EstanteLadoEscalera"
        ));
        
        obstacles.add(new Obstacle(
                new Rectangle2D(212,0,700,20),
                ObstacleType.DECORATION,
                "Meceta y adornos Fondo"
        ));
        
        obstacles.add(new Obstacle(
                new Rectangle2D(585,0,30,70),
                ObstacleType.DECORATION,
                "EstantePlatos"
        ));
        
        obstacles.add(new Obstacle(
                new Rectangle2D(655,0,80,70),
                ObstacleType.DECORATION,
                "Piano"
        ));
        
         obstacles.add(new Obstacle(
                new Rectangle2D(310.5,300,8,8),
                ObstacleType.DECORATION,
                "CajaCentro"
        ));
         
          obstacles.add(new Obstacle(
                new Rectangle2D(350,250,27,50),
                ObstacleType.DECORATION,
                "Silla1"
        ));
          
           obstacles.add(new Obstacle(
                new Rectangle2D(490,250,27,50),
                ObstacleType.DECORATION,
                "Silla2"
        ));
           
            obstacles.add(new Obstacle(
                new Rectangle2D(390,295,85,75),
                ObstacleType.DECORATION,
                "MesaCentro"
        ));
    }
    
    // ---------------- movimiento , y entradas ----------------
    private void positionHeroAtEntrance() {
        double startX = (worldW - HERO_W) / 2.0;
        double startY = worldH - HERO_H - 8.0;

        startX = clamp(startX, 0, Math.max(0, worldW - HERO_W));
        startY = clamp(startY, 0, Math.max(0, worldH - HERO_H));

        Rectangle2D heroRect = new Rectangle2D(startX, startY, HERO_W, HERO_H);
        for (ForestHouse.Obstacle ob : obstacles) {
            if (heroRect.intersects(ob.collisionRect)) {
                startY = ob.collisionRect.getMinY() - HERO_H - 5;
                break;
            }
        }

        heroView.setLayoutX(startX);
        heroView.setLayoutY(startY);
        updateCamera();
    }

    private void createStartRectAtHeroStart() {
        if (startRect != null) {
            world.getChildren().remove(startRect);
            startRect = null;
        }
        double rx = 379.26;
        double ry = 576.0;
        double rw = HERO_W + 8;
        double rh = HERO_H + 8;

        startRect = new Rectangle(rx - 4, ry - 4, rw, rh);
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

    private void createHouseEntranceRect() { 
    if (houseEntranceRect != null) {
        world.getChildren().remove(houseEntranceRect);
        houseEntranceRect = null;
    }
    
     double rx = 352;
        double ry = 397.98;
        double rw = 50;
        double rh = 20;
    
    houseEntranceRect = new Rectangle(rx - 4, ry - 4, rw, rh);
    houseEntranceRect.setFill(Color.rgb(0, 120, 255, 0.28));
    houseEntranceRect.setStroke(Color.rgb(0, 80, 200, 0.9));
    houseEntranceRect.setMouseTransparent(true);
    houseEntranceRect.getProperties().put("tag", "house_entrance");

    if (!world.getChildren().contains(houseEntranceRect)) {
        world.getChildren().add(houseEntranceRect);
    }
    houseEntranceRect.toBack();
    heroView.toFront();
}
    
    private void createHouseExitRect() { 
    if (houseExitRect != null) {
        world.getChildren().remove(houseExitRect);
        houseEntranceRect = null;
    }
    
     double rx = 380;
        double ry = 580;
        double rw = 50;
        double rh = 50;
    
    houseExitRect = new Rectangle(rx - 4, ry - 4, rw, rh);
    houseExitRect.setFill(Color.rgb(0, 120, 255, 0.28));
   houseExitRect.setStroke(Color.rgb(0, 80, 200, 0.9));
    houseExitRect.setMouseTransparent(true);
    houseExitRect.getProperties().put("tag", "house_exit");

    if (!world.getChildren().contains(houseExitRect)) {
        world.getChildren().add(houseExitRect);
    }
   houseExitRect.toBack();
    heroView.toFront();
}
    
     private void createFloor2EntranceRect(){
        if (floor2EntranceRect!= null) {
        world.getChildren().remove(floor2EntranceRect);
        floor2EntranceRect = null;
    }
    
     double rx = 0;
        double ry = 0;
        double rw = 100;
        double rh = 50;
    
   floor2EntranceRect = new Rectangle(rx - 4, ry - 4, rw, rh);
   floor2EntranceRect.setFill(Color.rgb(0, 120, 255, 0.28));
   floor2EntranceRect.setStroke(Color.rgb(0, 80, 200, 0.9));
   floor2EntranceRect.setMouseTransparent(true);
   floor2EntranceRect.getProperties().put("tag", "floor_entrance");

    if (!world.getChildren().contains(floor2EntranceRect)) {
        world.getChildren().add(floor2EntranceRect);
    }
  floor2EntranceRect.toBack();
    heroView.toFront();
    }

     private void createFloor2ExitRect(){
         if (floor2ExitRect!= null) {
        world.getChildren().remove(floor2ExitRect);
        floor2ExitRect = null;
    }
    
     double rx = 0;
        double ry = 560;
        double rw = 30;
        double rh = 30;
    
   floor2ExitRect = new Rectangle(rx - 4, ry - 4, rw, rh);
   floor2ExitRect.setFill(Color.rgb(0, 120, 255, 0.28));
   floor2ExitRect.setStroke(Color.rgb(0, 80, 200, 0.9));
   floor2ExitRect.setMouseTransparent(true);
   floor2ExitRect.getProperties().put("tag", "floor_entrance");

    if (!world.getChildren().contains(floor2EntranceRect)) {
        world.getChildren().add(floor2EntranceRect);
    }
  floor2EntranceRect.toBack();
    heroView.toFront();
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
                System.out.println("Hero position (aldea): (" + heroView.getLayoutX() + ", " + heroView.getLayoutY() + ")");
                System.out.println("Hero world center (aldea): (" + (heroView.getLayoutX() + HERO_W / 2) + ", " + (heroView.getLayoutY() + HERO_H / 2) + ")");
                System.out.println("Hero direction: " + getHeroDirection().name());
            }

            if (k == KeyCode.I || k == KeyCode.ADD || k == KeyCode.PLUS) {
                clearInputState();
                openInventory();
            }

            if (k == KeyCode.ENTER) {
                if (onStartRect) {
                    clearInputState();
                    try {
                        if (game != null && game.getHero() != null) {
                            Hero h = game.getHero();
                            h.setLastLocation(Hero.Location.FOREST_HOUSE);
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
                
                if(onHouseEntranceRect){
                   intoHouse();
                } 
                if(onFloor2Entrance){
                   floor2Into();
                    
                }
               
                if(onHouseExit){
                    exitHouse();
                } 
                    
                if(onFloor2Exit){
                    intoHouse();
                }
            }

            if (k == KeyCode.ESCAPE) {
                clearInputState();

                Alert dlg = new Alert(Alert.AlertType.CONFIRMATION);
                dlg.setTitle("Volver al menú");
                dlg.setHeaderText("¿Quieres volver al menú principal?");
                dlg.setContentText("Si vuelves al menú, la partida seguirá guardada en disco.");
                try {
                    if (root.getScene() != null && root.getScene().getWindow() != null) {
                        dlg.initOwner(root.getScene().getWindow());
                    }
                } catch (Throwable ignored) {
                }
                dlg.setOnHidden(eh -> {
                    clearInputState();
                    Platform.runLater(root::requestFocus);
                });

                Optional<ButtonType> opt = dlg.showAndWait();
                boolean ok = opt.isPresent() && opt.get() == ButtonType.OK;
                if (ok) {
                    try {
                        if (game != null && game.getHero() != null) {
                            Hero h = game.getHero();
                            h.setLastLocation(Hero.Location.FOREST_HOUSE);
                            h.setLastPosY(heroView.getLayoutY());
                            h.setLastPosX(heroView.getLayoutX());
                            try {
                                game.createSaveGame();
                            } catch (Throwable ignored) {
                            }
                        }
                    } catch (Throwable ignored) {
                    }

                    stopVillageMusic();
                    try {
                        FXGL.getGameScene().removeUINode(root);
                    } catch (Throwable ignored) {
                    }
                    MainScreen.restoreMenuAndMusic();
                } else {
                    clearInputState();
                    Platform.runLater(root::requestFocus);
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

        // Pasar referencia para que InventoryScreen pueda guardar la posición y reanudar foco
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

                if (root.getScene() == null || !root.isFocused()) {
                    clearInputState();
                    return;
                }

                updateAndMove(dt);
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

        ForestHouse.Direction newDir = (vx != 0 || vy != 0)
                ? directionFromVector(vx, vy) : ForestHouse.Direction.NONE;
        setDirectionIfChanged(newDir);

        if (vx == 0 && vy == 0) {
            checkStartIntersection();
            checkHouseEntranceIntersection();
            checkHouseExitIntersection();
            checkfloor2EntranceIntersection();
            checkfloor2EnxitIntersection();
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

        for (ForestHouse.Obstacle ob : obstacles) {
            if (heroRect.intersects(ob.collisionRect)) {
                collision = true;
                break;
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

            for (ForestHouse.Obstacle ob : obstacles) {
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

    private ForestHouse.Direction directionFromVector(double vx, double vy) {
        if (vx == 0 && vy == 0) {
            return ForestHouse.Direction.NONE;
        }
        double angle = Math.toDegrees(Math.atan2(-vy, vx));
        if (angle < 0) {
            angle += 360.0;
        }

        if (angle >= 337.5 || angle < 22.5) {
            return ForestHouse.Direction.E;
        }
        if (angle < 67.5) {
            return ForestHouse.Direction.NE;
        }
        if (angle < 112.5) {
            return ForestHouse.Direction.N;
        }
        if (angle < 157.5) {
            return ForestHouse.Direction.NW;
        }
        if (angle < 202.5) {
            return ForestHouse.Direction.W;
        }
        if (angle < 247.5) {
            return ForestHouse.Direction.SW;
        }
        if (angle < 292.5) {
            return ForestHouse.Direction.S;
        }
        if (angle < 337.5) {
            return ForestHouse.Direction.SE;
        }
        return ForestHouse.Direction.NONE;
    }

    private void setDirectionIfChanged(ForestHouse.Direction newDir) {
        if (newDir == null) {
            newDir = ForestHouse.Direction.NONE;
        }
        currentDirection = newDir;
    }

    public ForestHouse.Direction getHeroDirection() {
        return currentDirection;
    }

    private void checkStartIntersection() {
        if (startRect == null) {
            onStartRect = false;
            return;
        }
        boolean intersects = heroView.getBoundsInParent().intersects(startRect.getBoundsInParent());
        onStartRect = intersects;
        startRect.setFill(intersects ? Color.rgb(0, 120, 255, 0.42) : Color.rgb(0, 120, 255, 0.28));
    }

    private void checkHouseEntranceIntersection() {
    if (houseEntranceRect == null) {
        onHouseEntranceRect = false;
        return;
    }
    boolean intersects = heroView.getBoundsInParent().intersects(houseEntranceRect.getBoundsInParent());
    onHouseEntranceRect = intersects;
    houseEntranceRect.setFill(intersects ? Color.rgb(0, 120, 255, 0.42) : Color.rgb(0, 120, 255, 0.28));
}

     private void checkHouseExitIntersection(){
       if (houseExitRect == null) {
        onHouseExit = false;
        return;
    }
    boolean intersects = heroView.getBoundsInParent().intersects(houseExitRect.getBoundsInParent());
    onHouseExit = intersects;
    houseExitRect.setFill(intersects ? Color.rgb(0, 120, 255, 0.42) : Color.rgb(0, 120, 255, 0.28));
 
    }
     
      private void checkfloor2EntranceIntersection(){
       if (floor2EntranceRect == null) {
        onFloor2Entrance = false;
        return;
    }
    boolean intersects = heroView.getBoundsInParent().intersects(floor2EntranceRect.getBoundsInParent());
    onFloor2Entrance = intersects;
    floor2EntranceRect.setFill(intersects ? Color.rgb(0, 120, 255, 0.42) : Color.rgb(0, 120, 255, 0.28));
 
    }
    
    private void checkfloor2EnxitIntersection(){
       if (floor2ExitRect == null) {
        onFloor2Exit = false;
        return;
    }
    boolean intersects = heroView.getBoundsInParent().intersects(floor2ExitRect .getBoundsInParent());
    onFloor2Exit = intersects;
   floor2ExitRect .setFill(intersects ? Color.rgb(0, 120, 255, 0.42) : Color.rgb(0, 120, 255, 0.28));
 
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
   //---Metodo para cambiar el fondo la musica y las colisiones,y todo dentro del 1er piso-----
    private void intoHouse(){ 
        obstacles.clear(); //limpiando coaliciones
        colissionInSide();
        startRect=null;
        
       loadBackgroundImage("/Resources/textures/forestHouse/1stFloorForestHouse.png");// cambiando fondo
       
       stopVillageMusic();
       startVillageMusic("/Resources/music/inteiorOST.mp3");//camniar musica
       
      setHeroPosition(411.0, 576.0); //poisiconar heroe
      
      createHouseExitRect();
     createFloor2EntranceRect();
      
    
    }
    
     private void exitHouse(){
         obstacles.clear();;
                    populateForestHouseObstacles();
                    createHouseEntranceRect();
                    
                    loadBackgroundImage("/Resources/textures/forestHouse/foresthouseOutside2.png");// cambiando fondo
       
                    stopVillageMusic();
                   startVillageMusic("/Resources/music/forestHouse.mp3");//camniar musica
                   
                   setHeroPosition(379.0, 410.0); //poisiconar heroe 
    }
     
     private void  floor2Into(){
        obstacles.clear();
        colisicions2Floor();
        createFloor2ExitRect();
        houseExitRect=null;
        
        loadBackgroundImage("/Resources/textures/forestHouse/2ndFloorForestHouse.png");// cambiando fondo
        setHeroPosition(0, 530.0); //poisiconar heroe   
     }
          
}

