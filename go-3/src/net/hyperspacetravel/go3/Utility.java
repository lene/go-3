package net.hyperspacetravel.go3;


public class Utility {
	
	public static String getArg (String input, int field) {
		String [] fields = input.trim ().split ("\\s");         //  split input string at whitespace 
		try {
			return fields[field-1];
		} catch (ArrayIndexOutOfBoundsException e) {
			return null;
		}
	}
	
	public static String getArgs (String input, int startfield) { 
		String [] fields = input.trim ().split ("\\s", startfield); //  split input string at whitespace
		try {
			return fields[startfield-1];
		} catch (ArrayIndexOutOfBoundsException e) {
			return null;
		}
	}
	
	public static void sleep (int msec) {
		try {
			Thread t = new Thread ();
			Thread.sleep (msec);
			t.join (msec);
		} catch (InterruptedException e) { }
	}
	
	public static void debug (String d) {
		if (DEBUG) {
			Throwable t = new Throwable ();
			StackTraceElement [] st = t.getStackTrace ();
			String message = st[1].getClassName ()+"."+
				st[1].getMethodName ()+" (), line "+
				st[1].getLineNumber ()+": "+
				d; 
			if (false) 
				for (int i = 2; i < st.length; i++) 
					message += "    from "+
							   st[i].getClassName ()+"."+
							   st[i].getMethodName ()+" (), line "+
							   st[i].getLineNumber ();

			System.out.println (message);
			//	TODO: open a dialog window displaying the message
		}
	}
	
	public static void warning (String w) { System.err.println ("warning: "+w); }
	//	TODO: open a dialog window displaying the message
	
	
	/**
	 Print an error message with a cause, and the stack trace leading up to
	 it<br>
	 Usually called as <tt>Util.bitch (new Throwable ("message"))</tt><br>
	 @param t the Throwable object containing the stack trace
	 */
	public static void bitch (Throwable t) {
		StackTraceElement [] st = t.getStackTrace ();
		System.err.println (
				st[0].getClassName ()+"."+
				st[0].getMethodName ()+" (), line "+
				st[0].getLineNumber ()+": "+
				t.getMessage ());
		for (int i = 1; i < st.length; i++) 
			System.err.println ("    from "+
					st[i].getClassName ()+"."+
					st[i].getMethodName ()+" (), line "+
					st[i].getLineNumber ());
		//	TODO: open a dialog window displaying the message
	}

	private static boolean DEBUG = false;
	public static void setDebugMode (boolean debug) { DEBUG = debug; }
	public static boolean getDebugMode () { return DEBUG; }

}