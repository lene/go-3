package go3d.client

import scala.io.StdIn.readLine

class Exit extends RuntimeException

object AsciiClient:

  def main(args: Array[String]): Unit =
    try
      val input = readLine("your input: ")
      val Array(command, args) = (input+" ").split("\\s+", 2)
      command match
        case "new" => newGame(args)
        case "register" => register(args)
        case "set" => set(args)
        case "pass" => pass(args)
        case "status" => status(args)
        case "exit" => throw Exit()
        case "" => throw Exit()
        case _ => println("sorry but nope")
    catch
      case e: Exit => exit(0)
      case e: InterruptedException => exit(1)
    main(Array())

  def newGame(args: String): Unit = println("new board, size "+args)
  def register(args: String): Unit = println("register color "+args)
  def set(args: String): Unit = println("set "+args)
  def pass(args: String): Unit = println("pass "+args)
  def status(args: String): Unit = println("status "+args)

  def exit(status: Int): Unit =
    println("Goodbye.")
    System.exit(status)
