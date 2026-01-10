package Items;


public class Pike extends Spear{
    
    public Pike(String info, String name, String id, int attack, int lifeSpan,
            String effect,int cost, int salePrice){
        super(info, name, id, attack, lifeSpan, effect,cost, salePrice);
        setType("pike");
     }
}
