import zio._

// API notes and examples for the ZIO data structure in ZIO

object API {
  
    ////////////////////////////////////////////////////////////////////////
    // absolve

    // def absolve[R, E, A](v: => ZIO[R, E, Either[E, A]]): ZIO[R, E, A]

    //  - submerges the error case of an Either into the ZIO
    //  - We can submerge failures with ZIO.absolve, which is the opposite of
    //      "either" and turns a ZIO[R, Nothing, Either[E, A]] into a ZIO[R, E, A]

    def sqrt(io: UIO[Double]): IO[String, Double] =
        ZIO.absolve {
            io.map( value =>
                if (value < 0.0) 
                    Left("value must be positive") 
                else 
                    Right(Math.sqrt(value))    
            )
        }

    ////////////////////////////////////////////////////////////////////////
    




}
