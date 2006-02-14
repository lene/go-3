
class ConnectionData extends GameBase {

	public int getBoardSize() {
		return Math.max(MIN_GRID_SIZE, 
				Math.min(MAX_GRID_SIZE, super.getBoardSize()));
	}

	public String getServerHost() { return serverHost; }
	public void setServerHost(String serverHost) { this.serverHost = serverHost; }

	public String getUsername() { return username; }
	public void setUsername(String username) { this.username = username; }
	
	public boolean getStartGame() {	return startGame; }
	public void setStartGame(boolean startGame) { this.startGame = startGame; }

	public String getGame() { return game; }
	public void setGame(String game) { this.game = game; }

	/**	 server host */
	private String serverHost = "localhost";
	/**	 user id	 */
	private String username = "";
	/** game to connect to */
	private String game = "";
	/** whether to start own game (or connect to a running one) */
	private boolean startGame = false;
}
