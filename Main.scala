package momijikawa.exercisezmq



object Main extends App {
  import akka.actor._
  import akka.zeromq._
  import akka.util.ByteString
  import akka.pattern.ask
  import concurrent.duration._
  import collection.immutable.Seq
  import scala.concurrent.ExecutionContext
  import scala.util.{Failure, Success}

  class Listener(sessioner: ActorRef) extends Actor with ActorLogging {
    import log.{debug, warning}
    def receive: Receive = {
      case m: ZMQMessage =>
        debug("ZMQMessage: " + m.frame(0).decodeString("UTF-8"))
        sessioner ! ('rep, m)

      case Connecting => debug("Connecting..")
      case Closed => debug("Closed")

      case unknown => warning("Unknown message received: " + unknown)
    }
  }

  class Sessioner extends Actor with ActorLogging {
    import log.{debug, warning, error => logError}
    var sock: ActorRef = null
    val mapping = collection.mutable.Map[Int, ActorRef]()
    implicit val execContext: ExecutionContext = context.dispatcher
    def receive = inactive
    def inactive: Receive = {
      case ('defsock, a: ActorRef) =>
        debug("actor has been activated")
        sock = a
        context.become(active)
      case unknown => log.error("Unknown message received: " + unknown)
    }
    def active: Receive = {
      case x: Seq[ByteString] =>
        import scala.util.Random
        debug("sending message, recipient is " + sender.toString())
        val randInt = Random.nextInt()
        debug("seq no " + randInt)
        mapping += randInt -> sender
        debug("now mapping is " + mapping.toString())
        val sendingFrames: Seq[ByteString] = ByteString(randInt.toString) +: x
        sock ! ZMQMessage(sendingFrames)

      case ('rep, m: ZMQMessage) =>
        debug("reply incoming")
        val key = m.frame(0).decodeString("UTF-8").toInt
        if(mapping.contains(key)) {
          val recipient = mapping(key)
          debug("recipient found: " + recipient.toString())
          mapping -= key
          recipient ! m.frames.tail
        } else {
          warning("no recipient matches: " + m.frame(0))
          debug("now mapping is " + mapping.toString())
        }

      case unknown => logError("Unknown message received: " + unknown)
    }
  }

  override def main(args: Array[String]): Unit = {
    val system = ActorSystem("Exercise-ZMQ")
    implicit val timeout: akka.util.Timeout = 20 seconds
    implicit val execContext: ExecutionContext = system.dispatcher

    val sessioner = system.actorOf(Props[Sessioner], name = "sessioner")
    val listener = system.actorOf(Props(classOf[Listener], sessioner), "listener")
    val reqSocket = ZeroMQExtension(system).newReqSocket(Array(Listener(listener), Connect("tcp://127.0.0.1:21231")))
    sessioner ! ('defsock, reqSocket)

    while(true) {
      print("send?> ")
      val readString = readLine()
      (sessioner ? Seq(ByteString(readString))).mapTo[Seq[ByteString]] onComplete {
        case Success(xs) => println(xs)
        case Failure(why) => println(why.getMessage)
      }
    }
  }
}