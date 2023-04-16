package concurrency.structures.promise

import zio._
import java.io.File
import java.io.IOException
import scala.util.Random


// ██████╗ ██████╗  ██████╗ ███╗   ███╗██╗███████╗███████╗
// ██╔══██╗██╔══██╗██╔═══██╗████╗ ████║██║██╔════╝██╔════╝
// ██████╔╝██████╔╝██║   ██║██╔████╔██║██║███████╗█████╗  
// ██╔═══╝ ██╔══██╗██║   ██║██║╚██╔╝██║██║╚════██║██╔══╝  
// ██║     ██║  ██║╚██████╔╝██║ ╚═╝ ██║██║███████║███████╗
// ╚═╝     ╚═╝  ╚═╝ ╚═════╝ ╚═╝     ╚═╝╚═╝╚══════╝╚══════╝
                                                       

// Check out the official documentation for ZIO Queues here:
//  https://github.com/zio/zio/blob/series/2.x/docs/reference/concurrency/promise.md


object Promise_  extends ZIOAppDefault{

    // The purpose of the Promise data type in ZIO is to synchronize work among the
    // running fibers.

    /*
    *   trait Promise[E, A] {
    *      def await: IO[E, A]
    * 
    *      def fail(e: E): UIO[Boolean] 
    * 
    *      def succeed(a: A): UIO[Boolean]
    * 
    *      def complete(io: IO[E, A]): UIO[Boolean]
    *           - complete completes a Promise with the result of an effect
    * 
    *      def completeWith(io: IO[E, A]): UIO[Boolean]
    *           - completeWith completes a promise with the effect itself.
    * 
    *      def isDone: UIO[Boolean]
    * 
    *      def poll: UIO[Option[IO[E, A]]]
    * 
    *      def interrupt: UIO[Boolean]
    *   }
    */
        
    // a Promise can be either empty or it can be full with either
    // a value A or a failure E

    // The value of a promise can be set only once

    // Essentially, a Promise can be used to have one or more fibers wait
    // on the completion of work of other fibers.  So it's use case is whenever
    // we want to hand over work of one fiber to another, or if we need to suspend
    // the execution of a fiber based on another.

    // Promise is useful for when the work or task of one fiber is dependent on
    // the result of another fiber's result, also when the work of one fiber is
    // dependent on the result of another, but in our concurrent application, we 
    // don't know which fiber is going to provide this result.

    // Note** the ZIO Promise data type is equivelent to the Scala promise, 
    // with one difference being the ZIO Promise has two type parameters- 
    // Promise[E, A], as well as needing to call await on the ZIO Promise
    // to wait for the Promise to complete.

    // By calling 'await' on the Promise, the current fiber is suspended until
    // the promise returns it's result.

    // A Promise begins with an empty state, and the Promise can be completed
    // only once, with either the Promise.succeed method to set the success value,
    // or Promise.fail to set the state of the promise to an error

    // This example value represents all the ways that the promise can be completed
    // from the official docs
    val race: IO[String, Int] = for {
        p     <- Promise.make[String, Int]
        _     <- p.succeed(1).fork 
                // the Promise succeeds with 1
        _     <- p.complete(ZIO.succeed(2)).fork 
                // with result of effect IO[E, A] using complete
        _     <- p.completeWith(ZIO.succeed(3)).fork 
                // with effect IO[E, A] using completeWith -
                // first fiber that calls completeWith wins and sets the effect 
                // that will be executed by each awaiting fiber
                // be careful when using p.completeWith(someEffect), 
                // rather use complete
        _     <- p.done(Exit.succeed(4)).fork
                // with Exit[E, A] using done - each await will get this exit propagated
        _     <- p.fail("5")
                // simply fail with E using fail
        _     <- p.failCause(Cause.die(new Error("6")))
                // fail or defect with Cause[E] using failCause
        _     <- p.die(new Error("7"))
                // simply defect with Throwable using die
        _     <- p.interrupt.fork
                // interrupt with interrupt
        value <- p.await

    } yield value


    // The "result" of a Promise is the value that is returned when the 
    // Promise.await method is called, the "return" value of a Promise
    // is the value that is returned when the Promise 
    // succeeds/fails/completes/etc, which is a Boolean value

    val randomInt: UIO[Int] =
        ZIO.succeed(Random.nextInt())

    val returnedVSResult: UIO[Unit] = for {
        p <- Promise.make[Nothing, Int]
        returned <- p.complete(randomInt)  // return: true
        _ <- ZIO.debug(returned)
        result <- p.await  // result: 9763525
        _ <- ZIO.debug(result)
    } yield ()


    // This example shows how we can use promises to suspend the 'right' fiber
    // from printing to the console until the 'left' fiber completes the Promise,
    // and then use the value the Promise was completed with to continue the 
    // effect and print "Hello, World!"

    def awaitPromise = for {
        promise <- Promise.make[Nothing, String]
        // we can force the order of execution of fibers by using Promise
        left <- (Console.print("Hello, ") *> promise.succeed("World")).fork
        right <- (promise.await.flatMap(prom => Console.printLine(prom))).fork
        _ <- left.join *> right.join
        _ <- ZIO.debug(promise)
    } yield ()

    // The act of completing a Promise results in an UIO[Boolean], 
    // where the Boolean represents whether the Promise value has been 
    // set (true) or whether it was already set (false).

    val ioPromise1: UIO[Promise[Exception, String]] = 
        Promise.make[Exception, String]

    val ioBooleanSucceeded: UIO[Boolean] = 
        ioPromise1.flatMap(promise => promise.succeed("I'm done"))  // def succeed(a: A): UIO[Boolean]

    val ioPromise2: UIO[Promise[Exception, Nothing]] = 
        Promise.make[Exception, Nothing]

    val ioBooleanFailed: UIO[Boolean] = 
        ioPromise2.flatMap(promise => promise.fail(new Exception("boom"))) //def fail(e: E): UIO[Boolean]

    // The boolean tells us whether the Promise was successfully set or not,
    // not the value the Promise is set to.

    // To query the state of whether the Promise was successfully completed,
    // we can use poll

    val ioPromise4: UIO[Promise[Exception, String]] = 
        Promise.make[Exception, String]

    val ioIsItDone: UIO[Option[IO[Exception, String]]] = 
        ioPromise4.flatMap(p => p.poll)

    val ioIsItDone2: IO[Option[Nothing], IO[Exception, String]] = 
        ioPromise4.flatMap(p => p.poll.some)




    // //////////////////////////////////////////////////////////////////////////
    // Example of using Promise to wait for a file to be completed to perform
    // some action on the file on another file

    def getFile(path: String): UIO[File] =
        ZIO.succeed {
            val file = new File(path)
            file
        }

    def awaitFile = for {
        promise <- Promise.make[Nothing, File]
        // we can force the order of execution of fibers by using Promise
        getter <- (getFile("test.txt").flatMap(file => promise.succeed(file))).fork
        user <- (promise.await.flatMap(prom => {
                    Console.printLine("Found File?") *> 
                    Console.printLine(s"PATH: ${prom.getAbsolutePath}") 
                })).fork
        _ <- getter.join *> user.join
        _ <- ZIO.debug(promise)
    } yield ()
    // //////////////////////////////////////////////////////////////////////////
    
    val example: ZIO[Any, IOException, Unit] = 
        for {
            promise         <-  Promise.make[Nothing, String]
            sendHelloWorld  =   (ZIO.succeed("hello world") <* ZIO.sleep(1.second)).flatMap(promise.succeed(_))
            getAndPrint     =   promise.await.flatMap(Console.printLine(_))
            fiberA          <-  sendHelloWorld.fork
            fiberB          <-  getAndPrint.fork
            _               <-  (fiberA zip fiberB).join
        } yield ()




    def run = for {
        _ <- Console.printLine("Promises")
        _ <- awaitPromise // Hello, World
        _ <- ioBooleanSucceeded.debug // true
        _ <- ioBooleanFailed.debug // true
        _ <- awaitFile
        _ <- returnedVSResult
        _ <- example
        _ <- ZIO.foreachParDiscard((1 to 4))(Console.printLine(_) *> example)
    } yield ()

}
