package go3d.client.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader
import com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader.ParticleEffectLoadParameter
import com.badlogic.gdx.graphics.g3d.particles.{ParticleEffect, ParticleSystem}
import com.badlogic.gdx.graphics.g3d.particles.batches.PointSpriteParticleBatch
import com.badlogic.gdx.graphics.g3d.{Environment, ModelBatch, RenderableProvider}
import com.badlogic.gdx.math.{Matrix4, Vector3}
import go3d.Position

import scala.collection.immutable.List
import scala.jdk.CollectionConverters.*

case class ParticleMarker(assetFile: String, camera: PerspectiveCamera):
  private val particleSystem: Option[ParticleSystem] = createParticleSystem
  private var currentEffects: Option[ParticleEffect] = None

  private def createParticleSystem: Option[ParticleSystem] =
    val pointSpriteBatch = PointSpriteParticleBatch()
    pointSpriteBatch.setCamera(camera)
    val particles = ParticleSystem()
    particles.add(pointSpriteBatch)
    currentEffects = Some(createEffects(particles))
    currentEffects.foreach(_.init())
    particles.add(currentEffects.get)
    Some(particles)

  private def createEffects(particles: ParticleSystem): ParticleEffect =
    val assets = new AssetManager()
    val loadParam = ParticleEffectLoadParameter(particles.getBatches);
    val loader = ParticleEffectLoader(InternalFileHandleResolver())
    assets.setLoader(classOf[ParticleEffect], loader)
    assets.load(assetFile, classOf[ParticleEffect], loadParam)
    // halt main thread until assets are loaded. bad for production, okay for demonstration purposes
    assets.finishLoading()

    assets.get(assetFile, classOf[ParticleEffect]).copy

  def render(modelBatch: ModelBatch, position: Option[Position]): Unit =
    if position.isDefined then
      println(s"rendering particle marker at $position")
      val targetPos = Vector3(position.get.x.toFloat, position.get.y.toFloat, position.get.z.toFloat)
      val targetMatrix = Matrix4()
      targetMatrix.idt()
      targetMatrix.translate(targetPos)
      currentEffects.foreach(_.setTransform(targetMatrix))
      particleSystem.foreach(_.update())
      particleSystem.foreach(_.begin())
      particleSystem.foreach(_.draw())
      particleSystem.foreach(_.end())
      particleSystem.foreach(modelBatch.render(_))


case class GDXResources(boardSize: Int):

  private val cameraPosition = Vector3(-boardSize*2f, boardSize*1f, -boardSize*1f).scl(3f/4f)
  private val environment: Environment = createEnvironment
  private val camera: PerspectiveCamera = createCamera(cameraPosition)
  Gdx.input.setInputProcessor(new Go3DInputMultiplexer(camera))
  private val modelBatch = new ModelBatch
  private val particleMarker = ParticleMarker("data/PointSprite.pfx", camera)

  def render(latest: Option[Position], models: List[RenderableProvider]*): Unit =
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth, Gdx.graphics.getHeight)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT)
    modelBatch.begin(camera)
    models.foreach(model => modelBatch.render(model.asJava, environment))
    particleMarker.render(modelBatch, latest)
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
