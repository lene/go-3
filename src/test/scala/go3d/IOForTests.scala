package go3d

import go3d.server.Games

import java.io.File
import java.nio.file.{Files, Paths}

object IOForTests:

  def exists(filename: String): Boolean =
    Games.checkInitialized()
    Files.exists(Paths.get(Games.fileIO.get.baseFolder, filename))

  def open(filename: String): File =
    Games.checkInitialized()
    Paths.get(Games.fileIO.get.baseFolder, filename).toFile

  def files: List[File] =
    Games.checkInitialized()
    Games.fileIO.get.getListOfFiles(".json")

