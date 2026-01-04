package Characters;

import Items.*;
import Logic.Game;
import Tree.*;
import Misc.*;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import javafx.scene.image.Image;

public class Hero implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Location {
        MAP, FIELD_VILLAGE, FOREST_HOUSE, SWAMP, SWAMP_DUNGEON, UNKNOWN
    }

    private String name;
    private String spritePath;
    private transient Image fxImage;
    private int expMax;
    private int expActual;
    private int attack;
    private int magic;
    private int defense;
    private int level;
    private int life;
    private int actualLife;
    private int money;
    private int defeatedMonsters;
    private LinkedList<Item> items;
    private Weapon actualWeapon;
    private Armor armor;
    private GeneralTree<Classes> unlockedClasses;
    private Queue<Task> tasks;
    private Deque<Task> completedTasks;

    private Location lastLocation = Location.UNKNOWN;
    private double lastPosX = 0.0;
    private double lastPosY = 0.0;

    public Hero(String name, Weapon weapon, Armor armor, Classes root) {
        setName(name);
        setLife(150);
        setActualLife(150);
        setSpritePath("/Resources/sprites/hero/hero.png");
        setAttack(7);
        setMagic(20);
        setDefense(3);
        setLevel(1);
        setExpMax(100);
        setExpActual(0);
        setArmor(armor);
        setMoney(50);
        items = new LinkedList<>();
        actualWeapon = weapon;
        BinaryTreeNode<Classes> heroRoot = new BinaryTreeNode<>(root);
        unlockedClasses = new GeneralTree<>(heroRoot);
        tasks = new ArrayDeque<>();
        completedTasks = new ArrayDeque<>();
        loadFxImage();
        defeatedMonsters = 0;
    }

    public void growDefeatedMonsters() {
        defeatedMonsters++;
    }

    public int getDefeatedMonsters() {
        return defeatedMonsters;
    }

    public int getMoney() {
        return money;
    }

    public void setMoney(int money) {
        this.money = money;
    }

    public void sumExp(int exp) {
        setExpActual(expActual + exp);
    }

    public boolean levelUp() {
        boolean levelUp = false;
        if (expActual >= expMax) {
            Random rnd = new Random();
            int temp = expActual - expMax;

            setExpActual(0 + temp);
            setExpMax(expMax + 50);
            levelUp = true;
            level++;
            setDefense(defense + rnd.nextInt(1, 5));
            setLife(life + rnd.nextInt(1, 5));
            setActualLife(life);
            setAttack(attack + rnd.nextInt(1, 5));
        }
        return levelUp;
    }

    public boolean searchHeroSkillTreeNode(String nodeId) {
        boolean found = false;
        BinaryTreeNode<Classes> ahya = (BinaryTreeNode<Classes>) unlockedClasses.getRoot();
        InBreadthIterator<Classes> it = unlockedClasses.inBreadthIterator();
        while (it.hasNext() && !found) {
            BinaryTreeNode<Classes> node = it.nextNode();
            Classes cl = (Classes) node.getInfo();
            if (cl.getId().equalsIgnoreCase(nodeId)) {
                found = true;
            }

        }
        return found;
    }

    public int getExpMax() {
        return expMax;
    }

    public void setExpMax(int expMax) {
        this.expMax = expMax;
    }

    public int getExpActual() {
        return expActual;
    }

    public void setExpActual(int expActual) {
        this.expActual = expActual;
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public void setLastLocation(Location lastLocation) {
        this.lastLocation = lastLocation != null ? lastLocation : Location.UNKNOWN;
    }

    public double getLastPosX() {
        return lastPosX;
    }

    public void setLastPosX(double lastPosX) {
        this.lastPosX = lastPosX;
    }

    public double getLastPosY() {
        return lastPosY;
    }

    public void setLastPosY(double lastPosY) {
        this.lastPosY = lastPosY;
    }

    public Armor getArmor() {
        return armor;
    }

    public boolean setArmor(Armor armor) {
        boolean done = false;
        if (armor != null) {
            this.armor = armor;
            done = true;
        }
        return done;
    }

    public GeneralTree<Classes> getUnlockedClasses() {
        return unlockedClasses;
    }

    public void setUnlockedClasses(GeneralTree<Classes> unlockedClasses) {
        this.unlockedClasses = unlockedClasses;
    }

    public int getLife() {
        return life;
    }

    public void setLife(int life) {
        if (life > 0) {
            this.life = life;
        } else {
            throw new IllegalArgumentException("Debe ser mayor que 0");
        }
    }

    public int getActualLife() {
        return actualLife;
    }

    public void setActualLife(int actualLife) {
        this.actualLife = actualLife;
    }

    private void loadFxImage() {
        if (!(spritePath == null || spritePath.isEmpty())) {
            try {
                fxImage = new Image(getClass().getResourceAsStream(spritePath));
                if (fxImage.isError()) {
                    fxImage = null;
                }
            } catch (Throwable ignored) {
                fxImage = null;
            }
        }
    }

    public Image getImage() {
        if (fxImage != null) {
            return fxImage;
        }
        loadFxImage();
        return fxImage;
    }

    public void setSpritePath(String spritePath) {
        this.spritePath = spritePath;
        this.fxImage = null;
    }

    public String getSpritePath() {
        return spritePath;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (!name.isEmpty()) {
            this.name = name;
        } else {
            throw new IllegalArgumentException("Debe ser mayor que 0");
        }
    }

    public int getAttack() {
        return attack;
    }

    public void setAttack(int attack) {
        if (attack >= 1) {
            this.attack = attack;
        } else {
            throw new IllegalArgumentException("Debe ser mayor que 0");
        }
    }

    public int getMagic() {
        return magic;
    }

    public void setMagic(int magic) {
        if (magic >= 1) {
            this.magic = magic;
        } else {
            throw new IllegalArgumentException("Debe ser mayor que 0");
        }
    }

    public int getDefense() {
        return defense;
    }

    public void setDefense(int defense) {
        if (defense >= 1) {
            this.defense = defense;
        } else {
            throw new IllegalArgumentException("Debe ser mayor que 0");
        }
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        if (level >= 1) {
            this.level = level;
        } else {
            throw new IllegalArgumentException("Debe ser mayor que 0");
        }
    }

    public LinkedList<Item> getItems() {
        return items;
    }

    public void setItems(LinkedList<Item> items) {
        this.items = items;
    }

    public Weapon getActualWeapon() {
        return actualWeapon;
    }

    public void setActualWeapon(Weapon actualWeapon) {
        this.actualWeapon = actualWeapon;
    }

    public Deque<Task> getCompletedTasks() {
        return completedTasks;
    }

    public void addCompletedTasks(Task t) {
        completedTasks.push(t);
    }

    public Queue<Task> getTasks() {
        return tasks;
    }

    public void addTasks(Task t) {
        tasks.offer(t);
    }

    public void completeTask(Task t) {
        Queue<Task> aux = new ArrayDeque<>();
        boolean found = false;
        while (!tasks.isEmpty()) {
            Task quest = tasks.poll();
            if (t.getId().equalsIgnoreCase(quest.getId())) {
                found = true;
                completedTasks.push(quest);
            } else {
                aux.offer(quest);
            }

        }
        while (!aux.isEmpty()) {
            tasks.offer(aux.poll());
        }
    }

    public boolean existsPendingTask(Task t) {
        Queue<Task> aux = new ArrayDeque<>();
        boolean found = false;
        while (!tasks.isEmpty()) {
            Task quest = tasks.poll();
            if (t.getId().equalsIgnoreCase(quest.getId())) {
                found = true;
            }
            aux.offer(quest);
        }
        while (!aux.isEmpty()) {
            tasks.offer(aux.poll());
        }
        return found;
    }

    public boolean existsCompletedTask(Task t) {
        Deque<Task> aux = new ArrayDeque<>();
        boolean found = false;
        while (!completedTasks.isEmpty()) {
            Task quest = completedTasks.pop();
            if (t.getId().equalsIgnoreCase(quest.getId())) {
                found = true;
            }
            aux.push(quest);

        }
        while (!aux.isEmpty()) {
            completedTasks.push(aux.pop());
        }
        return found;
    }

    public Task searchPendingTask(Task task) {
        Task t = null;
        Queue<Task> aux = new ArrayDeque<>();
        while (!tasks.isEmpty()) {
            Task quest = tasks.poll();
            if (t.getId().equalsIgnoreCase(quest.getId())) {
                t = quest;
            }
            aux.offer(t);
        }
        while (!aux.isEmpty()) {
            tasks.offer(aux.poll());
        }
        return t;
    }

    public Task searchCompletedTask(Task task) {
        Task t = null;
        Deque<Task> aux = new ArrayDeque<>();
        while (!completedTasks.isEmpty()) {
            Task quest = tasks.poll();
            if (t.getId().equalsIgnoreCase(quest.getId())) {
                t = quest;
            }
            aux.push(quest);
        }
        while (!aux.isEmpty()) {
            completedTasks.push(aux.pop());
        }
        return t;
    }

}
