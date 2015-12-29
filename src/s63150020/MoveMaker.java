package s63150020;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import skupno.Polje;


/**
 * Magic.
 */
public class MoveMaker {

    /**
     * Time treshold for falling back to emergency strategy.
     * In milliseconds.
     */
    private final int TIME_CRITICAL = 500;

    private final int MAX_DEPTH = 30;

    /**
     * Boolean indicating whether the player tries to connect edges vertically.
     */
    private boolean playsVertically;

    /**
     * Game board.
     */
    private Board board;

    /**
     * Dimensions of the game board.
     */
    private int boardDimensions;

   /**
    * RNG for Monte Carlo-based algorithm
    */
    private Random generator;


    /**
     * Indicates whether fields (center, center), (-1, 1) and (1, -1) are still free.
     */
    private boolean strongPointsFree = true;


    public MoveMaker(Board board, boolean playsVertically) {
        this.board = board;
        this.boardDimensions = board.getDimensions();
        this.playsVertically = playsVertically;
        this.generator = new Random();
    }

    /**
     * Chooses the next move by using predefined presets or using MC-UCT algorithm.
     *
     * Note: If move is made in under 50 ms, supervisor does not substract time!
     */
    public Polje nextMove(long remainingTime) {
        Polje move = null;

        if(strongPointsFree) {
            move = makeFirstMove();
            if(move != null) {
                return move;
            }
        }

        if((remainingTime < TIME_CRITICAL) && (board.getNFree() > 50)) {
            move = makeRandom();
        } else if(remainingTime < TIME_CRITICAL) {
            move = calculateMove(45);
        } else {
            int allocatedTime = calcAllocatedTime((int) remainingTime);
            move = monteCarlo(allocatedTime);
        }

        assert(move != null);
        return move;
    }

    private Polje makeFirstMove() {
        Polje move;

        int centerY = boardDimensions / 2; 
        int centerX = centerY;
        if((boardDimensions % 2) == 0) {
            centerY--;
        }
        if(board.isFree(centerY, centerX)) {
            move = board.play(Owner.Me, centerY, centerX);
        /*
        } else if(boardDimensions > 6) {
            strongPointsFree = false;
            return null;
        } else if(board.isFree(1, -1)) {
            move = board.play(Owner.Me, 1, -1);
        } else if(board.isFree(-1, 1)) {
            move = board.play(Owner.Me, -1, 1);
        */
        } else {
            strongPointsFree = false;
            return null;
        }

        int x = move.vrniStolpec();
        int y = move.vrniVrstico();

        board.play(Owner.AssumePlayed, y - 1, x);
        board.play(Owner.AssumePlayed, y - 1, x + 1);
        board.play(Owner.AssumePlayed, y + 1, x);
        board.play(Owner.AssumePlayed, y + 1, x - 1);

        return move;
    }

    private Polje makeRandom() {
        Field move;

        ArrayList<Field> assumed = board.getAssumePlayed();
        if(assumed.size() > 0) {
            int randomIdx = generator.nextInt(assumed.size()); 
            move = assumed.get(randomIdx);
        } else {
            ArrayList<Field> free = board.getFreeFields();
            int randomIdx = generator.nextInt(free.size()); 
            move = free.get(randomIdx);
        }

        move.setOwner(Owner.Me);
        return move.toPolje();
    }

    private Polje calculateMove(int allocatedTime) {
        ArrayList<Field> empty = board.getFreeFields();
        int[] gamesWon = new int[empty.size()];
        int played = 0;

        long start = System.currentTimeMillis();
        for(; played < 10000; played++) {
            for(int i = 0; i < gamesWon.length; i++) {

                boolean won = runSim(empty.get(i));
                if(won) {
                    gamesWon[i]++;
                }
            }

            if((System.currentTimeMillis() - start) > allocatedTime) {
                break;
            }
        }

        Logger.log("Time: %4d / %4d, nPasses: %d", System.currentTimeMillis() - start, allocatedTime, played);

        ArrayList<Integer> list = Utilities.findMax(gamesWon);
        Field best = empty.get(list.get(generator.nextInt(list.size())));

        best.setOwner(Owner.Me);
        return best.toPolje();
    }

    private boolean runSim(Field nextMove) {
        board.resetSim();

        nextMove.simOwner(Owner.Me);
        ArrayList<Field> options = board.getFreeFields(true);

        boolean myTurn = false;
        while(options.size() > 0) {
            int choice = generator.nextInt(options.size());
            Field move = options.get(choice);
            options.remove(choice);
            move.simOwner(myTurn ? Owner.Me : Owner.Other);

            myTurn = !myTurn;
        }

        return board.edgesConnected(Owner.Me, true);
    }

    private Polje monteCarlo(int allocatedTime) {
        long nano = System.nanoTime();
        long start = System.currentTimeMillis();
        Node root = new Node(null, null);
        root.createChildren(board.getFreeFields());

        int playouts = 0;
        while((System.currentTimeMillis() - start) < allocatedTime) {
            monteCarloUctSearch(root);
            playouts++;
        }

        Field best = root.getBestMove();
        best.setOwner(Owner.Me);

        long elapsed = System.nanoTime() - nano;
        Logger.log("Found best move. Took %d ms for %d playouts (%d ns/p)", elapsed / 1000000, playouts, elapsed / playouts);
        return best.toPolje();
    }

    private void monteCarloUctSearch(Node root) {
        board.resetSim();

        Node next = root;
        boolean playerMoves = true;
        int depth = 0;
        while(next.hasChildren() && (depth <= MAX_DEPTH)) {
            next = next.chooseChild(generator);
            next.getMove().simOwner(playerMoves ? Owner.Me : Owner.Other);
            playerMoves = !playerMoves;
            depth++;
        }

        if(!next.hasChildren()) {
            next.createChildren(board.getFreeFields(true));
        }
        next.getMove().simOwner(playerMoves ? Owner.Me : Owner.Other);
        playerMoves = !playerMoves;

        int status = playout(playerMoves);
        next.updateScore(status);
    }

    private int playout(boolean playerMoves) {
        boolean myTurn = playerMoves;
        ArrayList<Field> options = board.getFreeFields(true);

        while(options.size() > 0) {
            int choice = generator.nextInt(options.size());
            Field move = options.get(choice);
            options.remove(choice);
            move.simOwner(myTurn ? Owner.Me : Owner.Other);

            myTurn = !myTurn;
        }

        return board.edgesConnected(Owner.Me, true) ? 1 : 0;
    }

    private int calcAllocatedTime(int remaining) {
        int free = board.getNFree();

        int t = (int) Math.sqrt((remaining * remaining) / (free * Math.sqrt(free * 4 / 7)));
        return t * 6 / 3;

        // Pretty good:
        //int t = (int) Math.sqrt((remaining * remaining) / (free * free));
        //return t * 10;
    }
}
