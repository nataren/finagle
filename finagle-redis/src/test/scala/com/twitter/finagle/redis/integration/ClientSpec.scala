package com.twitter.finagle.redis.integration

import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.redis.util.{RedisCluster, BytesToString}
import com.twitter.finagle.redis.{Client, Redis}
import com.twitter.util.Future
import org.specs.Specification

object ClientSpec extends Specification {

  "A BaseClient" should {
    /**
     * Note: This integration test requires a real Redis server to run.
     */
    var client: Client = null
    val foo = "foo".getBytes
    val bar = "bar".getBytes
    val baz = "baz".getBytes
    val boo = "boo".getBytes
    val moo = "moo".getBytes

    doBefore {
      RedisCluster.start(1)
      val service = ClientBuilder()
        .codec(new Redis())
        .hosts(RedisCluster.hostAddresses())
        .hostConnectionLimit(1)
        .build()
      client = Client(service)
    }

    doAfter {
      RedisCluster.stop()
    }

    "perform simple commands" in {

      "append" in {
        client.del(Seq("foo"))()
        client.set("foo", "bar".getBytes)()
        client.append("foo", "baz".getBytes)() mustEqual 6
      }

      "decrBy" in {
        client.del(Seq("foo"))()
        client.set("foo", "21".getBytes)()
        client.decrBy("foo", 2)() mustEqual 19
      }

      "exists" in {
        client.del(Seq("foo"))()
        client.set("foo", "bar".getBytes)()
        client.exists("foo")() mustEqual true
      }

      "get range" in {
        client.del(Seq("foo"))()
        client.set("foo", "boing".getBytes)()
        BytesToString(client.getRange("foo", 0, 2)().get) mustEqual "boi"
      }

      "set & get" in {
        client.del(Seq("foo"))()
        client.get("foo")() mustEqual None
        client.set("foo", "bar".getBytes)()
        BytesToString(client.get("foo")().get) mustEqual "bar"
      }

      "flush" in {
        client.set("foo", "bar".getBytes)()
        client.flushDB
        client.get("foo")() mustEqual None
      }

      "select" in {
        client.select(1)() mustEqual "OK"
      }

    }


    "perform hash commands" in {

      "hash set and get" in {
        client.hSet(foo, bar, baz)()
        BytesToString(client.hGet(foo, bar)().get) mustEqual "baz"
        client.hGet(foo, boo)() mustEqual None
        client.hGet(bar, baz)() mustEqual None
      }

      "delete a single field" in {
        client.hSet(foo, bar, baz)()
        client.hDel("foo", Seq("bar"))() mustEqual 1
        client.hDel("foo", Seq("baz"))() mustEqual 0
      }

      "delete multiple fields" in {
        client.hSet(foo, bar,  baz)()
        client.hSet(foo, boo, moo)()
        client.hDel("foo", Seq("bar", "boo"))() mustEqual 2
      }

      "get multiple values" in {
        client.hSet(foo, bar, baz)()
        client.hSet(foo, boo, moo)()
        BytesToString.fromList(client.hMGet("foo", Seq("bar", "boo"))().toList) mustEqual Seq("baz", "moo")
      }

      "get multiple values at once" in {
        client.hSet(foo, bar, baz)()
        client.hSet(foo, boo, moo)()
        BytesToString.fromList(client.hGetAll(foo)().toList) mustEqual Seq("bar", "baz", "boo", "moo")
      }

    }


    "perform sorted set commands" in {

      "add members and get scores" in {
        client.zAdd(foo, 10, bar)() mustEqual 1
        client.zAdd(foo, 20, baz)() mustEqual 1
        BytesToString(client.zScore(foo, bar)().get) mustEqual "10"
        BytesToString(client.zScore(foo, baz)().get) mustEqual "20"
      }

      "add members and get the zcount" in {
        client.zAdd(foo, 10, bar)() mustEqual 1
        client.zAdd(foo, 20, baz)() mustEqual 1
        client.zCount(foo, 0, 30)() mustEqual 2
        client.zCount(foo, 40, 50)() mustEqual 0
      }

      "get the zRangeByScore" in {
        client.zAdd(foo, 10, bar)() mustEqual 1
        client.zAdd(foo, 20, baz)() mustEqual 1
        BytesToString.fromList(
          client.zRangeByScoreWithScores(foo, 0, 30, 0, 5)().toList) mustEqual Seq("bar", "10", "baz", "20")
      }

    }

  }

}