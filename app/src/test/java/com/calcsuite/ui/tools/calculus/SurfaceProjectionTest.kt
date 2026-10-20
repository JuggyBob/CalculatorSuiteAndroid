package com.calcsuite.ui.tools.calculus

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the three-dimensional picture of a surface.
 *
 * The rules are pinned here so that the picture cannot be drawn inside out:
 * a point that is higher in the function is always higher on the screen, the
 * pieces are drawn from the far ones to the near ones, and a drag carries the
 * part of the surface nearest the viewer with it.
 */
class SurfaceProjectionTest {

    private val rotation = -0.6f
    private val tilt = 0.9f

    @Test fun aHigherPointIsAlwaysHigherOnTheScreen() {
        for (angle in listOf(0.15f, 0.4f, 0.9f, 1.2f, 1.5f)) {
            val low = SurfaceProjection.project(0.0, 0.0, 0.0, rotation, angle)
            val high = SurfaceProjection.project(0.0, 0.0, 1.0, rotation, angle)
            assertTrue(
                "at a tilt of $angle a higher point must be higher on the screen",
                high.second > low.second,
            )
        }
    }

    @Test fun theDepthGrowsWithTheDistanceIntoTheScreen() {
        // A point further away must have the greater depth.
        for (angle in listOf(0.15f, 0.9f, 1.5f)) {
            val near = SurfaceProjection.project(0.0, 0.0, 0.0, rotation, angle)
            val far = SurfaceProjection.project(0.0, 1.0, 0.0, rotation, angle)
            assertTrue(
                "at a tilt of $angle the depth must order the pieces",
                far.third > near.third,
            )
        }
    }

    @Test fun theNearEdgeFollowsTheFingerToTheRight() {
        // The piece nearest the viewer is the one with the smallest depth.
        fun nearestEdgeX(turn: Float): Double {
            var best = Double.MAX_VALUE
            var bestU = 0.0
            for (i in 0..20) {
                for (j in 0..20) {
                    val x = -1.0 + 2.0 * i / 20
                    val y = -1.0 + 2.0 * j / 20
                    val (u, _, depth) = SurfaceProjection.project(x, y, 0.0, turn, tilt)
                    if (depth < best) {
                        best = depth
                        bestU = u
                    }
                }
            }
            return bestU
        }

        val before = nearestEdgeX(rotation)
        val (turned, _) = SurfaceProjection.turn(rotation, tilt, 200.0, 0.0)
        val after = nearestEdgeX(turned)
        assertTrue(
            "the near edge should move to the right with the finger, but it went to $after from $before",
            after > before,
        )
    }

    @Test fun theNearEdgeFollowsTheFingerDownwards() {
        // The height of the near edge on the screen must fall when the finger
        // goes down.
        fun nearEdgeHeight(angle: Float): Double {
            var best = Double.MAX_VALUE
            var bestHeight = 0.0
            for (i in 0..20) {
                for (j in 0..20) {
                    val x = -1.0 + 2.0 * i / 20
                    val y = -1.0 + 2.0 * j / 20
                    val (_, height, depth) = SurfaceProjection.project(x, y, 0.0, rotation, angle)
                    if (depth < best) {
                        best = depth
                        bestHeight = height
                    }
                }
            }
            return bestHeight
        }

        val before = nearEdgeHeight(tilt)
        val (_, tipped) = SurfaceProjection.turn(rotation, tilt, 0.0, 200.0)
        val after = nearEdgeHeight(tipped)
        assertTrue(
            "the near edge should move down with the finger, but it went to $after from $before",
            after < before,
        )
    }

    @Test fun theTiltIsKeptWithinSaneBounds() {
        var (rotation, tilt) = SurfaceProjection.turn(0f, 0.9f, 0.0, 0.0)
        repeat(40) {
            val turned = SurfaceProjection.turn(rotation, tilt, 0.0, 1000.0)
            rotation = turned.first
            tilt = turned.second
        }
        assertTrue("the tilt must not go past the top", tilt <= 1.5f)
        repeat(80) {
            val turned = SurfaceProjection.turn(rotation, tilt, 0.0, -1000.0)
            rotation = turned.first
            tilt = turned.second
        }
        assertTrue("the tilt must not go past the side", tilt >= 0.15f)
    }
}
