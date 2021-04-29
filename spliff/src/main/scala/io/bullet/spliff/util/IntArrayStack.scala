/*
 * Copyright (c) 2021 Mathias Doenitz
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.bullet.spliff.util

private[spliff] class IntArrayStack(initialSize: Int) {
  private[this] var array    = new Array[Int](initialSize)
  private[this] var top: Int = _

  def nonEmpty: Boolean = top != 0

  def push4(a: Int, b: Int, c: Int, d: Int): this.type = {
    while (top + 4 > array.length)
      array = java.util.Arrays.copyOf(array, array.length << 1)
    array(top + 0) = a
    array(top + 1) = b
    array(top + 2) = c
    array(top + 3) = d
    top += 4
    this
  }

  @inline def pop(): Int = {
    top -= 1
    array(top)
  }
}
