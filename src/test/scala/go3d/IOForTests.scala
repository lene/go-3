package go3d

import go3d.server.Games

import java.io.File
import java.nio.file.{Files, Paths}

object IOForTests:

  def exists(filename: String): Boolean =
    Games.checkInitialized()
    Games.fileIO.fold(false)(io => Files.exists(Paths.get(io.baseFolder, filename)))

  def open(filename: String): File =
    Games.checkInitialized()
    Games.fileIO.fold(new File(""))(io => Paths.get(io.baseFolder, filename).toFile)

  def files: List[File] =
    Games.checkInitialized()
    Games.fileIO.fold(List.empty[File])(_.getListOfFiles(".json"))
