package go3d.engine

import go3d.{Black, Pass, Position}
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

class TestEncoding:

  private val size = 5
  private val cells = size * size * size

  private def plane(planes: Array[Float], index: Int): Seq[Float] =
    planes.slice(index * cells, (index + 1) * cells).toSeq

  private def oneAt(cell: Int): Seq[Float] = Seq.tabulate(cells)(c => if c == cell then 1f else 0f)

  @Test def testWorkedExamplePlanes(): Unit =
    val planes = Encoding.encode(workedExample)
    Assertions.assertEquals(Encoding.InputPlanes * cells, planes.length)
    Assertions.assertEquals(oneAt(0), plane(planes, 0))
    Assertions.assertEquals(oneAt((2 * size + 2) * size + 2), plane(planes, 1))
    Assertions.assertEquals(Seq.fill(cells)(1f), plane(planes, 2))
    Assertions.assertEquals(Seq.fill(cells)(2f / 125), plane(planes, 3))
    Assertions.assertEquals(Seq.fill(cells)(0f), plane(planes, 4))

  @Test def testWorkedExampleActions(): Unit =
    Assertions.assertEquals(62, Encoding.action(Position(3, 3, 3), size))
    Assertions.assertEquals(125, Encoding.action(Pass(Black), size))
    Assertions.assertEquals(126, Encoding.actionCount(size))

  @Test def testAfterAPassWhiteIsToMove(): Unit =
    val planes = Encoding.encode(played(size, 0.5, Pass(Black)))
    Assertions.assertEquals(Seq.fill(cells)(0f), plane(planes, 0))
    Assertions.assertEquals(Seq.fill(cells)(0f), plane(planes, 2))
    Assertions.assertEquals(Seq.fill(cells)(1f / 125), plane(planes, 3))
    Assertions.assertEquals(Seq.fill(cells)(1f), plane(planes, 4))

  @Test def testOwnStonesAreThoseOfThePlayerToMove(): Unit =
    val planes = Encoding.encode(workedExample.makeMove(Pass(Black)).success.value)
    Assertions.assertEquals(oneAt((2 * size + 2) * size + 2), plane(planes, 0))
    Assertions.assertEquals(oneAt(0), plane(planes, 1))

  @Test def testPositionIsTheInverseOfAction(): Unit = (0 until cells).foreach(cell =>
    Assertions.assertEquals(cell, Encoding.action(Encoding.position(cell, size), size))
  )
