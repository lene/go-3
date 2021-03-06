package net.hyperspacetravel.go3;

public class Colour extends GameBase {
    public static final int EMPTY = 0, 
    BLACK = 1,
	WHITE = 2, 
	RED = 3, 
	GREEN = 4, 
	BLUE = 5, 
	OCCUPIED = -1,
	UNDEFINED = -2;
    
    public static String name (int i) {
		assert precondition ((i >= -2 && i < 5), 
		"color must lie between -2 and 5");

    	switch (i) {
    	case BLACK: return "Black";
    	case WHITE: return "White";
    	default:    return "Undefined";
    	}
    }

}
