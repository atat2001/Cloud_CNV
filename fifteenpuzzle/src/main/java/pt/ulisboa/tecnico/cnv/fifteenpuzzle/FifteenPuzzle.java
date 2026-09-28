package pt.ulisboa.tecnico.cnv.fifteenpuzzle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class FifteenPuzzle {
    private final int size;
    final int[] tiles;
    private final int displayWidth;
    private int blankPos;
    private static class BoardState {
        final int[] tiles;
        final int blankPos;

        BoardState(int[] tiles, int blankPos) {
            this.tiles = tiles;
            this.blankPos = blankPos;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BoardState)) return false;
            BoardState that = (BoardState) o;
            return Arrays.equals(this.tiles, that.tiles);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(tiles);
        }
    }

    public FifteenPuzzle(int size) {
        this.size = size;
        this.tiles = new int[size * size];
        int cnt = 1;
        for (int i = 0; i < size * size - 1; i++) {
            tiles[i] = cnt++;
        }
        tiles[size * size - 1] = 0;
        blankPos = size * size - 1;
        displayWidth = Integer.toString(size * size).length();
    }

    public FifteenPuzzle(int[] tiles, int blankPos, int size) {
        this.size = size;
        this.tiles = Arrays.copyOf(tiles, tiles.length);
        this.blankPos = blankPos;
        this.displayWidth = Integer.toString(size * size).length();
    }

    public void shuffle(int moves, Random random) {
        int prevDir = -1; // -1 = none, 0 = up, 1 = down, 2 = left, 3 = right
    
        for (int i = 0; i < moves; i++) {
            List<Integer> valid = new ArrayList<>();
            int row = blankPos / size;
            int col = blankPos % size;
    
            if (row > 0 && prevDir != 1) valid.add(0); // UP
            if (row < size - 1 && prevDir != 0) valid.add(1); // DOWN
            if (col > 0 && prevDir != 3) valid.add(2); // LEFT
            if (col < size - 1 && prevDir != 2) valid.add(3); // RIGHT
    
            int dir = valid.get(random.nextInt(valid.size()));
            int newBlankPos;
            switch (dir) {
                case 0: 
                    newBlankPos = blankPos - size; // up
                    break;
                case 1: 
                    newBlankPos = blankPos + size; // down
                    break;
                case 2: 
                    newBlankPos = blankPos - 1; // left
                    break;
                case 3: 
                    newBlankPos = blankPos + 1; // right
                    break;
                default: 
                    throw new IllegalStateException();
            }
    
            tiles[blankPos] = tiles[newBlankPos];
            tiles[newBlankPos] = 0;
            blankPos = newBlankPos;
            prevDir = dir;
        }
    }

    private List<Integer> validMoves(int blankPos) {
        List<Integer> moves = new ArrayList<>();
        int x = blankPos / size;
        int y = blankPos % size;
        int[][] directions = {{-1,0}, {1,0}, {0,-1}, {0,1}};
        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                moves.add(nx * size + ny);
            }
        }
        return moves;
    }

    public String getData() {
        StringBuilder sb = new StringBuilder();
        sb.append("-".repeat(size * (displayWidth + 3) + 1)).append("\n");
        for (int i = 0; i < size; i++) {
            sb.append("| ");
            for (int j = 0; j < size; j++) {
                int val = tiles[i * size + j];
                String s = (val == 0) ? "" : Integer.toString(val);
                while (s.length() < displayWidth) s = " " + s;
                sb.append(s).append(" | ");
            }
            sb.append("\n");
        }
        sb.append("-".repeat(size * (displayWidth + 3) + 1)).append("\n");
        return sb.toString();
    }

    private boolean isSolved(int[] board) {
        for (int i = 0; i < size * size - 1; i++) {
            if (board[i] != i + 1) return false;
        }
        return board[size * size - 1] == 0;
    }

    int manhattanDistance(int[] board) {
        int sum = 0;
        for (int i = 0; i < board.length; i++) {
            int val = board[i];
            if (val != 0) {
                int targetX = (val - 1) / size;
                int targetY = (val - 1) % size;
                int currX = i / size;
                int currY = i % size;
                sum += Math.abs(currX - targetX) + Math.abs(currY - targetY);
            }
        }
        return sum;
    }

    public List<FifteenPuzzle> idaStarSolve() {
        BoardState start = new BoardState(this.tiles, this.blankPos);
        int threshold = manhattanDistance(start.tiles);
        List<BoardState> path = new ArrayList<>();
        path.add(start);
    
        while (true) {
            Set<BoardState> visited = new HashSet<>();
            int temp = idaSearch(path, 0, threshold, visited);
            if (temp == -1) {
                // Found goal
                List<FifteenPuzzle> result = new ArrayList<>();
                for (BoardState state : path) {
                    result.add(new FifteenPuzzle(state.tiles, state.blankPos, size));
                }
                return result;
            }
            if (temp == Integer.MAX_VALUE) {
                return null; // No solution
            }
            threshold = temp;
        }
    }
    
    private int idaSearch(List<BoardState> path, int g, int threshold, Set<BoardState> visited) {
        BoardState current = path.get(path.size() - 1);
        int f = g + manhattanDistance(current.tiles);
        if (f > threshold) return f;
        if (isSolved(current.tiles)) return -1;
    
        visited.add(current);
        int min = Integer.MAX_VALUE;
        for (int move : validMoves(current.blankPos)) {
            int[] newTiles = Arrays.copyOf(current.tiles, current.tiles.length);
            newTiles[current.blankPos] = newTiles[move];
            newTiles[move] = 0;
            BoardState neighbor = new BoardState(newTiles, move);
    
            if (visited.contains(neighbor)) continue;
    
            path.add(neighbor);
            int temp = idaSearch(path, g + 1, threshold, visited);
            if (temp == -1) return -1;
            if (temp < min) min = temp;
            path.remove(path.size() - 1);
        }
        visited.remove(current);
        return min;
    }

    public static FifteenPuzzle fromArray(int[] board, int size) {
        FifteenPuzzle puzzle = new FifteenPuzzle(size);
        System.arraycopy(board, 0, puzzle.tiles, 0, board.length);
        for (int i = 0; i < board.length; i++) {
            if (board[i] == 0) {
                puzzle.blankPos = i;
                break;
            }
        }
        return puzzle;
    }

    static String getSolutionData(List<FifteenPuzzle> solution) {
        if (solution == null) return "No solution found.";
        return "\nSolution found in " + (solution.size() - 1) + " moves.";
        // Optional: Uncomment to show each step of the solution
        //for (FifteenPuzzle step : solution) {
        //		sb.append(step.getData());
        //}
    }
} 
