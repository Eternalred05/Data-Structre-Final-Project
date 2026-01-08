package Items;

public class Claymore extends Sword {

    public Claymore(String info, String name, String id, int attack, int lifeSpan,
            String effect, int cost, int salePrice) {
        super(info, name, id, attack, lifeSpan, effect, cost, salePrice);
        setType("claymore");
    }

}
