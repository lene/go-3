package go3d.client

import go3d.{Position, newGoban, Goban}
import go3d.client.fx.{MouseHandler, Xform, XformedCamera}

import scala.annotation.tailrec

import scalafx.application.JFXApp3
import scalafx.geometry.Insets
import scalafx.scene.{Node,Scene,PerspectiveCamera}
import scalafx.scene.effect.DropShadow
import scalafx.scene.layout.HBox
import scalafx.scene.paint.Color._
import scalafx.scene.paint._
import scalafx.scene.text.Text
import scalafx.scene.shape.{Shape3D,Sphere}
import scalafx.stage.StageStyle
import scalafx.Includes.jfxGroup2sfx

object FXClient2 extends JFXApp3:

  val sphereRadius = 10
  val sphereTranslation = 25
  val cameraBaseDistance = 100

  override def start(): Unit =

    val goban = newGoban(7)
    val mainCamera = XformedCamera(cameraBaseDistance*goban.size)

    stage = new JFXApp3.PrimaryStage {
      initStyle(StageStyle.Unified)
      title = "3D Go Board"

      scene = new Scene {
        fill = Color.rgb(28, 28, 28)
        content = new HBox {
          padding = Insets(30, 50, 30, 50)
          children += gobanXform(goban)
          children += mainCamera.xform
        }
        camera = mainCamera.camera
      }
      MouseHandler(Scene(scene()), mainCamera).handleMouse()
    }

  private def gobanXform(goban: Goban): Xform =
    val stones = allStones(goban)
    val xform = Xform()
    val center = goban.size*(sphereTranslation+sphereRadius)/2
    xform.setTranslate(center, -center, -center)
    stones.foreach(xform.children += _)
    xform

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
    col match
      case go3d.Black => blackMaterial
      case go3d.White => whiteMaterial
      case _ => blueMaterial
