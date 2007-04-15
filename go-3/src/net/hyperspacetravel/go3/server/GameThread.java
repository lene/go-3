package net.hyperspacetravel.go3.server;

import net.hyperspacetravel.go3.Game;
import net.hyperspacetravel.go3.GameBase;

/**
 	this class runs a <tt>Game</tt>, after it has enough players.
 */
public class GameThread extends Thread {	

	public GameThread (Game g) {
		this.game = g;
	}
	
	final public void run () {
		assert GameBase.precondition(game != null, "game == null");
		game.startGame();
	}

	Game game = null;
}
