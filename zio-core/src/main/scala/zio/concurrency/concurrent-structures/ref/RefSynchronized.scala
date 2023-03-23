package concurrency.structures.ref

import zio._

// ██████╗ ███████╗███████╗███████╗██╗   ██╗███╗   ██╗ ██████╗██╗  ██╗██████╗  ██████╗ ███╗   ██╗██╗███████╗███████╗██████╗ 
// ██╔══██╗██╔════╝██╔════╝██╔════╝╚██╗ ██╔╝████╗  ██║██╔════╝██║  ██║██╔══██╗██╔═══██╗████╗  ██║██║╚══███╔╝██╔════╝██╔══██╗
// ██████╔╝█████╗  █████╗  ███████╗ ╚████╔╝ ██╔██╗ ██║██║     ███████║██████╔╝██║   ██║██╔██╗ ██║██║  ███╔╝ █████╗  ██║  ██║
// ██╔══██╗██╔══╝  ██╔══╝  ╚════██║  ╚██╔╝  ██║╚██╗██║██║     ██╔══██║██╔══██╗██║   ██║██║╚██╗██║██║ ███╔╝  ██╔══╝  ██║  ██║
// ██║  ██║███████╗██║██╗  ███████║   ██║   ██║ ╚████║╚██████╗██║  ██║██║  ██║╚██████╔╝██║ ╚████║██║███████╗███████╗██████╔╝
// ╚═╝  ╚═╝╚══════╝╚═╝╚═╝  ╚══════╝   ╚═╝   ╚═╝  ╚═══╝ ╚═════╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═══╝╚═╝╚══════╝╚══════╝╚═════╝ 
                                                                                                                         
// Check out the official documentation for ZIO Ref.Synchronized here:
    // https://github.com/zio/zio/blob/series/2.x/docs/reference/concurrency/refsynchronized.md                                                                                                                       

object RefSynchronized /*extends ZIOAppDefault*/ {

    // Ref.Synchronized is the effectful version of Ref, with almost all of the same operations, however
    // ops like modifyZIO and updateZIO, among others- can be effectful and run these effects to change the
    // shared state of the Ref.Synchronized.

    // simple example:
    def rfsynch1 = 
        for {
            ref <- Ref.Synchronized.make("initial")
            _ <- Console.printLine(s"Initial Ref: $ref")
            anUpdateEffect = ZIO.succeed("update")
            _ <- ref.updateZIO(_ => anUpdateEffect)
            _ <- Console.printLine(s"Updated Ref: $ref")
        } yield ()

    // Ref.Synchronized is great for real world applications, were you want to maybe run an effect to collect
    // data from the outside world, and use such data to update the shared state of your application,
    // this is what Ref.Synchronized allows us to do.

    val getNumEffect: Task[Int] =
        for {
            value <- Console.readLine("Enter a number to update the shared state of this app: ")
                        .map(_.toInt)
        } yield value

        
    def updateWithEffect(effect: Task[Int], ref: Ref.Synchronized[Int]) = {
        ref.updateZIO(_ => effect)
    }


    def updateWithEffect2(ref: Ref.Synchronized[String]) = {
        for {
            _ <- Console.printLine("\npretend like this is a database") *>
                  Console.printLine("or any effectful operation really") *>
                   Console.printLine("Let's get a Pizza to put in the ref...\n\n")
            pizza = ZIO.succeed("pizza")
            _ <- ref.updateZIO(_ => pizza)
        } yield ()
    }
    
    // The following example from the official ZIO docs uses a Map to mimic
    // a database and retrieving data from it, and updating the shared state...
    val users: List[String] = List("User1", "User2")

    object API {
        val users = Map("User1" -> 20, "User2" -> 30)

        def getAge(user: String): UIO[Int] = ZIO.succeed(users(user))
    }

    val meanAge =
        for {
            ref <- Ref.Synchronized.make(0)
            _ <- ZIO.foreachPar(users) { user =>
                    ref.updateZIO(sumOfAges =>
                        API.getAge(user).map(_ + sumOfAges)    
                    )    
                }
            v <- ref.get
        } yield (v / users.length)
   
    // Ref.Sychronized has additional overhead versus Ref, so it is important to do 
    // the least amount of work possible within a modify operation and if it is possible
    // to just use a regular Ref, do that.

    // an example way to get around using Ref.Sychronized, while still using effects...
    // def updateRefAndLog[A](ref: Ref[A])(func: A => A): URIO[Any, Unit] = {
    //     ref.modify { old =>
    //         val nu = func(old)
    //         ((old, nu), nu) // <- we return the old And the new values so we can use them later
    //     }.flatMap { case (old, nu) =>  // rather than putting the effect in the modify operation
    //         Console.printLine(s"updated ${old} to ${nu}")    
    //     }
    // }

    // One of the most common uses cases for Ref.Sychronized is to allocate mutable state 
    // within an update/modify operation, similar to a cache.  The example above of the
    // meanAge is an example of this, where we use the Ref.Synchronized as a mutable state
    // to accumulate values to be aggregated.  One way to think about it is to use the
    // Ref.synchronized similar to the 'acc' in a foldLeft operation where it acts as 
    // a temporary cache while we are aggregating the values within the monad it is operating
    // on. Again, Ref.Synchronized has a higher overhead than Ref, so only use it if you can't
    // solve the problem with Ref, and when you do, do minimal work within a modify operation,
    // and rather do most of your work later, using the returned value with a flatMap or in
    // a for-comprehension.

    
    def run = for {
        _ <- rfsynch1

        ref1 <- Ref.Synchronized.make(0)
        ref1g <- ref1.get
        _ <- ZIO.debug(s"Initial Ref.Synchronized value: ${ref1g}\n")
        _ <- updateWithEffect(getNumEffect, ref1)
        ref1Get <- ref1.get
        _ <- ZIO.debug(s"New Ref.Synchronized value: ${ref1Get}\n")    

        ref2 <- Ref.Synchronized.make("empty")
        _ <- updateWithEffect2(ref2)
        newRef2 <- ref2.get
        _ <- ZIO.debug(s"New Ref2 ${newRef2}\n") //New Ref2 pizza

        age <- meanAge
        _ <- ZIO.debug(age) // 25
         
    } yield ()
    
  
}
