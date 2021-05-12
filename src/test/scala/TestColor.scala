import org.junit.Test
import org.junit.Assert.*

class TestColor:
  @Test def testColorsNotEqual(): Unit = {
    assertNotEquals(go3d.Color.Black, go3d.Color.White)
    assertNotEquals(go3d.Color.Black, go3d.Color.Undefined)
    assertNotEquals(go3d.Color.White, go3d.Color.Undefined)
    assertNotEquals(go3d.Color.Black, go3d.Color.Empty)
    assertNotEquals(go3d.Color.White, go3d.Color.Empty)
    assertNotEquals(go3d.Color.Empty, go3d.Color.Undefined)
  }
