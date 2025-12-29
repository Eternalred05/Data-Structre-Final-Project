package Items;

public class Gun extends Weapon {

    private double range; // number of monsters to attack
    
    public Gun(String info, String name, String id, int attack, int lifeSpan,
            String effect, String type,int cost, double range){
        super(info, name, id, attack, lifeSpan, effect,cost);
        setRange(range);
        setType("gun");
    }

    public double getRange() {
        return range;
    }

    public void setRange(double range) {
        this.range = range;
    }
    
    
}
