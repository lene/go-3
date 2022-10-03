package go3d.client

import go3d.newGame
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration

import scala.annotation.tailrec

object GDXClient:

    type OptionMap = Map[String, Int|String]
    final val defaults: OptionMap = Map("size" -> 7)
    final val COLOR_BITS = 8
    final val DEPTH_BITS = 16
    final val STENCIL_BITS = 0
    final val NUM_ANTIALIAS_SAMPLES = 4

    def main(args: Array[String]): Unit = {
        val options = nextOption(defaults, args.toList)
        val config = getApplicationConfiguration("3D Go", 640, 480)
        new Lwjgl3Application(
            new GDXClient2(Util.gameWithCornerStones(options("size").asInstanceOf[Int])),
            config
        )
    }

    def getApplicationConfiguration(applicationName: String, width: Int, height: Int): Lwjgl3ApplicationConfiguration =
        val config = new Lwjgl3ApplicationConfiguration()
        config.disableAudio(true)
        config.setTitle(applicationName)
        config.setWindowedMode(width, height)
        config.setBackBufferConfig(
            COLOR_BITS, COLOR_BITS, COLOR_BITS, COLOR_BITS, DEPTH_BITS, STENCIL_BITS,
            NUM_ANTIALIAS_SAMPLES
        )
        config
    @tailrec
    def nextOption(map: OptionMap, list: List[String]): OptionMap =
        list match
            case Nil => map
            case "--size" :: value :: tail =>
                nextOption(map ++ Map("size" -> value.toInt), tail)
            case option :: tail =>
                println("Unknown option " + option)
                System.exit(1)
                map

