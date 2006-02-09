import java.io.BufferedReader;
import java.io.IOException;
import java.util.Stack;

/**
 client side implementation of the GoGridProtocol.
 
 the protocol specified:
 <ol>
 <li>connect to the server
 <li>read your color
 <li>in no particular order:
 <ul>
 <li>read grid size and grid
 <li>read number of players
 <li>?
 </ul>
 <li>now the client is ready to:
 <ul>
 <li>send messages to any and all other clients
 <li>request change of board size
 <li>request change in number of players
 <li>request change of own handicaps
 <li>request change of color
 <li>request start of game
 </ul>
 <li>after starting the game, the client may:
 <ul>
 <li>request an update of the grid
 </ul>
 <li>when on the move, it may additionally
 <ul>
 <li>set a stone
 <li>pass
 </ul>
 </ol>
 
 TODO decouple the ClientProtocol from the GoGridClient, so that the client 
 doesn't need to know the Protocol syntax.
 TODO redesign and extend the protocol. 
 */

class ClientProtocol extends GoGridProtocol {
	
	BufferedReader in;
	GoGridClient client;
	
	ClientProtocol (GoGridClient client, BufferedReader in) {
		super ();
		
		assert GameBase.precondition(client != null, "Client must exist");
		assert GameBase.precondition(in != null, "Instream must exist");
		
		this.client = client;
		this.in = in;
	}
	
	public void run () {
		
		while (true) {
			
			String inputLine = new String ();
			
			try {
				inputLine = in.readLine ();
			} catch (IOException e) {
				Utility.bitch (new Throwable ("error reading line from socket!"));
				break;
			}
			if (inputLine == null) break;
			
			messages.push(inputLine);
			
			processInput (inputLine);
		}
		Utility.warning ("null input - server terminated!");
		client.lostServerConnection ();
	}
	
	
	/**
	 read a command from the server and act on it
	 @param input the command - redundant, could read lastMessage()
	 */
	void processInput (String input) {
		
		Utility.debug (input);
		
		if (input.startsWith ("start game")) {
			client.startGame ();
			return;
		}
		
		if (input.startsWith ("ready")) {
			client.activate ();
			return;
		}
		
		if (input.startsWith ("ok")) {
			client.deactivate ();
			defineStatus (true);
			return;
		}
		
		if (input.startsWith ("error")) {
			defineStatus (false);
			return;
		}
		
		if (input.startsWith ("size")) {
			//	TODO decouple client code from protocol syntax
			client.setSize (input);
			return;
		}
		
		if (input.startsWith ("stones")) {
			//	TODO decouple client code from protocol syntax
			client.updateBoard (input);
			return;
		}
		
		if (input.startsWith ("message")) {
			client.message (Utility.getArgs (input, 2));
			return;
		}
		if (input.startsWith ("liberties")) {
			try {
				liberties = Integer.parseInt (Utility.getArg (input, 2));
			} catch (NumberFormatException e) {
				Utility.debug ("NumberFormatException: "+input);
				liberties = -1;
			} 
			return;
		}
		
		Utility.debug ("input sucks: "+input);
	}
	
	/** used to exchange data with the GoGridClient, which reads it */
	protected int liberties = -1;
	int liberties () { 
		int l = liberties;
		liberties = -1;		//	marks "read" state, not yet re-set by the server
		return l; }
	void liberties (int l) { liberties = l; }
	
	protected boolean status = false;
	boolean status () { return status; }
	
	protected boolean statusDefined = false;
	boolean statusDefined () { return statusDefined; }
	void undefineStatus () { statusDefined = false; }    
	private void defineStatus (boolean s) {
		statusDefined = true;
		status = s;
	}
	
	protected void lostConnection () {
		Utility.bitch(new Throwable ("Implement me!"));
	}
	
	/** sort of an anally retentive inclination of mine to keep all messages  */
	protected Stack<String> messages = new Stack<String>();
	String lastMessage () { return messages.peek(); }
}