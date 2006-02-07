
import java.io.*;
import java.net.*;

/* Created on Feb 5, 2006
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

/**
 * @author helge
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
class ConnectedPlayer extends Player {
	
	ConnectedPlayer (Player p) { super (p); }
	ConnectedPlayer (Player p, Socket clientSocket) { 
		super (p); 
		
		if (clientSocket != null) {
		setClientSocket (clientSocket);
		
		try {
			setOutStream (new PrintWriter (
					clientSocket.getOutputStream(), true));
			setInStream (new BufferedReader (new InputStreamReader(
					clientSocket.getInputStream())));
//			setProtocol ();
		
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println (e.getMessage());
		}
		}
	}
	
	public Socket getClientSocket() { return clientSocket;	}
	public void setClientSocket(Socket clientSocket) { this.clientSocket = clientSocket; }
	
	public BufferedReader getInStream() { return in; }
	public void setInStream(BufferedReader in) { this.in = in; }
	
	public PrintWriter getOutStream() { return out; }
	public void setOutStream(PrintWriter out) { this.out = out; }
	
	public GoGridProtocol getProtocol() { return proto; }
	public void setProtocol(GoGridProtocol proto) { this.proto = proto; }
	
	protected Socket clientSocket = null;
	protected PrintWriter out = null;
	protected BufferedReader in = null;
	protected GoGridProtocol proto = null;
	
}
