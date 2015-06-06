package momijikawa.exercisezmq

object Main extends App {
  import akka.actor._
  import akka.zeromq._

  override def main(args: Array[String]): Unit = {
    val system = ActorSystem("Exercise-ZMQ")

    val subscribingTopic = "ping"

    class Listener extends Actor {
      def receive: Receive = {
        case Connecting =>
          println("Connecting..")
        case Closed =>
          println("Closed")
        case m: ZMQMessage =>
          println(s"ZMQMessage on topic [${m.frame(0).decodeString("UTF-8")}]: ${m.frame(1).decodeString("UTF-8")}")
        case unknown =>
          println("Unknown message: " + unknown)
      }
    }

    val listener = system.actorOf(Props(classOf[Listener]))
    val reqSocket = ZeroMQExtension(system).newSubSocket(
      Array(Listener(listener), Connect("tcp://127.0.0.1:21231")))

    reqSocket ! Subscribe(subscribingTopic)
  }
}