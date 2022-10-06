package go3d.client

import go3d.{Black, Game, Position, White}
import go3d.server.StatusResponse

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.{Environment, Material, ModelBatch, RenderableProvider}
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.utils.Timer

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

class GDXClient2(client: BaseClient, boardSize: Int) extends ApplicationListener:

  final val CAMERA_POSITION: Position = Position(boardSize, boardSize, boardSize)
  final val UPDATE_DELAY_SECONDS = 2f
  final val UPDATE_INTERVAL_SECONDS = 1f

  private[this] val environment: Environment = createEnvironment

  private[this] var camera: PerspectiveCamera = null
  private[this] var camController: CameraInputController = null
  private[this] var stonesModel: List[RenderableProvider] = List()
  private[this] var game: Game = null
  private[this] var builder: GeometryBuilder = null
  private[this] var modelBatch : ModelBatch = null

  @Override def create(): Unit =
    val status = client.status
    modelBatch = new ModelBatch
    builder = GeometryBuilder(status.game.size)

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
      stonesModel = builder.createStones(game)
      println(s"Move ${game.moves.length}: $lastMove $captures")

  private def lastMove: String =
    if (game == null) || (game.moves.length == 0)
    then "waiting for game to start"
    else game.moves.last.toString

  private def captures: String =
    if game == null then ""
    else "Captures: " + Seq(Black, White).foldLeft("")(
      (caps, col) => caps + s"$col: ${game.captures(col)} "
    )

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

  @Override def render(): Unit =
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight())
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT)
    modelBatch.begin(camera)
    modelBatch.render(builder.gridModel.asJava, environment)
    modelBatch.render(stonesModel.asJava, environment)
    modelBatch.end()

  @Override def dispose(): Unit =
    modelBatch.dispose()
    builder.dispose()

  @Override def resume(): Unit = println("resume")

  @Override def resize(width: Int, height: Int): Unit =
    camera.viewportWidth = Gdx.graphics.getWidth().toFloat
    camera.viewportHeight = Gdx.graphics.getHeight().toFloat
    camera.update()

  @Override def pause(): Unit = println("pause")
