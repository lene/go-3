package go3d.server

import javax.servlet.http.HttpServletResponse

trait ServletOutput:
  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse
  def maxRequestLength: Int
