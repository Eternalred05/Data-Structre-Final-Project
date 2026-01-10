package Items;


public class Shotgun extends Gun{
    
    public Shotgun(String info, String name, String id, int attack, int lifeSpan,
            String effect,int cost, double range, int salePrice){
        super(info, name, id, attack, lifeSpan, effect,"shotgun",cost, range, salePrice);
    }
}
