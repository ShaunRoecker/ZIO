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

object Queues {
    
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

    // With bounded Queues, they are back-pressured by default meaning that if the queue is 
    // full, any offers to the Queue will be suspended until a value is taken from the queue.

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

    val res5: UIO[Unit] = for {
        queue <- Queue.bounded[String](2)
        _ <- queue.offer("ping")
                .tap(_ => Console.printLine("ping"))
                .forever
                .fork
    } yield ()

    // One thing to consider for bounded Queues with back-pressure is that
    // if the queue is full and the upstream producers are suspended from
    // pushing values to the queue, the downstream consumers have to go 
    // through all the old values before they can process new ones.  This
    // back-pressure strategy doesn't make sense for use cases when new
    // values being pushed to the queue are more important than the older 
    // ones that the queue is currently filled with.  

    // Sliding Queue 
    // With the sliding strategy, when a Queue is full then oldest values
    // are dropped to make room for new values, so there is no suspension
    // and newer values are favored over older ones, this makes sense for 
    // for financial market data.  Another example use case that could 
    // benefit from sliding Queues is weather data for short-term forecasts
    // or storm tracking, where the most recent observations might be 
    // thought to have higher value for making predictions.

    val slidding: UIO[(Int, Int)] = for {
        queue <- Queue.sliding[Int](2)
        _ <- ZIO.foreach(List(1, 2, 3))(queue.offer)
        a <- queue.take
        b <- queue.take

    } yield ((a, b))  // (2, 3)

    // Dropping Queues
    // The dropping strategy is similar to the sliding strategy, but
    // if the queue is full then the value that is trying to be added
    // is dropped. An example use case would be longer-term weather
    // recordings, and the distribution of recordings is more important
    // than having the most recent.
    
    val dropping: UIO[(Int, Int)] = for {
        queue <- Queue.dropping[Int](2)
        _ <- ZIO.foreach(List(1, 2, 3))(queue.offer)
        a <- queue.take
        b <- queue.take

    } yield ((a, b))  // (1, 2)

    // here are some additional methods (combinators) for the ZIO Queue
    // data structure:

    trait QueueInterface[A] {
        // Push/Pull Operations
        def offer(a: A): UIO[Boolean] 
        def take: UIO[A]
        def offerAll(as: Iterable[A]): UIO[Boolean]
        def poll: UIO[Option[A]] // returns optional value of the next value in the queue
        def takeAll: UIO[Chunk[A]]
        def takeUpTo(max: Int): UIO[Chunk[A]]
        def takeBetween(min: Int, max: Int): UIO[Chunk[A]]
        // Metrics
        def capacity: Int // returns the max capacity of the queue
        def size: UIO[Int] // returns the number of values in the queue
        // Shutdown Queues
        def awaitShutDown: UIO[Unit]  // returns an effect that suspends until the queue is shutdown
        def isShutdown: UIO[Boolean] // returns true if the queue is shutdown
        def shutdown: UIO[Unit] // shutdown the queue
    }

    // when working with queues, its good practice to shut them down when finished with them

    // Note: for performace, Queues should be implemented with powes of 2
    val queue32 = for {
        queue <- Queue.bounded[Int](16) // <- 2^4 => 16 
        f1 <- queue.offer(10).fork
        pollVal <- queue.poll
        _ <- ZIO.debug(pollVal)
        _ <- f1.join
    } yield ()

    def producer(queue: Queue[Int]): ZIO[Clock, Nothing, Unit] =
        ZIO.foreachDiscard(0 to 5){ i =>
            queue.offer(i) *> ZIO.sleep(100.milliseconds)    
        }

    def consumer(id: Int)(queue: Queue[Int]): ZIO[Console, Nothing, Nothing] =
        queue.take.flatMap { i => 
            Console.printLine{
                scala.Console.CYAN +
                    s"Consumer $id got $i\n" +
                scala.Console.RESET
            }.!  //-> ".!" is an alias for ".orDie", its why we can have IO
        }.forever       // and Nothing in the error type.

    def example7: ZIO[Console with Clock, Nothing, Unit] = for {
        queue <- Queue.bounded[Int](16)
        producerFiber <- producer(queue).fork
        consumer1Fiber <- consumer(1)(queue).fork
        consumer2Fiber <- consumer(2)(queue).fork
        _ <- producerFiber.join
        _ <- ZIO.sleep(1.second)
        _ <- consumer1Fiber.interrupt
        _ <- consumer2Fiber.interrupt
    } yield ()
    
    
    def run =
        for {
            _ <- Console.printLine("Queues")
            _ <- res1.debug // 1
            _ <- res2
            _ <- res3
            _ <- polled.debug // Some(10)
            _ <- res4
            _ <- res5
            _ <- slidding.debug // (2, 3)
            _ <- dropping.debug // (1, 2)
            _ <- queue32
            
            
            
            
        } yield ()
  
}
