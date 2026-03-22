package go3d.server.http4s

import cats.effect.IO
import org.http4s.Request

/** Extract the client IP address from a request.
 *
 *  Prefers X-Forwarded-For (reverse proxy), then X-Real-IP, then the
 *  direct remote address.  Falls back to "unknown" when nothing is available.
 */
def clientIp(request: Request[IO]): String =
  val headers = request.headers.headers.map(h => h.name.toString.toLowerCase -> h.value).toMap
  headers.get("x-forwarded-for").map(_.split(",").head.trim)
    .orElse(headers.get("x-real-ip"))
    .getOrElse(request.remoteAddr.map(_.toString).getOrElse("unknown"))
