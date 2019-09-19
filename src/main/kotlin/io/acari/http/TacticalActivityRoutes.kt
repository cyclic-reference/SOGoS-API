package io.acari.http

import io.acari.memory.Effect
import io.acari.memory.TacticalActivitySchema
import io.acari.memory.user.EFFECT_CHANNEL
import io.acari.security.USER_IDENTIFIER
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.mongo.MongoClient
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.Router.router
import java.time.Instant
import java.time.temporal.ChronoUnit

const val STARTED_TACTICAL_ACTIVITY = "STARTED_TACTICAL_ACTIVITY"
const val REMOVED_TACTICAL_ACTIVITY = "REMOVED_TACTICAL_ACTIVITY"
const val UPDATED_TACTICAL_ACTIVITY = "UPDATED_TACTICAL_ACTIVITY"

private val mappings = mapOf(
  CREATED to STARTED_TACTICAL_ACTIVITY,
  UPDATED to UPDATED_TACTICAL_ACTIVITY,
  DELETED to REMOVED_TACTICAL_ACTIVITY
)

private fun mapTypeToEffect(uploadType: String): String =
  mappings[uploadType] ?: STARTED_ACTIVITY


fun createTacticalActivityRoutes(vertx: Vertx, mongoClient: MongoClient): Router {
  val router = router(vertx)

  router.get("/").handler { requestContext ->
    val request = requestContext.request()
    val userIdentifier = request.getParam("userIdentifier")
    val response = requestContext.response()
    val meow = Instant.now()

    response.isChunked = true
    response.putHeader(HttpHeaderNames.CONTENT_TYPE, JSON_STREAM)
    mongoClient.findBatch(
      TacticalActivitySchema.COLLECTION, jsonObjectOf(
        TacticalActivitySchema.GLOBAL_USER_IDENTIFIER to userIdentifier,
        TacticalActivitySchema.REMOVED to false

      )
    ).toFlowable()
      .map {
//        Json.encode(activityFromJson(it))
      }
      .subscribe({
//        response.write(it)
      }, {
//        logger.warn("Unable to fetch activity feed for $userIdentifier because reasons.", it)
      }, {
        response.end()
      })
  }


  //todo: sharing and caring
  router.post("/bulk").handler { requestContext ->
    val bodyAsJsonArray = requestContext.bodyAsJsonArray
    val userIdentifier = requestContext.request().headers().get(USER_IDENTIFIER)
    bodyAsJsonArray.stream()
      .map { it as JsonObject }
      .filter { cachedActivity ->
        uploadStatus.contains(cachedActivity.getString("uploadType"))
      }
      .forEach { cachedActivity ->
        val activity = cachedActivity.getJsonObject("activity")
        vertx.eventBus().publish(
          EFFECT_CHANNEL,
          Effect(
            userIdentifier,
            Instant.now().toEpochMilli(),
            activity.getLong("antecedenceTime"),
            mapTypeToEffect(cachedActivity.getString("uploadType")),
            activity.getJsonObject("content") ?: JsonObject(),
            extractValuableHeaders(requestContext)
          )
        )
      }
    requestContext.response().putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON).setStatusCode(200).end()
  }



  router.post("/").handler { requestContext ->
    val bodyAsJson = requestContext.bodyAsJson
    val timeCreated = Instant.now().toEpochMilli()
    val userIdentifier = requestContext.request().headers().get(USER_IDENTIFIER)
    vertx.eventBus().publish(
      EFFECT_CHANNEL, Effect(
        userIdentifier,
        timeCreated,
        bodyAsJson.getLong("antecedenceTime"),
        STARTED_TACTICAL_ACTIVITY,
        bodyAsJson.getJsonObject("content") ?: JsonObject(),
        extractValuableHeaders(requestContext)
      )
    )
    requestContext.response().putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON).setStatusCode(200).end()
  }

  router.put("/").handler { requestContext ->
    val bodyAsJson = requestContext.bodyAsJson
    val timeCreated = Instant.now().toEpochMilli()
    val userIdentifier = requestContext.request().headers().get(USER_IDENTIFIER)
    vertx.eventBus().publish(
      EFFECT_CHANNEL, Effect(
        userIdentifier,
        timeCreated,
        bodyAsJson.getLong("antecedenceTime"),
        UPDATED_TACTICAL_ACTIVITY,
        bodyAsJson.getJsonObject("content") ?: JsonObject(),
        extractValuableHeaders(requestContext)
      )
    )
    requestContext.response().putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON).setStatusCode(200).end()
  }

  router.delete("/").handler { requestContext ->
    val bodyAsJson = requestContext.bodyAsJson
    val timeCreated = Instant.now().toEpochMilli()
    val userIdentifier = requestContext.request().headers().get(USER_IDENTIFIER)
    vertx.eventBus().publish(
      EFFECT_CHANNEL, Effect(
        userIdentifier,
        timeCreated,
        bodyAsJson.getLong("antecedenceTime"),
        REMOVED_TACTICAL_ACTIVITY,
        bodyAsJson.getJsonObject("content") ?: JsonObject(),
        extractValuableHeaders(requestContext)
      )
    )
    requestContext.response().putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON).setStatusCode(200).end()
  }

  return router
}