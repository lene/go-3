
import java.io.*;
import java.util.LinkedList;

/**
 	this class runs a <tt>Game</tt>, after it has enough players.
 */
class GameThread extends Thread {	

	GameThread (Game g) {
		this.game = g;
	}
	
	final public void run () {
		game.startGame();
	}

	Game game = null;
}
