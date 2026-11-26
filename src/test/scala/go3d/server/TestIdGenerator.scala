package go3d.server

import org.junit.jupiter.api.{Assertions, Test}

class TestIdGenerator:

  @Test def testIdLength(): Unit =
    val id = IdGenerator.getId
    Assertions.assertTrue(IdGenerator.isValidId(id))

  @Test def testIdsAreUnique(): Unit =
    val id1 = IdGenerator.getId
    val id2 = IdGenerator.getId
    Assertions.assertNotEquals(id1, id2)

  @Test def testBase62IdHasTheLastSixDigitsWithLeadingZeros(): Unit =
    Assertions.assertEquals("000000", IdGenerator.base62Id(0))
    Assertions.assertEquals("00000z", IdGenerator.base62Id(61))
    Assertions.assertEquals("000010", IdGenerator.base62Id(62))
    Assertions.assertEquals("023ENq", IdGenerator.base62Id(30_322_950))
    Assertions.assertEquals("zzzzzz", IdGenerator.base62Id(IdGenerator.IdCount - 1))
    Assertions.assertEquals("000000", IdGenerator.base62Id(IdGenerator.IdCount))

  @Test def testBase62IdIsAValidId(): Unit =
    Assertions.assertTrue(IdGenerator.isValidId(IdGenerator.base62Id(-1)))
    Assertions.assertEquals(IdGenerator.base62Id(IdGenerator.IdCount - 1), IdGenerator.base62Id(-1))
