package Characters;

import Items.*;
import Misc.Task;

public class Villager extends NPC {

    private boolean gift;
    private Item giftItem;
    private Task task;

    public Villager(boolean gift, Item giftItem, String name, String sprite, Task task) {
        super(name, sprite);
        this.gift = gift;
        this.giftItem = giftItem;
        this.task = task;
    }

    public boolean taskGiven(Hero h) {
        boolean given = false;
        if (h.existsPendingTask(task)) {

        }
        return given;
    }
    

    public void giveGift(Hero h) {
        h.getItems().add(giftItem);
    }

    public boolean giveMissionReward(Hero h) {
        boolean give = false;
        if (h.existsPendingTask(task)) {
            if (h.searchCompletedTask(task).isState()) {
                give = true;
            }
        }
        return give;
    }

    public boolean isGift() {
        return gift;
    }

    public void setGift(boolean gift) {
        this.gift = gift;
    }

    public Item getGiftItem() {
        return giftItem;
    }

    public void setGiftItem(Item giftItem) {
        this.giftItem = giftItem;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }
    
    
}
