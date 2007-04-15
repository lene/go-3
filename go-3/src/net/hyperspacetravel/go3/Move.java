package net.hyperspacetravel.go3;

/**
 * class Move represents a move on the go grid, consisting of a position
 * and a colour.
 * <p>
 * Constructor:
 * Move (int x, int y, int z, int col)
 * <p> 
 */

class Move extends GameBase {
	public Move (int x, int y, int z, int col) {

		assert precondition (x >= 0 && x < MAX_GRID_SIZE &&
				y >= 0 && y < MAX_GRID_SIZE &&
				z >= 0 && z < MAX_GRID_SIZE,
				"point ["+x+", "+y+", "+z+"] must lie inside the allowed grid size!");
		assert precondition ((col >= Colour.BLACK && col <= Colour.WHITE), 
				"color must lie between "+Colour.name(Colour.BLACK)+" and "+Colour.name(Colour.WHITE));

		this.x = x;
		this.y = y;
		this.z = z;
		this.col = col;
	}

	@SuppressWarnings("unused")
	private int x, y, z;
	@SuppressWarnings("unused")
	private int col;
}
