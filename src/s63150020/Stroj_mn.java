package s63150020;

import skupno.Polje;
import skupno.Stroj;


public class Stroj_mn implements Stroj {

    public static final String NAME = "mn";
    public static final String VERSION = "0.2b";

    /**
     * Score for the current match.
     *
     * score = [nWon, nLost]
     */
    private int[] score;

   /**
     * Indicates whether a game is in progress.
     */
    private boolean playing;

    /**
     * List of moves played by both players.
     */ 
    private Board board;

    /**
     * Next move calculator. 
     */
    private MoveMaker movemaker;


    public Stroj_mn() {
        String hexDebug = System.getenv("HEX_DEBUG");
        if(hexDebug != null) {
            Logger.setLevel(Integer.parseInt(hexDebug));
        }

        Logger.startUpSequence();
        this.score = new int[2];
    }

    @Override
    public void novaPartija(int dimensions, boolean isRed) {
        Logger.log("New game started. Playing as %s.", isRed ? "red" : "blue");

        board = new Board(dimensions, isRed);
        movemaker = new MoveMaker(board, isRed);
        playing = true;
    }

    @Override
    public Polje izberiPotezo(long remainingTime) {
        Polje move = movemaker.nextMove(remainingTime);
        return move;
    }

    @Override
    public void sprejmiPotezo(Polje field) {
        board.play(Owner.Other, field);
    }

    @Override
    public void rezultat(boolean won) {
        if(won) {
            score[0]++;
        } else {
            score[1]++;
        }

        playing = false;

        Logger.log("Game %s. Current score: %d:%d", won ? "won" : "lost", score[0], score[1]);
    }
}