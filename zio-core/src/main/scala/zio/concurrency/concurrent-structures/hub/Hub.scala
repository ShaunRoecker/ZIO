package concurrency.structures.hub

import zio._

object Hubs{
    // Hubs solve a class of problems related to how to distribute work.

    def run = for {
        _ <- Console.printLine("Hubs")

    } yield ()
}
