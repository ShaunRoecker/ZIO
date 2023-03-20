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

object RefSynchronized extends ZIOAppDefault {

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

   

    
    def run = for {
        _ <- rfsynch1
        
    } yield ()
    
  
}
