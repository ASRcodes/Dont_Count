package com.example.dontcount

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View

// connections are Triple<PointF, PointF, Int> (p1, p2, color)
class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paintPoint = Paint().apply {
        strokeWidth = 12f
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val paintLine = Paint().apply {
        strokeWidth = 10f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private var points: List<Pair<PointF, Int>> = emptyList()
    private var connections: List<Triple<PointF, PointF, Int>> = emptyList()

    // points: Pair(point, color)
    fun update(points: List<Pair<PointF, Int>>, connections: List<Triple<PointF, PointF, Int>>) {
        this.points = points
        this.connections = connections
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // draw lines
        for ((p1, p2, color) in connections) {
            paintLine.color = color
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paintLine)
        }
        // draw points
        for ((pt, color) in points) {
            paintPoint.color = color
            canvas.drawCircle(pt.x, pt.y, 14f, paintPoint)
        }
    }
}
