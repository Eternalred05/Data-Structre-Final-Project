package Items;


public class Rifle extends Gun{
    
    public Rifle(String info, String name, String id, int attack, int lifeSpan,
            String effect,int cost, double range, int salePrice){
        super(info, name, id, attack, lifeSpan, effect,"rifle",cost, range, salePrice);
        setType("rifle");
    }
}
