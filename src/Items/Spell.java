
package Items;


public class Spell extends Weapon {

    public Spell(String info, String name, String id, int attack, int lifeSpan, String effect,int cost, int salePrice) {
        super(info, name, id, attack, lifeSpan, effect,cost, salePrice);
        setType("spell");
    }
    
    
}
