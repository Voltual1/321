/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.core.utils.extension

data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

operator fun <A, B, C, D> Quadruple<A, B, C, D>.component1() = first
operator fun <A, B, C, D> Quadruple<A, B, C, D>.component2() = second
operator fun <A, B, C, D> Quadruple<A, B, C, D>.component3() = third
operator fun <A, B, C, D> Quadruple<A, B, C, D>.component4() = fourth

data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
)

operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component1() = first
operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component2() = second
operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component3() = third
operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component4() = fourth
operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component5() = fifth