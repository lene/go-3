package go3d.client

import go3d.{Position, newGoban, Goban}
import scala.annotation.tailrec

import scalafx.application.JFXApp3
import scalafx.geometry.Insets
import scalafx.scene.{Scene,PerspectiveCamera}
import scalafx.scene.effect.DropShadow
import scalafx.scene.layout.HBox
import scalafx.scene.paint.Color._
import scalafx.scene.paint._
import scalafx.scene.text.Text
import scalafx.scene.shape.{Shape3D,Sphere}
import scalafx.stage.StageStyle

object FXClient2 extends JFXApp3:

  val sphereRadius = 10
  val sphereTranslation = 25
  val cameraDistance = 500
  val globalCamera: PerspectiveCamera = createCamera(cameraDistance)
  val whiteMaterial: PhongMaterial = new PhongMaterial {
    diffuseColor = Color.White
    specularColor = Color.LightBlue
  }
  val blackMaterial: PhongMaterial = new PhongMaterial {
    diffuseColor = Color.Black
    specularColor = Color.LightBlue
  }
  val blueMaterial: PhongMaterial = new PhongMaterial {
    diffuseColor = Color.Blue
    specularColor = Color.LightBlue
  }

  override def start(): Unit =
    stage = new JFXApp3.PrimaryStage {
      initStyle(StageStyle.Unified)
      title = "3D Go Board"

      scene = new Scene {
        fill = Color.rgb(28, 28, 28)
        content = new HBox {
          padding = Insets(30, 50, 30, 50)
          children += gobanXform(3, allStones(newGoban(3)))
          children += buildCamera()
        }
        camera = globalCamera
      }
    }

  private def gobanXform(size: Int, goban: Seq[Shape3D]): Xform =
    val xform = Xform()
    xform.setTranslate(-size/2, -size, -size/2)
    goban.foreach(xform.children += _)
    xform

  private def buildCamera(): Xform =
    val cameraXform = Xform()
    cameraXform.ry.angle = 320.0
    cameraXform.rx.angle = 40
    val cameraXform2 = Xform()
    cameraXform.children += cameraXform2
    val cameraXform3 = Xform()
    cameraXform3.rotateZ = 180.0
    cameraXform2.children += cameraXform3
    cameraXform3.children += globalCamera
    cameraXform

  def createCamera(distance: Double): PerspectiveCamera =
    new PerspectiveCamera(true) {
      nearClip = 0.1
      farClip = 10000.0
      translateZ = -distance
    }

  def stone(pos: Position, mat: Material): Sphere =
    new Sphere {
      material = mat
      radius = sphereRadius
      translateX = pos.x * sphereTranslation
      translateY = pos.y * sphereTranslation
      translateZ = pos.z * sphereTranslation
      effect = new DropShadow {
        color = DarkGray
        radius = sphereRadius * 1.25
        spread = sphereRadius * 0.25
      }
    }

  def allStones(goban: Goban): Seq[Shape3D] =
    @tailrec
    def addStone(positions: Seq[Position], stones: Seq[Shape3D]): Seq[Shape3D] =
      if positions.isEmpty then stones
      else addStone(positions.tail, stones :+ stone(positions.head, stoneMaterial(goban.at(positions.head))))
    addStone(goban.allPositions, Seq())

  def stoneMaterial(col: go3d.Color): Material =
    col match
      case go3d.Black => blackMaterial
      case go3d.White => whiteMaterial
      case _ => blueMaterial
