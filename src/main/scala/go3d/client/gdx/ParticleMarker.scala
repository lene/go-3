package go3d.client.gdx

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver
import com.badlogic.gdx.graphics.{Color, PerspectiveCamera}
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.{ModelBatch, ModelInstance}
import com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader.ParticleEffectLoadParameter
import com.badlogic.gdx.graphics.g3d.particles.{ParticleEffect, ParticleEffectLoader, ParticleSystem}
import com.badlogic.gdx.graphics.g3d.particles.batches.PointSpriteParticleBatch
import com.badlogic.gdx.math.Vector3
import go3d.Position

trait Marker:
  def render(modelBatch: ModelBatch, position: Option[Position]): Unit


case class SphereMarker(cursorModel: ModelInstance, boardSize: Int) extends Marker:
  def render(modelBatch: ModelBatch, targetPos: Option[Position]): Unit =
    if targetPos.isDefined then
      println(s"rendering cursor at ${targetPos.get}")
      val offset = -(boardSize + 1) / 2f
      cursorModel.transform.setToTranslationAndScaling(
        targetPos.get.x.toFloat + offset,
        targetPos.get.y.toFloat + offset,
        targetPos.get.z.toFloat + offset,
        1.1f, 1.1f, 1.1f
      )
      val tempMaterial = cursorModel.materials.get(0)
//      cursorModel.materials.clear()
      tempMaterial.clear()
      tempMaterial.set(
        ColorAttribute.createAmbient(Color(0.2, 0.5, 0.1, 0.2)),
        ColorAttribute.createDiffuse(Color(0.2, 0.5, 0.1, 0.2)),
        ColorAttribute.createSpecular(Color(0.4, 1.0, 0.1, 0.4))
      )
//      cursorModel.materials.set(0, tempMaterial)
//      println(s"cursor material: ${cursorModel.materials.get(0)}")
      modelBatch.render(cursorModel)

case class ParticleMarker(assetFile: String, camera: PerspectiveCamera) extends Marker:
  private val particleSystem: ParticleSystem = createParticleSystem
  private var currentEffects: Option[ParticleEffect] = None

  private def createParticleSystem: ParticleSystem =
    val pointSpriteBatch = PointSpriteParticleBatch()
    pointSpriteBatch.setCamera(camera)
    val particles = ParticleSystem()
    particles.add(pointSpriteBatch)
    currentEffects = Some(createEffects(particles))
    currentEffects.foreach(_.init())
    particles.add(currentEffects.get)
    particles

  private def createEffects(particles: ParticleSystem): ParticleEffect =
    val assets = AssetManager()
    val loadParam = ParticleEffectLoadParameter(particles.getBatches);
    val loader = ParticleEffectLoader(InternalFileHandleResolver())
    assets.setLoader(classOf[ParticleEffect], loader)
    assets.load(assetFile, classOf[ParticleEffect], loadParam)
    // halt main thread until assets are loaded. bad for production, okay for demonstration purposes
    assets.finishLoading()
    assets.get(assetFile, classOf[ParticleEffect]).copy

  private def translateParticleEffect(translation: Vector3): Unit =
    currentEffects.foreach(_.getControllers.forEach(controller => controller.translate(translation)))

  def render(modelBatch: ModelBatch, position: Option[Position]): Unit =
    if position.isDefined then
      val targetPos = Vector3(position.get.x.toFloat, position.get.y.toFloat, position.get.z.toFloat)
//      currentEffects.foreach(_.translate(targetPos))
      translateParticleEffect(targetPos)
      particleSystem.update()
      particleSystem.begin()
      particleSystem.draw()
      particleSystem.end()
      modelBatch.render(particleSystem)
