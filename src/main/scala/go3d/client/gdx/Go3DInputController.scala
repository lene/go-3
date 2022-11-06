package go3d.client.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3

class Go3DInputController(camera: PerspectiveCamera) extends InputAdapter:

  private val defaultPos = camera.position.cpy
  private val defaultDirection = camera.direction.cpy
  private val defaultUp = camera.up.cpy

  private var ctrl = false
  private var rotatePressed: Map[Int, Boolean] = Map().withDefaultValue(false)

  override def keyDown(keycode: Int): Boolean =
    keycode match
      case Keys.CONTROL_LEFT | Keys.CONTROL_RIGHT => setCtrl(true)
      case Keys.LEFT | Keys.RIGHT | Keys.UP | Keys.DOWN => setRotatePressed(keycode, true)
      case Keys.ESCAPE => resetCamera
      case Keys.Q =>
        if ctrl then System.exit(0)
        true
      case _ => false

  override def keyUp(keycode: Int): Boolean =
    keycode match
      case Keys.CONTROL_LEFT | Keys.CONTROL_RIGHT => setCtrl(false)
      case Keys.LEFT | Keys.RIGHT | Keys.UP | Keys.DOWN => setRotatePressed(keycode, false)
      case _ => false

  // algorithm pulled from
  // https://github.com/libgdx/libgdx/blob/master/gdx/src/com/badlogic/gdx/graphics/g3d/utils/CameraInputController.java#L187
  private final val rotateAngle = 360f
  def update(): Unit =
    if rotatePressed.values.exists(_ == true) then
      val delta = Gdx.graphics.getDeltaTime
      if (rotatePressed(Keys.RIGHT)) camera.rotateAround(new Vector3, Vector3.Y, -delta*rotateAngle)
      if (rotatePressed(Keys.LEFT)) camera.rotateAround(new Vector3, Vector3.Y, delta*rotateAngle)
      val tmpXZ = new Vector3
      tmpXZ.set(camera.direction).crs(camera.up).y = 0f
      if (rotatePressed(Keys.UP)) camera.rotateAround(new Vector3, tmpXZ.nor, delta*rotateAngle)
      if (rotatePressed(Keys.DOWN)) camera.rotateAround(new Vector3, tmpXZ.nor, -delta*rotateAngle)
      camera.update

  private def setCtrl(mode: Boolean): Boolean =
    ctrl = mode
    true

  private def setRotatePressed(keycode: Int, mode: Boolean): Boolean =
    this.rotatePressed += keycode -> mode
    update()
    true

  private def resetCamera: Boolean =
    camera.position.set(defaultPos)
    camera.direction.set(defaultDirection)
    camera.up.set(defaultUp)
    camera.lookAt(0, 0, 0)
    camera.update
    true
