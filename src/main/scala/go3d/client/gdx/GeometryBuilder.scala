package go3d.client.gdx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.g3d.{Material, Model, ModelInstance, RenderableProvider}
import go3d.client.StarPoints
import go3d.{Black, Game, Position, White}

import scala.annotation.tailrec

class GeometryBuilder(boardSize: Int):

  final val CENTER = (boardSize + 1) / 2
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

  val modelBuilder = new ModelBuilder
  val models: Map[String, Model] = createModels(boardSize)
  val gridModel: List[RenderableProvider] = createGrid(boardSize)

  def createStones(game: Game): List[RenderableProvider] =
    stonesOfColor(game, Black).map(createModel(BLACK, _, STONE_RADIUS, game.size)) :++
      stonesOfColor(game, White).map(createModel(WHITE, _, STONE_RADIUS, game.size))

  def createGrid(boardSize: Int): List[RenderableProvider] =
    (1 to boardSize).map(y => horizontalGrid(y, boardSize)).toList :++
      (-boardSize / 2 to boardSize / 2).map(z => verticalGrid(z, boardSize)).toList :++
      createStarPoints(boardSize)

  def dispose(): Unit = models.values.foreach(_.dispose())

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
        boardSize - 1, boardSize - 1, 1, 1,
        GRID_MATERIAL, Usage.Position | Usage.Normal
      ),
      STAR_POINT -> modelBuilder.createSphere(
        STAR_POINT_RADIUS, STAR_POINT_RADIUS, STAR_POINT_RADIUS, 8, 4,
        GRID_MATERIAL, Usage.Position | Usage.Normal
      ),
    )

  def createModel(name: String, pos: Position, scale: Float, boardSize: Int): ModelInstance =
    val instance = new ModelInstance(models(name))
    instance.transform.setToTranslationAndScaling(
      pos.x.toFloat, pos.y.toFloat, pos.z.toFloat, scale, scale, scale
    )
    instance.transform.translate(
      -(boardSize + 1) / 2f, -(boardSize + 1) / 2f, -(boardSize + 1) / 2f
    )
    instance


def stonesOfColor(game: Game, col: go3d.Color): List[Position] =
  @tailrec
  def addStone(positions: Seq[Position], stones: List[Position]): List[Position] =
    if positions.isEmpty then stones
    else if game.at(positions.head) != col then addStone(positions.tail, stones)
    else addStone(positions.tail, stones :+ positions.head)
  addStone(game.goban.allPositions, List())
