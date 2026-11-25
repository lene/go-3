package go3d.engine

import go3d.Black
import go3d.Game
import go3d.Move
import go3d.Pass
import go3d.Position

/** The board encoding and the action indexing of the model contract (`docs/model-contract.md`):
  * what the network sees of a game, and which of its outputs belongs to which move.
  */
object Encoding:
  /** The input planes of a position: own stones, opponent stones, Black to move, the moves played
    * and whether the previous move was a pass.
    */
  val InputPlanes: Int = 5

  def pointCount(size: Int): Int = size * size * size

  /** Every point plus a pass: size³ + 1. */
  def actionCount(size: Int): Int = pointCount(size) + 1

  def passAction(size: Int): Int = pointCount(size)

  /** The action of a stone at `position`, which is also the index of its cell in a plane. */
  def action(position: Position, size: Int): Int =
    ((position.x - 1) * size + (position.y - 1)) * size + (position.z - 1)

  def action(move: Move | Pass, size: Int): Int =
    move match
      case m: Move => action(m.position, size)
      case _: Pass => passAction(size)

  /** The position of the cell with index `cell < size³`, the inverse of [[action]]. */
  def position(cell: Int, size: Int): Position =
    new Position(cell / (size * size) + 1, (cell / size) % size + 1, cell % size + 1)

  /** The input planes `[5, s, s, s]` of `game`, flattened in row-major order. */
  def encode(game: Game): Array[Float] =
    val size = game.size
    val cells = pointCount(size)
    val toMove = game.moveColor
    val stones = IArray.tabulate(cells)(cell => game.at(position(cell, size)))
    val blackToMove = if toMove == Black then 1f else 0f
    val movesPlayed = game.moves.length.toFloat / cells
    val previousPass = game.moves.lastOption.fold(0f) {
      case _: Pass => 1f
      case _: Move => 0f
    }
    Array.tabulate(InputPlanes * cells) { index =>
      val cell = index % cells
      index / cells match
        case 0 => if stones(cell) == toMove then 1f else 0f
        case 1 => if stones(cell) == !toMove then 1f else 0f
        case 2 => blackToMove
        case 3 => movesPlayed
        case _ => previousPass
    }
