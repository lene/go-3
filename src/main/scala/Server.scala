package go3d.server

import org.eclipse.jetty.server.{NetworkConnector, Server}
import org.eclipse.jetty.servlet.ServletHandler
import ujson._

import java.util.concurrent.atomic.AtomicInteger
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

object JettyExample {
  val incrementRoute = "/increment"
  val resetRoute = "/reset"
  val server = createServer()
  val handler = new ServletHandler()

  def createServer() = new Server(3333)
  def port() = {
    val conn = server.getConnectors()(0).asInstanceOf[NetworkConnector]
    conn.getLocalPort()
  }
  object CounterServlets {
    private var requestCount = AtomicInteger(0) // encapsulate the state in a Thread safe way

    class IncrementServlet extends HttpServlet {
      override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
        requestCount.getAndIncrement()

        response.setContentType("application/json")
        response.setStatus(HttpServletResponse.SC_OK)
        val output = ujson.Arr(
          ujson.Obj("hello" -> "world", "answer" -> requestCount.get()),
          true
        )
        response.getWriter().println(ujson.write(output))
      }
    }

    class ResetServlet extends HttpServlet {
      override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
        requestCount.set(0)

        response.setContentType("application/json")
        response.setStatus(HttpServletResponse.SC_OK)
        val output = ujson.Arr(
          ujson.Obj("reset" -> "yup", "answer" -> requestCount.get()),
          true
        )
        response.getWriter().println(ujson.write(output))
      }
    }
  }
  def runServer() = {
    print("Hello...")
    server.setHandler(handler)
    handler.addServletWithMapping(classOf[CounterServlets.IncrementServlet], incrementRoute)
    handler.addServletWithMapping(classOf[CounterServlets.ResetServlet], resetRoute)
    println(" World!")
    server.start()
    println(s"Server started on ${port()} with routes: '$incrementRoute'")
    server.join()
  }
}
