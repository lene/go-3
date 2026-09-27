package go3d.client

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import go3d.client.gdx.GobanDisplay
import go3d.client.gdx.ReplayState

import scala.util.Try

object GDXClient extends InteractiveClient:

    private final val COLOR_BITS = 8
    private final val DEPTH_BITS = 16
    private final val STENCIL_BITS = 0
    private final val NUM_ANTIALIAS_SAMPLES = 4

    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    private[client] var replayEnabled: Boolean = false
    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    private[client] var replayFrom: Int = 0
    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    private[client] var replayTo: Int = Int.MaxValue
    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    private[client] var replaySpeed: Float = 1.0f
    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    private[client] var cursorFade: Float = 10.0f

    override def parseArgs(args: Array[String]): Try[BaseClient] =
        val result = super.parseArgs(args)
        result.foreach { _ =>
            val conf = new ClientCLIConf(args.toList)
            replayEnabled = conf.replay()
            replayFrom    = conf.from()
            replayTo      = conf.to()
            replaySpeed   = conf.replaySpeed()
            cursorFade    = conf.cursorFadeSeconds()
        }
        result

    def mainLoop(client: BaseClient): Try[Unit] =
        println("Starting 3D Go client")
        client.status.map(status => startApplication(client, status.game.size))

    private def startApplication(client: BaseClient, boardSize: Int): Unit =
        val config = getConfiguration("3D Go", 1280, 960)
        if replayEnabled then
            val state = new ReplayState(client, replayFrom, replayTo, replaySpeed)
            state.init()
            new Lwjgl3Application(
                new GobanDisplay(client, boardSize, cursorFade, Some(state)), config
            )
        else
            new Lwjgl3Application(new GobanDisplay(client, boardSize, cursorFade), config)

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
