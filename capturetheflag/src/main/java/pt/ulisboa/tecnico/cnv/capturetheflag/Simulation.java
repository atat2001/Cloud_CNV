package pt.ulisboa.tecnico.cnv.capturetheflag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class Simulation {
    Grid grid;
    int blueScore;
    int redScore;
    boolean moveDetected;
    List<int[]> blueFlagPositions;
    List<int[]> redFlagPositions;
    List<Agent> blueAgents;
    List<Agent> redAgents;
    int numFlagsPerTeam;

    StringBuilder sb;

    static final int[][] DIRECTIONS = {{-1,0},{1,0},{0,-1},{0,1}};

    public Simulation() {
        this.blueScore = 0;
        this.redScore = 0;
        this.blueFlagPositions = new ArrayList<>();
        this.redFlagPositions = new ArrayList<>();
        this.blueAgents = new ArrayList<>();
        this.redAgents = new ArrayList<>();
        this.sb = new StringBuilder();
    }

    public void init(int gridSize, char flagPlacementType, int numBlueAgents, int numRedAgents, int numFlagsPerTeam, boolean coloredOutput) {
        sb.append("[DEBUG] grid_size = ").append(gridSize).append("; flag_placement_type = ").append(flagPlacementType).append("\n");
        sb.append("[DEBUG] num_blue_agents = ").append(numBlueAgents).append("; num_red_agents = ").append(numRedAgents).append("\n");
        sb.append("\n");

        this.grid = new Grid(gridSize, coloredOutput);
        this.numFlagsPerTeam = numFlagsPerTeam;
        grid.init();
        grid.spawnObstacles();
        grid.spawnFlags(flagPlacementType, this.redFlagPositions, this.blueFlagPositions, numFlagsPerTeam);
        grid.spawnAgents(this.blueAgents, this.redAgents, numBlueAgents, numRedAgents);
    }

    public void run() {
        int round = 0;

        sb.append("[INFO] round = ").append(round).append("; score (blue, red) = (").append(blueScore).append(", ").append(redScore).append(")\n");
        this.grid.printBoard(sb);
        sb.append("\n");

        while (true) {
            round++;
            this.moveDetected = false;
            moveTeam(blueAgents, blueFlagPositions);
            moveTeam(redAgents, redFlagPositions);
            checkDeadlock();
            //this.grid.print(round, blueScore, redScore);
            //Thread.sleep(200);
            if (checkWin()) break;
        }

        this.grid.printBoard(sb);

        sb.append("[INFO] final score (blue, red) = (").append(blueScore).append(", ").append(redScore).append(")\n");
        sb.append("[INFO] num rounds = ").append(round).append("\n");
    }

    public boolean checkWin() {
        return blueScore == numFlagsPerTeam || redScore == numFlagsPerTeam;
    }

    // sanity check
    public void checkDeadlock() {
        if (!this.moveDetected) {
            boolean anyFrozen = true;
            for (Agent a : blueAgents) {
                if (a.cooldown > 0) { anyFrozen = false; break; }
            }
            if (anyFrozen) {
                for (Agent a : redAgents) {
                    if (a.cooldown > 0) { anyFrozen = false; break; }
                }
            }
            if (anyFrozen) {
                sb.append("[WARN] no possible moves for game state:").append("\n");
                for (Agent a : blueAgents) {
                    sb.append("- blue agent @ (").append(a.x).append(",").append(a.y).append("), cooldown = ").append(a.cooldown).append("\n");
                }
                for (Agent a : redAgents) {
                    sb.append("- red agent @ (").append(a.x).append(",").append(a.y).append("), cooldown = ").append(a.cooldown).append("\n");
                }
                throw new RuntimeException("no possible moves");
            }
        }
    }

    public boolean checkCooldown(Agent agent) {
        if (agent.cooldown > 0) {
            agent.tickCooldown();
            if (agent.cooldown == 0) {
                this.grid.markCellWithAgent(agent.x, agent.y, agent.isBlue());
                return true;
            }
            return false;
        }
        return true;
    }

    public void moveTeam(List<Agent> team, List<int[]> flagPositions) {
        for (Agent agent : team) {
            boolean canMove = checkCooldown(agent);
            if (!canMove) continue; // skip this agent

            int[] flagPos = findNearestPosition(agent.x, agent.y, flagPositions);
            if (flagPos == null) continue;

            int[] next = bfsNextStep(agent.x, agent.y, flagPos[0], flagPos[1]);
            if (next == null || this.grid.cellHasAgent(next[0], next[1])) continue;

            this.grid.markCellWithAgentTrace(agent.x, agent.y, agent.isBlue()); // mark agent traces
            agent.setPosition(next[0], next[1]);
            this.moveDetected = true;

            tryCaptureFlag(agent, flagPositions);

            if (agent.hasCooldown()) {
                this.grid.markCellWithAgentOnCooldown(agent.x, agent.y, agent.isBlue());
            } else {
                this.grid.markCellWithAgent(agent.x, agent.y, agent.isBlue());
            }
        }
    }

    public void tryCaptureFlag(Agent agent, List<int[]> flagPositions) {
        Iterator<int[]> it = flagPositions.iterator();
        while (it.hasNext()) {
            int[] p = it.next();
            if (agent.isAtPosition(p[0], p[1])) {
                it.remove();
                if (agent.isBlue()) {
                    this.blueScore++;
                } else {
                    this.redScore++;
                }
                agent.setCooldown();
                break;
            }
        }
    }

    public int[] findNearestPosition(int x, int y, List<int[]> posLst) {
        int best = 2*(this.grid.getSize()-1); // max from corners, e.g., [0, 0] to [n-1, n-1]
        int bx = -1;
        int by = -1;

        for (int[] pos : posLst) {
            // does not take any obstacles into account -- just raw distance
            int dis = Math.abs(pos[0] - x) + Math.abs(pos[1] - y); // Manhattan distance
            if (dis < best) {
                best = dis;
                bx = pos[0];
                by = pos[1];
            }
        }
        if (bx < 0) {
            return null;
        }
        return new int[]{bx, by};
    }

    public int[] bfsNextStep(int sx, int sy, int tx, int ty) {
        int gridSize = this.grid.getSize();
        Queue<int[]> q = new LinkedList<>();
        q.add(new int[]{sx, sy});
        boolean[][] visited = new boolean[gridSize][gridSize];
        visited[sx][sy] = true;

        // helpers for reconstructing path and return next step
        Map<Integer, Integer> parent = new HashMap<>();
        int key = positionToKey(gridSize, sx, sy);
        int value = -1;
        parent.put(key, value);

        while (!q.isEmpty()) {
            int[] c = q.poll();
            if (c[0] == tx && c[1] == ty) break;

            for (int[] dir : DIRECTIONS) {
                int nx = c[0] + dir[0];
                int ny = c[1] + dir[1];

                if (this.grid.inBounds(nx, ny) && !visited[nx][ny]) {
                    // agent cannot step into cell if:
                    // 1. there is as a flag and it is not the target one
                    // 2. there is an agent
                    // 3. there is an obstacle
                    if (this.grid.cellHasFlag(nx, ny) && (nx != tx || ny != ty)
                            || this.grid.cellHasAgent(nx, ny)
                            || this.grid.cellHasObstacle(nx, ny))
                        continue;

                    q.add(new int[]{nx, ny});
                    visited[nx][ny] = true;

                    key = positionToKey(gridSize, nx, ny);
                    value = positionToKey(gridSize, c[0], c[1]);
                    parent.put(key, value);
                }

            }
        }
        if (!visited[tx][ty])
            return null; // no path found

        // go backwards on constructed path and extract next move
        key = positionToKey(gridSize, tx, ty);
        key = findRootPosition(parent, key);
        return keyToPosition(gridSize, key);
    }

    private static int positionToKey(int gridSize, int x, int y) {
        return x * gridSize + y;
    }

    private static int[] keyToPosition(int gridSize, int key) {
        return new int[]{key / gridSize, key % gridSize};
    }

    private static int findRootPosition(Map<Integer, Integer> parent, int key) {
        int p = parent.get(key);
        while (parent.get(p) != -1) {
            key = p;
            p = parent.get(key);
        }
        return key;
    }

    public String getData() {
        return sb.toString();
    }
}
