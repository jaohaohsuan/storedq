package kb.node.storedq

import akka.actor.Actor
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

/**
  * Created by henry on 4/14/16.
  */
class WebServer extends Actor with api.Service {

  val actorRefFactory = context
  implicit val system = context.system
  implicit val materializer = ActorMaterializer()
  implicit val executor = system.dispatcher

  val config = system.settings.config.getConfig("storedq.http")

  val bindingFuture = Http().bindAndHandle(routes, config.getString("interface"), config.getInt("port"))

  override def postStop(): Unit = bindingFuture.flatMap(_.unbind())

  def receive: Receive = {
    case _ =>

  }
}
