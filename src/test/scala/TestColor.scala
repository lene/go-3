import org.junit.{Assert, Test}

class TestColor:
  @Test def testColorsNotEqual(): Unit =
    Assert.assertNotEquals(go3d.Color.Black, go3d.Color.White)
    Assert.assertNotEquals(go3d.Color.Black, go3d.Color.Undefined)
    Assert.assertNotEquals(go3d.Color.White, go3d.Color.Undefined)
    Assert.assertNotEquals(go3d.Color.Black, go3d.Color.Empty)
    Assert.assertNotEquals(go3d.Color.White, go3d.Color.Empty)
    Assert.assertNotEquals(go3d.Color.Empty, go3d.Color.Undefined)
    Assert.assertNotEquals(go3d.Color.Black, go3d.Color.Sentinel)
    Assert.assertNotEquals(go3d.Color.White, go3d.Color.Sentinel)
    Assert.assertNotEquals(go3d.Color.Empty, go3d.Color.Sentinel)
