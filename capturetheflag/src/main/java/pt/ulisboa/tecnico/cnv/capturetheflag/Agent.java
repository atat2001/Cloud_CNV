package pt.ulisboa.tecnico.cnv.capturetheflag;

public class Agent {
    int x, y;
    int cooldown = 0;
    boolean blue;
    int CAPTURE_COOLDOWN = 5;

    public Agent(int x, int y, boolean isBlue) {
        this.x = x;
        this.y = y;
        this.cooldown = 0;
        this.blue = isBlue;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean isAtPosition(int x, int y) {
        return this.x == x && this.y == y;
    }

    public boolean isBlue() {
        return this.blue;
    }

    public boolean hasCooldown() {
        return this.cooldown > 0;
    }

    public boolean cooldownIsZero() {
        return this.cooldown == 0;
    }

    public void setCooldown() {
        this.cooldown = CAPTURE_COOLDOWN;
    }

    public void tickCooldown() {
        this.cooldown--;
    }
}
