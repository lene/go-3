
class ConnectionData {
	/**
	 board size
	 */
	private int boardSize = 3;
	/**
	 server port
	 */
	private int serverPort = 6666;
	/**
	 server host
	 */
	private String serverHost = "localhost";
	/**
	 user id
	 */
	private String username = "";
	/**
	 * @return Returns the boardSize.
	 */
	public int getBoardSize() {
		return boardSize;
	}
	/**
	 * @param boardSize The boardSize to set.
	 */
	public void setBoardSize(int boardSize) {
		this.boardSize = boardSize;
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
	 * @return Returns the serverPort.
	 */
	public int getServerPort() {
		return serverPort;
	}
	/**
	 * @param serverPort The serverPort to set.
	 */
	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
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

}
