package com.mwai.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.random.Random

class MatrixRainView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : SurfaceView(ctx, attrs), SurfaceHolder.Callback {

    private var thread: MatrixThread? = null

    init {
        holder.addCallback(this)
        setZOrderOnTop(false)
        holder.setFormat(PixelFormat.TRANSPARENT)
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        thread = MatrixThread(h, width, height).also { it.start() }
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
        thread?.stopThread()
        thread = MatrixThread(h, w, hh).also { it.start() }
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        thread?.stopThread()
        thread = null
    }

    inner class MatrixThread(
        private val holder: SurfaceHolder,
        private val w: Int,
        private val h: Int
    ) : Thread() {

        @Volatile private var running = true
        private val CELL = 22f
        private val cols = (w / CELL).toInt() + 1
        private val rows = (h / CELL).toInt() + 2
        private val chars = "0123456789ABCDEF!@#\$%<>[]{}|MW".toCharArray()

        // Each column: current y position (float), speed, trail length
        private val yPos   = FloatArray(cols) { Random.nextFloat() * rows }
        private val speeds = FloatArray(cols) { 0.15f + Random.nextFloat() * 0.4f }
        private val trails = IntArray(cols)   { 6 + Random.nextInt(10) }
        // Grid of chars
        private val grid   = Array(cols) { Array(rows) { chars.random() } }

        private val paintHead  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE; textSize = CELL * 0.85f; color = Color.WHITE
        }
        private val paintGreen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE; textSize = CELL * 0.85f
        }
        private val paintDim   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE; textSize = CELL * 0.85f
            color = Color.argb(30, 180, 0, 0)
        }
        private val paintClear = Paint().apply { color = Color.TRANSPARENT; xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

        fun stopThread() { running = false }

        override fun run() {
            while (running) {
                val canvas = try { holder.lockCanvas() } catch (_: Exception) { null } ?: continue
                try {
                    // Clear with semi-transparent black — creates fade trail effect
                    canvas.drawColor(Color.argb(60, 0, 0, 0), PorterDuff.Mode.SRC_OVER)

                    for (col in 0 until cols) {
                        val headRow = yPos[col].toInt()

                        // Randomly mutate chars in column
                        if (Random.nextFloat() > 0.85f && headRow in 0 until rows)
                            grid[col][headRow] = chars.random()

                        // Draw trail
                        for (t in 0 until trails[col]) {
                            val row = headRow - t
                            if (row < 0 || row >= rows) continue
                            val ch = grid[col][row].toString()
                            val x = col * CELL
                            val y = row * CELL + CELL

                            when (t) {
                                0 -> { // Bright white head
                                    paintHead.alpha = 255
                                    canvas.drawText(ch, x, y, paintHead)
                                }
                                1 -> { // Near-head — bright red
                                    paintGreen.color = Color.argb(230, 255, 80, 80)
                                    canvas.drawText(ch, x, y, paintGreen)
                                }
                                else -> { // Fading red trail
                                    val fade = 1f - t.toFloat() / trails[col]
                                    val alpha = (fade * 200).toInt().coerceIn(20, 200)
                                    val red   = (80 + fade * 150).toInt().coerceIn(60, 230)
                                    paintGreen.color = Color.argb(alpha, red, 0, 0)
                                    canvas.drawText(ch, x, y, paintGreen)
                                }
                            }
                        }

                        // Advance drop
                        yPos[col] += speeds[col]
                        if (yPos[col] > rows + trails[col]) {
                            yPos[col] = -Random.nextFloat() * (rows / 2)
                            speeds[col] = 0.15f + Random.nextFloat() * 0.4f
                            trails[col] = 6 + Random.nextInt(10)
                        }
                    }

                    // Scatter random dim chars
                    repeat(3) {
                        val c = Random.nextInt(cols)
                        val r = Random.nextInt(rows)
                        canvas.drawText(grid[c][r].toString(), c * CELL, r * CELL + CELL, paintDim)
                    }

                } finally {
                    try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
                }
                sleep(40) // ~25 FPS
            }
        }
    }
}
