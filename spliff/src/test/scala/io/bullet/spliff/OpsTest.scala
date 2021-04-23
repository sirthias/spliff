/*
 * Copyright (c) 2021 Mathias Doenitz
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.bullet.spliff

class OpsTest extends SpliffSuite {
  import Diff.Op._

  test("equal strings") {
    diff("abc", "abc")()
  }

  test("deleted prefix") {
    diff("xabc", "abc")(
      Delete(0, 1)
    )
  }

  test("deleted middle") {
    diff("abcde", "abe")(
      Delete(2, 2)
    )
  }

  test("deleted suffix") {
    diff("abcx", "abc")(
      Delete(3, 1)
    )
  }

  test("inserted prefix") {
    diff("abc", "xabc")(
      Insert(0, 0, 1)
    )
  }

  test("inserted middle") {
    diff("abcde", "axybcde")(
      Insert(1, 1, 2)
    )
  }

  test("inserted suffix") {
    diff("abc", "abcx")(
      Insert(3, 3, 1)
    )
  }

  test("replaced prefix") {
    diff("abcde", "xyde")(
      Replace(0, 3, 0, 2)
    )
  }

  test("replaced middle") {
    diff("abcde", "abxyze")(
      Replace(2, 2, 2, 3)
    )
  }

  test("replaced suffix") {
    diff("abcde", "abcdxyz")(
      Delete(4, 1),
      Insert(5, 4, 3)
    )
  }

  test("move prefix") {
    diff("abcde", "cdabe")(
      Move(0, 4, 2)
    )
  }

  test("move middle") {
    diff("abcdefgh", "abefgcdh")(
      Move(2, 7, 2)
    )
  }

  test("move suffix") {
    diff("abcdefgh", "afghbcde")(
      Move(5, 1, 3)
    )
  }

  private def diff(s1: String, s2: String)(expected: Diff.Op*)(implicit l: munit.Location): Unit = {
    Diff(s1, s2).allOps ==> expected

    Diff.minEditDistance(s1, s2) ==> Diff(s1, s2).delInsOps.map(_.count).sum
  }
}
