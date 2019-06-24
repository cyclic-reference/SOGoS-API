package io.acari

import io.acari.http.attachNonSecuredRoutes
import io.acari.http.mountAPIRoute
import io.acari.http.mountSupportingRoutes
import io.acari.memory.MemoryInitializations
import io.acari.security.attachSecurityToRouter
import io.acari.security.setUpOAuth
import io.acari.util.loggerFor
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.vertx.core.Future
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.net.JksOptions
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.core.http.HttpServer
import io.vertx.reactivex.ext.auth.oauth2.OAuth2Auth
import io.vertx.reactivex.ext.mongo.MongoClient
import io.vertx.reactivex.ext.web.Router

class HttpVerticle : AbstractVerticle() {
  companion object {
      private val logger = loggerFor(HttpVerticle::class.java)
  }

  override fun start(startFuture: Future<Void>) {
    val memoryConfiguration = config().getJsonObject("memory")
    val mongoClient = MongoClient.createShared(vertx, memoryConfiguration)
    val configuration = config()
    setUpOAuth(vertx, configuration)
      .zipWith(setUpDB(mongoClient).toSingle { mongoClient },
        BiFunction<OAuth2Auth, MongoClient, Pair<OAuth2Auth, MongoClient>> { oauth2, mongoClientComplet -> Pair(oauth2, mongoClientComplet)})
      .flatMap { pair ->
        val (oauth2, reactiveMongoClient) = pair
        val router = Router.router(vertx)
        val configuredRouter = attachNonSecuredRoutes(router, configuration)
        val securedRoute = attachSecurityToRouter(configuredRouter, oauth2, configuration)
        val supplementedRoutes = mountSupportingRoutes(vertx, securedRoute, configuration)
        val apiRouter = mountAPIRoute(vertx, reactiveMongoClient, supplementedRoutes)
        startServer(apiRouter)
      }
      .subscribe({
        startFuture.complete()
        val jsonObject = configuration.getJsonObject("server")
        logger.info("HTTP${if(jsonObject.getBoolean("SSL-Enabled"))"S" else ""} server started on port ${jsonObject.getInteger("port")}")
      }) {
        startFuture.fail("Unable to start HTTP Verticle because ${it.message}")
      }
  }

  private fun setUpDB(mongoClient: MongoClient) =
    MemoryInitializations.setUpCollections(mongoClient)
      .andThen(MemoryInitializations.registerCodecs(vertx))
      .andThen(MemoryInitializations.registerMemoryWorkers(vertx, mongoClient))

  private fun startServer(router: Router): Single<HttpServer>{
    val serverConfig = config().getJsonObject("server")
    return vertx
      .createHttpServer(
        HttpServerOptions()
        .setSsl(serverConfig.getBoolean("SSL-Enabled"))
        .setKeyStoreOptions(JksOptions()
          .setPassword(serverConfig.getString("Keystore-Password"))
          .setPath(serverConfig.getString("Keystore-Path")))
      )
      .requestHandler(router)
      .rxListen(serverConfig.getInteger("port"))
  }
}
