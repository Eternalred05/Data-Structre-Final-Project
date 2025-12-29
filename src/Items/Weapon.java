package Items;

import Utils.Buyable;

public abstract class Weapon extends Item implements Buyable {

    protected int attack;
    protected int lifeSpan;
    protected String effect;
    protected String type;
    protected int cost;

    public Weapon(String info, String name, String id, int attack, int lifeSpan,
            String effect, int cost) {
        super(info, name, id);
        setAttack(attack);
        setLifeSpan(lifeSpan);
        setEffect(effect);
        setCost(cost);
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public int getCost() {
        return cost;
    }

    public int getAttack() {
        return attack;
    }

    public void setAttack(int attack) {
        this.attack = attack;
    }

    public int getLifeSpan() {
        return lifeSpan;
    }

    public void setLifeSpan(int lifeSpan) {
        this.lifeSpan = lifeSpan;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}
