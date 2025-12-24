/*
 * Copyright (c) 2021 - 2024 Mathias Doenitz
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.bullet.spliff

import io.bullet.spliff.util.{IntArrayStack, SimpleBitSet}

import java.{util => jutil}
import scala.annotation.{nowarn, tailrec}
import scala.collection.immutable.ArraySeq
import scala.collection.IndexedSeqView
import scala.reflect.ClassTag
import scala.util.Try

/**
 * The result of running Myers' diff algorithm against two [[IndexedSeq]] instances. Provides the basic [[deletes]] and
 * [[inserts]] operations required to transform [[base]] into [[target]] as well as higher-level logic that refines the
 * basic result (like detecting "move" and "replace" ops).
 *
 * Instances are created with `Diff(base, target)`.
 */
sealed abstract class Diff[T] {

  /**
   * The base sequence this [[Diff]] was created against.
   */
  def base: IndexedSeq[T]

  /**
   * The target sequence this [[Diff]] was created against.
   */
  def target: IndexedSeq[T]

  /**
   * The [[Diff.Op.Delete]] operations that, together with [[inserts]], are required to transform [[base]] into
   * [[target]].
   */
  def deletes: ArraySeq[Diff.Op.Delete]

  /**
   * The [[Diff.Op.Insert]] operations that, together with [[deletes]], are required to transform [[base]] into
   * [[target]].
   */
  def inserts: ArraySeq[Diff.Op.Insert]

  /**
   * Returns the basic [[deletes]] and [[inserts]] in one sequence, with the [[deletes]] preceding the [[inserts]].
   */
  def delInsOps: ArraySeq[Diff.Op.DelIns]

  /**
   * Returns the [[delInsOps]] sorted by `baseIx`.
   */
  def delInsOpsSorted: ArraySeq[Diff.Op.DelIns]

  /**
   * Returns an optimal number of the [[Diff.Op.DelInsMov]] operations required to transform [[base]] into [[target]].
   * Spends an additional O(N^^2) time on finding [[Diff.Op.Move]] operations.
   *
   * Note that a move is only identified as such if a [[Diff.Op.Delete]] has a directly corresponding
   * [[Diff.Op.Insert]]. There is no further search for moves in subsets of individual deletes or inserts. Or said
   * differently: Deletes and Inserts are never split to identify potential moves between parts of them.
   */
  def delInsMovOps: ArraySeq[Diff.Op.DelInsMov]

  /**
   * Returns the [[delInsMovOps]] sorted by `baseIx`.
   */
  def delInsMovOpsSorted: ArraySeq[Diff.Op.DelInsMov]

  /**
   * Identifies all operation types ([[Diff.Op.Delete]], [[Diff.Op.Insert]], [[Diff.Op.Move]] and [[Diff.Op.Replace]] )
   * and returns them sorted by `baseIx`.
   *
   * The difference to [[delInsMovOpsSorted]] is that deletes and inserts targeting the same `baseIx` are combined into
   * [[Diff.Op.Replace]] instances.
   */
  def allOps: ArraySeq[Diff.Op]

  /**
   * Identifies move operations and packages the diff data into a [[Diff.Patch]] instances, which contains all
   * information required to produce the `target` sequence when only the `base` sequence is given.
   */
  def patch(implicit ct: ClassTag[T]): Diff.Patch[T]

  /**
   * Segments the inputs into a sequence of chunks representing the diff in a alternative form, that is sometimes better
   * suited to the task at hand than [[allOps]] and friends.
   */
  def chunks: ArraySeq[Diff.Chunk[T]]

  /**
   * Creates a bidirectional index mapping between [[base]] and [[target]], without taking moves into account. This
   * means that elements that have been moved will not have their indices mapped. They simply appear as "not present" in
   * the other sequence.
   *
   * The benefit over [[bimap]] is the reduced overhead since the O(N^^2) move detection doesn't have to be performed.
   */
  def basicBimap: Diff.Bimap

  /**
   * Creates a bidirectional index mapping between [[base]] and [[target]], taking moves into account.
   */
  def bimap: Diff.Bimap

  /**
   * Returns a longest common subsequence of the `base` and `target` sequences. or [[None]], if the two sequences have
   * no elements in common. Stacksafe and reasonably efficient.
   *
   * NOTE: If you only need the `longestCommonSubsequence` then directly calling `Diff.longestCommonSubsequence(base,
   * target)` is more efficient than `Diff(base, target).longestCommonSubsequence`!
   */
  def longestCommonSubsequence(implicit ct: ClassTag[T]): ArraySeq[T]

  /**
   * Returns the minimum number of edits required to transform `base` and `target`, whereby one "edit" corresponds to
   * deleting or inserting one single element.
   *
   * Same as `delInsOps.map(_.count).sum` but slightly more efficient.
   *
   * NOTE: If you only need the `minEditDistance` then directly calling `Diff.minEditDistance(base, target)` is more
   * efficient than `Diff(base, target).minEditDistance`!
   */
  def minEditDistance: Int
}

object Diff {

  /**
   * An ADT for all "operations" the diff algorithm can derive. Operations don't contain any elements themselves, they
   * only hold indices into the `base` and `target` sequences for maximum efficiency.
   *
   * If you need something that holds all data required to transform `base` into `target`, including the actual
   * elements, check out [[Patch]].
   */
  sealed trait Op {

    /**
     * the index of the first element in the base sequence that is affected by this operation
     */
    def baseIx: Int
  }

  object Op {

    /**
     * The super type of [[Delete]], [[Insert]] and [[Move]].
     */
    sealed trait DelInsMov extends Op {
      def count: Int
    }

    /**
     * The super type of the two basic diff operations returned by Myers' Diff algorithm, [[Delete]] and [[Insert]].
     */
    sealed trait DelIns extends DelInsMov

    /**
     * Represents a number of contiguous elements that is present in the base sequence but not in the target.
     *
     * @param baseIx
     *   the index of the first element in the base sequence that is deleted
     * @param count
     *   the number of elements in the deleted chunk
     */
    final case class Delete(baseIx: Int, count: Int) extends DelIns with Patch.Step[Nothing] {
      if (count <= 0) throw new IllegalArgumentException
    }

    /**
     * Represents a number of contiguous elements that is present in the target sequence but not in the base.
     *
     * @param baseIx
     *   the index of the element in the base sequence where the new elements are inserted
     * @param targetIx
     *   the index of the first element in the target sequence that is inserted
     * @param count
     *   the number of elements in the inserted chunk
     */
    final case class Insert(baseIx: Int, targetIx: Int, count: Int) extends DelIns {
      if (count <= 0) throw new IllegalArgumentException
    }

    /**
     * Represents a number of contiguous elements that is present in both the base and target sequences but in a
     * different position in the sequence relative to its surrounding elements.
     *
     * @param origIx
     *   the index into the base sequence where the elements are moved from (i.e. deleted)
     * @param destIx
     *   the index into the base sequence where the elements are moved to (i.e. inserted)
     * @param count
     *   the number of elements in the moved chunk
     */
    final case class Move(origIx: Int, destIx: Int, count: Int) extends DelInsMov with Patch.Step[Nothing] {
      if (count <= 0) throw new IllegalArgumentException
      if (origIx == destIx) throw new IllegalArgumentException

      /**
       * True if this operation moves elements from higher indices to lower indices
       */
      def isForwardMove: Boolean = destIx < origIx

      /**
       * True if this operation moves elements from lower indices to higher indices
       */
      def isBackwardMove: Boolean = origIx < destIx

      // we always sort 'Move' ops according to the _insertion_ point, not the deletion point
      def baseIx = destIx
    }

    /**
     * Represents a number of contiguous elements in the base that was replaced with a chunk of a potentially differing
     * (non-zero) length in the target.
     *
     * This operation is essentially a combination of a [[Delete]] and an [[Insert]] at the same `baseIx`.
     *
     * @param baseIx
     *   the index of the first element in the base sequence that are replaced
     * @param delCount
     *   the number of elements in the base sequence that are replaced
     * @param targetIx
     *   the index of the first element in the target sequence that are inserted
     * @param insCount
     *   the number of elements that are inserted from the target sequence
     */
    final case class Replace(baseIx: Int, delCount: Int, targetIx: Int, insCount: Int) extends Op {
      if (delCount <= 0) throw new IllegalArgumentException
      if (insCount <= 0) throw new IllegalArgumentException
    }

    final private val _ordering: Ordering[Op]   = (x: Op, y: Op) => x.baseIx - y.baseIx
    implicit def ordering[T <: Op]: Ordering[T] = _ordering.asInstanceOf[Ordering[T]]
  }

  /**
   * A [[Patch]] encapsulates all information required to transform the `base` sequence into the `target` sequence.
   *
   * In addition to the data of which elements need to be deleted and/or moved it also contains the actual elements that
   * are to be inserted.
   */
  final case class Patch[T](baseSize: Int, targetSize: Int, steps: ArraySeq[Patch.Step[T]]) {

    /**
     * True if the patch is a NOP, i.e. doesn't affect the `base` at all when applied.
     */
    def isEmpty: Boolean = steps.isEmpty

    /**
     * Returns an equivalent patch that has all its steps sorted by `baseIx`.
     *
     * NOTE: The steps will be sorted anyway during application of the patch against a `base` sequence, so the steps can
     * be held in any order in the [[Patch]] sequence. Pre-sorting of the steps as it is done here is therefore not
     * required, but may be beneficial for presentation or other "normalization" processes.
     */
    def sorted: Patch[T] = copy(steps = steps.sorted)

    /**
     * Applies this patch to the given `base` sequence, producing either the original `target` sequence or an error.
     */
    def apply(base: IndexedSeq[T])(implicit ct: ClassTag[T]): Either[Patch.Failure, ArraySeq[T]] =
      try Right(throwingApply(base))
      catch {
        case e: Patch.Failure => Left(e)
      }

    /**
     * Applies this patch to the given `base` sequence, producing a [[Try]] instance holding either the original
     * `target` sequence or a [[Patch.Failure]].
     */
    def tryApply(base: IndexedSeq[T])(implicit ct: ClassTag[T]): Try[ArraySeq[T]] =
      Try(throwingApply(base))

    /**
     * Applies this patch to the given `base` sequence, producing the original `target` sequence or throwing a
     * [[Patch.Failure]].
     */
    def throwingApply(base: IndexedSeq[T])(implicit ct: ClassTag[T]): ArraySeq[T] =
      Diff.applyPatch(base, baseSize, targetSize, steps)
  }

  object Patch {

    sealed trait Step[+T] {
      def baseIx: Int
      def count: Int
    }

    type Delete = Op.Delete
    val Delete = Op.Delete

    type Move = Op.Move
    val Move = Op.Move

    final case class Insert[T](baseIx: Int, values: ArraySeq[T]) extends Step[T] {
      def count = values.size
    }

    sealed abstract class Failure(msg: String) extends RuntimeException(msg)

    final case class BaseSizeMismatch(actualSize: Int, expectedSize: Int)
        extends Failure(s"Base sequence size was $actualSize but patch expected size $expectedSize")

    case object IntegrityFailure extends Failure("Patch steps and target length mismatch")

    final private val _ordering: Ordering[Step[_]] = (x: Step[_], y: Step[_]) => x.baseIx - y.baseIx
    implicit def ordering[T]: Ordering[Step[T]]    = _ordering.asInstanceOf[Ordering[Step[T]]]

    /**
     * Creates a `Patch` only from the `baseSize` and the `steps`. The `targetSize` is derived from the steps.
     */
    def apply[T](baseSize: Int, steps: ArraySeq[Patch.Step[T]]): Patch[T] =
      Diff.patchFromBaseSizeAndSteps(baseSize, steps)
  }

  /**
   * Alternative representation of the diffing output that holds _all_ data elements, changed and unchanged ones.
   */
  sealed trait Chunk[T]

  object Chunk {

    /**
     * A chunk, which appears identically in both, the base and the target sequence. The `baseElements` and
     * `targetElements` contain identical elements but may differ in their index ranges.
     *
     * @param baseElements
     *   the slice of the base sequence
     * @param targetElements
     *   the slice of the target sequence
     */
    final case class InBoth[T](baseElements: Slice[T], targetElements: Slice[T]) extends Chunk[T] {
      if (baseElements.isEmpty) throw new IllegalArgumentException
      if (baseElements.length != targetElements.length) throw new IllegalStateException

      /**
       * Source-agnostic access to the unchanged slice of data elements.
       */
      def elements: IndexedSeqView[T] = baseElements
    }

    /**
     * A chunk, which only appears in the base sequence.
     *
     * @param elements
     *   the slice of the base sequence
     */
    final case class InBase[T](elements: Slice[T]) extends Chunk[T] {
      if (elements.isEmpty) throw new IllegalArgumentException
    }

    /**
     * A chunk, which only appears in the target sequence.
     *
     * @param elements
     *   the slice of the target sequence
     */
    final case class InTarget[T](elements: Slice[T]) extends Chunk[T] {
      if (elements.isEmpty) throw new IllegalArgumentException
    }

    /**
     * A chunk, which combines two chunks that are unique to each sequence. The `baseElements` appear in the base
     * sequence at the same relative position as the `targetElements` in the target sequence.
     *
     * @param baseElements
     *   the slice of the base sequence
     * @param targetElements
     *   the slice of the target sequence
     */
    final case class Distinct[T](baseElements: Slice[T], targetElements: Slice[T]) extends Chunk[T] {
      if (baseElements.isEmpty) throw new IllegalArgumentException
      if (targetElements.isEmpty) throw new IllegalArgumentException
    }
  }

  /**
   * An [[IndexedSeqView.Slice]] that surfaces the underlying sequence as well as the index range.
   */
  final class Slice[T](val underlying: IndexedSeq[T], _from: Int, _until: Int)
      extends IndexedSeqView.Slice[T](underlying, _from, _until) {

    def from: Int  = lo
    def until: Int = hi

    /**
     * Merges this slice with the given slice. Requires that the two slices represent directly adjacent chunks of the
     * underlying sequence. If not an [[IllegalArgumentException]] is thrown.
     */
    def merge(that: Slice[T]): Slice[T] =
      if (this.until == that.from) new Slice(underlying, this.from, that.until)
      else if (that.until == this.from) new Slice(underlying, that.from, this.until)
      else throw new IllegalArgumentException
  }

  object Slice {

    /**
     * [[IndexedSeq]] sequences can be transparently (implicitly) converted into [[Slice]] instances.
     */
    implicit def apply[T](seq: IndexedSeq[T]): Slice[T] = new Slice(seq, 0, seq.size)
  }

  /**
   * Allows for mapping indices bidirectionally between the `base` and `target` sequences.
   */
  sealed trait Bimap {

    /**
     * Maps base indices to target indices.
     *
     * Returns [[None]] if the given index is outside the index range of the base sequence or the element at the
     * respective place was deleted and therefore doesn't appear in the target.
     */
    def baseToTargetIndex(ix: Int): Option[Int]

    /**
     * Maps target indices to base indices.
     *
     * Returns [[None]] if the given index is outside the index range of the target sequence or the element at the
     * respective place was insert and therefore doesn't appear in the base.
     */
    def targetToBaseIndex(ix: Int): Option[Int]
  }

  /**
   * Very simple equality type class, which allows for customizing the comparison logic employed by the diff algorith.
   * By default universal value equality is used.
   */
  trait Eq[T] {
    def apply(a: T, b: T): Boolean
  }

  object Eq {

    private[this] val _default: Eq[Any] = (a: Any, b: Any) => a == b
    implicit def default[T]: Eq[T]      = _default.asInstanceOf[Eq[T]]
  }

  /**
   * Runs a relatively efficient, stacksafe, linear space implementation of the Myers diff algorithm and returns the
   * result as a [[Diff]] instance, which serves as the tee-off point for further, downstream logic.
   *
   * The core algorithm is based on the work of Robert Elder.
   *
   * @see
   *   https://blog.robertelder.org/diff-algorithm/
   * @see
   *   https://github.com/RobertElderSoftware/roberteldersoftwarediff/blob/master/myers_diff_and_variations.py
   * @see
   *   https://blog.jcoglan.com/2017/02/12/the-myers-diff-algorithm-part-1/
   */
  def apply[T: Eq](base: IndexedSeq[T], target: IndexedSeq[T]): Diff[T] =
    (new Myers).diff(base, target)

  /**
   * Returns a longest common subsequence of the `base` and `target` sequences. or [[None]], if the two sequences have
   * no elements in common. Stacksafe and reasonably efficient.
   */
  def longestCommonSubsequence[T: Eq: ClassTag](base: IndexedSeq[T], target: IndexedSeq[T]): ArraySeq[T] =
    if (base.nonEmpty && target.nonEmpty) {

      // Optimized transcription of the "longest_common_subsequence" implementation in
      // https://github.com/RobertElderSoftware/roberteldersoftwarediff/blob/master/myers_diff_and_variations.py
      // which is licensed under the Apache License Version 2.0.

      val buf   = ArraySeq.newBuilder[T]
      val stack = new IntArrayStack(64)

      @tailrec def appendSlice(seq: IndexedSeq[T], start: Int, end: Int): Unit =
        if (start < end) {
          buf += seq(start)
          appendSlice(seq, start + 1, end)
        }

      stack.push4(0, base.size, 0, target.size)
      while (stack.nonEmpty) {
        val M = stack.pop()
        val j = stack.pop()
        val N = stack.pop()
        val i = stack.pop()
        if (j >= 0) {
          val D = findMiddleSnake(base, i, N, target, j, M, stack)
          val v = stack.pop()
          val u = stack.pop()
          val y = stack.pop()
          val x = stack.pop()
          if (D > 1) {
            if (N > u && M > v) stack.push4(i + u, N - u, j + v, M - v)
            if (u > x) stack.push4(i + x, i + u, -1, 0)
            if (x > 0 && y > 0) stack.push4(i, x, j, y)
          } else if (M > N) appendSlice(base, i, i + N)
          else appendSlice(target, j, j + M)
        } else appendSlice(base, i, N)
      }

      buf.result()
    } else ArraySeq.empty

  /**
   * Returns the minimum number of edits required to transform `base` and `target`, whereby one "edit" corresponds to
   * deleting or inserting one single element.
   *
   * Equal to `Diff(base, target).delInsOps.map(_.count).sum` but more efficient.
   */
  def minEditDistance[T](base: IndexedSeq[T], target: IndexedSeq[T])(implicit eq: Eq[T]): Int = {
    // Optimized transcription of the "myers_diff_length_half_memory" implementation in
    // https://github.com/RobertElderSoftware/roberteldersoftwarediff/blob/master/myers_diff_and_variations.py
    // which is licensed under the Apache License Version 2.0.
    val N          = base.size
    val M          = target.size
    val MAX        = N + M
    val MAX2       = MAX + 2
    val V          = new Array[Int](MAX2)
    def v(ix: Int) = V(if (ix >= 0) ix else MAX2 + ix)
    var D          = 0
    while (D <= MAX) {
      var k    = -(D - 2 * math.max(0, D - M))
      val kmax = D - 2 * math.max(0, D - N)
      while (k <= kmax) {
        var x = if (k == -D || k != D && v(k - 1) < v(k + 1)) v(k + 1) else v(k - 1) + 1
        var y = x - k
        while (x < N && y < M && eq(base(x), target(y))) { x += 1; y += 1 }
        V(if (k >= 0) k else MAX2 + k) = x
        if (x == N && y == M) return D
        k += 2
      }
      D += 1
    }
    failDiff()
  }

  //////////////////////////////////////////// IMPLEMENTATION //////////////////////////////////////////////

  /**
   * Optimized transcription of the "find_middle_snake_myers_original" implementation in
   * https://github.com/RobertElderSoftware/roberteldersoftwarediff/blob/master/myers_diff_and_variations.py which is
   * licensed under the Apache License Version 2.0.
   */
  private def findMiddleSnake[T](
      base: IndexedSeq[T],
      i: Int,
      N: Int,
      target: IndexedSeq[T],
      j: Int,
      M: Int,
      stack: IntArrayStack)(implicit eq: Eq[T]): Int = {
    val MAX2                = N + M + 2
    val Delta               = N - M
    val deltaOdd            = (Delta & 1) != 0
    val Vf                  = new Array[Int](MAX2)
    val Vb                  = new Array[Int](MAX2)
    @inline def vf(ix: Int) = Vf(if (ix >= 0) ix else MAX2 + ix)
    @inline def vb(ix: Int) = Vb(if (ix >= 0) ix else MAX2 + ix)
    var D                   = 0
    val Dlimit              = (MAX2 >> 1) + (MAX2 & 1)
    while (D < Dlimit) {

      var k = -D
      while (k <= D) {
        var x   = if (k == -D || k != D && vf(k - 1) < vf(k + 1)) vf(k + 1) else vf(k - 1) + 1
        var y   = x - k
        val x_i = x
        val y_i = y
        while (x < N && y < M && eq(base(i + x), target(j + y))) { x += 1; y += 1 }
        Vf(if (k >= 0) k else MAX2 + k) = x
        val kd = -(k - Delta)
        val D1 = D - 1
        if (deltaOdd && kd >= -D1 && kd <= D1 && vf(k) + vb(kd) >= N) {
          stack.push4(x_i, y_i, x, y)
          return 2 * D - 1
        }
        k += 2
      }

      k = -D
      while (k <= D) {
        var x   = if (k == -D || k != D && vb(k - 1) < vb(k + 1)) vb(k + 1) else vb(k - 1) + 1
        var y   = x - k
        val x_i = x
        val y_i = y
        while (x < N && y < M && eq(base(i + N - x - 1), target(j + M - y - 1))) { x += 1; y += 1 }
        Vb(if (k >= 0) k else MAX2 + k) = x
        val kd = -(k - Delta)
        if (!deltaOdd && kd >= -D && kd <= D && vb(k) + vf(kd) >= N) {
          stack.push4(N - x, M - y, N - x_i, M - y_i)
          return 2 * D
        }
        k += 2
      }

      D += 1
    }
    failDiff()
  }

  final private class Myers extends IntArrayStack(64) {
    private[this] var _lastDelBaseIx: Int   = -1
    private[this] var _lastDelCount: Int    = _
    private[this] var _lastInsBaseIx: Int   = -1
    private[this] var _lastInsTargetIx: Int = _
    private[this] var _lastInsCount: Int    = _

    def diff[T](base: IndexedSeq[T], target: IndexedSeq[T])(implicit eq: Eq[T]): Diff[T] = {
      val deletes              = ArraySeq.newBuilder[Diff.Op.Delete]
      val inserts              = ArraySeq.newBuilder[Diff.Op.Insert]
      val stack: IntArrayStack = this

      def appendCollapsingDelete(baseIx: Int, count: Int): Unit =
        if (baseIx != _lastDelBaseIx + _lastDelCount) {
          if (_lastDelBaseIx >= 0) deletes += Op.Delete(_lastDelBaseIx, _lastDelCount)
          _lastDelBaseIx = baseIx
          _lastDelCount = count
        } else _lastDelCount += count

      def appendCollapsingInsert(baseIx: Int, targetIx: Int, count: Int): Unit =
        if (baseIx != _lastInsBaseIx || targetIx != _lastInsTargetIx + _lastInsCount) {
          if (_lastInsBaseIx >= 0) inserts += Op.Insert(_lastInsBaseIx, _lastInsTargetIx, _lastInsCount)
          _lastInsBaseIx = baseIx
          _lastInsTargetIx = targetIx
          _lastInsCount = count
        } else _lastInsCount += count

      @inline def flushLastOps(): Unit = {
        if (_lastDelBaseIx >= 0) deletes += Op.Delete(_lastDelBaseIx, _lastDelCount)
        if (_lastInsBaseIx >= 0) inserts += Op.Insert(_lastInsBaseIx, _lastInsTargetIx, _lastInsCount)
      }

      // relatively direct transcription of https://blog.robertelder.org/diff-algorithm/,
      // with a few optimizations, like stack-safe (i.e. heap-based) recursion and immediate
      // collapsing of contiguous sequences of deletes or inserts, respectively
      //
      // complexity:
      // - time: O(min(len(a),len(b)) * D)
      // - space: 2 * (2 * min(len(a),len(b))) space
      def rec(): Unit = {
        val M     = stack.pop()
        val j     = stack.pop()
        val N     = stack.pop()
        val i     = stack.pop()
        val L     = N + M
        val Z     = 2 * Math.min(N, M) + 2
        val modL2 = Math.floorMod(L, 2)
        if (N > 0 && M > 0) {
          val w      = N - M
          val g      = new Array[Int](Z)
          val p      = new Array[Int](Z)
          var h      = 0
          val hLimit = L / 2 + modL2 + 1
          while (h < hLimit) {
            var c   = g
            var d   = p
            var o   = 1
            var m   = 1
            var ii0 = i
            var jj0 = j
            while (o >= 0) {
              var k      = -(h - 2 * Math.max(0, h - M))
              val kLimit = h - 2 * Math.max(0, h - N) + 1
              while (k < kLimit) {
                val mkz0 = Math.floorMod(k - 1, Z)
                val mkz1 = Math.floorMod(k + 1, Z)
                var a    = if (k == -h || k != h && c(mkz0) < c(mkz1)) c(mkz1) else c(mkz0) + 1
                var b    = a - k
                val s    = a
                val t    = b
                var ii   = ii0 + m * a
                var jj   = jj0 + m * b
                while (a < N && b < M && eq(base(ii), target(jj))) { a += 1; b += 1; ii += m; jj += m }
                val modKZ = Math.floorMod(k, Z)
                c(modKZ) = a
                val z = -(k - w)
                if (modL2 == o && z >= -(h - o) && z <= h - o && c(modKZ) + d(Math.floorMod(z, Z)) >= N) {
                  val _D = 2 * h - o
                  var x  = s
                  var y  = t
                  var u  = a
                  var v  = b
                  if (o == 0) { x = N - a; y = M - b; u = N - s; v = M - t }
                  if (_D > 1 || x != u && y != v) {
                    stack.push4(i + u, N - u, j + v, M - v)
                    stack.push4(i, x, j, y)
                  } else if (M > N) {
                    stack.push4(i + N, 0, j + N, M - N)
                  } else if (M < N) {
                    stack.push4(i + M, w, j + M, 0)
                  }
                  return
                }
                k += 2
              }
              c = p
              d = g
              o -= 1
              m = -1
              ii0 += N - 1
              jj0 += M - 1
            }
            h += 1
          }
          failDiff()
        } else if (N > 0) appendCollapsingDelete(baseIx = i, count = N)
        else if (M > 0) appendCollapsingInsert(baseIx = i, targetIx = j, count = M)
      }

      stack.push4(0, base.size, 0, target.size)
      while (stack.nonEmpty) rec()
      flushLastOps()

      new Impl(base, target, deletes.result(), inserts.result())
    }
  }

  final private class Impl[T](
      val base: IndexedSeq[T],
      val target: IndexedSeq[T],
      val deletes: ArraySeq[Op.Delete],
      val inserts: ArraySeq[Op.Insert])(implicit eq: Eq[T])
      extends Diff[T] {

    def delInsOps: ArraySeq[Op.DelIns] = {
      val deletes = this.deletes.unsafeArray.asInstanceOf[Array[Op.Delete]]
      val inserts = this.inserts.unsafeArray.asInstanceOf[Array[Op.Insert]]
      val result  = new Array[Op.DelIns](deletes.length + inserts.length)
      System.arraycopy(deletes, 0, result, 0, deletes.length)
      System.arraycopy(inserts, 0, result, deletes.length, inserts.length)
      ArraySeq.unsafeWrapArray(result)
    }

    def delInsOpsSorted: ArraySeq[Op.DelIns] = {
      val result = delInsOps.sorted
      val array  = result.unsafeArray.asInstanceOf[Array[Op.DelIns]]
      jutil.Arrays.sort(array, Op.ordering)
      result // we can return the same instance because it has not leaked to the outside before the potential mutation
    }

    def delInsMovOps: ArraySeq[Op.DelInsMov] = {
      val deletes = this.deletes.unsafeArray.asInstanceOf[Array[Op.Delete]]
      val inserts = this.inserts.unsafeArray.asInstanceOf[Array[Op.Insert]]

      if (deletes.length > 0 && inserts.length > 0) {
        val result        = new Array[Op.DelInsMov](deletes.length + inserts.length) // length is upper bound
        val pairedInserts = SimpleBitSet.withSize(inserts.length)

        @tailrec def rec(delIx: Int, insIx: Int, resIx: Int): Array[Op.DelInsMov] =
          if (delIx < deletes.length) {
            val del = deletes(delIx)
            val ins = inserts(insIx)

            @tailrec def doMatch(i: Int): Boolean =
              i == del.count || eq(base(del.baseIx + i), target(ins.targetIx + i)) && doMatch(i + 1)

            if (del.count == ins.count && !pairedInserts.contains(insIx) && doMatch(0)) {
              // this del and ins match completely, so merge them into an `Op.Move`
              pairedInserts += insIx // remember that this insert is "taken"
              rec(delIx + 1, 0, setAndGetNextIndex(result, resIx, Op.Move(del.baseIx, ins.baseIx, del.count)))
            } else if (insIx + 1 == inserts.length) {
              // we have not found a matching insert for the current delete, so append it and continue with the next one
              rec(delIx + 1, 0, setAndGetNextIndex(result, resIx, del))
            } else rec(delIx, insIx + 1, resIx)
          } else {
            @tailrec def appendUnpairedInserts(ii: Int, ir: Int): Int =
              if (ii < inserts.length) {
                if (pairedInserts.contains(ii)) appendUnpairedInserts(ii + 1, ir)
                else appendUnpairedInserts(ii + 1, setAndGetNextIndex(result, ir, inserts(ii)))
              } else ir

            val endIr = appendUnpairedInserts(0, resIx)
            if (endIr < result.length) jutil.Arrays.copyOfRange(result, 0, endIr) else result
          }

        ArraySeq.unsafeWrapArray(rec(0, 0, 0))
      } else if (deletes.length > 0) this.deletes
      else this.inserts
    }

    def delInsMovOpsSorted: ArraySeq[Op.DelInsMov] = {
      val result = delInsMovOps.sorted
      val array  = result.unsafeArray.asInstanceOf[Array[Op.DelInsMov]]
      jutil.Arrays.sort(array, Op.ordering)
      result // we can return the same instance because it has not leaked to the outside before the potential mutation
    }

    def allOps: ArraySeq[Op] = {
      val dimOps   = delInsMovOpsSorted
      val dimArray = dimOps.unsafeArray.asInstanceOf[Array[Op.DelInsMov]]
      val result   = new Array[Op](dimArray.length) // upper bound, actual number of elems might be less

      @tailrec def rec(dimIx: Int, resIx: Int, last: Op): Int =
        if (dimIx < dimArray.length) {
          val op   = dimArray(dimIx)
          var ir1  = resIx
          val next = last -> op match {
            case (Op.Delete(delBaseIx, delCnt), Op.Insert(insBaseIx, targetIx, insCnt)) if delBaseIx == insBaseIx =>
              ir1 = resIx - 1
              Op.Replace(delBaseIx, delCnt, targetIx, insCnt)
            case _ => op
          }
          result(ir1) = next
          rec(dimIx + 1, ir1 + 1, next)
        } else resIx

      val ir = rec(0, 0, null)
      if (ir < result.length) ArraySeq.unsafeWrapArray(jutil.Arrays.copyOf(result, ir))
      else dimOps.asInstanceOf[ArraySeq[Op]] // if there are no replaces we can simply reuse the existing instance
    }

    def longestCommonSubsequence(implicit ct: ClassTag[T]) = Diff.longestCommonSubsequence(base, target)

    def minEditDistance = {
      val sumCount = (_: Int) + (_: Op.DelIns).count
      deletes.foldLeft(0)(sumCount) + inserts.foldLeft(0)(sumCount)
    }

    def basicBimap: Bimap = {
      val delIns = delInsOpsSorted.unsafeArray.asInstanceOf[Array[Op.DelIns]]
      val bttMap = new Array[Int](base.size)
      val ttbMap = new Array[Int](target.size)

      @tailrec def mapUp(baseCursor: Int, targetCursor: Int, baseEnd: Int): Int =
        if (baseCursor < baseEnd) {
          bttMap(baseCursor) = targetCursor
          ttbMap(targetCursor) = baseCursor
          mapUp(baseCursor + 1, targetCursor + 1, baseEnd)
        } else targetCursor

      @tailrec def rec(delInsIx: Int, baseCursor: Int, targetCursor: Int): Unit =
        if (delInsIx < delIns.length) {
          val op              = delIns(delInsIx)
          val newTargetCursor = mapUp(baseCursor, targetCursor, op.baseIx)
          op match {
            case Op.Delete(baseIx, count) =>
              jutil.Arrays.fill(bttMap, baseIx, baseIx + count, -1)
              rec(delInsIx + 1, baseIx + count, newTargetCursor)

            case Op.Insert(baseIx, targetIx, count) =>
              if (newTargetCursor != targetIx) throw new IllegalStateException
              jutil.Arrays.fill(ttbMap, targetIx, targetIx + count, -1)
              rec(delInsIx + 1, math.max(baseCursor, baseIx), targetIx + count)
          }
        } else mapUp(baseCursor, targetCursor, bttMap.length): @nowarn

      rec(0, 0, 0)

      new BimapImpl(bttMap, ttbMap)
    }

    def bimap: Bimap = {
      val bmap = basicBimap.asInstanceOf[BimapImpl]

      @tailrec def rec(baseCursor: Int, targetCursor: Int, count: Int): Unit =
        if (count > 0) {
          bmap.bttMap(baseCursor) = targetCursor
          bmap.ttbMap(targetCursor) = baseCursor
          rec(baseCursor + 1, targetCursor + 1, count - 1)
        }

      delInsMovOps.foreach {
        case Op.Move(baseIx, targetIx, count) => rec(baseIx, targetIx, count)
        case _                                =>
      }
      bmap
    }

    final private class BimapImpl(val bttMap: Array[Int], val ttbMap: Array[Int]) extends Bimap {
      def baseToTargetIndex(ix: Int): Option[Int] = getFrom(bttMap, ix)
      def targetToBaseIndex(ix: Int): Option[Int] = getFrom(ttbMap, ix)

      private def getFrom(array: Array[Int], ix: Int): Option[Int] =
        if (0 <= ix && ix < array.length) {
          array(ix) match {
            case -1 => None
            case x  => Some(x)
          }
        } else None
    }

    def chunks: ArraySeq[Chunk[T]] = {
      import Chunk._
      val buf     = ArraySeq.newBuilder[Chunk[T]]
      val deletes = this.deletes.unsafeArray.asInstanceOf[Array[Op.Delete]]
      val inserts = this.inserts.unsafeArray.asInstanceOf[Array[Op.Insert]]

      def append(last: Chunk[T], next: Chunk[T]): Chunk[T] =
        (last, next) match {
          case (null, _)                     => next
          case (InBase(a), InTarget(b))      => Distinct(a, b)
          case (InTarget(b), InBase(a))      => Distinct(a, b)
          case (Distinct(a, b), InBase(c))   => Distinct(a merge c, b)
          case (Distinct(a, b), InTarget(c)) => Distinct(a, b merge c)
          case _                             =>
            buf += last
            next
        }

      // @param delIx index into `deletes`
      // @param insIx index into `inserts`
      // @param cb cursor into base sequence
      // @param ct cursor into target sequence
      // @param last last chunk, not yet appended to `buf`
      @tailrec def rec(delIx: Int, insIx: Int, cb: Int, ct: Int, last: Chunk[T]): ArraySeq[Chunk[T]] = {
        def lastAfterUnchanged(baseIx: Int) =
          if (cb < baseIx) append(last, InBoth(new Slice(base, cb, baseIx), new Slice(target, ct, ct + baseIx - cb)))
          else last

        val del = if (delIx < deletes.length) deletes(delIx) else null
        val ins = if (insIx < inserts.length) inserts(insIx) else null

        if ((del ne null) && ((ins eq null) || del.baseIx <= ins.baseIx)) {
          val deleted = InBase(new Slice(base, del.baseIx, del.baseIx + del.count))
          val newLast = append(lastAfterUnchanged(del.baseIx), deleted)
          rec(delIx + 1, insIx, del.baseIx + del.count, if (del.baseIx > cb) ct + del.baseIx - cb else ct, newLast)
        } else if (ins ne null) {
          val inserted = InTarget(new Slice(target, ins.targetIx, ins.targetIx + ins.count))
          val newLast  = append(lastAfterUnchanged(ins.baseIx), inserted)
          rec(delIx, insIx + 1, math.max(cb, ins.baseIx), ins.targetIx + ins.count, newLast)
        } else buf.addOne(lastAfterUnchanged(base.size)).result()
      }

      rec(0, 0, 0, 0, null)
    }

    def patch(implicit ct: ClassTag[T]): Patch[T] = {
      val steps = delInsMovOps.map {
        case Op.Insert(baseIx, targetIx, count) =>
          val values                         = new Array[T](count)
          @tailrec def rec(i: Int): Array[T] =
            if (i < values.length) rec(setAndGetNextIndex(values, i, target(targetIx + i))) else values
          Patch.Insert(baseIx, ArraySeq.unsafeWrapArray(rec(0)))
        case x: Patch.Step[_] => x.asInstanceOf[Patch.Step[T]]
      }
      Patch(base.size, target.size, steps)
    }
  }

  private def applyPatch[T: ClassTag](
      base: IndexedSeq[T],
      baseSize: Int,
      targetSize: Int,
      steps: ArraySeq[Patch.Step[T]]): ArraySeq[T] =
    if (base.size == baseSize) {
      if (steps.size > Int.MaxValue / 2) throw new IllegalArgumentException
      val allSteps = new Array[Patch.Step[T]](steps.size * 2) // worst case: all steps are moves -> size doubles

      @tailrec def expandSteps(i: Int, j: Int): Int =
        if (i < steps.size) {
          val nextj = steps(i) match {
            case x @ Patch.Move(origIx, _, count) =>
              setAndGetNextIndex(allSteps, setAndGetNextIndex(allSteps, j, Patch.Delete(origIx, count)), x)
            case x =>
              setAndGetNextIndex(allSteps, j, x)
          }
          expandSteps(i + 1, nextj)
        } else j

      val stepsCount = expandSteps(0, 0)
      jutil.Arrays.sort(allSteps, 0, stepsCount, Patch.ordering[T])

      val target = new Array[T](targetSize)

      @tailrec def copyFromBase(baseStartIx: Int, baseEndIx: Int, targetIx: Int): Int =
        if (baseStartIx < baseEndIx) {
          if (targetIx < targetSize) {
            target(targetIx) = base(baseStartIx)
            copyFromBase(baseStartIx + 1, baseEndIx, targetIx + 1)
          } else throw Patch.IntegrityFailure
        } else targetIx

      @tailrec def applyRemainingSteps(stepsIx: Int, baseIx: Int, targetIx: Int): Array[T] =
        if (stepsIx < stepsCount) {
          allSteps(stepsIx) match {
            case Patch.Delete(bix, count) =>
              applyRemainingSteps(stepsIx + 1, bix + count, copyFromBase(baseIx, bix, targetIx))

            case Patch.Insert(bix, values) =>
              val bbix         = math.max(bix, baseIx)
              val tix          = copyFromBase(baseIx, bbix, targetIx)
              val valuesArray  = values.unsafeArray.asInstanceOf[Array[T]]
              val nextTargetIx = tix + valuesArray.length
              if (nextTargetIx <= targetSize) {
                System.arraycopy(valuesArray, 0, target, tix, valuesArray.length)
                applyRemainingSteps(stepsIx + 1, bbix, nextTargetIx)
              } else throw Patch.IntegrityFailure

            case Patch.Move(origIx, destIx, count) =>
              val tix          = copyFromBase(baseIx, destIx, targetIx)
              val nextTargetIx = copyFromBase(origIx, origIx + count, tix)
              applyRemainingSteps(stepsIx + 1, math.max(destIx, baseIx), nextTargetIx)
          }
        } else if (copyFromBase(baseIx, base.size, targetIx) == targetSize) target
        else throw Patch.IntegrityFailure

      ArraySeq.unsafeWrapArray(applyRemainingSteps(0, 0, 0))
    } else throw Patch.BaseSizeMismatch(actualSize = base.size, expectedSize = baseSize)

  private def patchFromBaseSizeAndSteps[T](baseSize: Int, steps: ArraySeq[Patch.Step[T]]): Patch[T] = {
    val targetSize = steps.foldLeft(baseSize) {
      case (acc, Patch.Delete(_, count))  => acc - count
      case (acc, Patch.Insert(_, values)) => acc + values.size
      case (acc, _: Patch.Move)           => acc
    }
    Patch(baseSize, targetSize, steps)
  }

  @inline private def setAndGetNextIndex[T](array: Array[T], i: Int, value: T): Int = {
    array(i) = value
    i + 1
  }

  private def failDiff() =
    throw new RuntimeException("Diff algorithm unexpectedly failed. Were the data mutated during the diffing process?")
}
