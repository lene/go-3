package go3d.client.fx

import scalafx.scene.{Scene,PerspectiveCamera}
import scalafx.scene.input.MouseEvent
import scalafx.Includes.{jfxMouseEvent2sfx,jfxGroup2sfx}


case class MouseHandler(scene: Scene, camera: XformedCamera):

  private var mousePosX: Double = 0
  private var mousePosY: Double = 0
  private var mouseOldX: Double = 0
  private var mouseOldY: Double = 0
  private var mouseDeltaX: Double = 0
  private var mouseDeltaY: Double = 0

  def handleMouse(): Unit =
    scene.onMousePressed = onMousePressed
    scene.onMouseDragged = onMouseDragged

  private def onMousePressed(me: MouseEvent): Unit =
    mousePosX = me.sceneX
    mousePosY = me.sceneY
    mouseOldX = me.sceneX
    mouseOldY = me.sceneY

  private def onMouseDragged(me: MouseEvent): Unit =
    mouseOldX = mousePosX
    mouseOldY = mousePosY
    mousePosX = me.sceneX
    mousePosY = me.sceneY
    mouseDeltaX = mousePosX - mouseOldX
    mouseDeltaY = mousePosY - mouseOldY
    val modifier = if (me.isControlDown) 0.1 else if (me.isShiftDown) 10 else 1.0
    val modifierFactor = 0.1
    if (me.isPrimaryButtonDown)
      camera.xform.ry.angle = camera.xform.ry.angle() - mouseDeltaX * modifierFactor * modifier * 2
      camera.xform.rx.angle = camera.xform.rx.angle() + mouseDeltaY * modifierFactor * modifier * 2
    else if (me.isSecondaryButtonDown)
      val z = camera.camera.translateZ()
      val newZ = z + mouseDeltaX * modifierFactor * modifier
      camera.camera.translateZ = newZ
    else if (me.isMiddleButtonDown)
      camera.xform2.t.x = camera.xform2.t.x() + mouseDeltaX * modifierFactor * modifier * 3
      camera.xform2.t.y = camera.xform2.t.y() + mouseDeltaY * modifierFactor * modifier * 3
