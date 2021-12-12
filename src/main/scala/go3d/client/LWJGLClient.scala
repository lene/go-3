package go3d.client

import go3d.BadColor
import go3d.server.StatusResponse
import org.lwjgl.Version
import org.lwjgl.glfw.{GLFWErrorCallback, GLFWKeyCallback, GLFWVidMode}
import org.lwjgl.opengl.GL
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.{
  glfwWindowShouldClose, glfwSwapBuffers, glfwSetWindowShouldClose, GLFW_RELEASE, GLFW_KEY_ESCAPE,
  glfwShowWindow, glfwSwapInterval, glfwMakeContextCurrent,
  glfwSetKeyCallback, glfwCreateWindow, glfwWindowHint, GLFW_RESIZABLE, GLFW_TRUE, GLFW_VISIBLE,
  GLFW_FALSE, glfwPollEvents, glfwTerminate, glfwSetErrorCallback, glfwInit, glfwDefaultWindowHints
}
import org.lwjgl.opengl.GL11.{glClearColor, glClear, GL_COLOR_BUFFER_BIT, GL_DEPTH_BUFFER_BIT}
import org.lwjgl.system.MemoryUtil.NULL
import java.io.IOException
import java.net.{ConnectException, UnknownHostException}

object LWJGLClient extends InteractiveClient:
  private var window: Long = 0

  def mainLoop(args: Array[String]): Unit =
    System.out.println("Using LWJGL " + Version.getVersion() + "!")

    // This line is critical for LWJGL's interoperation with GLFW's OpenGL context, or any context
    // that is managed externally.
    // LWJGL detects the context that is current in the current thread, creates the GLCapabilities
    // instance and makes the OpenGL bindings available for use.
    GL.createCapabilities()

    glClearColor(1.0, 0.0, 0.0, 0.0)

    while (!glfwWindowShouldClose(window))
      glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT) // clear the framebuffer

      glfwSwapBuffers(window) // swap the color buffers

      // Poll for window events. The key callback above will only be invoked during this call.
      glfwPollEvents()

    // Free the window callbacks and destroy the window
    glfwFreeCallbacks(window)
    // Terminate GLFW and free the error callback
    glfwTerminate()
    glfwSetErrorCallback(null).free()

  override def init(): Unit =
    // Setup an error callback. The default implementation will print error messages in System.err.
    GLFWErrorCallback.createPrint(System.err).set()

    // Initialize GLFW. Most GLFW functions will not work before doing this.
    if (!glfwInit())
      throw new IllegalStateException("Unable to initialize GLFW")

    // Configure our window
    glfwDefaultWindowHints() // optional, the current window hints are already the default
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // the window will stay hidden after creation
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE) // the window will be resizable

    val WIDTH: Int = 300
    val HEIGHT: Int = 300

    // Create the window
    window = glfwCreateWindow(WIDTH, HEIGHT, "Hello World!", NULL, NULL)

    if (window == NULL)
      throw new RuntimeException("Failed to create the GLFW window!")

    // Setup a key callback. It will be called every time a key is pressed, repeated or released.
    val kb = new KeyboardHandler()
    glfwSetKeyCallback(window, kb)

    glfwMakeContextCurrent(window)  // Make the OpenGL context current
    glfwSwapInterval(1)  // Enable v-sync
    glfwShowWindow(window)  // Make the window visible


class KeyboardHandler extends GLFWKeyCallback:
  def invoke(window: Long, key: Int, scancode: Int, action: Int, mods: Int): Unit =
    (key, action) match
      case (GLFW_KEY_ESCAPE, GLFW_RELEASE) => glfwSetWindowShouldClose(window, true) // We will detect this in our rendering loop
      case _ =>