package io.acari.memory.activity

import io.acari.memory.ActivitySchema
import io.acari.memory.user.User
import io.acari.util.loggerFor
import io.reactivex.Maybe
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.reactivex.core.eventbus.Message
import io.vertx.reactivex.ext.mongo.MongoClient

data class Activity(val antecedenceTime: Long, val content: JsonObject)
data class CurrentActivityRequest(override val guid: String) : User
data class CurrentActivityResponse(val activityEffect: Activity)

class CurrentActivityListener(private val mongoClient: MongoClient) :
  Handler<Message<CurrentActivityRequest>> {
  val log = loggerFor(javaClass)
  override fun handle(message: Message<CurrentActivityRequest>) {
    val (guid) = message.body()
    findCurrentActivity(mongoClient, guid)
      .map { activityJson ->
        Activity(
          antecedenceTime = activityJson.getLong(ActivitySchema.TIME_OF_ANTECEDENCE),
          content = activityJson.getJsonObject(ActivitySchema.CONTENT)
        )
      }
      .subscribe({ currentActivity ->
        message.reply(CurrentActivityResponse(currentActivity))
      }) {
        log.warn("Unable to fetch current activity for $guid because reasons.", it)
        message.fail(404, "Shit's broke yo")
      }
  }

  private fun findCurrentActivity(
    mongoClient: MongoClient,
    globalUserIdentifier: String
  ): Maybe<JsonObject> {
    return mongoClient.rxFindOne(
      ActivitySchema.COLLECTION, jsonObjectOf(
        ActivitySchema.GLOBAL_USER_IDENTIFIER to globalUserIdentifier
      ), jsonObjectOf()
    )
  }
}
