package concurrency.structures.ref

import zio._

// ██████╗ ███████╗███████╗
// ██╔══██╗██╔════╝██╔════╝
// ██████╔╝█████╗  █████╗  
// ██╔══██╗██╔══╝  ██╔══╝  
// ██║  ██║███████╗██║     
// ╚═╝  ╚═╝╚══════╝╚═╝  

// Check out the official documentation for ZIO Refs here:
    // https://github.com/zio/zio/blob/series/2.x/docs/reference/concurrency/ref.md

object Refs /*extends ZIOAppDefault*/ {
    // Refs are instances of mutable state that are shared accross all fibers

    // Refs are essentially one variable that is shared across different fibers

    // Refs are distinct from 'FiberRef' which is the same concept, but only
    //  within the scope of select fibers
    
    // Refs are created with the "make" method
        // 'make' signature:
        // def make[A](a: A): UIO[Ref[A]]

    val ref1 = Ref.make(0)

    // Refs can be any type, but can only be one. 
    val ref2 = Ref.make("")

    // Refs need an initial value to be created
    // val ref3 = Ref.make[String]()   <- does not compile

    // Refs can be created with as complex of data types as you want
    val complex: (Map[String, List[Int]], Map[Int, String], List[String]) = 
        (Map(("" -> List[Int]())), Map((0 -> "")), List[String]())
    
    val refComplex = Ref.make(complex)

    // *Note* don't store mutable data inside refs, but I know you won't because mutable
    // datas are like toilet-spiders to FP devs, don't let them happen to you.

    // Besides the constructor method 'make', there are 4 important methods on Refs:
        // - get
        // - set
        // - update
        // - modify

    // GET ***********************************************************************************
    // Returns current Ref value

    lazy val getRef = 
        for {
            ref <- Ref.make(10)
            returned <- ref.get
        } yield returned

    def get2Ref = 
        Ref.make(0)
            .flatMap(_.get)
            .flatMap(current => Console.printLine(s"current ref value: $current"))

    // *Note* We cant use a reference outside of the monadic operation (for comprehension 
    //   or flatMap chain) that it's created in, meaning we can't try to get one of the Refs
    //   that was created above like ref1

    // val getRefWontCompile = ref1.get

    // SET ***********************************************************************************
    // Sets the current Ref value
    def setThatRef = 
        for {
            ref <- Ref.make("Initial Ref Value")
            _ <- ZIO.debug(ref) // Ref(Initial Ref Value)

            _ <- ref.set("Changed Ref Value")
            _ <- ZIO.debug(ref) // Ref(Changed Ref Value)

            refVal <- ref.get
            _ <- ZIO.debug(refVal) // Changed Ref Value
        } yield ()

    // Note that this way of get/set or set/get is not atomic (executed as one action), 
    //  so when you go all Doc Holliday with these fibers, you might not be getting 
    //  what you think you are.  Which leads us to the second two methods that are 
    //  safer and generally more useful.

    // UPDATE ********************************************************************************
    // Update is an atomic operation on Ref, which can be thought of as a get-set composition,
    // although it's not.  Update is the operation you want if you want to update the state
    // of Ref

    // Note update requires a pure function (if your Ref is of type A, update requires an A => A),
    // update has access to the previous Ref state, this makes the operation like a get-set composition,
    // although it is atomic. If you simply want to set the state of Ref to a constant value, use 'set'
    // instead.
    def updateRef =
        for {
            ref <- Ref.make(0)
            _ <- ref.update(_ + 10)
            _ <- ZIO.debug(ref) // Ref(10)
        } yield ()

    
    def updateRefOps(ref: Ref[Int]) = {
        for {
            _ <- ref.update(_ + 1)
            refGet <- ref.get
            _ <- Console.printLine(s"Current ref: ${refGet}")
        } yield ()
    }

    // example of a use case for Ref and update- a repeat ZIO combinator:
    def repeat[E, A](n: Int)(io: IO[E, A]): IO[E, Unit] = {
        Ref.make(0).flatMap { iRef =>
            def loop: IO[E, Unit] = iRef.get.flatMap { i =>
                if (i < n)
                    io *> iRef.update(_ + 1) *> loop
                else
                    ZIO.unit    
            }
            loop    
        } 
    }

    // MODIFY ********************************************************************************
    // Modify is a more powerful version of update, and the best way I've found to think about it
    // is a "get-set-get" or "update-get" method that is atomic.

    // modify signature:
        // def modify[B](f: A => (B, A)): IO[E, B]

    def modifyRef = 
        for {
            ref <- Ref.make(0)
            newValueReturned <- ref.modify { value => 
                (value + 1, value + 1) // <- how the Ref is updated
            //        ^value returned by modify
            }
            _ <- Console.printLine(s"newValueReturned: ${newValueReturned}, currentRef: ${ref}")
        } yield ()

    // For the modify operator
        //  newVal <- ref.modify { value => 
                // (value + 1, value + 1)   <- tuple2 **see below**
        //        
        // }
        // The value that is returned by the modify operator (newVal) is in tuple2._1,
        // and you can do whatever you want with it- meaning you don't have
        // to return the same value that you updated the Ref with (which is in tuple2._2),
        // in fact you don't even have to return the same type here, so you can use whatever
        // logic you want to return whatever you want, and it uses whatever was "previously"
        // the value of the Ref as a parameter in this pure function.  The Ref is updated
        // with the pure function in tuple2._2, and this does have to return the same type A
        // that you created your Ref as - Ref.make(A)
    
    // Example of modify that returns a different type as what the Ref type is, as well as obviously
    // different logic:
    def modifyRef2 = 
        for {
            ref <- Ref.make(0)
            waldo <- ref.modify { value => 
                ((value.toString + "Waldo").contains("Waldo"), value + 100) // <- how the Ref is updated
            //        ^value returned by modify
            }
            _ <- Console.printLine(s"Did we find Waldo? ${waldo}, currentRef: ${ref}")
        } yield ()
    
    // This is stupid obviously, but I returned a Boolean after applying logic to the original Ref value
    // that was also a String at one point there, the point is that your return value doesn't have to be
    // the same thing as the logic to update the Ref.  So, the full power of modify can be thought of:
        // 1. get  
        // 2. set - with logic  
        // 3. get - with logic  
        // -- ALL ATOMICALLY

    // A simple example...
    def modifyOps(ref: Ref[Int]) = {
        for {
            modVal <- ref.modify(rv => (rv + 1, rv + 1))
            _ <- Console.printLine(s"value after modify: $modVal")
        } yield ()
    }

    // Adding some spices...
    def addApple(ref: Ref[Map[String, Int]]) =
        for {
            oldR <- ref.get
            newR <- ref.modify { rmap => 
                def addMap(m1: Map[String, Int], m2: Map[String, Int]) = {
                    m1.foldLeft(m2) { case (map, (k, v)) => 
                        map.get(k) match {
                            case Some(newV) => map + (k -> (newV + v))
                            case None => map + (k -> v)
                        }    
                    }
                }
                val oneApple: Map[String, Int] = Map("Apple" -> 1)
                (addMap(rmap, oneApple), addMap(rmap, oneApple))    
            }
            _ <- Console.printLine(s"Old Ref: ${oldR}, New Ref: ${newR}")
        } yield ()


    // In Summary, Ref is an amazing tool for building state into applications and sharing data 
    // between fibers, and with operations that are atomic, Ref plays well with concurrency. 
    // Another tool in the toolbox to compose more advanced structures from simple ones.
    // Remember, keep it immutable, keep it safe.  



    def run = for {
        _ <- Console.printLine("Refs")
        _ <- ref1.debug //Ref(0)
        _ <- ref2.debug //Ref()
        _ <- refComplex.debug //Ref((Map( -> List()),Map(0 -> ),List()))
        ref3 <- getRef
        _ <- ZIO.debug(ref3) // 10
        _ <- get2Ref //current ref value: 0
        _ <- setThatRef
        _ <- updateRef
        newRef <- Ref.make(0)
        _ <- updateRefOps(newRef) zipPar updateRefOps(newRef)
        rn <- newRef.get
        _ <- Console.printLine(s"Total updates: ${rn}")
        _ <- repeat(3)(Console.printLine("ZIO")) /// ZIO ZIO ZIO
        _ <- modifyRef // newValueReturned: 1, currentRef: Ref(1)
        _ <- modifyRef2 // Did we find Waldo? true, currentRef: Ref(100)
        newRef2 <- Ref.make(100)
        _ <- modifyOps(newRef2) //value after modify: 101

        fruit <- Ref.make(Map("Banana" -> 3, "Apple" -> 1))
        _ <- addApple(fruit)  //Old Ref: Map(Banana -> 3, Apple -> 1), New Ref: Map(Apple -> 2, Banana -> 3)

    } yield () 


}
