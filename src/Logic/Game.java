package Logic;

import Items.*;
import Characters.*;
import Misc.*;
import Tree.*;
import Utils.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalDateTime;
import java.util.ArrayList;

public class Game {

    private File save;
    private File archives;
    private Hero hero;
    private ArrayList<NPC> characters;
    private ArrayList<Task> tasks;
    private ArrayList<Item> items;
    private GeneralTree<Classes> classes;
    private LocalDateTime playedTime;

    public Hero getHero() {
        return hero;
    }

    public void createHero(String name) {

        Classes root = ((BinaryTreeNode<Classes>) classes.getRoot()).getInfo();

        hero = new Hero(name, (Weapon) items.get(0), (Armor) items.get(8), root);
        // Para testear el inventario 
        hero.getItems().addLast(items.get(1));
        hero.getItems().addLast(items.get(7));
        hero.getItems().addLast(items.get(7));
        hero.getItems().addLast(items.get(14));
        hero.getItems().addLast(items.get(15));
        hero.getItems().addLast(items.get(16));
        hero.getItems().addLast(items.get(9));
        hero.getItems().addLast(items.get(23));
        hero.getItems().addLast(items.get(24));

        // Para agregar misiones
        hero.addTasks(tasks.get(0));
        hero.addTasks(tasks.get(1));
        hero.addTasks(tasks.get(2));
    }

    public boolean levelUp() {
        boolean leveled = hero.levelUp();
        unlockHeroClassNode();
        return leveled;
    }

    public void unlockHeroClassNode() {
        int level = hero.getLevel();

        if (level % 5 == 0) {
            int nodesToUnlock = level / 5;
            BinaryTreeNode<Classes> targetNode = findNthNode(nodesToUnlock);

            if (targetNode != null && !hero.searchHeroSkillTreeNode(targetNode.getInfo().getId())) {
                insertNodeInHeroTree(targetNode);
            }
        }
    }

    private BinaryTreeNode<Classes> findNthNode(int n) {
        InBreadthIterator<Classes> it = classes.inBreadthIterator();
        int counter = 0;
        BinaryTreeNode<Classes> result = null;

        while (it.hasNext() && result == null) {
            BinaryTreeNode<Classes> node = it.nextNode();
            if (counter == n) {
                result = node;
            }
            counter++;
        }

        return result;
    }

    private void insertNodeInHeroTree(BinaryTreeNode<Classes> nodeToInsert) {
        BinaryTreeNode<Classes> parentGame = classes.getFather(nodeToInsert);
        BinaryTreeNode<Classes> parentHero = findNodeById(parentGame.getInfo().getId());

        if (parentHero != null) {
            hero.getUnlockedClasses().insertNode(new BinaryTreeNode<>(nodeToInsert.getInfo()), parentHero);
        }
    }

    private BinaryTreeNode<Classes> findNodeById(String id) {
        InBreadthIterator<Classes> it = hero.getUnlockedClasses().inBreadthIterator();
        BinaryTreeNode<Classes> result = null;

        while (it.hasNext() && result == null) {
            BinaryTreeNode<Classes> node = it.nextNode();
            if (node.getInfo().getId().equalsIgnoreCase(id)) {
                result = node;
            }
        }

        return result;
    }

    public LocalDateTime getPlayedTime() {
        return playedTime;
    }

    public void setPlayedTime(LocalDateTime playedTime) {
        this.playedTime = playedTime;
    }

    public File getSave() {
        return save;
    }

    public void setSave(File save) {
        this.save = save;
    }

    public ArrayList<NPC> getCharacters() {
        return characters;
    }

    public void addCharacters(NPC npc) {
        characters.add(npc);
    }

    public ArrayList<Task> getTasks() {
        return tasks;
    }

    public void addTasks(Task t) {
        tasks.add(t);
    }

    public ArrayList<Item> getItems() {
        return items;
    }

    public void addItems(Item i) {
        items.add(i);
    }

    public File getArchives() {
        return archives;
    }

    public void setArchives(File archives) {
        this.archives = archives;
    }

    public GeneralTree<Classes> getClasses() {
        return classes;
    }

    public void setClasses(GeneralTree<Classes> classes) {
        this.classes = classes;
    }

    public Game() {
        this.save = new File("C:\\Ale\\Codigos\\Filetest", "file0.sav");
        this.archives = new File("C:\\Ale\\Codigos\\Filetest", "file.sav");

        File parent = save.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        this.characters = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.items = new ArrayList<>();
        this.classes = new GeneralTree<>();

        createClassTree();
    }

    public ArrayList<Weapon> getHeroWeapons() {
        ArrayList<Weapon> weapons = new ArrayList<>();
        for (Item i : hero.getItems()) {
            if (i instanceof Weapon) {
                weapons.add((Weapon) i);
            }
        }
        return weapons;
    }

    public ArrayList<Wares> getHeroWares() {
        ArrayList<Wares> wares = new ArrayList<>();
        for (Item i : hero.getItems()) {
            if (i instanceof Wares) {
                wares.add((Wares) i);
            }
        }
        return wares;
    }

    public ArrayList<Armor> getHeroArmors() {
        ArrayList<Armor> armors = new ArrayList<>();
        for (Item i : hero.getItems()) {
            if (i instanceof Armor) {
                armors.add((Armor) i);
            }
        }
        return armors;
    }

    public ArrayList<String> inventoryNames(ArrayList<Item> inventory) {
        ArrayList<String> names = new ArrayList<>();
        for (Item i : inventory) {
            names.add(i.getName());
        }
        return names;
    }

    public boolean equipWeapon(Weapon w) {
        boolean equiped = false;
        boolean canEquip = hero.searchHeroSkillTreeNode(w.getType());
        if (canEquip) {
            equiped = true;
            hero.getItems().addLast(hero.getActualWeapon());
            hero.setActualWeapon(w);
            hero.getItems().remove(w);
        }
        return equiped;
    }

    public boolean equipArmor(Armor a) {
        hero.getItems().addLast(hero.getArmor());
        boolean equiped = hero.setArmor(a);
        hero.getItems().remove(a);
        return equiped;
    }

    public boolean createSaveGame() {
        boolean created = false;

        File parent = save.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (RandomAccessFile raf = new RandomAccessFile(save, "rw")) {
            byte[] data = Convert.toBytes(hero);
            raf.writeInt(data.length);
            raf.write(data);
            created = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return created;
    }

    public boolean readSaveGame() {
        boolean correct = false;
        try (RandomAccessFile raf = new RandomAccessFile(save, "rw")) {
            int len = raf.readInt();
            byte[] data = new byte[len];
            raf.readFully(data);
            hero = (Hero) Convert.toObject(data);
            correct = true;
            raf.close();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return correct;
    }

    public boolean deleteSaveGame() {
        boolean correct = false;
        if (save.exists()) {
            correct = save.delete();
        }
        return correct;
    }

    public boolean combat(Monster m) {
        int attack = m.getAttack() + m.getActualWeapon().getAttack();
        int defense = hero.getDefense() + hero.getArmor().getDefense();
        int damage = (attack - defense);
        boolean attacked = false;
        if (damage > 0) {
            int life = (hero.getActualLife()) - damage;
            if (life < 0) {
                hero.setActualLife(0);
            } else {
                hero.setActualLife(life);
            }
            attacked = true;
        }
        return attacked;
    }

    public boolean heroCombat(Monster m) {
        int attack = hero.getAttack() + hero.getActualWeapon().getAttack();
        int defense = m.getDefense();
        int damage = (attack - defense);
        boolean attacked = false;
        if (damage > 0) {
            int life = (m.getActualLife()) - damage;
            if (life < 0) {
                m.setActualLife(0);
            } else {
                m.setActualLife(life);
            }
            attacked = true;
        }
        return attacked;
    }

    public boolean checkGameOver(int life) {
        boolean dead = false;
        if (life == 0) {
            dead = true;
        }
        return dead;

    }

    public boolean heal(Wares w) {
        boolean cured = false;
        if (hero.getActualLife() < hero.getLife()) {
            if (hero.getLife() < w.getHealing() + hero.getActualLife()) {
                hero.setActualLife(hero.getLife());

            } else {
                hero.setActualLife(hero.getActualLife() + w.getHealing());
            }
            cured = true;
        }
        return cured;
    }
    
    public void createItems() {
        //Weapons
        items.add(new Fist("Your hands, it is the easiest way to attack!", "Bare Hands", "H000", 5, 120000, "Inflicts damage.", -1));
        items.add(new Sword("Basic Sword made of old trees.", "Wooden Sword", "SW000", 9, 120000, "Hard but cuts", 30));
        items.add(new Spell("Basic thrown magic with hands", "Basic Spell", "H001", 6, 10, "It can attack", 50));
        items.add(new Gun("It shoots.", "Desert Eagle", "GUN01", 20, 100, "Inflict damage.",
                "A", 30, 50.0));
        items.add(new Sword("It cuts.", "Guardian Sword", "SW01", 30, 100, "Inflicts damage.", 100));
        items.add(new Spear("It drills.", "Guardian Spear", "SP000", 18, 100, "Inflicts damage.", 100));
        items.add(new Claymore("An old weapon belonging to the royal guard of the kingdom.", "Royal Claymore", "CLY01", 100, 100, "Inflicts damage", 300));
        items.add(new Wares("It cures.", "Healing Bandages", "WS01", 50, 10));
        items.add(new Armor("Basic Armor", "Broken Cloath", "A000", 5, "Offers extra Defense", 5));
        items.add(new Fist("Monster Claws to scratch the enemy", "Claws", "H002", 13, 120000, "Inflicts damage by scratching.", 15));
        items.add(new Spear("A punzanct surface weapon", "Spike", "SP001", 6, 2, "It can hurt a bit more on some attacks", 10));
        items.add(new Spell("Dark magic spells", "Basic Spell", "H003", 6, 10, "It can attack launching shadow balls", 25));
        items.add(new Fist("Tentacles to constrict the enemy", "Tentacles", "H004", 14, 120000, "Inflicts damage by constriction.", 10));
        items.add(new Spell("Fire throwing spell", "Flamethrower", "H005", 12, 15, "Inflicts damage by burning.", 120));
        items.add(new Armor("Basic Armor", "Adventurer´s Cloath", "A001", 9, "Worn by a mysterious adventurer that once saved the Kingdom.", 45));
        //    Weapon w = new Weapon(info, name, id, 0, 0, effect,cost) 

        //Healing items
        items.add(new Wares("It cures.", "Ultra Potion", "P000", 75, 120));
        items.add(new Wares("It cures.", "Sacred Potion", "P001", 100, 200));
        items.add(new Wares("It cures.", "Blueberry", "B000", 10, 15));
        items.add(new Wares("It cures.", "Raspberry", "B001", 25, 25));
        //Wares(String description, String name, String id, int healing,int cost)

        //Key Items
        items.add(new KeyItem("A mysterious orb found in the Swamp.", "Toxic Orb.", "K000"));
        items.add(new KeyItem("A mysterious orb found in the Volcano.", "Lava Orb.", "K001"));
        items.add(new KeyItem("Use it to open a door.", "Key.", "K002"));
        items.add(new KeyItem("The Village's Mayor authorization.", "Mayor's letter.", "K003"));
        //KeyItem(String info, String name, String id)

        //More armor
        items.add(new Armor("Stronger armor", "Steel Plate", "A002", 14, "A sturdy armor forged by a great smith.", 200));
        items.add(new Armor("Stronger armor", "Sapphire Plate", "A003", 20, "Majestic armor worn by the most royal soldiers.", 500));
        //Armor(String info, String name, String id ,int defense, String effect)

    }

    public void createMonsters() {
        //Overworld
        characters.add(new Monster((Weapon) items.get(2), 2, 5, "Gnome", "/Resources/sprites/Monsters/goblin01.png", 20, 20, 20, 25, "Overworld"));
        characters.add(new Monster((Weapon) items.get(1), 4, 3, "Goblin", "/Resources/sprites/Monsters/elf01.png", 25, 20, 15, 25, "Overworld"));
        characters.add(new Monster((Weapon) items.get(0), 3, 3, "Mystical Crab", "/Resources/sprites/Monsters/crab01.png", 20, 20, 12, 25, "Overworld"));
        characters.add(new Monster((Weapon) items.get(10), 5, 4, "Mechanical Bee", "/Resources/sprites/Monsters/fieldBee.png", 55, 55, 25, 50, "Overworld"));
        characters.add(new Monster((Weapon) items.get(2), 6, 4, "Foongus", "/Resources/sprites/Monsters/fieldFoongus.png", 40, 40, 20, 35, "Overworld"));
        characters.add(new Monster((Weapon) items.get(9), 2, 1, "Messi", "/Resources/sprites/Monsters/messi.png", 10, 10, 10, 15, "Overworld"));
        characters.add(new Monster((Weapon) items.get(9), 3, 3, "Dark Crow", "/Resources/sprites/Monsters/Crow.png", 75, 75, 50, 100, "Overworld"));
        //Swamp
        characters.add(new Monster((Weapon) items.get(9), 12, 7, "Zombie Dog", "/Resources/sprites/Monsters/swampMonster00.png", 75, 75, 50, 100, "Swamp"));
        characters.add(new Monster((Weapon) items.get(0), 9, 4, "Zombie", "/Resources/sprites/Monsters/swampMonster02.png", 100, 100, 55, 100, "Swamp"));
        characters.add(new Monster((Weapon) items.get(9), 15, 9, "Shadow Fiend", "/Resources/sprites/Monsters/swampMonster01.png", 87, 87, 70, 120, "Swamp"));
        characters.add(new Monster((Weapon) items.get(11), 14, 12, "Pot Fiend", "/Resources/sprites/Monsters/swampMonster04.png", 52, 52, 60, 125, "Swamp"));
        characters.add(new Monster((Weapon) items.get(9), 12, 7, "Toxic Lizard", "/Resources/sprites/Monsters/swampMonster03.png", 75, 75, 50, 100, "Swamp"));
        characters.add(new Boss((Weapon) items.get(9), 32, 15, "Venom Demon King", "/Resources/sprites/Monsters/swampBoss01.png", 280, 180, 200, 800, "SwampBoss"));
        //Volcano
        characters.add(new Monster((Weapon) items.get(13), 18, 10, "Salaflamender", "/Resources/sprites/Monsters/volcano00.png", 105, 105, 100, 150, "Volcano"));
        characters.add(new Monster((Weapon) items.get(9), 16, 13, "Dragon Egg", "/Resources/sprites/Monsters/volcano01.png", 110, 110, 95, 140, "Volcano"));
        characters.add(new Monster((Weapon) items.get(9), 20, 14, "Figoat", "/Resources/sprites/Monsters/volcano02.png", 105, 105, 100, 160, "Volcano"));
        characters.add(new Monster((Weapon) items.get(4), 18, 15, "Stone Soldier", "/Resources/sprites/Monsters/volcano03.png", 110, 110, 110, 170, "Volcano"));
        characters.add(new Monster((Weapon) items.get(13), 50, 50, "Phoenix", "/Resources/sprites/Monsters/volcanoBoss00.png", 300, 300, 300, 300, "Boss"));
        characters.add(new Monster((Weapon) items.get(13), 62, 90, "Fire Demon King", "/Resources/sprites/Monsters/volcanoBoss01.png", 500, 500, 500, 1500, "Boss"));
        //Sky
        characters.add(new Monster((Weapon) items.get(12), 21, 15, "Fly Sight", "/Resources/sprites/Monsters/skyMonster00.png", 260, 260, 290, 270, "Sky"));
        characters.add(new Monster((Weapon) items.get(9), 24, 12, "Dragon Minion", "/Resources/sprites/Monsters/skyMonster01.png", 295, 225, 320, 310, "Sky"));
        characters.add(new Monster((Weapon) items.get(11), 26, 10, "Novel Sorcerer", "/Resources/sprites/Monsters/skyMonster02.png", 280, 220, 220, 300, "Sky"));
        characters.add(new Monster((Weapon) items.get(2), 20, 14, "Flower Fairy", "/Resources/sprites/Monsters/skyMonster03.png", 290, 290, 200, 180, "Sky"));
        characters.add(new Monster((Weapon) items.get(2), 25, 14, "Demon Harpy", "/Resources/sprites/Monsters/skyMonster04.png", 320, 320, 250, 180, "Sky"));
        characters.add(new Monster((Weapon) items.get(13), 85, 90, "Light Rider", "/Resources/sprites/Monsters/skyBoss01.png", 1500, 1500, 1500, 1000, "SkyBoss"));
        characters.add(new Monster((Weapon) items.get(13), 125, 100, "Count Artigas", "/Resources/sprites/Monsters/finalBoss.png", 3500, 3500, 2500, 10500, "SkyBoss"));

        //Monster m = new Monster(weapon, attack, defense, name, sprite, life, actualLife, exp, money, encounter)
    }

    public void createClassTree() {

        BinaryTreeNode<Classes> warrior = new BinaryTreeNode<>(
                new WarriorClass("The basic class. Hits using fists and Hand to hand combat.", true, "fist"));
        BinaryTreeNode<Classes> swordman = new BinaryTreeNode<>(
                new SwordmanClass("Attacks using basic swords. Can be upgraded to anothers blades throug progression.", false, "sword"));
        BinaryTreeNode<Classes> spearman = new BinaryTreeNode<>(
                new SpearClass("Attacks using a spear. Can be upgraded to anothers spears-like weapons with progress.", false, "spear"));
        BinaryTreeNode<Classes> gunner = new BinaryTreeNode<>(
                new GunnerClass("Attacks using a gun. Can be upgraded to anothers fire weapons eventually.", false, "gun"));
        BinaryTreeNode<Classes> claymoreUser = new BinaryTreeNode<>(
                new ClaymoreUserClass("Hits using claymore-swords.", false, "claymore"));
        BinaryTreeNode<Classes> sabreUser = new BinaryTreeNode<>(
                new SaberUserClass("Hits using a saber.", false, "saber"));
        BinaryTreeNode<Classes> rifleUser = new BinaryTreeNode<>(
                new RifleUserClass("Hits using a rifle.", false, "rifle"));
        BinaryTreeNode<Classes> shotgunUser = new BinaryTreeNode<>(
                new ShotgunUserClass("Hits using a shotgun.", false, "shotgun"));
        BinaryTreeNode<Classes> halberdUser = new BinaryTreeNode<>(
                new HalberdUserClass("Hits using a halberd.", false, "halberd"));
        BinaryTreeNode<Classes> pikeUser = new BinaryTreeNode<>(
                new PikeUserClass("Hits using a pike.", false, "pike"));
        BinaryTreeNode<Classes> Magician = new BinaryTreeNode<>(
                new PikeUserClass("Hits using spells.", false, "spell"));
        BinaryTreeNode<Classes> wands = new BinaryTreeNode<>(
                new PikeUserClass("Hits using spells with wands", false, "wand"));
        BinaryTreeNode<Classes> healing = new BinaryTreeNode<>(
                new PikeUserClass("Lets you heal", false, "healingSpell"));
        //Classes(String description, boolean unlocked, String id)

        classes.setRoot(warrior);

        classes.insertAsFirstSon(swordman, warrior);
        classes.insertAsFirstSon(claymoreUser, swordman);
        classes.insertNode(sabreUser, swordman);

        classes.insertNode(spearman, warrior);
        classes.insertAsFirstSon(halberdUser, spearman);
        classes.insertNode(pikeUser, spearman);

        classes.insertNode(gunner, warrior);
        classes.insertAsFirstSon(shotgunUser, gunner);
        classes.insertNode(rifleUser, gunner);

        classes.insertNode(Magician, warrior);
        classes.insertAsFirstSon(wands, Magician);
        classes.insertNode(healing, Magician);

    }

    public void createTasks() {
        //Main Quests
        addTasks(new Task("Explore the Misterious Sky Ruins.", "Search what is hidden in the Sky Island.", "M000", 10000, true));
        addTasks(new Task("Search the Toxic Orb.", "Complete the road of Swamp.", "M001", 1000, true));
        addTasks(new Task("Search the Lava Orb.", "Complete the road of Volcano.", "M002", 1000, true));
        addTasks(new Task("Battle against the swamp boss.", "Defeat the Swamp Boss Monster to get the toxic orb.", "M003", 0, true));
        addTasks(new Task("Get the Mayor's authorization.", "Talk with Village's Mayor.", "M003", 500, true));
        //Secondary Quests
        addTasks(new Task("Defeat 5 monsters.", "Kill 5 monsters anywhere.", "Q000", 500, false));
        addTasks(new Task("Defeat 30 monsters.", "Kill 30 monsters anywhere.", "Q001", 1000, false));
        addTasks(new Task("Heal yourself.", "Use a healing item once.", "Q002", 100, false));
        //Task(String name, String info, String id, int money, boolean mainQuest)
    }

    public Task searchTask(String id) {
        Task t = null;
        for (Task task : tasks) {
            if (t.getId().equals(task.getId())) {
                t = task;
            }
        }

        return t;
    }

    public void giveReward(boolean reward, Task t) {
        if (reward) {
            for (Item i : t.getRewards()) {
                hero.getItems().add(i);
            }
        }
    }

    public void completeSecondaryQ000() {
        if (hero.getDefeatedMonsters() >= 5) {
            hero.completeTask(searchTask("Q000"));
        }
    }

    public void completeMainQ003() {

    }
}
