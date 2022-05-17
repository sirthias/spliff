/*
 * Copyright (c) 2021 Mathias Doenitz
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.bullet.spliff.util

import scala.collection.mutable

sealed abstract private[spliff] class SimpleBitSet {
  def contains(i: Int): Boolean
  def +=(i: Int): Unit
}

private[spliff] object SimpleBitSet {

  def withSize(size: Int): SimpleBitSet = if (size <= 64) new Small else new Large(size)

  private class Small extends SimpleBitSet {
    private[this] var long: Long = _

    def contains(i: Int) = (long >> i & 0x1) != 0
    def +=(i: Int): Unit = long |= 0x1 << i
  }

  private class Large(size: Int) extends SimpleBitSet {
    private[this] val set = new mutable.BitSet(size)

    def contains(i: Int) = set.contains(i)
    def +=(i: Int): Unit = set += i
  }
}
