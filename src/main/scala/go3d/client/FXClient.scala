package go3d.client

import go3d.{Position, Delta, newGoban, Goban, newGame, Game, GameOver, Move, Pass}
import go3d.client.fx.{MouseHandler, Xform, XformedCamera}

import scala.annotation.tailrec
import scala.util.Random

import scalafx.animation.AnimationTimer
import scalafx.application.JFXApp3
import scalafx.geometry.{Insets, Point3D}
import scalafx.scene.{Node, Scene, PerspectiveCamera}
import scalafx.scene.effect.DropShadow
import scalafx.scene.layout.HBox
import scalafx.scene.paint.Color._
import scalafx.scene.paint._
import scalafx.scene.text.Text
import scalafx.scene.shape.{Shape3D, Line, Box, Cylinder, Sphere}
import scalafx.stage.StageStyle
import scalafx.Includes.{jfxGroup2sfx,jfxAnimationTimer2sfx}

object FXClient extends JFXApp3:

  val sphereRadius = 10
  val sphereTranslation = 25
  val cameraBaseDistance = 100

  private var game = newGame(7) //presetGame

  val mainCamera: XformedCamera = XformedCamera(cameraBaseDistance*game.goban.size)

  var globalStage: JFXApp3.PrimaryStage = null
  var globalScene: Scene = null

  override def start(): Unit =
    globalScene = createScene(mainCamera)

    globalStage = new JFXApp3.PrimaryStage {
      initStyle(StageStyle.Unified)
      title = "3D Go Board"
      scene = globalScene
      MouseHandler(Scene(scene()), mainCamera).handleMouse()
      AnimationTimer(handleAnimation).start()
    }

  private def gobanXform(goban: Goban): Xform =
    val stones = allStones(goban)
    val lines = drawLines(goban.size)
    val stars = starPoints(goban.size)
    val xform = Xform()
    val center = goban.size*(sphereTranslation+sphereRadius)/2
    xform.setTranslate(center, -center, -center)
    stones.foreach(xform.children += _)
    lines.foreach(xform.children += _)
    stars.foreach(xform.children += _)
    xform

  def stone(pos: Position, mat: Material): Sphere = sphere(pos, sphereRadius, mat)

  def sphere(pos: Position, rad: Double, mat: Material): Sphere =
    new Sphere {
      material = mat
      radius = rad
      translateX = pos.x * sphereTranslation
      translateY = pos.y * sphereTranslation
      translateZ = pos.z * sphereTranslation
      effect = new DropShadow {
        color = DarkGray
        radius = sphereRadius * 1.25
        spread = sphereRadius * 0.25
      }
    }

  def drawLines(size: Int): Seq[Box] =
    var lines = Seq[Box]()
    for (z <- 1 to size; //  draw  all  xy-planes
         xy <- 1 to size)
      lines = lines.appended(newLine(Position(xy, 1, z), Position(xy, size, z), size))
      lines = lines.appended(newLine(Position(1 , xy, z), Position(size, xy, z), size))

    for (x <- 1 to size; //  draw  all  z-lines
         y <- 1 to size)
      lines = lines.appended(newLine(Position(x, y, 1), Position(x, y, size), size))
    lines

  def newLine(boardStart: Position, boardEnd: Position, size: Int): Box =
    val lineThickness = 0.1/size
    val (deltaScale_x, deltaScale_y, deltaScale_z) = ((boardEnd-boardStart).dx + lineThickness, (boardEnd-boardStart).dy + lineThickness, (boardEnd-boardStart).dz + lineThickness)
    val (deltaTranslation_x, deltaTranslation_y, deltaTranslation_z) = ((boardStart+boardEnd).dx/2.0, (boardStart+boardEnd).dy/2.0, (boardStart+boardEnd).dz/2.0)
    new Box(deltaScale_x, deltaScale_y, deltaScale_z) {
      translateX = deltaTranslation_x * sphereTranslation
      translateY = deltaTranslation_y * sphereTranslation
      translateZ = deltaTranslation_z * sphereTranslation
      scaleX = sphereTranslation
      scaleY = sphereTranslation
      scaleZ = sphereTranslation
      material = lineMaterial
    }

  def starPoints(size: Int): Seq[Sphere] =
    if size > 5 then StarPoints(size).all.map(sphere(_, 1, lineMaterial))
    else Seq()

  def allStones(goban: Goban): Seq[Shape3D] =
    @tailrec
    def addStone(positions: Seq[Position], stones: Seq[Shape3D]): Seq[Shape3D] =
      if positions.isEmpty then stones
      else if goban.at(positions.head) == go3d.Empty then addStone(positions.tail, stones)
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
      diffuseColor = Color.rgb(0, 0, 255, 0.25)
      specularColor = Color.LightBlue
    }
    col match
      case go3d.Black => blackMaterial
      case go3d.White => whiteMaterial
      case _ => blueMaterial

  val lineMaterial: Material =
    new PhongMaterial {
      diffuseColor = Color.rgb(40, 40, 40, 0.7)
      specularColor = Color.White
    }

  private var playerColor: go3d.Color = go3d.Black
  private var lastTime: Long = 0
  private val strategies = Array("maximizeOwnLiberties", "closestToStarPoints")
  def handleAnimation(currentTime: Long): Unit =
    if currentTime > lastTime+500000000 then
      lastTime = currentTime
      val strategy = SetStrategy(game, strategies)
      val possible = game.possibleMoves(playerColor)
      print(s"$playerColor: ${possible.length} possible ")
      if possible.nonEmpty then
        val setPosition = randomMove(strategy.narrowDown(possible, strategies))
        game = game.setStone(Move(setPosition.x, setPosition.y, setPosition.z, playerColor))
        println(s"${game.captures(playerColor)} captured")
        val newCamera: XformedCamera = XformedCamera(cameraBaseDistance*game.goban.size)
        globalScene = createScene(newCamera)
        globalStage.setScene(globalScene)
      else
        if game.isOver then
          println(game.score)
          Thread.sleep(5000)
          System.exit(0)

        println("pass")
        game = game.makeMove(Pass(playerColor))
      playerColor = !playerColor

  def createScene(newCamera: XformedCamera): Scene =
    new Scene(1600, 1600) {
      fill = Color.rgb(28, 28, 28)
      content = new HBox {
        padding = Insets(30, 50, 30, 50)
        children += gobanXform(game.goban)
        children += newCamera.xform
      }
      camera = newCamera.camera
    }
def fromStrings(levels: Map[Int, String]): Goban =
  if levels.isEmpty then throw IllegalArgumentException("nothing to generate")
  val goban = newGoban((levels.head._2.stripMargin.replace("|", "").split("\n").length))
  for (z, level) <- levels do
    val lines = level.stripMargin.replace("|", "").split("\n")
    for (line, y) <- lines.zipWithIndex do
      for (stone, x) <- line.zipWithIndex do
        goban.stones(x+1)(y+1)(z) = go3d.Color(stone)
  goban

val random: Random = Random()
def randomMove(possible: Seq[Position]): Position =
  possible(random.nextInt(possible.length.max(1)))

val presetGame = Game(5, fromStrings(Map(
  1 ->
    """O   O|
      | @   |
      |     |
      |     |
      |O   O|""",
  2 ->
    """ @   |
      |@ @  |
      | @   |
      |     |
      |     |""",
  3 ->
    """ O   |
      |O@O  |
      | O   |
      |     |
      |     |""",
  4 ->
    """     |
      | O   |
      |     |
      |     |
      |     |""",
  5 ->
    """O   O|
      |     |
      |     |
      |     |
      |O   O|""",
)), Array(), Map())