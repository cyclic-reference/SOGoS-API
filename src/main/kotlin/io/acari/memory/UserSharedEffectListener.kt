package io.acari.memory

import io.acari.http.DISABLED_SHARED_DASHBOARD
import io.acari.http.ENABLED_SHARED_DASHBOARD
import io.acari.http.USER_WELCOMED
import io.acari.memory.user.UserMemoryWorkers
import io.acari.util.toOptional
import io.vertx.core.Handler
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.eventbus.Message
import io.vertx.reactivex.ext.mongo.MongoClient
import java.util.*

const val HAS_SHARED_DASHBOARD = "hasShared"

class UserSharedEffectListener(private val mongoClient: MongoClient, private val vertx: Vertx) :
  Handler<Message<Effect>> {
  companion object {
    private val miscUserMappings =
      mapOf(
        ENABLED_SHARED_DASHBOARD to true,
        DISABLED_SHARED_DASHBOARD to false
      )
  }


  override fun handle(message: Message<Effect>) {
    val effect = message.body()
    extractUpdateType(effect)
      .ifPresent { sharedValue ->
        mongoClient.rxFindOneAndUpdate(
          UserSchema.COLLECTION,
          jsonObjectOf(UserSchema.GLOBAL_USER_IDENTIFIER to effect.guid),
          jsonObjectOf(
            "\$set" to jsonObjectOf(
              "security.$HAS_SHARED_DASHBOARD" to sharedValue
            )
          )
        )
          .subscribe({}) {
            UserMemoryWorkers.log.warn("Unable to update user security attributes for raisins -> ", it)
          }
      }
  }

  private fun extractUpdateType(effect: Effect): Optional<Boolean> {
    return effect.toOptional()
      .filter { miscUserMappings.containsKey(effect.name) }
      .map {
        miscUserMappings[effect.name]
      }
  }
}