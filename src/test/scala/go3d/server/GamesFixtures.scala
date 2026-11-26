package go3d.server

import go3d.Komi
import org.scalatest.TryValues.*

/** Registers a new game of `size` with the default komi in [[Games]]; returns its id. */
def registerGame(size: Int): String = Games.register(size, Komi.Default).success.value
