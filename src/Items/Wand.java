package Items;

public class Wand extends Weapon {

    public Wand(String info, String name, String id, int attack, int lifeSpan, String effect, int cost) {
        super(info, name, id, attack, lifeSpan, effect,cost);
        setType("wand");
    }

}
