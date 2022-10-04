package go3d.client

import go3d.{Black, Game, Position, White}
import go3d.server.StatusResponse

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.{Environment, Material, Model, ModelBatch, ModelInstance, RenderableProvider}
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.utils.Timer

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

class GDXClient2(client: BaseClient, boardSize: Int) extends ApplicationListener:

  final val CENTER = (boardSize+1)/2
  final val STONE_RADIUS = 1
  final val STAR_POINT_RADIUS = 0.08f
  final val SPHERE_DIVISIONS = 32
  final val WHITE = "white stone"
  final val BLACK = "black stone"
  final val GRID = "horizontal grid"
  final val STAR_POINT = "star point"
  final val BLACK_MATERIAL = new Material(
    ColorAttribute.createAmbient(new Color(0.1, 0.1, 0.1, 1.0)),
    ColorAttribute.createDiffuse(new Color(0.1, 0.1, 0.1, 1.0)),
    ColorAttribute.createSpecular(new Color(0.7, 0.7, 1.0, 1.0))
  )
  final val WHITE_MATERIAL = new Material(
    ColorAttribute.createAmbient(new Color(0.1, 0.1, 0.1, 1.0)),
    ColorAttribute.createDiffuse(new Color(0.8, 0.8, 0.8, 1.0)),
    ColorAttribute.createSpecular(new Color(1.0, 1.0, 1.0, 1.0))
  )
  final val GRID_MATERIAL = new Material(
    ColorAttribute.createAmbient(new Color(0.1, 0.1, 0.1, 0.4)),
    ColorAttribute.createDiffuse(new Color(0.2, 0.2, 0.2, 0.4)),
    ColorAttribute.createSpecular(new Color(0.8, 0.8, 0.8, 0.4))
  )
  final val CAMERA_POSITION: Position = Position(boardSize, boardSize, boardSize)
  final val UPDATE_DELAY_SECONDS = 2f
  final val UPDATE_INTERVAL_SECONDS = 1f

  val environment: Environment = createEnvironment
  val modelBuilder = new ModelBuilder

  var camera: PerspectiveCamera = null
  var camController: CameraInputController = null
  var models: Map[String, Model] = Map()
  var modelBatch: ModelBatch = null
  var stonesModel: List[RenderableProvider] = List()
  var gridModel: List[RenderableProvider] = List()
  var game: Game = null

  @Override def create(): Unit =
    val status = client.status

    modelBatch = new ModelBatch
    models = createModels(status.game.size)
    gridModel = createGrid(status.game.size)

    updateGame(status)

    camera = createCamera(CAMERA_POSITION)
    camController = new CameraInputController(camera)
    Gdx.input.setInputProcessor(camController)

    Timer.schedule(new Timer.Task {
      @Override def run(): Unit = updateGame(client.status)
    }, UPDATE_DELAY_SECONDS, UPDATE_INTERVAL_SECONDS)

  private def updateGame(status: StatusResponse): Unit =
    if game == null || status.game.moves.length != game.moves.length then
      game = status.game
      stonesModel = stonesOfColor(game, Black).map(createModel(BLACK, _, STONE_RADIUS, game.size)) :++
        stonesOfColor(game, White).map(createModel(WHITE, _, STONE_RADIUS, game.size))
      println(s"Move ${game.moves.length}: ${game.moves.last} ")

  private def createCamera(cameraPos: Position): PerspectiveCamera =
    val cam = new PerspectiveCamera(67, Gdx.graphics.getWidth().toFloat, Gdx.graphics.getHeight().toFloat) {
      near = 1.0
      far = 300.0
    }
    cam.position.set(cameraPos.x.toFloat, cameraPos.y.toFloat, cameraPos.z.toFloat)
    cam.lookAt(0, 0, 0)
    cam.update()
    cam

  private def createEnvironment: Environment =
    val localEnv = new Environment()
    localEnv.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4, 0.4, 0.4, 1.0))
    localEnv.add(new DirectionalLight().set(0.8, 0.8, 0.8, -1, -0.8, -0.2))
    localEnv

  def createGrid(boardSize: Int): List[RenderableProvider] =
    (1 to boardSize).map(y => horizontalGrid(y, boardSize)).toList :++
      (-boardSize/2 to boardSize/2).map(z => verticalGrid(z, boardSize)).toList :++
      createStarPoints(boardSize)

  def horizontalGrid(y: Int, boardSize: Int): ModelInstance =
    createModel(GRID, Position(CENTER, y, CENTER), 1, boardSize)

  def verticalGrid(z: Int, boardSize: Int): ModelInstance =
    val grid = createModel(GRID, Position(1, 1, 1), 1, boardSize)
    grid.transform.setToRotation(0, 0, 1, 90)
    grid.transform.translate(0, z.toFloat, 0)
    grid

  def createStarPoints(boardSize: Int): List[RenderableProvider] =
    if boardSize < 7 then List()
    else StarPoints(boardSize).all.map(createModel(STAR_POINT, _, 1, boardSize)).toList

  def createModels(boardSize: Int): Map[String, Model] =
    Map(
      WHITE -> modelBuilder.createSphere(
        1, 1, 1, 2 * SPHERE_DIVISIONS, SPHERE_DIVISIONS,
        WHITE_MATERIAL, Usage.Position | Usage.Normal
      ),
      BLACK -> modelBuilder.createSphere(
        1, 1, 1, 2 * SPHERE_DIVISIONS, SPHERE_DIVISIONS,
        BLACK_MATERIAL, Usage.Position | Usage.Normal
      ),
      GRID -> modelBuilder.createLineGrid(
        boardSize-1, boardSize-1, 1, 1,
        GRID_MATERIAL, Usage.Position | Usage.Normal
      ),
      STAR_POINT -> modelBuilder.createSphere(
        STAR_POINT_RADIUS, STAR_POINT_RADIUS, STAR_POINT_RADIUS, 8, 4,
        GRID_MATERIAL, Usage.Position | Usage.Normal
      ),
    )

  def createModel(name: String, pos: Position, scale: Float, boardSize: Int): ModelInstance =
    val instance = new ModelInstance(models(name))
    instance.transform.setToTranslationAndScaling(pos.x.toFloat, pos.y.toFloat, pos.z.toFloat, scale, scale, scale)
    instance.transform.translate(-(boardSize+1)/2f, -(boardSize+1)/2f, -(boardSize+1)/2f)
    instance

  @Override def render(): Unit =
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight())
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT)
    modelBatch.begin(camera)
    modelBatch.render(gridModel.asJava, environment)
    modelBatch.render(stonesModel.asJava, environment)
    modelBatch.end()

  @Override def dispose(): Unit =
    modelBatch.dispose()
    models.values.foreach(_.dispose())

  @Override def resume(): Unit = println("resume")
  @Override def resize(width: Int, height: Int): Unit =
    camera.viewportWidth = Gdx.graphics.getWidth().toFloat
    camera.viewportHeight = Gdx.graphics.getHeight().toFloat
    camera.update()

  @Override def pause(): Unit = println("pause")

def stonesOfColor(game: Game, col: go3d.Color): List[Position] =
  @tailrec
  def addStone(positions: Seq[Position], stones: List[Position]): List[Position] =
    if positions.isEmpty then stones
    else if game.at(positions.head) != col then addStone(positions.tail, stones)
    else addStone(positions.tail, stones :+ positions.head)
  addStone(game.goban.allPositions, List())
