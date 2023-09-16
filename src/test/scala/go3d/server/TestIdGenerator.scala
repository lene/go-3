package go3d.server

import org.junit.jupiter.api.{Assertions, Test}

class TestIdGenerator:

  @Test def testIdLength(): Unit =
    val id = IdGenerator.getId
    Assertions.assertEquals(IdGenerator.IdLength, id.length)

  @Test def testIdsAreUnique(): Unit =
    val id1 = IdGenerator.getId
    val id2 = IdGenerator.getId
    Assertions.assertNotEquals(id1, id2)


