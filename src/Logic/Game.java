package Logic;

import Items.*;
import Characters.*;
import Misc.*;
import cu.edu.cujae.ceis.tree.general.GeneralTree;
import Utils.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;

public class Game {

    private File save;
    private File archives;
    private Hero hero;
    private ArrayList<NPC> characters;
    private Queue<Task> tasks;
    private ArrayList<Item> items;
    private GeneralTree<Classes> classes;
    private LocalDateTime playedTime;

    public Hero getHero() {
        return hero;
    }

    public void createHero(String name) {
        hero = new Hero(name);

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

    public Queue<Task> getTasks() {
        return tasks;
    }

    public void addTasks(Task t) {
        tasks.offer(t);
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
        this.characters = new ArrayList<>();
        this.tasks = new ArrayDeque<>();
        this.items = new ArrayList<>();
        this.classes = new GeneralTree<>();
    }

    public boolean createSaveGame() {
        boolean created = false;
        try {
            RandomAccessFile raf = new RandomAccessFile(save, "rw");
            byte[] data = Convert.toBytes(hero);
            raf.write(data.length);
            raf.write(data);
            created = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return created;
    }

    public boolean readSaveGame() {
        boolean correct = false;
        try {
            RandomAccessFile raf = new RandomAccessFile(save, "rw");
            int len = raf.readInt();
            byte[] data = new byte[len];
            raf.read(data);
            hero = (Hero) Convert.toObject(data);
            correct = true;

        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return correct;
    }

}
