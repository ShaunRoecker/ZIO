package zio.concurrency.structures.semaphore

import zio._
//import zio.Clock._ 
import zio.Console._
import java.util.concurrent.TimeUnit

//  Check out the official documentation for ZIO Semaphore here:
    // https://github.com/zio/zio/blob/series/2.x/docs/reference/concurrency/semaphore.md

object Semaphores {

    // Have you ever seen one of those little signs outside of an auditorium, restaurant,
    // or bar that says "Max Occupancy 112" ? A Semaphore is like an enforcer of 
    // this sign, only the Max Occupency is the maximum number of fibers that can be 
    // executing a block of code at a given time.

    // Semaphores are useful when we don't want to overwelm a certain part of our system,
    // by limiting the degree of concurrency with just a simple data structure.

    trait SemaphoreInterface {
        def withPermits[R, E, A](n: Long)(task: ZIO[R, E, A]): ZIO[R, E, A]

        // common shorthand for only issuing 1 permit for a block of code...
        def withPermit[R, E, A](task: ZIO[R, E, A]): ZIO[R, E, A] = 
            withPermits(1)(task)
   
    }

    // example from the official docs:
    
    val task = for {
        _ <- printLine("start")
        _ <- ZIO.sleep(Duration(2, TimeUnit.SECONDS))
        _ <- printLine("end")
    } yield ()

    // In this example, the task is just an effect or series of effects

    val semTask = (sem: Semaphore) => for {
        _ <- sem.withPermit(task)
    } yield ()

    // We can create the semTask, that given a Semaphore- will execute the task
    // with the maximum occupancy limits of the Semaphore

    val semTaskSeq = 
        (sem: Semaphore) => (1 to 3).map(_ => semTask(sem))

    // semTaskSeq is just an Iterator of Semaphore restricted tasks (effects)

    val programSemaphore = for {

        _ <- printLine(scala.Console.CYAN + 
                        "***** Semaphore Permit = 1 Example *****" + 
                            scala.Console.RESET)

        sem <- Semaphore.make(permits = 1)
            // create a limiter of 1 for the semaphore - meaning that whatever
            // block of code we apply this semaphore to will be limited to only
            // executing with one fiber max, and all other would be fibers will
            // suspend
        seq <- ZIO.succeed(semTaskSeq(sem))

        _ <- ZIO.collectAllPar(seq)
            // start  ... wait 2 seconds
            // end
            // start  ... wait 2 seconds
            // end
            // start  ... wait 2 seconds
            // end
            // -- Even though the 3 effects are called in parallel,
            //    the Semaphore limits it to one at a time and we can 
            //    see that in the output
        
       _ <- printLine(scala.Console.CYAN + 
                        "***** Semaphore Permit = 3 Example *****" + 
                            scala.Console.RESET)

        sem2 <- Semaphore.make(permits = 3)
        seq2 <- ZIO.succeed(semTaskSeq(sem2))

        _ <- ZIO.collectAllPar(seq2)
            // start  -instant
            // start  -instant
            // start  -instant ... wait 2 seconds
            // end -instant
            // end -instant
            // end -instant
    } yield ()

    def ioTaskLimiter(task: IO[Throwable, Unit])(implicit sem: Semaphore): IO[Throwable, Unit] = {
        sem.withPermit(task)
    }

    val ioEffect = for {
        output <- ZIO.foreachParDiscard((1 to 3)){_ => 
            Console.printLine("Semaphore") *> 
                ZIO.sleep(Duration(1, TimeUnit.SECONDS))
        }
    } yield output

    val semEffect = for {
        sem <- Semaphore.make(permits = 4)
        _ <- sem.withPermit(ioEffect)
    } yield ()

    val semaphoreEx4 = for {
        _ <- printLine(scala.Console.CYAN + 
                "***** Semaphore Example 4 *****" + 
                    scala.Console.RESET)

        semaphore <- Semaphore.make(2)
        effect = semaphore.withPermit(semEffect)
        _ <- ZIO.foreachParDiscard(1 to 5)(_ => effect)
    } yield ()




    def run = for {
        _ <- programSemaphore
        // _ <- semEffect
        _ <- semaphoreEx4
            

    } yield ()
}
