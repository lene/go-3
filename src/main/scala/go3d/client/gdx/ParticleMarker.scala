package go3d.client.gdx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.{ModelBatch, ModelInstance}
import go3d.Position

trait Marker:
  def render(modelBatch: ModelBatch, position: Option[Position]): Unit


case class SphereMarker(cursorModel: ModelInstance, boardSize: Int, isGreen: Boolean) extends Marker:
  def render(modelBatch: ModelBatch, targetPos: Option[Position]): Unit =
    if targetPos.isDefined then
      val offset = -(boardSize + 1) / 2f
      cursorModel.transform.setToTranslationAndScaling(
        targetPos.get.x.toFloat + offset,
        targetPos.get.y.toFloat + offset,
        targetPos.get.z.toFloat + offset,
        1.1f, 1.1f, 1.1f
      )
      val tempMaterial = cursorModel.materials.get(0)
      tempMaterial.clear()
      if isGreen then
        tempMaterial.set(
          ColorAttribute.createAmbient(Color(0.2, 0.5, 0.1, 0.2)),
          ColorAttribute.createDiffuse(Color(0.2, 0.5, 0.1, 0.2)),
          ColorAttribute.createSpecular(Color(0.4, 1.0, 0.1, 0.4))
        )
      else
        tempMaterial.set(
          ColorAttribute.createAmbient(Color(0.5, 0.1, 0.1, 0.2)),
          ColorAttribute.createDiffuse(Color(0.5, 0.1, 0.1, 0.2)),
          ColorAttribute.createSpecular(Color(1.0, 0.2, 0.2, 0.4))
        )
      modelBatch.render(cursorModel)
