package io.janstenpickle.hotswapref

import cats.effect.kernel.{Concurrent, Poll, Ref, Resource, Unique}
import cats.effect.std.{Hotswap, Semaphore}
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
    * swapped in the ref, but will block until all references to `R` are removed and `R` is torn down.
    *
    * A semaphore guarantees that concurrent access to [[swap]] will wait while previous resources are finalized.
    */
  def swap(next: Resource[F, R]): F[Unit]

  /** Access `R` safely
    *
    * Note that [[cats.effect.kernel.Resource]] `R` here is to maintain a count of the number of
    * references that are currently open. A resource `R` with open references cannot be finalized and therefore
    * cannot be fully swapped.
    */
  def access: Resource[F, R]
}

object HotswapRef {

  /** Creates a new [[HotswapRef]] initialized with the specified resource. The [[HotswapRef]] instance is returned
    * within a [[cats.effect.kernel.Resource]]
    */
  def apply[F[_]: Concurrent, R](initial: Resource[F, R]): Resource[F, HotswapRef[F, R]] = {

    /** Secure a resource by enriching it with a semaphore and a unique token and by modifying its `release` action.
      *
      * The semaphore is used as a share lock during `access` to the resource (permits concurrent access but prohibits
      * release) and as an exclusive lock during `release` of the resource (prohibits access).
      *
      * The token is used during `access` for consistent read of the holder reference.
      *
      * The `release` action of the resource is protected by an exclusive lock on the semaphore.
      */
    def secure(res: Resource[F, R]): Resource[F, (R, Semaphore[F], Unique.Token)] = for {
      sem <- Resource.eval(Semaphore(Long.MaxValue))
      r <- Resource
        .makeFull((poll: Poll[F]) => poll(res.allocated)) { case (_, release) =>
          Resource
            .makeFull((poll: Poll[F]) => poll(sem.acquireN(Long.MaxValue)))(_ => sem.releaseN(Long.MaxValue))
            .surround(release)
        }
        .map(_._1)
      token <- Resource.eval(Unique[F].unique)
    } yield (r, sem, token)

    for {
      (hotswap, securedR) <- Hotswap(secure(initial))
      holder <- Resource.eval(Ref.of(securedR))
      swapSem <- Resource.eval(Semaphore(1L))
    } yield new HotswapRef[F, R] {
      override def swap(next: Resource[F, R]): F[Unit] =
        swapSem.permit.surround(hotswap.swap(secure(next).evalTap(holder.set))).void

      override val access: Resource[F, R] = {

        /** Access to the resource is protected by a share lock on the semaphore.
          * The holder reference is read at least twice: first, to retrieve its content,
          * and then, after capturing the lock, to check if the content hasn't change
          * since the first read. If the holder has been swapped, the new content is passed
          * to the next step of the loop. Otherwise, it's used to build the resulting `Resource`.
          */
        val step: (R, Semaphore[F], Unique.Token) => F[Either[(R, Semaphore[F], Unique.Token), (R, F[Unit])]] =
          (r, sem, token) =>
            sem.permit.allocated.flatMap { case (_, semRelease) =>
              holder.get.map { case (r1, sem1, token1) =>
                Either.cond(token == token1, (r, semRelease), (r1, sem1, token1))
              }
            }

        val allocated = holder.get.flatMap(_.tailRecM(step.tupled))

        Resource.makeFull((poll: Poll[F]) => poll(allocated))(_._2).map(_._1)
      }
    }
  }
}
