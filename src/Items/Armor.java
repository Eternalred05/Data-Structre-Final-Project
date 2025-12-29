package Items;

import Utils.Buyable;

public class Armor extends Item implements Buyable {

    private int defense;
    private String effect;
    private int cost;

    public Armor(String info, String name, String id, int defense, String effect, int cost) {
        super(info, name, id);
        setDefense(defense);
        setEffect(effect);
        setCost(cost);
    }

    public int getDefense() {
        return defense;
    }

    public void setDefense(int defense) {
        this.defense = defense;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public int getCost() {
        return cost;
    }

}
