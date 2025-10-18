package go3d.client.gdx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.attributes.{BlendingAttribute, ColorAttribute}
import com.badlogic.gdx.graphics.g3d.{ModelBatch, ModelInstance}
import go3d.Position

trait Marker:
  def render(modelBatch: ModelBatch, position: Option[Position], fadeAlpha: Float): Unit


case class SphereMarker(cursorModel: ModelInstance, boardSize: Int, isGreen: Boolean) extends Marker:
  def render(modelBatch: ModelBatch, targetPos: Option[Position], fadeAlpha: Float): Unit =
    if targetPos.isDefined && fadeAlpha < 1.0f then
      val offset = -(boardSize + 1) / 2f
      cursorModel.transform.setToTranslationAndScaling(
        targetPos.get.x.toFloat + offset,
        targetPos.get.y.toFloat + offset,
        targetPos.get.z.toFloat + offset,
        1.1f, 1.1f, 1.1f
      )
      val tempMaterial = cursorModel.materials.get(0)
      tempMaterial.clear()
      val alpha = 1.0f - fadeAlpha
      if isGreen then
        tempMaterial.set(
          ColorAttribute.createAmbient(Color(0.2f, 0.5f, 0.1f, alpha)),
          ColorAttribute.createDiffuse(Color(0.2f, 0.5f, 0.1f, alpha)),
          ColorAttribute.createSpecular(Color(0.4f, 1.0f, 0.1f, alpha)),
          new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, alpha)
        )
      else
        tempMaterial.set(
          ColorAttribute.createAmbient(Color(0.5f, 0.1f, 0.1f, alpha)),
          ColorAttribute.createDiffuse(Color(0.5f, 0.1f, 0.1f, alpha)),
          ColorAttribute.createSpecular(Color(1.0f, 0.2f, 0.2f, alpha)),
          new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, alpha)
        )
      modelBatch.render(cursorModel)
