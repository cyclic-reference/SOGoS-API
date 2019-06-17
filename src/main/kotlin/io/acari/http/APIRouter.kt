package io.acari.http

import io.acari.security.createVerificationHandler
import io.acari.util.loggerFor
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.reactivex.ext.mongo.MongoClient

private val logger = loggerFor("APIRouter")

fun mountAPIRoute(vertx: Vertx, mongoClient: MongoClient, router: Router): Router {
  router.mountSubRouter("/api", createAPIRoute(vertx, mongoClient))

  // Static content path must be mounted last, as a fall back
  router.get("/*")
    .failureHandler { routingContext ->
      val statusCode = routingContext.statusCode()
      if(statusCode != 401 && statusCode != 403){
        routingContext.reroute("/")
      } else {
        routingContext.response().setStatusCode(404).end()
      }
    }

  return router
}


fun createAPIRoute(vertx: Vertx, mongoClient: MongoClient): Router {
  val router = Router.router(vertx)
  router.get("/user").handler(createUserHandler(vertx, mongoClient))
  router.mountSubRouter("/history", createHistoryRoutes(vertx, mongoClient))
  router.route().handler(createVerificationHandler())
  router.mountSubRouter("/activity", createActivityRoutes(vertx, mongoClient))
  return router
}
