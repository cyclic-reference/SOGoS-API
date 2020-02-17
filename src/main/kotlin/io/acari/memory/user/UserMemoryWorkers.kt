package io.acari.memory.user

import io.acari.memory.EffectListener
import io.acari.memory.SOGoSUserEffectListener
import io.acari.memory.TacModEffectListener
import io.acari.util.loggerFor
import io.reactivex.Completable
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.mongo.MongoClient

interface User {
  val guid: String
}

const val EFFECT_CHANNEL = "effects"

object UserMemoryWorkers {

  val log = loggerFor(javaClass)

  fun registerWorkers(vertx: Vertx, mongoClient: MongoClient): Completable {
    val eventBus = vertx.eventBus()
    eventBus.consumer(EFFECT_CHANNEL, EffectListener(mongoClient, vertx))
    eventBus.consumer(EFFECT_CHANNEL, SOGoSUserEffectListener(mongoClient, vertx))
    eventBus.consumer(EFFECT_CHANNEL, TacModEffectListener(mongoClient, vertx))
    return Completable.complete()
  }
}
