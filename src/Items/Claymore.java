package Items;

public class Claymore extends Sword {

    public Claymore(String info, String name, String id, int attack, int lifeSpan,
            String effect, int cost) {
        super(info, name, id, attack, lifeSpan, effect, cost);
        setType("claymore");
    }

}
