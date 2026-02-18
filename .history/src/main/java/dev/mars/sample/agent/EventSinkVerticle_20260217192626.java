package dev.mars.sample.agent;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

import java.util.logging.Logger;

public class EventSinkVerticle extends AbstractVerticle {

  private static final Logger LOG = Logger.getLogger(EventSinkVerticle.class.getName());

  @Override
  public void start(Promise<Void> startPromise) {
    vertx.eventBus().consumer(Addresses.EVENTS_OUT, msg -> {
      JsonObject event = (JsonObject) msg.body();
      LOG.info("[EVENTS_OUT] " + event.encode());
    });
    startPromise.complete();
  }
}
