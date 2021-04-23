/*
 * Copyright (c) 2021 Mathias Doenitz
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.bullet.spliff

import munit.FunSuite

import scala.annotation.implicitNotFound

abstract class SpliffSuite extends FunSuite {

  implicit class ArrowAssert[A](lhs: A) {

    def ==>[B](rhs: B)(implicit ev: SpliffSuite.CanEqual[A, B], loc: munit.Location): Unit = {
      def arrayToSeq(x: Any) =
        x match {
          case a: Array[_] => a.toSeq
          case _           => x
        }
      assertEquals(arrayToSeq(lhs), arrayToSeq(rhs))
    }
  }
}

object SpliffSuite {

  @implicitNotFound("Types ${A} and ${B} cannot be compared")
  sealed trait CanEqual[A, B]

  object CanEqual extends LowerPrioCanEqual {
    implicit def canEqual1[A, B <: A]: CanEqual[A, B] = null // phantom type
  }

  abstract class LowerPrioCanEqual {
    implicit def canEqual2[A, B <: A]: CanEqual[B, A] = null // phantom type
  }
}
