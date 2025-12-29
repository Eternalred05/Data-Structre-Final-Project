package Characters;

import Items.Weapon;

public class Boss extends Monster {

    public Boss(Weapon actualWeapon, int attack, int defense, String name, String sprite, int life, int actualLife,
            int exp, int money, String encounter) {
        super(actualWeapon, attack, defense, name, sprite, life, actualLife, exp, money, encounter);
    }

}
