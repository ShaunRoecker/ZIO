package concurrency.structures.hub

import zio._
import zio.Chunk


// ██╗  ██╗██╗   ██╗██████╗ 
// ██║  ██║██║   ██║██╔══██╗
// ███████║██║   ██║██████╔╝
// ██╔══██║██║   ██║██╔══██╗
// ██║  ██║╚██████╔╝██████╔╝
// ╚═╝  ╚═╝ ╚═════╝ ╚═════╝ 

// Check out the official documentation for ZIO Queues here:
//  https://github.com/zio/zio/blob/series/2.x/docs/reference/concurrency/hub.md


object Hubs extends ZIOAppDefault {
    // Hubs solve a class of problems related to how to distribute work.

    // Hubs follow the Publish/Subscribe model, where publishers add values
    // to the Hub and those values are then recieved by all the subscribers
    // to the hub

    // The publish/subscribe Hub api:
    // trait Hub[A] {
    //     def publish(a: A): UIO[Boolean]
    //     def subscribe: ZIO[Scope, Nothing, Dequeue[A]]
    // } 
    
    // the 'Publish' method publishes a value of type A to the hub,
    // and returns a boolean as to whether that value A was successfully
    // publishes to the Hub. 

    // The 'Subscribe' method returns a scoped effect in which a subscriber 
    // is subscribed within the scope of the effect, and unsubscribed when 
    // the scope is exited.

    // Before the current Hub data type was implemented in ZIO 2.0, in order to 
    // solve the problem of broadcasting work, this was solved by using a Chunk 
    // of Queues, with a Queue for each subscriber, that solution would look
    // something like this:

    final case class NaiveHub[A](queues: Chunk[Queue[A]]) {
        def offer(a: A): ZIO[Any, Nothing, Unit] =
            ZIO.foreachDiscard(queues)(_.offer(a))

        def take(n: Int): ZIO[Any, Nothing, A] =
            queues(n).take
    }

    object NaiveHub {
        def make[A](n: Int): ZIO[Any, Nothing, NaiveHub[A]] =
            ZIO.foreach(Chunk.fromIterable(0 until n))( _ => Queue.bounded[A](16)) // Create a Queue
                .map(qs => NaiveHub(qs)) // For each of these Queues, put it inside the NaiveHub
    }

    val naiveHub: ZIO[Any, Any, Any] = for {
        hub <- NaiveHub.make[Int](3)
        _ <- hub.offer(1)
        a <- hub.take(0)
        _ <- ZIO.debug(a)
    } yield ()

    def hubConsumer(label: String, index: Int)(hub: NaiveHub[Int]): ZIO[Any, Nothing, Nothing] = 
        hub.take(index).flatMap { i =>
            Console.printLine(s"$label got $i").!
        }.forever

    // Before the current ZIO Hub, there was a Queue for every consumer

    // NaiveHub
    // index   0 1 2 3 4
    // Queue1 | |2|3|4|5|
    // Queue2 | | |3|4| |

    // With the Hub data structure, there is one array for all consumers
    //   and where each is on the consuming of the values in the Hub is 
    //   kept track of by its index

    // Hub
    // index  0 1 2 3 4
    // Array | |2|3|4|5| |
    //  consumer 1 index == 1
    //  consumer 3 index == 2

    //   ZIO Hub
    def ZIOHubConsumer(label: String)(hub: Hub[Int]): ZIO[Any, Nothing, Nothing] = 
        ZIO.scoped {
            hub.subscribe.flatMap { dequeue => 
                dequeue.take.flatMap { i =>
                    Console.printLine(s"$label got $i").!   
                }.forever
            }
        }



    // One important thing about subscribe is that the subscribe operator returns 
    // a scoped ZIO effect that subscribes to the hub and unsubscribes from the hub 
    // when the scope is closed.
    val hub1: ZIO[Any, Nothing, Unit] = Hub.bounded[String](2).flatMap { hub =>
        ZIO.scoped {
            hub.subscribe.zip(hub.subscribe).flatMap { case (left, right) =>
                for {
                    _ <- hub.publish("Hello from a hub!")
                    _ <- left.take.flatMap(Console.printLine(_)).!
                    _ <- right.take.flatMap(Console.printLine(_)).!
                } yield ()
            }
        }
    }
    // A subscriber will only receive messages that are published to the hub while it 
    // is subscribed. We can do this by publishing a message to the hub within the scope 
    // of the subscription as in the example above or by using other coordination 
    // mechanisms such as completing a PROMISE when scope has been opened.
    // 
   

    // The bounded constructor is the most common way to create a hub, also note that for 
    // maxiumum efficiency, its important to use powers of two for the Hub capacity.

    // This method creates a hub with a set capacity of the next power of two from the
    // value entered as requestedCapacity, so if you want a Hub with capacity of 100,
    // this method will create a hub with capacity of 128, if you want a Hub with capacity of
    // 129, it will set it to 256
    def boundedHubOptimized[A](requestedCapacity: Int): UIO[Hub[A]] = {
      import scala.math._
      val optimizedCapacity: Int = 
        pow(2, ((log(requestedCapacity.toDouble) / log(2d)).floor + 1d)).toInt
      Hub.bounded[A](optimizedCapacity)
    } 


    val hub2: ZIO[Any, Nothing, Unit] = 
        boundedHubOptimized[String](100).flatMap { hub => 
            ZIO.scoped {
                for {
                    _ <- hub.subscribe.flatMap { sub =>
                            for {
                                _ <- hub.publish("hub optimized for capacity")
                                _ <- sub.take.flatMap(Console.printLine(_)).!
                            } yield ()
                    }
                    _ <- ZIO.debug(hub.capacity) // capacity: 128
                } yield ()
            }
        }


    def run = for {
        _ <- Console.printLine("Hubs")
        _ <- hub1
        _ <- naiveHub // 1
        _ <- hub2
    } yield ()
}
