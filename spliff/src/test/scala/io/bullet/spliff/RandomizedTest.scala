/*
 * Copyright (c) 2021 Mathias Doenitz
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.bullet.spliff

import munit.ScalaCheckSuite
import org.scalacheck.Gen

abstract class RandomizedTest extends SpliffSuite with ScalaCheckSuite {

  val alphabet = "abcdef"

  val testSeq = Gen.stringOfN(12, Gen.oneOf(alphabet))
}
