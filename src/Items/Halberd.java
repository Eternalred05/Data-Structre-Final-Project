package Items;


public class Halberd extends Spear{
    
    public Halberd(String info, String name, String id, int attack, int lifeSpan,
            String effect,int cost, int salePrice){
        super(info, name, id, attack, lifeSpan, effect,cost, salePrice);
        setType("Halberd");
     }
    
}
