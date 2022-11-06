package go3d.client.gdx

import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController

class Go3DInputMultiplexer(camera: PerspectiveCamera) extends InputMultiplexer:
  addProcessor(new CameraInputController(camera))
  addProcessor(new Go3DInputController(camera))

