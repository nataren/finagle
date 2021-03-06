package com.twitter.finagle.builder

import org.specs.Specification
import org.specs.mock.Mockito
import org.mockito.Matchers

import com.twitter.util.{Promise, Return, Future}

import com.twitter.finagle._
import com.twitter.finagle.channel.ChannelService
import com.twitter.finagle.integration.IntegrationBase
import com.twitter.finagle.tracing.Tracer

object ClientBuilderSpec extends Specification with IntegrationBase with Mockito {
  "ClientBuilder" should {
    "invoke rawPrepareClientConnFactory on connection" in {
      val preparedFactory = mock[ServiceFactory[String, String]]
      val preparedServicePromise = new Promise[Service[String, String]]
      preparedFactory() returns preparedServicePromise

      val m = new MockChannel
      (m.codec.rawPrepareClientConnFactory(Matchers.any[ServiceFactory[Any, Any]])
       returns preparedFactory)

      // Client
      val client = m.build()
      val requestFuture = client("123")

      there was one(m.codec).rawPrepareClientConnFactory(any)
      there was one(preparedFactory)()

      requestFuture.isDefined must beFalse
      val service = mock[Service[String, String]]
      service("123") returns Future.value("321")
      preparedServicePromise() = Return(service)
      there was one(service)("123")
      requestFuture.poll must beSome(Return("321"))
    }

    "releaseExternalResources once all clients are released" in {
      val m = new MockChannel
      val client1 = m.build()
      val client2 = m.build()

      client1.release()
      there was no(m.channelFactory).releaseExternalResources()
      client2.release()
      there was one(m.channelFactory).releaseExternalResources()
    }

    "notify resources when client is released" in {
      val tracer = mock[Tracer]
      var called = false

      val client = new MockChannel().clientBuilder
        .tracerFactory { h =>
          h.onClose { called = true }
          tracer
        }
        .build()

      called must beFalse
      client.release()
      called must beTrue
    }
  }
}

