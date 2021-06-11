package io.janstenpickle.hotswapref

import cats.effect.kernel.{Concurrent, Poll, Resource}
import cats.effect.std.Semaphore
import cats.syntax.functor._

trait Lock[F[_]] {
  def shared: Resource[F, Unit]
  def exclusive: Resource[F, Unit]
}

object Lock {
  def apply[F[_]: Concurrent]: F[Lock[F]] = apply(Long.MaxValue)
  def apply[F[_]: Concurrent](maxShared: Long): F[Lock[F]] = {
    Semaphore[F](maxShared).map { sem =>
      new Lock[F] {
        val shared: Resource[F, Unit] = sem.permit
        val exclusive: Resource[F, Unit] =
          Resource.makeFull((poll: Poll[F]) => poll(sem.acquireN(maxShared)))(_ => sem.releaseN(maxShared))
      }
    }
  }
}
