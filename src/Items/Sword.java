package Items;

public class Sword extends Weapon {

    public Sword(String info, String name, String id, int attack, int lifeSpan,
            String effect, int cost, int salePrice) {
        super(info, name, id, attack, lifeSpan, effect,cost, salePrice);
        setType("sword");
    }
}
