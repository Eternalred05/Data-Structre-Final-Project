package Items;

public class HealingSpell extends Spell {

    public HealingSpell(String info, String name, String id, int attack, int lifeSpan, String effect, int cost, int salePrice) {
        super(info, name, id, attack, lifeSpan, effect, cost, salePrice);
        setType("healingSpell");
    }

}
