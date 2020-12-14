package com.softbankrobotics.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.softbankrobotics.app.R
import com.softbankrobotics.dx.pepperextras.geometry.times
import com.softbankrobotics.dx.pepperextras.util.TAG


enum class ViewType {
    RIGHT,
    TOP,
    BACK
}

class FrameView(context: Context, attributeSet: AttributeSet): View(context, attributeSet) {

    private lateinit var backgroundRect: RectF
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#D6EAF8")
        style = Paint.Style.FILL
    }
    private val xPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        strokeWidth = 3f
    }
    private val yPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 3f
    }
    private val zPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
        strokeWidth = 3f
    }
    var list = listOf<Vector3>()
    var viewType = ViewType.RIGHT

    init {
        context.theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.FrameView,
            0, 0
        ).apply {
            try {
                viewType = ViewType.values()[getInt(R.styleable.FrameView_viewType, 0)]
            } finally {
                recycle()
            }
        }
    }

    fun setMarker(markerTransform: Transform) {
        val r = markerTransform.rotation
        val vX = r * Vector3(1.0, 0.0, 0.0)
        val vY = r * Vector3(0.0, 1.0, 0.0)
        val vZ = r * Vector3(0.0, 0.0, 1.0)
        list = listOf(vX, vY, vZ)
        postInvalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        Log.i(TAG, "Size: $w $h")
        backgroundRect = RectF(0f, 0f, width.toFloat(), height.toFloat())

    }

    fun drawAxis(canvas : Canvas, end: Vector3, paint : Paint) {

        val centerI = width / 2
        val centerJ = height / 2
        if (viewType == ViewType.RIGHT)
            canvas.drawLine(
                centerI.toFloat(), centerJ.toFloat(),
                centerI.toFloat() + end.x.toFloat() * 100f,
                centerJ.toFloat() - end.z.toFloat() * 100f,
                paint
            )
        else if (viewType == ViewType.TOP)
            canvas.drawLine(
                centerI.toFloat(), centerJ.toFloat(),
                centerI.toFloat() - end.y.toFloat() * 100f,
                centerJ.toFloat() - end.x.toFloat() * 100f,
                paint
            )
        else if (viewType == ViewType.BACK)
            canvas.drawLine(
                centerI.toFloat(), centerJ.toFloat(),
                centerI.toFloat() - end.y.toFloat() * 100f,
                centerJ.toFloat() - end.z.toFloat() * 100f,
                paint
            )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(backgroundRect, backgroundPaint)
        if (list.size > 1) {
            drawAxis(canvas, list[0], xPaint)
            drawAxis(canvas, list[1], yPaint)
            drawAxis(canvas, list[2], zPaint)
        }
    }
}

