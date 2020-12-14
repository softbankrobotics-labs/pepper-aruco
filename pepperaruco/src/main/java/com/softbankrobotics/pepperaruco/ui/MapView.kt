package com.softbankrobotics.pepperaruco.ui

import android.content.Context
import android.graphics.*
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.softbankrobotics.dx.pepperextras.geometry.toApacheRotation
import com.softbankrobotics.dx.pepperextras.util.TAG
import com.softbankrobotics.pepperaruco.actuation.ArucoMarker
import org.apache.commons.math3.geometry.euclidean.threed.Rotation
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import java.lang.Exception
import java.util.concurrent.atomic.AtomicReference
import kotlin.Comparator

open class MapElement(val label: String, val realPosition: PointF, val rotation: Rotation, val paint: Paint, val mapView: ElementView, val zIndex: Int) {
    protected val defaultWidth = 40f
    protected val mapWidth = 0
    protected val mapHeight = 0
    protected var mapPosition = PointF()

    open fun draw(canvas: Canvas, width: Int, height: Int) {
        if (width != mapWidth || height != mapHeight) computeGraphicElement()
    }

    protected open fun computeGraphicElement() {
        // Coordinated are swapped between real coord and map coord:
        // Real coord (x, y) = map coord (y, x)
        mapPosition = mapView.convertToMapCoordinates(realPosition.let { PointF(it.y, it.x) })
    }

    protected fun rotatedPoint(p: PointF): PointF {
        // Coordinated are swapped between real coord and map coord:
        // Real coord (x, y) = map coord (y, x)
        // So invert them when applying the rotation
        return rotation.applyInverseTo(Vector3D(p.y.toDouble(), p.x.toDouble(), 0.0)).let { PointF(it.y.toFloat(), it.x.toFloat())}
    }
}

open class LineElement(label: String, realPosition: PointF, rotation: Rotation, paint: Paint, mapView: ElementView, zIndex: Int)
    : MapElement(label, realPosition, rotation, paint, mapView, zIndex) {
    protected var lines = FloatArray(0)

    protected fun line(p1: PointF, p2: PointF): FloatArray {
        return floatArrayOf(p1.x + mapPosition.x, p1.y + mapPosition.y, p2.x + mapPosition.x, p2.y + mapPosition.y)
    }

    override fun draw(canvas: Canvas, width: Int, height: Int) {
        super.draw(canvas, width, height)
        canvas.drawLines(lines, paint)
    }
}

open class PathElement(label: String, realPosition: PointF, rotation: Rotation, paint: Paint, mapView: ElementView, zIndex: Int)
    : MapElement(label, realPosition, rotation, paint, mapView, zIndex) {
    protected var path = Path()

    protected fun lineTo(p: PointF) {
        path.lineTo(p.x + mapPosition.x, p.y + mapPosition.y)
    }

    protected fun moveTo(p: PointF) {
        path.moveTo(p.x + mapPosition.x, p.y + mapPosition.y)
    }

    override fun draw(canvas: Canvas, width: Int, height: Int) {
        super.draw(canvas, width, height)
        canvas.drawPath(path, paint)
    }
}

class SquareElement(label: String, realPosition: PointF, rotation: Rotation, paint: Paint, mapView: ElementView, zIndex: Int)
    : PathElement(label, realPosition, rotation, paint, mapView, zIndex) {

    override fun computeGraphicElement() {
        super.computeGraphicElement()
        val w = defaultWidth / 2

        val v_bottom_left = rotatedPoint(PointF(-w, w)) // bas gauche
        val v_top_left = rotatedPoint(PointF(-w, -w)) // Haut gauche
        val v_bottom_right = rotatedPoint(PointF(w, w)) // bas droite
        val v_top_right = rotatedPoint(PointF(w, -w)) // haut droite

        path = Path()
        path.fillType = Path.FillType.EVEN_ODD
        moveTo(v_bottom_left)
        lineTo(v_top_left)
        lineTo(v_top_right)
        lineTo(v_bottom_right)
        lineTo(v_bottom_left)
        path.close()
    }
}

class TriangleElement(label: String, realPosition: PointF, rotation: Rotation, paint: Paint, mapView: ElementView, zIndex: Int)
    : PathElement(label, realPosition, rotation, paint, mapView, zIndex) {

    override fun computeGraphicElement() {
        super.computeGraphicElement()
        val w = defaultWidth / 2

        val v_bottom_left =rotatedPoint(PointF(-w, -w)) // haut gauche
        val v_top_middle = rotatedPoint(PointF(0f, w)) // Bas milieu
        val v_bottom_right = rotatedPoint(PointF(w, -w)) // haut droite

        path = Path()
        path.fillType = Path.FillType.EVEN_ODD
        moveTo(v_bottom_left)
        lineTo(v_top_middle)
        lineTo(v_bottom_right)
        lineTo(v_bottom_left)
        path.close()
    }
}

class CrossElement(label: String, realPosition: PointF, rotation: Rotation, paint: Paint, mapView: ElementView, zIndex: Int)
    : LineElement(label, realPosition, rotation, paint, mapView, zIndex) {

    override fun computeGraphicElement() {
        super.computeGraphicElement()
        val w = (defaultWidth / 2)

        val left = rotatedPoint(PointF(-w, 0f)) // gauche
        val right = rotatedPoint(PointF(w, 0f)) // droite
        val bottom = rotatedPoint(PointF(0f, w)) // bas
        val top = rotatedPoint(PointF(0f, -w)) // haut

        lines = line(left, right) + line(top, bottom)
    }
}

class TextElement(val text: String, label: String, realPosition: PointF, rotation: Rotation, paint: Paint, mapView: ElementView, zIndex: Int)
    : MapElement(label, realPosition, rotation, paint, mapView, zIndex) {

    override fun draw(canvas: Canvas, width: Int, height: Int) {
        super.draw(canvas, width, height)
        canvas.drawText(text, mapPosition.x- 10, mapPosition.y + 2, paint)
    }
}

enum class ElementType {
    SQUARE, TRIANGLE, CROSS, TEXT
}


class MapElementComparator() : Comparator<MapElement> {
    override fun compare(p0: MapElement, p1: MapElement): Int {
        val indexCompare = p0.zIndex - p1.zIndex
        if (indexCompare == 0) {
            return p0.label.compareTo(p1.label)
        }
        return indexCompare
    }
}


open class ElementView(context: Context, attributeSet: AttributeSet): View(context, attributeSet) {

    val mapMargin = 20f

    var meterToPixel = 0.0f
    var minYCoord = 0.0f
    var minXCoord = 0.0f
    var maxYCoord = 0.0f
    var maxXCoord = 0.0f
    // Margin to apply to center elements inside the map on the x axis
    var centerMarginXPixel = 0.0f
    // Margin to apply to center elements inside the map on the y axis
    var centerMarginYPixel = 0.0f

    private lateinit var backgroundRect: RectF
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#D6EAF8")
        style = Paint.Style.FILL
    }

    val elements = sortedSetOf<MapElement>(MapElementComparator())

    val uiHandler = android.os.Handler(Looper.getMainLooper())
    private fun addElementPrivate(e: MapElement) {
        // Add on UI thread to prevent java.util.ConcurrentModificationException
        // So we access & update the list from the same thread (the UI thread)
        uiHandler.post({
            elements.remove(e)
            elements.add(e)
        })
    }

    fun addElement(type: ElementType, label: String, position: PointF, rotation: Rotation, paint: Paint, zIndex: Int = 1) {
        try {
            when (type) {
                ElementType.SQUARE -> addElementPrivate(
                    SquareElement(
                        label,
                        position,
                        rotation,
                        paint,
                        this,
                        zIndex
                    )
                )
                ElementType.TRIANGLE -> addElementPrivate(
                    TriangleElement(
                        label,
                        position,
                        rotation,
                        paint,
                        this,
                        zIndex
                    )
                )
                ElementType.CROSS -> addElementPrivate(
                    CrossElement(
                        label,
                        position,
                        rotation,
                        paint,
                        this,
                        zIndex
                    )
                )
                ElementType.TEXT -> addElementPrivate(
                    TextElement(
                        label,
                        label,
                        position,
                        rotation,
                        paint,
                        this,
                        zIndex
                    )
                )
            }
            if (!isCoordinateInActualBounds(position)) {
                findMinMaxCoordinates()
                computeConversionData()
            }
        } catch (e: Exception) {
            Log.e(TAG, "exception: ${e}")
        }

        postInvalidate()
    }

    private fun findMinMaxCoordinates() {
        minYCoord = 0.0f
        minXCoord = 0.0f
        maxYCoord = 0.0f
        maxXCoord = 0.0f
        getAllElementCoords().forEach { p ->
            if (minXCoord > p.x) minXCoord = p.x
            if (maxXCoord < p.x) maxXCoord = p.x
            if (minYCoord > p.y) minYCoord = p.y
            if (maxYCoord < p.y) maxYCoord = p.y
        }
    }

    private fun isCoordinateInActualBounds(coord: PointF): Boolean {
        return (coord.x >= minXCoord) && (coord.x <= maxXCoord) && (coord.y >= minYCoord) && (coord.y <= maxYCoord)
    }

    private fun computeConversionData() {
        val pixelWidthWithMargin = width - mapMargin * 2
        val pixelHeightWithMargin = height - mapMargin * 2
        val meterWidth = Math.max(maxXCoord - minXCoord, 0.01f)
        val meterHeight = Math.max(maxYCoord - minYCoord, 0.01f)
        meterToPixel = Math.min(pixelWidthWithMargin / meterWidth, pixelHeightWithMargin / meterHeight)
        centerMarginXPixel = (pixelWidthWithMargin - meterWidth * meterToPixel) / 2
        centerMarginYPixel = (pixelHeightWithMargin - meterHeight * meterToPixel) / 2
        //Log.i(TAG, "Margin ares $centerMarginXPixel $centerMarginYPixel")
    }

    private fun getAllElementCoords(): List<PointF> {
        // Coordinated are swapped between real coord and map coord:
        // Real coord (x, y) = map coord (y, x)
        return elements.map { PointF(it.realPosition.y, it.realPosition.x) }
    }

    fun convertToMapCoordinates(coord: PointF): PointF {
        return PointF(
            (coord.x - minXCoord) * meterToPixel + mapMargin + centerMarginXPixel,
            (coord.y - minYCoord) * meterToPixel + mapMargin + centerMarginYPixel
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        //Log.i(TAG, "Size: $w $h")
        backgroundRect = RectF(0f, 0f, width.toFloat(), height.toFloat())

        findMinMaxCoordinates()
        computeConversionData()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(backgroundRect, backgroundPaint)

        for (element in elements) {
            element.draw(canvas, width, height)
        }
    }
}

class MapView(context: Context, attributeSet: AttributeSet): ElementView(context, attributeSet) {

    private lateinit var mapOrigin: Frame

    fun setMapOrigin(mapOrigin: Frame) {
        this.mapOrigin = mapOrigin
        addElement(ElementType.CROSS, "mapOrigin", mapOrigin, Color.BLUE, Paint.Style.STROKE)
    }

    fun addMarker(marker: ArucoMarker) {
        addElement(ElementType.SQUARE, "Marker0${marker.id}${Math.random()}", marker.frame, Color.LTGRAY, Paint.Style.FILL)
        addElement(ElementType.SQUARE, "Marker${marker.id}", marker.frame, Color.BLACK, Paint.Style.FILL)
        addElement(ElementType.TEXT, "${marker.id}", marker.frame, Color.WHITE, Paint.Style.STROKE, 10)
    }

    fun setTarget(frame: Frame) {
        addElement(ElementType.SQUARE, "Target", frame, Color.MAGENTA, Paint.Style.FILL, 5)
    }

    fun addElement(type: ElementType, label: String, frame: Frame, paint: Paint, zIndex: Int = 1): Future<Void> {
        return frame.async().computeTransform(mapOrigin).andThenConsume { mapToElement ->
            val position = mapToElement.transform.translation.let { PointF(it.x.toFloat(), it.y.toFloat()) }
            val rotation = mapToElement.transform.rotation.toApacheRotation()
            addElement(type, label, position, rotation, paint, zIndex)
        }
    }

    fun addElement(type: ElementType, label: String, frame: Frame, color: Int, style: Paint.Style, zIndex: Int = 1) {
        val paint = Paint().apply {
            this.color = color
            this.style = style
        }
        if (type == ElementType.TEXT)
            paint.textSize = 25f
        else
            paint.strokeWidth = 4f

        addElement(type, label, frame, paint, zIndex)
    }

    var trackingFuture: AtomicReference<Future<Unit>?> = AtomicReference(null)

    fun trackRobotPosition(qiContext: QiContext): Future<Unit> {
        val promise = Promise<Unit>()
        // Do not track robot position if already running
        if (!trackingFuture.compareAndSet(null, promise.future))
            return trackingFuture.get()!!
        var trackingRun = true
        val period: Long = 500
        val handler = android.os.Handler(Looper.getMainLooper())
        var callback: () -> Unit = {}
        callback = {
            if (trackingRun) {
                qiContext.actuation.async().robotFrame().andThenConsume { robotFrame ->
                    addElement(ElementType.TRIANGLE, "robot", robotFrame, Paint().apply {
                        this.color = Color.RED
                        this.style = Paint.Style.STROKE
                        this.strokeWidth = 4f
                    })
                    postInvalidate()
                    handler.postDelayed(callback, period)
                }
            }
            else {
                promise.setCancelled()
            }
        }
        handler.postDelayed(callback, period)
        promise.setOnCancel {
            trackingRun = false
            trackingFuture.set(null)
        }
        return promise.future
    }
}
