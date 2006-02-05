

class FieldNotPresentException extends IllegalArgumentException {
}

class OutsideGridException extends IllegalArgumentException {
}

class Utility {
    private static boolean DEBUG = true;
	
	static void setDebugMode (boolean debug) {
		DEBUG = debug;
	}
	
	static boolean getDebugMode () {
		return DEBUG;
	}

    static String getArg (String input, int field) {
	String [] fields = input.trim ().split ("\\s");         //  split input string at whitespace 
	try {
	    return fields[field-1];
	} catch (ArrayIndexOutOfBoundsException e) {
	    return null;
	}
    }

    static String getArgs (String input, int startfield) { 
	String [] fields = input.trim ().split ("\\s", startfield); //  split input string at whitespace
	try {
	    return fields[startfield-1];
	} catch (ArrayIndexOutOfBoundsException e) {
	    return null;
	}
    }

    static void sleep (int msec) {
	try {
	    Thread t = new Thread ();
	    Thread.sleep (msec);
	    t.join (msec);
	} catch (InterruptedException e) { }
    }

    static void debug (String d) {
	if (DEBUG) {
	    Throwable t = new Throwable ();
	    StackTraceElement [] st = t.getStackTrace ();
	    System.out.println (
				st[1].getClassName ()+"."+
				st[1].getMethodName ()+" (), line "+
				st[1].getLineNumber ()+": "+
				d);
	}
    }

    static void warning (String w) {
	System.err.println ("warning: "+w);
    }


   /**
       Print an error message with a cause, and the stack trace leading up to
       it<br>
       Usually called as <tt>Util.bitch (new Throwable ("message"))</tt><br>
       @param t the Throwable object containing the stack trace
    */
    static void bitch (Throwable t) {
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
    }

    /**
       complain that a function is called which should be declared abstract, but
       hasn't yet; in the near future, when the class hierarchy has been cleaned
       up, this method should be obsolete<br>
       @param t info about the location of the pseudo-abstract method
       THIS FUNCTION IS PROBABLY OBSOLETE, BUT I LIKE TO KEEP IT AROUND
    */
    private void bitchPseudoAbstract (Throwable t) {
	StackTraceElement[] st = t.getStackTrace ();
	System.err.println (
			    st[0].getClassName ()+"."+
			    st[0].getMethodName ()+"("+
			    t.getMessage ()+"), line "+
			    st[0].getLineNumber ()+": "+
			    "pseudo-abstract method called!");
	for (int i = 1; i < st.length; i++)
	    System.err.println (
				"    Called from "+
				st[i].getClassName ()+"."+
				st[i].getMethodName ()+"(), line "+
				st[i].getLineNumber ());
    }
    
}

