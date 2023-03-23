package concurrency.structures.queue

import zio._

//  ██████╗ ██╗   ██╗███████╗██╗   ██╗███████╗
// ██╔═══██╗██║   ██║██╔════╝██║   ██║██╔════╝
// ██║   ██║██║   ██║█████╗  ██║   ██║█████╗  
// ██║▄▄ ██║██║   ██║██╔══╝  ██║   ██║██╔══╝  
// ╚██████╔╝╚██████╔╝███████╗╚██████╔╝███████╗
//  ╚══▀▀═╝  ╚═════╝ ╚══════╝ ╚═════╝ ╚══════╝


// Check out the official documentation for ZIO Queues here:
// https://github.com/zio/zio/blob/series/2.x/docs/reference/concurrency/queue.md

object Queues extends ZIOAppDefault {
    
    // Queues in ZIO, like any version of a queue I've ever heard of are a first in - first out 
    // data structure.  They are used as buffers between push and pull interfaces and are used to
    // distribute work to different "consumers".

    // Queues have 2 main operations: which are push and pull operations, and in the ZIO api they
    // are "offer" and "take"

    // There are four main "types" of queues, based on how they handle data that would be overflowing
    // out of the queue when it's at it's size capacity.  These are bounded (back-pressure), dropping,
    // slidding, and unbounded queues.

    // the bounded (default) Queue
    val res1: UIO[Int] = for {
        queue <- Queue.bounded[Int](100)
        _ <- queue.offer(1)
        v1 <- queue.take
    } yield v1

    // Queues are of type A
    // Bounded Queues have a set number of values that can be in the queue

    final case class Structure(a: (String, Int), b: Double)


    val structureList: List[Structure] =
        List(
            Structure(("a", 1), 1.0), 
            Structure(("b", 2), 2.0), 
            Structure(("c", 3), 3.0)
        )

    // 
    val res2: IO[Throwable, Structure] = for {
        queue <- Queue.bounded[Structure](10)
        struct <- ZIO.succeed(structureList)
        _ <- ZIO.foreach(struct)(queue.offer)
        // _ <- queue.offer(struct.head)
        v1 <- queue.take
        _ <- ZIO.debug(v1) // Structure((a,1),1.0)
        _ <- Console.printLine(v1.a) // (a,1)
        _ <- queue.take.tap(n => Console.printLine(s"queue value: ${n}")).forever.fork
        //queue value: Structure((b,2),2.0)
        //queue value: Structure((c,3),3.0)
        _ <- queue.offer(struct.head)
        //queue value: Structure((a,1),1.0)
    } yield v1

    // With bounded Queues, they are back-pressured meaning that if the queue is full,
    // any offers to the Queue will be suspended until a value is taken from the queue.

    val res3: IO[Throwable, Unit] = for {
        queue <- Queue.bounded[String](1)
        _ <- queue.offer("one")
        f <- queue.offer("two").fork // will be suspended because the queue is full
        // use fork to have the fiber wait until there's room in the queue
        _ <- queue.take.tap(n => Console.printLine(n)) // one
        _ <- f.join
        _ <- queue.take.tap(n => Console.printLine(n)) // two
    } yield ()

    // You can consume the first item (or the next item) with poll
    val polled: UIO[Option[Int]] = for {
        queue <- Queue.bounded[Int](100)
        _ <- queue.offer(10)
        _ <- queue.offer(20)
        head <- queue.poll
    } yield head

    // you can offer multiple items with offerAll
    val res4: UIO[Unit] = for {
        queue <- Queue.bounded[Int](100)
        items = Range.inclusive(1, 10).toList
        _ <- queue.offerAll(items)
    } yield ()
    
    
    def run =
        for {
            _ <- Console.printLine("Queues")
            _ <- res1.debug // 1
            _ <- res2
            _ <- res3
            _ <- polled.debug // Some(10)
            _ <- res4
        } yield ()
  
}
//