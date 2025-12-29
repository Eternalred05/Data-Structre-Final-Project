package Misc;

import java.util.ArrayList;
import Items.Item;
import java.io.Serializable;

public class Task implements Serializable {

    private static final long serialVersionUID = 1L;
    private String name;
    private String info;
    private String id;
    private boolean state;
    private boolean mainQuest;
    private ArrayList<Item> rewards;
    private int money;

    public Task(String name, String info, String id, int money, boolean mainQuest) {
        setName(name);
        setInfo(info);
        setId(id);
        setMoney(money);
        setMainQuest(mainQuest);
        this.state = false;
        this.rewards = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isState() {
        return state;
    }

    public void setState(boolean state) {
        this.state = state;
    }

    public boolean isMainQuest() {
        return mainQuest;
    }

    public void setMainQuest(boolean mainQuest) {
        this.mainQuest = mainQuest;
    }

    public int getMoney() {
        return money;
    }

    public void setMoney(int money) {
        this.money = money;
    }

    public void addReward(Item i) {
        rewards.add(i);

    }

    public ArrayList<Item> getRewards() {
        return rewards;
    }

    public void setRewards(ArrayList<Item> rewards) {
        this.rewards = rewards;
    }

}
