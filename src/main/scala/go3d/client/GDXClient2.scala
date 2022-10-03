package go3d.client

import go3d.{Black, Game, Position, White}
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
import scala.math.Pi

class GDXClient2(game: Game) extends ApplicationListener:

  final val CENTER = (game.size+1)/2
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
    ColorAttribute.createAmbient(new Color(0.1, 0.1, 0.1, 0.5)),
    ColorAttribute.createDiffuse(new Color(0.2, 0.2, 0.2, 0.5)),
    ColorAttribute.createSpecular(new Color(0.8, 0.8, 0.8, 0.5))
  )
  final val CAMERA_POSITION: Position = Position(game.size, game.size, game.size)

  val environment: Environment = createEnvironment
  val modelBuilder = new ModelBuilder

  var camera: PerspectiveCamera = null
  var camController: CameraInputController = null
  var models: Map[String, Model] = Map()
  var modelBatch: ModelBatch = null
  var instances: List[RenderableProvider] = List()

  @Override def create(): Unit =
    camera = createCamera(CAMERA_POSITION)

    modelBatch = new ModelBatch
    models = createModels()

    instances = stonesOfColor(game, Black).map(createModel(BLACK, _, STONE_RADIUS)) :++
      stonesOfColor(game, White).map(createModel(WHITE, _, STONE_RADIUS)) :++
      createGrid()

    camController = new CameraInputController(camera)
    Gdx.input.setInputProcessor(camController)

    val delaySeconds = 2f
    val intervalSeconds = 1f
    Timer.schedule(new Timer.Task {
      @Override def run(): Unit = print(".")
    }, delaySeconds, intervalSeconds)

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

  def createGrid(): List[RenderableProvider] =
    (1 to game.size).map(y => createModel(GRID, Position(CENTER, y, CENTER), 1)).toList :++
      (-game.size/2 to game.size/2).map(z => verticalGrid(z)).toList :++
      createStarPoints()

  def verticalGrid(z: Int): ModelInstance =
    val grid = createModel(GRID, Position(1, 1, 1), 1)
    grid.transform.setToRotation(0, 0, 1, 90)
    grid.transform.translate(0, z.toFloat, 0)
    grid

  def createStarPoints(): List[RenderableProvider] =
    if game.size < 7 then List()
    else StarPoints(game.size).all.map(createModel(STAR_POINT, _, 1)).toList

  def createModels(): Map[String, Model] =
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
        game.size-1, game.size-1, 1, 1,
        GRID_MATERIAL, Usage.Position | Usage.Normal
      ),
      STAR_POINT -> modelBuilder.createSphere(
        STAR_POINT_RADIUS, STAR_POINT_RADIUS, STAR_POINT_RADIUS, 8, 4,
        GRID_MATERIAL, Usage.Position | Usage.Normal
      ),
    )

  def createModel(name: String, pos: Position, scale: Float): ModelInstance =
    val instance = new ModelInstance(models(name))
    instance.transform.setToTranslationAndScaling(pos.x.toFloat, pos.y.toFloat, pos.z.toFloat, scale, scale, scale)
    instance.transform.translate(-(game.size+1)/2f, -(game.size+1)/2f, -(game.size+1)/2f)
    instance

  @Override def render(): Unit =
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight())
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT)
    modelBatch.begin(camera)
    modelBatch.render(instances.asJava, environment)
    modelBatch.end()

  @Override def dispose(): Unit =
    modelBatch.dispose()
    models.values.foreach(_.dispose())

  @Override def resume(): Unit = println("resume")
  @Override def resize(width: Int, height: Int): Unit = println(s"resize $width/$height")
  @Override def pause(): Unit = println("pause")

def stonesOfColor(game: Game, col: go3d.Color): List[Position] =
  @tailrec
  def addStone(positions: Seq[Position], stones: List[Position]): List[Position] =
    if positions.isEmpty then stones
    else if game.at(positions.head) != col then addStone(positions.tail, stones)
    else addStone(positions.tail, stones :+ positions.head)
  addStone(game.goban.allPositions, List())
