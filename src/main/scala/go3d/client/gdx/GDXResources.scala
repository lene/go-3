package go3d.client.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.{Environment, ModelBatch, RenderableProvider}
import com.badlogic.gdx.math.Vector3

import scala.collection.immutable.List
import scala.jdk.CollectionConverters.*

case class GDXResources(boardSize: Int):

  private val cameraPosition = Vector3(-boardSize*2f, boardSize*1f, -boardSize*1f).scl(3f/4f)
  private val environment: Environment = createEnvironment
  private val camera: PerspectiveCamera = createCamera(cameraPosition)
  Gdx.input.setInputProcessor(new Go3DInputMultiplexer(camera))
  private val modelBatch = new ModelBatch

  def render(models: List[RenderableProvider]*): Unit =
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth, Gdx.graphics.getHeight)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT)
    modelBatch.begin(camera)
    models.foreach(model => modelBatch.render(model.asJava, environment))
    modelBatch.end()

  def resize(): Unit =
    camera.viewportWidth = Gdx.graphics.getWidth.toFloat
    camera.viewportHeight = Gdx.graphics.getHeight.toFloat
    camera.update()

  def dispose(): Unit = modelBatch.dispose()

  private def createEnvironment: Environment =
    val localEnv = new Environment()
    localEnv.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4, 0.4, 0.4, 1.0))
    localEnv.add(new DirectionalLight().set(0.8, 0.8, 0.8, -1, -0.8, -0.2))
    localEnv

  private def createCamera(cameraPos: Vector3): PerspectiveCamera =
    val cam = new PerspectiveCamera(67, Gdx.graphics.getWidth.toFloat, Gdx.graphics.getHeight.toFloat) {
      near = 1.0
      far = 300.0
    }
    cam.position.set(cameraPos)
    cam.lookAt(0, 0, 0)
    cam.update()
    cam
