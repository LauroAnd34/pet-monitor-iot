package com.lauro.petguardian.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max

class DraggableCropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private var sourceBitmap: Bitmap? = null
    private var zoom: Float = 1.15f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var dragging = false
    private val drawMatrix = Matrix()

    init {
        scaleType = ScaleType.MATRIX
    }

    fun setEditorBitmap(bitmap: Bitmap) {
        sourceBitmap = bitmap
        setImageBitmap(bitmap)
        offsetX = 0f
        offsetY = 0f
        applyMatrix()
    }

    fun setZoomValue(value: Float) {
        zoom = value.coerceAtLeast(1f)
        applyMatrix()
    }

    fun exportCroppedBitmap(targetSize: Int): Bitmap? {
        val bitmap = sourceBitmap ?: return null
        if (width <= 0 || height <= 0) return null

        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val exportMatrix = Matrix(drawMatrix)
        val scaleFactor = targetSize.toFloat() / width.toFloat()
        exportMatrix.postScale(scaleFactor, scaleFactor)
        canvas.drawBitmap(bitmap, exportMatrix, null)
        return output
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y
                offsetX += dx
                offsetY += dy
                applyMatrix()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyMatrix()
    }

    private fun applyMatrix() {
        val bitmap = sourceBitmap ?: return
        if (width <= 0 || height <= 0) return

        val baseScale = max(width.toFloat() / bitmap.width.toFloat(), height.toFloat() / bitmap.height.toFloat())
        val finalScale = baseScale * zoom
        val scaledWidth = bitmap.width * finalScale
        val scaledHeight = bitmap.height * finalScale

        val maxOffsetX = max(0f, (scaledWidth - width) / 2f)
        val maxOffsetY = max(0f, (scaledHeight - height) / 2f)
        offsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
        offsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)

        val tx = (width - scaledWidth) / 2f + offsetX
        val ty = (height - scaledHeight) / 2f + offsetY

        drawMatrix.reset()
        drawMatrix.postScale(finalScale, finalScale)
        drawMatrix.postTranslate(tx, ty)
        imageMatrix = drawMatrix
        invalidate()
    }
}
