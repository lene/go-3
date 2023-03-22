package go3d

import go3d.server.Io

import java.io.File
import java.nio.file.{Files, Paths}

object XIO:

  def exists(filename: String): Boolean =
    if Io.basePath == null then throw IllegalArgumentException("call Io.init() before using Io")
    Files.exists(Paths.get(Io.baseFolder, filename))

  def open(filename: String): File = Paths.get(Io.baseFolder, filename).toFile


