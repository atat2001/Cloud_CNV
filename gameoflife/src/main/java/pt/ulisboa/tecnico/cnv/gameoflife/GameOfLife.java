package pt.ulisboa.tecnico.cnv.gameoflife;

import java.io.IOException;

/**
 * Inspired by: https://www.algosome.com/articles/conway-game-of-life-2d.html
 * Read more details and rules here: https://en.wikipedia.org/wiki/Conway%27s_Game_of_Life
 */
public class GameOfLife {

    private final static byte ALIVE = 1;
    private final static byte DEAD = 0;

    private final int width;
    private final int height;
    private final int size;
    /**
     * Data representing the grid in 1d format. Specified as byte to allow for multiple phases.
     */
    private byte[] data;

    /**
     * Constructs a new Game of Life with the specified dimensions.
     */
    public GameOfLife(int width, int height, byte[] data) {
        this.width = width;
        this.height = height;
        this.size = width * height;
        this.data = data;
    }

    public void play(int iterations) {
        for (int i = 0; i < iterations; i++) {
            iterate();
        }
    }

    public void playCLI(int iterations) {
        System.out.println("Press Enter to make a step.");
        for (int i = 0; i < iterations; i++) {
            System.out.println(gridToString());
            iterate();
            try {
                System.in.read();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Iterates the game one step forward.
     */
    private void iterate() {
        byte[] next = new byte[size];
        for (int col = 0; col < width; col++) {
            for (int row = 0; row < height; row++) {
                next[row * width + col] = isAlive(col, row, data);
            }
        }
        System.arraycopy(next, 0, data, 0, size);
    }

    /**
     * Checks if the cell is alive.
     */
    private byte isAlive(int x, int y, byte[] d) {
        int aliveNeighbors = 0;
        int currentCellPos = y * width + x;
        for (int col = x - 1; col <= x + 1; col++) {
            for (int row = y - 1; row <= y + 1; row++) {
                int neighborPos = row * width + col;
                // To connect edges.
                neighborPos = (((neighborPos % size) + size) % size);
                if (neighborPos != currentCellPos && d[neighborPos] == ALIVE) {
                    aliveNeighbors++;
                }
            }
        }

        // If dead.
        if (d[currentCellPos] == DEAD) {
            if (aliveNeighbors == 3) {
                // Make alive.
                return ALIVE;
            }
            // Keep dead.
            return DEAD;
        } else { // If alive.
            if (aliveNeighbors < 2 || aliveNeighbors > 3) {
                // Make dead.
                return DEAD;
            }
            // Keep alive.
            return ALIVE;
        }
    }

    public String gridToString() {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                sb.append(data[row * width + col]);
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString().replace("1", " +").replace("0", " -"); // You can also use ⬛ ⬜.
    }

    public byte[] getData() {
        return data;
    }
}
