
class ConnectionData extends GameBase {

	public int getBoardSize() {
		return Math.max(MIN_GRID_SIZE, 
				Math.min(MAX_GRID_SIZE, super.getBoardSize()));
	}
	/**
	 * @return Returns the serverHost.
	 */
	public String getServerHost() {
		return serverHost;
	}
	/**
	 * @param serverHost The serverHost to set.
	 */
	public void setServerHost(String serverHost) {
		this.serverHost = serverHost;
	}
	/**
	 * @return Returns the username.
	 */
	public String getUsername() {
		return username;
	}
	/**
	 * @param username The username to set.
	 */
	public void setUsername(String username) {
		this.username = username;
	}
	
	public boolean getStartGame() {
		return startGame;
	}
	public void setStartGame(boolean startGame) {
		this.startGame = startGame;
	}

	/**	 server host */
	private String serverHost = "localhost";
	/**	 user id	 */
	private String username = "";
	
	boolean startGame = false;

}
