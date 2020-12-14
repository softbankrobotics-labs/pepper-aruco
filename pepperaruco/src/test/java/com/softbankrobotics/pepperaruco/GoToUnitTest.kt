package com.softbankrobotics.pepperaruco

import com.softbankrobotics.pepperaruco.util.GotoUtils
import org.junit.Assert
import org.junit.Test

class GoToUnitTest {
    val DOUBLE_ASSERT_DELTA = 0.00000001

    @Test
    fun testAngleWithVectorX() {
        Assert.assertEquals(0.0, GotoUtils.computeAngleWithVectorX(2.0, 0.0), DOUBLE_ASSERT_DELTA)
        Assert.assertEquals(Math.PI / 4, GotoUtils.computeAngleWithVectorX(3.0, 3.0), DOUBLE_ASSERT_DELTA)
        Assert.assertEquals(Math.PI / 2, GotoUtils.computeAngleWithVectorX(0.0, 2.0), DOUBLE_ASSERT_DELTA)
        Assert.assertEquals(Math.PI * 3 / 4, GotoUtils.computeAngleWithVectorX(-3.0, 3.0), DOUBLE_ASSERT_DELTA)
        Assert.assertEquals(Math.PI, GotoUtils.computeAngleWithVectorX(-2.0, 0.0), DOUBLE_ASSERT_DELTA)
        Assert.assertEquals(-Math.PI * 3 / 4, GotoUtils.computeAngleWithVectorX(-3.0, -3.0), DOUBLE_ASSERT_DELTA)
        Assert.assertEquals(-Math.PI / 2, GotoUtils.computeAngleWithVectorX(0.0, -2.0), DOUBLE_ASSERT_DELTA)
        Assert.assertEquals(-Math.PI / 4, GotoUtils.computeAngleWithVectorX(3.0, -3.0), DOUBLE_ASSERT_DELTA)
    }
}