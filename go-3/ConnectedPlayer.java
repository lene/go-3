
import java.io.*;
import java.net.*;

/* Created on Feb 5, 2006
 */

/** a Player with a corresponding Socket and all that goes with it:
 *  Input Stream, Output Stream, GoGrid Protocol (, ...?) 
 *  @author helge
 */
class ConnectedPlayer extends Player {
	
	ConnectedPlayer (Player p) { super (p); }
	
	ConnectedPlayer (Player p, Socket clientSocket) { 
		super (p); 

		assert precondition ((clientSocket != null), 
		"Client Socket must not be null, else this is not a /Connected/ Player");
		
		setClientSocket (clientSocket);
		
		try {
			setOutStream (new PrintWriter (
					clientSocket.getOutputStream(), true));
			setInStream (new BufferedReader (new InputStreamReader(
					clientSocket.getInputStream())));
//			setProtocol ();
			isconnected = true;
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println (e.getMessage());
		}
	}
	
	public boolean isConnected () {
		return isconnected;
	}
	
	public Socket getClientSocket() { return clientSocket;	}
	public void setClientSocket(Socket clientSocket) { 
		assert precondition ((clientSocket != null), 
		"Client Socket must not be null, else this is not a /Connected/ Player");		
		this.clientSocket = clientSocket; }
	
	public BufferedReader getInStream() { return in; }
	public void setInStream(BufferedReader in) { 
		assert precondition ((in != null), 
		"Instream must not be null, ressource allocation seems to have failed");
		this.in = in; }
	
	public PrintWriter getOutStream() { return out; }
	public void setOutStream(PrintWriter out) { 
		assert precondition ((out != null), 
		"Outstream must not be null, ressource allocation seems to have failed");
		this.out = out; }
	
	public GoGridProtocol getProtocol() { return proto; }
	public void setProtocol(GoGridProtocol proto) { 
		assert precondition ((proto != null), 
		"Protocol must not be null, ressource allocation seems to have failed");
		this.proto = proto; }
	
	protected Socket clientSocket = null;
	protected PrintWriter out = null;
	protected BufferedReader in = null;
	protected GoGridProtocol proto = null;
	
	protected boolean isconnected = false;
}
