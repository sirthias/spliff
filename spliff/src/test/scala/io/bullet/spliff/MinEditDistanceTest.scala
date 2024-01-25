/*
 * Copyright (c) 2021 - 2024 Mathias Doenitz
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.bullet.spliff

import org.scalacheck.Prop

class MinEditDistanceTest extends RandomizedTest {

  property("Diff.minEditDistance") {

    Prop.forAll(testSeq, testSeq) { (base, target) =>
      Diff.minEditDistance(base, target) ==> Diff(base, target).delInsOps.map(_.count).sum
    }
  }

}
