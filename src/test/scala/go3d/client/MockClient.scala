package go3d.client

import go3d.newGame
import go3d.server.{RequestInfo, StatusResponse}

class MockClient extends BaseClient("mock server", "mock id", None):
  override def status: StatusResponse =
    StatusResponse(newGame(3), List(), true, false, RequestInfo(Map(), "", "", false))

