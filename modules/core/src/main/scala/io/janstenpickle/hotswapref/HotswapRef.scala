package io.janstenpickle.hotswapref

import cats.effect.kernel.Resource.ExitCase
import cats.effect.kernel.{Concurrent, Ref, Resource, Unique}
import cats.effect.std.{Hotswap, Semaphore}
import cats.syntax.eq._
import cats.syntax.flatMap._
import cats.syntax.functor._

/** A concurrent data structure that wraps a [[cats.effect.std.Hotswap]] providing access to `R` using a
  * [[cats.effect.kernel.Ref]], that is set on resource acquisition, while providing asynchronous hotswap functionality
  * via calls to [[swap]].
  *
  * In short, calls to [[swap]] do not block the usage of `R` via calls to [[access]].
  *
  * Repeated concurrent calls to [[swap]] are ordered by a semaphore to ensure that `R` doesn't churn unexpectedly.
  * As with calls to [[swap]] on [[cats.effect.std.Hotswap]], [[swap]] will block until the previous
  * [[cats.effect.kernel.Resource]] is finalized. Additionally open references to `R` are counted when it is accessed
  * via [[access]], any `R` with open references will block at finalization until all references are released, and
  * therefore subsequent calls to [[swap]] will block.
  */
trait HotswapRef[F[_], R] {

  /** Swap the current resource with a new version
    *
    * This makes use of `evalTap` on the provided [[cats.effect.kernel.Resource]] to ensure the
    * [[cats.effect.kernel.Ref]] with `R` is updated immediately on allocation and may be used by [[access]] calls while
    * [[swap]] blocks, waiting for the previous [[cats.effect.kernel.Resource]] to finalize.
    *
    * This means that while there is no previous finalization process in progress when this is called, `R` may be
    * swapped in the holder ref, but will block until all references to `R` are removed and `R` is torn down.
    *
    * A semaphore guarantees that concurrent access to [[swap]] will wait while previous resources are finalized.
    */
  def swap(next: Resource[F, R]): F[Unit]

  /** Access `R` safely
    *
    * Note that access to `R` is protected by a shared-mode lock via a [[cats.effect.kernel.Resource]] scope.
    * A resource `R` with unreleased locks cannot be finalized and therefore cannot be fully swapped.
    */
  def access: Resource[F, R]
}

object HotswapRef {

  /** Creates a new [[HotswapRef]] initialized with the specified resource. The [[HotswapRef]] instance is returned
    * within a [[cats.effect.kernel.Resource]]
    */
  def apply[F[_], R](initial: Resource[F, R])(implicit F: Concurrent[F]): Resource[F, HotswapRef[F, R]] = {

    type Secured[A] = (A, Lock[F], Unique.Token)
    type Allocated[A] = (A, ExitCase => F[Unit])

    /** Secure a resource by enriching it with a semaphore-based lock and a unique token and by modifying its finalizer.
      *
      * The lock is acquired in shared mode when the resource is accessed (permits concurrent access but prohibits
      * finalization) and in exclusive mode when the resource is finalized (prohibits access).
      *
      * The token is used during access for consistent read of the holder reference.
      */
    def secure(res: Resource[F, R]): Resource[F, Secured[R]] = {
      val allocated = Lock[F].flatMap { lock =>
        Unique[F].unique.flatMap { token =>
          res.allocated.map { case (r, release) =>
            ((r, lock, token), (_: ExitCase) => lock.exclusive.surround(release))
          }
        }
      }

      Resource.applyFull(poll => poll(allocated))
    }

    def impl(hotswap: Hotswap[F, Secured[R]], holder: Ref[F, Secured[R]], sem: Semaphore[F]): HotswapRef[F, R] =
      new HotswapRef[F, R] {
        override def swap(next: Resource[F, R]): F[Unit] =
          sem.permit.surround(hotswap.swap(secure(next).evalTap(holder.set))).void

        override val access: Resource[F, R] = {

          /** Access to the resource is protected by a shared-mode lock. The holder reference is read at least twice:
            * first, to retrieve its content, and then, after acquiring the lock, to check if the content hasn't changed
            * since the first read. If the holder has been swapped, the lock is released and the new content is passed
            * to the next step of the loop. Otherwise, it's used to build the resulting `Resource`.
            */
          val step: Secured[R] => F[Either[Secured[R], Allocated[R]]] = { case (r, lock, token) =>
            F.uncancelable { poll =>
              poll(lock.shared.allocated).flatMap { case (_, lockRelease) =>
                holder.get.flatMap { case tup1 @ (_, _, token1) =>
                  if (token =!= token1) lockRelease.as(Left(tup1))
                  else F.pure(Right((r, _ => lockRelease)))
                }
              }
            }
          }

          val allocated = holder.get.flatMap(_.tailRecM(step))

          Resource.applyFull(poll => poll(allocated))
        }

      }

    Hotswap(secure(initial)).evalMap { case (hotswap, securedR) =>
      Ref.of(securedR).flatMap { holder =>
        Semaphore(1L).map { sem =>
          impl(hotswap, holder, sem)
        }
      }
    }
  }
}
