package go3d.testing

import org.junit.{Assert, Test}
import go3d.server._

class TestIdGenerator:

  @Test def testIdLength(): Unit =
    val id = IdGenerator.getId
    Assert.assertEquals(IdGenerator.IdLength, id.length)

  @Test def testIdsAreUnique(): Unit =
    val id1 = IdGenerator.getId
    val id2 = IdGenerator.getId
    Assert.assertNotEquals(id1, id2)


