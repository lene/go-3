package go3d.client

import go3d.Game
import go3d.server.{RequestInfo, StatusResponse}

class MockClient extends BaseClient("mock server", "mock id", None):
  override def status: StatusResponse =
    StatusResponse(Game.start(3), List(), true, false, RequestInfo(Map(), "", "", false))

