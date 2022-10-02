package go3d.client.fx

import scalafx.scene.PerspectiveCamera

case class XformedCamera(zDistance: Double):
  val xform: Xform = Xform()
  val xform2: Xform = Xform()
  val camera: PerspectiveCamera = createCamera(zDistance)

  buildCamera()

  def buildCamera(): Xform =
    xform.ry.angle = 320.0
    xform.rx.angle = 20
    xform.children += xform2
    val xform3 = Xform()
    xform3.rotateZ = 180.0
    xform2.children += xform3
    xform3.children += camera
    xform

  def createCamera(distance: Double): PerspectiveCamera =
    new PerspectiveCamera(true) {
      nearClip = 0.1
      farClip = 10000.0
      translateZ = -distance
    }

