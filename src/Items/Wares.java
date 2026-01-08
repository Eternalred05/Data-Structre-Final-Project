package Items;

import Utils.Buyable;
import javax.swing.Icon;

public class Wares extends Item implements Buyable {

    private int healing;
    private int cost;
    private int salePrice;
    
    public Wares(String info, String name, String id, int healing, int cost, int salePrice) {
        super(info, name, id);
        setHealing(healing);
        setCost(cost);
        setSalePrice(salePrice);
    }
    
    public void setCost(int cost) {
        this.cost = cost;
    }
    
    public int getCost() {
        return cost;
    }

    public int getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(int salePrice) {
        this.salePrice = salePrice;
    }

   
    
    public int getHealing() {
        return healing;
    }
    
    public void setHealing(int healing) {
        this.healing = healing;
    }
    
    public String getInfo() {
        return info;
    }
    
    public void setInfo(String info) {
        this.info = info;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public Icon getIcon() {
        return icon;
    }
    
    public void setIcon(Icon icon) {
        this.icon = icon;
    }
    
}
