package Characters;

import java.util.ArrayList;
import javafx.scene.image.Image;

public abstract class NPC {
    
    protected String name;
    protected ArrayList<String> dialogue;
    protected String spritePath;
    protected transient Image fxImage;
    
    public NPC(String name, String sprite) {
        setSpritePath(sprite);
        loadFxImage(spritePath);
        setName(name);
    }
    
    public String getMessageFromList(int pos){
        return dialogue.get(pos);
    }
    
    public String getSpritePath() {
        return spritePath;
    }
    
    public void setSpritePath(String spritePath) {
        this.spritePath = spritePath;
    }
    
    private void loadFxImage(String spritePath) {
        if (!(spritePath == null || spritePath.isEmpty())) {
            
            try {
                fxImage = new Image(getClass().getResourceAsStream(spritePath));
                if (fxImage.isError()) {
                    fxImage = null;
                }
            } catch (Throwable ignored) {
                fxImage = null;
            }
        }
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Image getFxImage() {
        return fxImage;
    }
    
}
