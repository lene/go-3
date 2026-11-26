package go3d.server.lambda

import go3d.server.service.{
  FailingGameStore, GameService, InMemoryGameArchive, InMemoryGameStore, ServiceFixtures
}
import org.junit.jupiter.api.{Assertions, Test}

import java.util.{Map as JMap}
import scala.jdk.CollectionConverters._

// The AWS Lambda API allows a null Context and the handler does not use it.
@SuppressWarnings(Array("org.wartremover.warts.Null"))
class TestArchiveRecoveryHandler:

  private val store = InMemoryGameStore()
  private val payload = JMap.of[String, AnyRef]()

  private def serviceWith(archiveFails: Boolean): GameService =
    GameService(store, InMemoryGameArchive(archiveFails))

  /** A finished game whose archive failed at the end of the game; returns its id. */
  private def gameWithFailedArchive(): String =
    ServiceFixtures.gameWithFailedArchive(serviceWith(archiveFails = true))

  private def run(handler: ArchiveRecoveryHandler): Map[String, List[String]] =
    handler.handleRequest(payload, null).asScala.view.mapValues(_.asScala.toList).toMap

  @Test def testReturnsArchivedIdsAndNothingOnSecondRun(): Unit =
    val gameId = gameWithFailedArchive()
    val handler = new ArchiveRecoveryHandler(Some(serviceWith(archiveFails = false)))
    Assertions.assertEquals(
      Map("archived" -> List(gameId), "failed" -> List.empty[String]),
      run(handler)
    )
    Assertions.assertEquals(
      Map("archived" -> List.empty[String], "failed" -> List.empty[String]),
      run(handler)
    )

  @Test def testReturnsFailedIds(): Unit =
    val gameId = gameWithFailedArchive()
    val handler = new ArchiveRecoveryHandler(Some(serviceWith(archiveFails = true)))
    Assertions.assertEquals(
      Map("archived" -> List.empty[String], "failed" -> List(gameId)),
      run(handler)
    )

  @Test def testFailsWhenStoreCannotBeScanned(): Unit =
    val handler =
      new ArchiveRecoveryHandler(Some(GameService(FailingGameStore(), InMemoryGameArchive(false))))
    Assertions.assertThrows(classOf[IllegalStateException], () => run(handler))

  @Test def testFailsWithoutStore(): Unit =
    Assertions.assertThrows(
      classOf[ServiceUnavailable],
      () => run(new ArchiveRecoveryHandler(None))
    )

  @Test def testDefaultConstructorWithoutDynamoDBFails(): Unit =
    Assertions.assertThrows(classOf[ServiceUnavailable], () => run(new ArchiveRecoveryHandler()))
