package io.janstenpickle.hotswapref

import cats.Applicative
import cats.effect.IO
import cats.effect.kernel.{Deferred, Ref, Resource}
import cats.effect.testkit.TestInstances
import cats.effect.unsafe.IORuntime
import cats.syntax.all._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class HotswapRefSpec extends AnyFlatSpec with Matchers with TestInstances {
  implicit val runtime: IORuntime = IORuntime.global

  type R = Ref[IO, List[String]]
  val ref: Resource[IO, R] = Resource.eval(Ref[IO].of(List.empty[String]))

  behavior.of("HotswapRef")

  it should "swap resources" in {
    val test = (for {
      ref0 <- ref
      ref1 <- ref
      hotswap <- HotswapRef(Resource.pure[IO, R](ref0))
      _ <- Resource.eval(hotswap.access.use(_.update("test0" :: _)))
      _ <- Resource.eval(hotswap.swap(Resource.pure(ref1)))
      _ <- Resource.eval(hotswap.access.use(_.update("test1" :: _)))
    } yield (ref0.get, ref1.get)).use(_.tupled)

    val res = test.unsafeRunSync()

    // each ref has been used
    res._1.size should be(1)
    res._2.size should be(1)
    // access isn't blocked by hotswap
    res._1.head should be("test0")
    res._2.head should be("test1")
  }

  it should "not block on access while swap is allocating a new resource" in {
    val test = (for {
      ref0 <- ref
      ref1 <- ref
      gate0 <- Resource.eval(Deferred[IO, Unit])
      gate1 <- Resource.eval(Deferred[IO, Unit])
      hotswap <- HotswapRef(Resource.pure[IO, R](ref0))
      _ <- hotswap
        .swap(Resource.eval(gate0.complete(())) >> Resource.pure[IO, R](ref1).evalTap(_ => gate1.get))
        .background // in background: swap and hang on allocation
      _ <- Resource.eval(gate0.get >> hotswap.access.use(_.update("test0" :: _)))
    } yield ref0.get).use(identity)

    val res = test.unsafeRunSync()

    // ref0 has been used
    res.size should be(1)
    // access isn't blocked by hotswap
    res.head should be("test0")
  }

  it should "not block on access while swap is releasing the old resource" in {
    val test = (for {
      ref0 <- ref
      ref1 <- ref
      gate0 <- Resource.eval(Deferred[IO, Unit])
      gate1 <- Resource.eval(Deferred[IO, Unit])
      hotswap <- HotswapRef(Resource.pure[IO, R](ref0) <* Resource.unit.onFinalize(gate0.complete(()) >> gate1.get))
      _ <- hotswap.swap(Resource.pure[IO, R](ref1)).background // swap will hang on the release of ref0
      _ <- Resource.eval(gate0.get) // wait for the release of ref0 to begin
      _ <- Resource.eval(hotswap.access.use(_.update("test1" :: _))) // access to ref1 is not blocked
      _ <- Resource.eval(gate1.complete(())) // let the release of ref0 complete
    } yield ref1.get).use(identity)

    val res = test.unsafeRunSync()

    // ref1 has been used
    res.size should be(1)
    // access isn't blocked by hotswap
    res.head should be("test1")
  }

  it should "block on swap (release phase) while handles to the previous resource are held" in {
    val test = (for {
      ref0 <- ref
      ref1 <- ref
      gate0 <- Resource.eval(Deferred[IO, Unit])
      gate1 <- Resource.eval(Deferred[IO, Unit])
      gate2 <- Resource.eval(Deferred[IO, Unit])
      ref0Finalized <- Resource.eval(Ref[IO].of(false))
      hotswap <- HotswapRef(Resource.pure[IO, R](ref0).onFinalize(ref0Finalized.set(true)))
      _ <- hotswap.access
        .use(gate0.complete(()) >> gate1.get >> _.update("test0" :: _))
        .background // in background: enter the access scope of ref0 and hang
      _ <- (
        gate0.get >> hotswap.swap(Resource.pure[IO, R](ref1)) >> gate2.complete(())
      ).background // in background: try to swap and hang on release
      _ <- Resource.eval(IO.sleep(3.second)) // give fibers a chance to perform
      ref0FinCheck1 <- Resource.eval(ref0Finalized.get) // make sure ref0 is not released, must be false
      _ <- Resource.eval(hotswap.access.use(_.update("test1" :: _))) // access ref1
      _ <- Resource.eval(gate1.complete(()) >> gate2.get) // let ref0 be released and wait for swap to complete
      ref0FinCheck2 <- Resource.eval(ref0Finalized.get) // make sure ref0 is released, must be true
    } yield (ref0.get, ref1.get, ref0FinCheck1.pure[IO], ref0FinCheck2.pure[IO])).use(_.tupled)

    val res = test.unsafeRunSync()

    // each ref has been used
    res._1.size should be(1)
    res._2.size should be(1)
    res._1.head should be("test0")
    res._2.head should be("test1")
    // swap (release phase) is blocked by access
    res._3 should be(false)
    res._4 should be(true)
  }

  it should "block on further swaps while one is still in progress" in {
    val test = (for {
      ref0 <- ref
      ref1 <- ref
      ref2 <- ref
      gate0 <- Resource.eval(Deferred[IO, Unit])
      gate1 <- Resource.eval(Deferred[IO, Unit])
      gate2 <- Resource.eval(Deferred[IO, Unit])
      hotswap <- HotswapRef(Resource.pure[IO, R](ref0))
      _ <- hotswap
        .swap(Resource.eval(gate0.complete(())) >> Resource.pure(ref1).evalTap(_ => gate1.get))
        .background // in background: try to swap and hang
      _ <- (
        gate0.get >> hotswap.swap(Resource.pure(ref2)) >> gate2.complete(())
      ).background // in background: try to swap and get blocked by the first swap
      _ <- Resource.eval(IO.sleep(3.second)) // give fibers a chance to perform
      check <- Resource.eval(gate2.tryGet.map(_.isEmpty)) // make sure the second swap is not completed, must be true
      _ <- Resource.eval(gate1.complete(())) // let the first swap to finish
      _ <- Resource.eval(gate2.get) // wait for the second swap
    } yield check).use(_.pure[IO])

    val res = test.unsafeRunSync()

    assert(res)
  }

  it should "not block on repeated swaps while resource is not in use" in {

    val test = (for {
      hotswap <- HotswapRef(ref)
      _ <- Resource.eval(hotswap.swap(ref).replicateA(100))
    } yield ()).use_.as(true)

    val res = test.unsafeRunSync()
    assert(res)
  }

  it should "use a resource repeatedly" in {
    val test = (for {
      ref0 <- ref
      hotswap <- HotswapRef(Resource.pure[IO, R](ref0))
      _ <- Resource.eval(hotswap.access.use(_.update("" :: _)).replicateA(1000))
    } yield ref0.get).use(identity)

    val res = test.unsafeRunSync()

    res.size should be(1000)
  }

  it should "treat cancelation properly" in {
    val test = (for {
      hotswap <- HotswapRef(Resource.unit[IO])
      _ <- replicateA_(hotswap.access.use_.start.flatMap(_.cancel).background)(100000)
      _ <- Resource.eval(replicateA_(hotswap.swap(Resource.unit))(100))
    } yield ()).use_

    val res = test.unsafeRunTimed(60.seconds)

    res.isDefined should be(true)
  }

  private def replicateA_[F[_]: Applicative, A](fa: F[A])(n: Int): F[Unit] =
    (1 to n).map(_ => fa).foldLeft(Applicative[F].unit)(_ <* _)
}
