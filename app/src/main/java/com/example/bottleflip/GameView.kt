package com.example.bottleflip

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GameView(context: Context) : View(context) {

    private enum class State { READY, CHARGING, FLYING, LANDED, GAMEOVER }
    private var state = State.READY

    // ---- Бутылка (мировые X, экранные Y) ----
    private var bottleX = 200f
    private var bottleY = 0f
    private var bottleVX = 0f
    private var bottleVY = 0f
    private var rot = 0f
    private var rotVel = 0f

    // ---- Камера ----
    private var camX = 0f

    // ---- Размеры ----
    private val bottleW = 56f
    private val bottleH = 170f
    private val platformW = 220f

    // ---- Физика ----
    private val gravity = 1.2f
    private val launchVY = -30f
    private val minVX = 4f
    private val maxVX = 20f
    private val minRotVel = 6f
    private val maxRotVel = 24f
    private val uprightTol = 70f

    // ---- Заряд ----
    private var chargeStart = 0L
    private var power = 0f
    private val maxChargeMs = 1000L

    // ---- Платформы ----
    private val platforms = mutableListOf<Float>()  // world X centers
    private var currentIdx = 0

    private var landTimer = 0
    private var score = 0
    private var best = 0
    private var initialized = false

    // ---- Paints ----
    private val skyPaint = Paint()
    private val groundPaint = Paint().apply { color = Color.rgb(90, 170, 70); isAntiAlias = true }
    private val bottleBodyPaint = Paint().apply { isAntiAlias = true }
    private val waterPaint = Paint().apply { isAntiAlias = true }
    private val capPaint = Paint().apply { isAntiAlias = true; color = Color.rgb(220, 60, 60) }
    private val capHiPaint = Paint().apply { isAntiAlias = true; color = Color.argb(110, 255, 255, 255) }
    private val outlinePaint = Paint().apply {
        style = Paint.Style.STROKE; color = Color.rgb(90, 130, 160); strokeWidth = 3f; isAntiAlias = true
    }
    private val platformSidePaint = Paint().apply { isAntiAlias = true }
    private val platformTopPaint = Paint().apply { isAntiAlias = true }
    private val platformEdgePaint = Paint().apply {
        style = Paint.Style.STROKE; color = Color.rgb(210, 240, 255); strokeWidth = 3f; isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE; textSize = 90f; textAlign = Paint.Align.CENTER; isAntiAlias = true
        setShadowLayer(8f, 0f, 4f, Color.BLACK)
    }
    private val smallPaint = Paint().apply {
        color = Color.WHITE; textSize = 48f; textAlign = Paint.Align.CENTER; isAntiAlias = true
        setShadowLayer(6f, 0f, 3f, Color.BLACK)
    }
    private val powerFillPaint = Paint().apply { isAntiAlias = true; color = Color.rgb(255, 200, 0) }
    private val powerBgPaint = Paint().apply {
        isAntiAlias = true; color = Color.argb(150, 0, 0, 0)
        style = Paint.Style.STROKE; strokeWidth = 5f
    }
    private val overlayPaint = Paint().apply { color = Color.argb(160, 0, 0, 0) }

    private val platformTopY: Float get() = height * 0.72f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initialized) { initialized = true; reset() }
    }

    private fun reset() {
        platforms.clear()
        platforms.add(200f)
        repeat(6) { platforms.add(platforms.last() + pickDistance()) }
        currentIdx = 0
        bottleX = platforms[0]
        bottleVX = 0f; bottleVY = 0f; rot = 0f; rotVel = 0f
        camX = bottleX - width * 0.35f
        score = 0
        power = 0f
        state = State.READY
        snapBottle()
    }

    private fun pickDistance(): Float {
        val magic = floatArrayOf(253f, 573f, 893f)
        val base = magic[Random.nextInt(magic.size)]
        return base + Random.nextFloat() * 50f - 25f
    }

    private fun snapBottle() {
        bottleY = platformTopY - bottleH / 2
        rot = 0f; bottleVX = 0f; bottleVY = 0f
    }

    // ============ UPDATE ============
    private fun update() {
        if (state == State.CHARGING) {
            power = ((System.currentTimeMillis() - chargeStart).toFloat() / maxChargeMs)
                .coerceIn(0f, 1f)
        }

        when (state) {
            State.FLYING -> {
                bottleVY += gravity
                bottleX += bottleVX
                bottleY += bottleVY
                rot += rotVel
                checkLanding()
                if (bottleY > height + 400) gameOver()
            }
            State.LANDED -> {
                landTimer--
                if (landTimer <= 0) {
                    currentIdx++
                    while (currentIdx + 5 >= platforms.size)
                        platforms.add(platforms.last() + pickDistance())
                    snapBottle()
                    state = State.READY
                }
            }
            else -> {}
        }

        val targetCam = bottleX - width * 0.35f
        camX += (targetCam - camX) * 0.12f
    }

    private fun gameOver() {
        state = State.GAMEOVER
        if (score > best) best = score
    }

    private fun checkLanding() {
        if (bottleVY <= 0) return
        val rad = Math.toRadians(rot.toDouble())
        val bottomOff = (bottleW / 2 * abs(sin(rad)) + bottleH / 2 * abs(cos(rad))).toFloat()
        val bottleBottom = bottleY + bottomOff
        if (bottleBottom < platformTopY) return

        for (i in platforms.indices) {
            val pX = platforms[i]
            if (bottleX in (pX - platformW / 2)..(pX + platformW / 2)) {
                if (i == currentIdx + 1) {
                    val norm = ((rot % 360f) + 360f) % 360f
                    val upright = norm < uprightTol || norm > 360f - uprightTol
                    if (upright) {
                        snapBottle()
                        state = State.LANDED
                        landTimer = 25
                        score++
                    } else {
                        bottleY = platformTopY - bottomOff
                        bottleVY = 0f; bottleVX = 0f
                        gameOver()
                    }
                } else {
                    gameOver()
                }
                return
            }
        }
    }

    // ============ DRAW ============
    override fun onDraw(canvas: Canvas) {
        update()

        // Небо
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.rgb(70, 150, 235), Color.rgb(190, 225, 255),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), skyPaint)

        // Земля/пол
        canvas.drawRect(0f, platformTopY + platformW / 6f,
            width.toFloat(), height.toFloat(), groundPaint)

        // Платформы
        for (pWorldX in platforms) {
            val pX = pWorldX - camX
            if (pX < -platformW || pX > width + platformW) continue
            drawPlatform(canvas, pX)
        }

        drawBottle(canvas)

        // HUD
        canvas.drawText(score.toString(), width / 2f, 150f, textPaint)
        canvas.drawText("Рекорд: $best", width / 2f, 215f, smallPaint)

        if (state == State.CHARGING) {
            val bw = width * 0.65f
            val bx = (width - bw) / 2f
            val by = height - 150f
            val bh = 34f
            canvas.drawRoundRect(bx, by, bx + bw, by + bh, 17f, 17f, powerBgPaint)
            canvas.drawRoundRect(bx, by, bx + bw * power, by + bh, 17f, 17f, powerFillPaint)
        }

        if (state == State.READY)
            canvas.drawText("Зажми и отпусти", width / 2f, height - 210f, smallPaint)

        if (state == State.GAMEOVER) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
            canvas.drawText("Промах!", width / 2f, height / 2f - 60f, textPaint)
            canvas.drawText("Счёт: $score", width / 2f, height / 2f + 40f, smallPaint)
            canvas.drawText("Нажми для рестарта", width / 2f, height / 2f + 130f, smallPaint)
        }

        postInvalidateOnAnimation()
    }

    private fun drawPlatform(canvas: Canvas, screenX: Float) {
        val topY = platformTopY
        val bottomY = height + 60f
        val hw = platformW / 2
        val ry = platformW / 6f

        // Боковая часть (градиент = цилиндр)
        platformSidePaint.shader = LinearGradient(
            screenX - hw, 0f, screenX + hw, 0f,
            intArrayOf(
                Color.rgb(70, 130, 190),
                Color.rgb(150, 205, 245),
                Color.rgb(70, 130, 190)
            ),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(screenX - hw, topY, screenX + hw, bottomY, platformSidePaint)

        // Верх (эллипс)
        platformTopPaint.shader = RadialGradient(
            screenX - hw * 0.3f, topY - ry * 0.3f, platformW * 0.75f,
            intArrayOf(Color.rgb(170, 230, 250), Color.rgb(100, 170, 215)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(screenX - hw, topY - ry, screenX + hw, topY + ry, platformTopPaint)
        canvas.drawOval(screenX - hw, topY - ry, screenX + hw, topY + ry, platformEdgePaint)
    }

    private fun drawBottle(canvas: Canvas) {
        canvas.save()
        canvas.translate(bottleX - camX, bottleY)
        canvas.rotate(rot)

        val hw = bottleW / 2
        val hh = bottleH / 2

        val bodyBottom = hh
        val bodyTop = -hh * 0.40f
        val shoulderTop = -hh * 0.65f
        val neckTop = -hh * 0.88f
        val capTop = -hh

        val path = Path().apply {
            moveTo(-hw, bodyBottom - 15f)
            quadTo(-hw, bodyBottom, 0f, bodyBottom)
            quadTo(hw, bodyBottom, hw, bodyBottom - 15f)
            lineTo(hw, bodyTop)
            lineTo(hw * 0.5f, shoulderTop)
            lineTo(hw * 0.5f, neckTop)
            lineTo(hw * 0.55f, neckTop)
            lineTo(hw * 0.55f, capTop)
            lineTo(-hw * 0.55f, capTop)
            lineTo(-hw * 0.55f, neckTop)
            lineTo(-hw * 0.5f, neckTop)
            lineTo(-hw * 0.5f, shoulderTop)
            lineTo(-hw, bodyTop)
            close()
        }

        // Корпус (стеклянный цилиндр)
        bottleBodyPaint.shader = LinearGradient(
            -hw, 0f, hw, 0f,
            intArrayOf(
                Color.rgb(150, 190, 220),
                Color.WHITE,
                Color.rgb(150, 190, 220)
            ),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, bottleBodyPaint)

        // Вода (нижние 55%)
        canvas.save()
        canvas.clipPath(path)
        val waterTop = hh - bottleH * 0.55f
        waterPaint.shader = LinearGradient(
            -hw, waterTop, hw, hh,
            Color.rgb(85, 165, 225), Color.rgb(30, 100, 170),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(-hw, waterTop, hw, hh, waterPaint)
        canvas.restore()

        // Контур
        canvas.drawPath(path, outlinePaint)

        // Крышка
        canvas.drawRect(-hw * 0.55f, capTop, hw * 0.55f, neckTop, capPaint)
        canvas.drawRect(-hw * 0.45f, capTop + 4f, -hw * 0.15f, neckTop - 2f, capHiPaint)

        canvas.restore()
    }

    // ============ INPUT ============
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (state == State.READY) {
                    state = State.CHARGING
                    chargeStart = System.currentTimeMillis()
                    power = 0f
                } else if (state == State.GAMEOVER) {
                    reset()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (state == State.CHARGING) launch()
            }
        }
        return true
    }

    private fun launch() {
        val p = power.coerceIn(0.05f, 1f)
        bottleVX = minVX + (maxVX - minVX) * p
        bottleVY = launchVY
        rotVel = minRotVel + (maxRotVel - minRotVel) * p
        state = State.FLYING
        power = 0f
    }
}
