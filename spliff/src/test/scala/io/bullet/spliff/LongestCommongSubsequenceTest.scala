/*
 * Copyright (c) 2021 Mathias Doenitz
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.bullet.spliff

import org.scalacheck.Prop

class LongestCommongSubsequenceTest extends RandomizedTest {

  property("Diff.longestCommonSubsequence") {

    Prop.forAll(testSeq, testSeq) { (base, target) =>
      val lcs    = Diff.longestCommonSubsequence(base, target).mkString
      val chunks = Diff(base, target).chunks

      lcs ==> chunks.collect({ case Diff.Chunk.InBoth(a, _) => a }).flatten.mkString
    }
  }

}
