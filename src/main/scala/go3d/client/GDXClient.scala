package go3d.client

import go3d.client.gdx.GobanDisplay

import com.badlogic.gdx.backends.lwjgl3.{Lwjgl3Application, Lwjgl3ApplicationConfiguration}

object GDXClient extends InteractiveClient:

    final val COLOR_BITS = 8
    final val DEPTH_BITS = 16
    final val STENCIL_BITS = 0
    final val NUM_ANTIALIAS_SAMPLES = 4

    def mainLoop(args: Array[String]): Unit =
        val config = getConfiguration("3D Go", 1280, 960)
        new Lwjgl3Application(new GobanDisplay(client), config)

    def getConfiguration(appName: String, width: Int, height: Int): Lwjgl3ApplicationConfiguration =
        val config = new Lwjgl3ApplicationConfiguration()
        config.disableAudio(true)
        config.setTitle(appName)
        config.setWindowedMode(width, height)
        config.setBackBufferConfig(
            COLOR_BITS, COLOR_BITS, COLOR_BITS, COLOR_BITS, DEPTH_BITS, STENCIL_BITS,
            NUM_ANTIALIAS_SAMPLES
        )
        config
