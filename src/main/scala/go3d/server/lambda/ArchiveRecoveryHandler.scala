package go3d.server.lambda

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.typesafe.scalalogging.LazyLogging
import go3d.server.service.{ArchiveRecovery, GameService}

import java.util.{List as JList, Map as JMap}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/** AWS Lambda handler for the manual archive recovery job: it archives every finished game that
  * has no archive key and sets its expiry (see [[GameService.recoverArchives]]). It is invoked by
  * hand, with any payload, and returns the ids as `{"archived": [...], "failed": [...]}`; a second
  * run with nothing left to do returns two empty lists.
  *
  * If the games cannot be listed the invocation fails, which shows as a Lambda error.
  *
  * @param service None when DynamoDB is not configured, which fails the invocation
  */
class ArchiveRecoveryHandler(service: Option[GameService])
    extends RequestHandler[JMap[String, AnyRef], JMap[String, JList[String]]]
    with LazyLogging:

  /** Used by the Lambda runtime: DynamoDB and S3 as configured in the environment. */
  def this() = this(LambdaHandler.serviceFromEnv())

  // the Lambda runtime reports a failed invocation by an exception from the handler
  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def handleRequest(input: JMap[String, AnyRef], context: Context): JMap[String, JList[String]] =
    recover() match
      case Success(result) =>
        logger.info(
          s"archive recovery: ${result.archived.size} archived, ${result.failed.size} failed"
        )
        asMap(result)
      case Failure(e) =>
        logger.error("archive recovery failed", e)
        throw e

  private def recover(): Try[ArchiveRecovery] =
    service.fold[Try[ArchiveRecovery]](Failure(ServiceUnavailable()))(_.recoverArchives())

  private def asMap(result: ArchiveRecovery): JMap[String, JList[String]] =
    Map("archived" -> result.archived.asJava, "failed" -> result.failed.asJava).asJava
