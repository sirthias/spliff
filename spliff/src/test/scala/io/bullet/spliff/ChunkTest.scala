/*
 * Copyright (c) 2021 Mathias Doenitz
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.bullet.spliff

class ChunkTest extends SpliffSuite {

  test("equal strings") {
    diff("abc", "abc")(
      inBoth("abc")
    )
  }

  test("different prefixes") {
    diff("abcd", "xbcd")(
      unique("a", "x"),
      inBoth("bcd"),
    )
  }

  test("different suffixes") {
    diff("abcd", "abcx")(
      inBoth("abc"),
      unique("d", "x"),
    )
  }

  test("different char in the middle") {
    diff("abcd", "abxd")(
      inBoth("ab"),
      unique("c", "x"),
      inBoth("d"),
    )
  }

  test("different prefix and suffix and char in the middle") {
    diff("abcdef", "xbcyez")(
      unique("a", "x"),
      inBoth("bc"),
      unique("d", "y"),
      inBoth("e"),
      unique("f", "z"),
    )
  }

  test("extra prefix in base") {
    diff("abcd", "bcd")(
      inFirst("a"),
      inBoth("bcd"),
    )
  }

  test("missing prefix in base") {
    diff("bcd", "abcd")(
      inSecond("a"),
      inBoth("bcd"),
    )
  }

  test("extra char in base") {
    diff("axb", "ab")(
      inBoth("a"),
      inFirst("x"),
      inBoth("b"),
    )
  }

  test("extra char in target") {
    diff("ab", "axb")(
      inBoth("a"),
      inSecond("x"),
      inBoth("b"),
    )
  }

  test("two extra chars in base") {
    diff("axyb", "ab")(
      inBoth("a"),
      inFirst("xy"),
      inBoth("b"),
    )
  }

  test("two extra chars in target") {
    diff("ab", "axyb")(
      inBoth("a"),
      inSecond("xy"),
      inBoth("b"),
    )
  }

  test("extra prefix and two extra chars in base") {
    diff("xayzb", "ab")(
      inFirst("x"),
      inBoth("a"),
      inFirst("yz"),
      inBoth("b"),
    )
  }

  test("extra prefix in base, two extra chars in target") {
    diff("xab", "ayzb")(
      inFirst("x"),
      inBoth("a"),
      inSecond("yz"),
      inBoth("b"),
    )
  }

  test("extra suffix, and two extra chars in base") {
    diff("axybz", "ab")(
      inBoth("a"),
      inFirst("xy"),
      inBoth("b"),
      inFirst("z"),
    )
  }

  test("extra suffix in base, two extra chars in target") {
    diff("abz", "axyb")(
      inBoth("a"),
      inSecond("xy"),
      inBoth("b"),
      inFirst("z"),
    )
  }

  test("extra suffix in base") {
    diff("abcx", "abc")(
      inBoth("abc"),
      inFirst("x"),
    )
  }

  test("extra suffix in target") {
    diff("abc", "abcx")(
      inBoth("abc"),
      inSecond("x"),
    )
  }

  test("extra prefix and suffix in base") {
    diff("xabcy", "abc")(
      inFirst("x"),
      inBoth("abc"),
      inFirst("y"),
    )
  }

  test("extra prefix in base, extra suffix in target") {
    diff("xabc", "abcy")(
      inFirst("x"),
      inBoth("abc"),
      inSecond("y"),
    )
  }

  test("extra prefix and suffix in target") {
    diff("abc", "xabcy")(
      inSecond("x"),
      inBoth("abc"),
      inSecond("y"),
    )
  }

  test("extra prefix, suffix, and char in base") {
    diff("xabycdz", "abcd")(
      inFirst("x"),
      inBoth("ab"),
      inFirst("y"),
      inBoth("cd"),
      inFirst("z"),
    )
  }

  test("extra prefix, suffix and char in target") {
    diff("abcd", "xabycdz")(
      inSecond("x"),
      inBoth("ab"),
      inSecond("y"),
      inBoth("cd"),
      inSecond("z"),
    )
  }

  test("extra prefix and suffix in target, extra char in base") {
    diff("abycd", "xabcdz")(
      inSecond("x"),
      inBoth("ab"),
      inFirst("y"),
      inBoth("cd"),
      inSecond("z"),
    )
  }

  test("extra prefix and suffix in base, extra char in target") {
    diff("xabcdz", "abycd")(
      inFirst("x"),
      inBoth("ab"),
      inSecond("y"),
      inBoth("cd"),
      inFirst("z"),
    )
  }

  test("example 1") {
    diff("vabwcd", "abxycdz")(
      inFirst("v"),
      inBoth("ab"),
      unique("w", "xy"),
      inBoth("cd"),
      inSecond("z"),
    )
  }

  test("example 2") {
    diff("abxycdz", "vabwcd")(
      inSecond("v"),
      inBoth("ab"),
      unique("xy", "w"),
      inBoth("cd"),
      inFirst("z"),
    )
  }

  test("example 3") {
    diff("waxb", "aybz")(
      inFirst("w"),
      inBoth("a"),
      unique("x", "y"),
      inBoth("b"),
      inSecond("z"),
    )
  }

  test("example big") {
    diff("abwxcdzvabwcdvabwcdzabcdabwcd_ending", "vabycdabxycdzabcdvabwcdzvabcdz_ending")(
      inSecond("v"),
      inBoth("ab"),
      unique("w", "ycdab"),
      inBoth("x"),
      inSecond("y"),
      inBoth("cdz"),
      inFirst("v"),
      inBoth("ab"),
      inFirst("w"),
      inBoth("cdvabwcdz"),
      unique("abcd", "v"),
      inBoth("ab"),
      inFirst("w"),
      inBoth("cd"),
      inSecond("z"),
      inBoth("_ending")
    )
  }

  private type ChunkFun = ((Int, Int, Seq[Product])) => (Int, Int, Seq[Product])

  private def diff(base: String, target: String)(expected: ChunkFun*)(implicit l: munit.Location): Unit = {
    import Diff.Chunk._
    Diff(base, target).chunks.map {
      case Unchanged(a, b) => (Unchanged, ((a.from, a.until, a.mkString), (b.from, b.until, b.mkString)))
      case Inserted(x)     => (Inserted, (x.from, x.until, x.mkString))
      case Deleted(x)      => (Deleted, (x.from, x.until, x.mkString))
      case Replaced(a, b)  => (Replaced, ((a.from, a.until, a.mkString), (b.from, b.until, b.mkString)))
    } ==> expected.foldLeft((0, 0, Seq.empty[Product]))((tuple, f: ChunkFun) => f(tuple))._3
  }

  private def inBoth(value: String): ChunkFun = {
    case (i, j, chunks) =>
      val i2 = i + value.length
      val j2 = j + value.length
      (i2, j2, chunks :+ ((Diff.Chunk.Unchanged, ((i, i2, value), (j, j2, value)))))
  }

  private def inFirst(value: String): ChunkFun = {
    case (i, j, chunks) =>
      val i2 = i + value.length
      (i2, j, chunks :+ ((Diff.Chunk.Deleted, (i, i2, value))))
  }

  private def inSecond(value: String): ChunkFun = {
    case (i, j, chunks) =>
      val j2 = j + value.length
      (i, j2, chunks :+ ((Diff.Chunk.Inserted, (j, j2, value))))
  }

  private def unique(a: String, b: String): ChunkFun = {
    case (i, j, chunks) =>
      val i2 = i + a.length
      val j2 = j + b.length
      (i2, j2, chunks :+ ((Diff.Chunk.Replaced, ((i, i2, a), (j, j2, b)))))
  }
}
