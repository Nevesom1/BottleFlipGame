package com.example.bottleflip

import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class GameView(context: Context) : View(context) {

    private enum class State { MENU, READY, CHARGING, FLYING, LANDED, GAMEOVER }
    private var state = State.MENU

    // Локации
    private enum class Theme { 
        BACKYARD, LIVING_ROOM, KITCHEN, GARAGE, 
        GARDEN, STORE, AIRPORT, SPACE 
    }

    private val prefs = context.getSharedPreferences("bottle_flip_prefs", Context.MODE_PRIVATE)
    private var best = prefs.getInt("best_score", 0)
    private val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    private var bottleX = 200f
    private var bottleY = 0f
    private var bottleVX = 0f
    private var bottleVY = 0f
    private var rot = 0f
    private var rotVel = 0f
    private var scaleY = 1f
    private var bounceVel = 0f
    private var camX = 0f

    private val bottleW = 56f
    private val bottleH = 170f
    
    // Предметы (платформы)
    private data class Item(val x: Float, val width: Float)
    private val items = mutableListOf<Item>()
    private var currentIdx = 0

    private val gravity = 1.2f
    private val launchVY = -30f
    private val minVX = 4f
    private val maxVX = 20f
    private val minRotVel = 7f
    private val maxRotVel = 28f
    private val uprightTol = 70f

    private var chargeStart = 0L
    private var power = 0f
    private val maxChargeMs = 1000L

    private var landTimer = 0
    private var score = 0
    private var initialized = false
    private var pulseTime = 0L
    
    private val density = resources.displayMetrics.density
    private val adBannerHeight = 60 * density

    private val platformTopY: Float get() = height * 0.72f
    
    // Текущая тема
    private val currentTheme: Theme get() = Theme.values()[(score / 10) % Theme.values().size]

    // Paints
    private val bgPaint = Paint().apply { isAntiAlias = true }
    private val floorPaint = Paint().apply { isAntiAlias = true; color = Color.rgb(60, 60, 60) }
    private val objectPaint = Paint().apply { isAntiAlias = true }
    private val objectDetailPaint = Paint().apply { isAntiAlias = true }
    private val bottleBodyPaint = Paint().apply { isAntiAlias = true }
    private val waterPaint = Paint().apply { isAntiAlias = true }
    private val shadowPaint = Paint().apply { color = Color.argb(80, 0, 0, 0); isAntiAlias = true }
    private val capPaint = Paint().apply { isAntiAlias = true; color = Color.rgb(220, 60, 60) }
    private val capHiPaint = Paint().apply { isAntiAlias = true; color = Color.argb(110, 255, 255, 255) }
    private val outlinePaint = Paint().apply {
        style = Paint.Style.STROKE; color = Color.rgb(90, 130, 160); strokeWidth = 3f; isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE; textSize = 90f; textAlign = Paint.Align.CENTER; isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 4f, Color.BLACK)
    }
    private val titlePaint = Paint().apply {
        color = Color.WHITE; textSize = 120f; textAlign = Paint.Align.CENTER; isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(10f, 0f, 5f, Color.BLACK)
    }
    private val buttonPaint = Paint().apply {
        color = Color.rgb(255, 200, 0); isAntiAlias = true
        setShadowLayer(10f, 0f, 5f, Color.argb(100, 0, 0, 0))
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initialized) { initialized = true; reset() }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        toneGen.release()
    }

    private fun reset() {
        items.clear()
        items.add(Item(200f, 220f))
        repeat(6) { items.add(Item(items.last().x + pickDistance(), 160f + Random.nextFloat() * 100f)) }
        currentIdx = 0
        bottleX = items[0].x
        bottleVX = 0f; bottleVY = 0f; rot = 0f; rotVel = 0f
        scaleY = 1f; bounceVel = 0f
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
        scaleY = 1f
    }

    private fun update() {
        pulseTime += 16
        
        if (state == State.CHARGING) {
            power = ((System.currentTimeMillis() - chargeStart).toFloat() / maxChargeMs).coerceIn(0f, 1f)
        }

        when (state) {
            State.FLYING -> {
                bottleVY += gravity
                bottleX += bottleVX
                bottleY += bottleVY
                
                rotVel *= 0.99f
                rot += rotVel
                
                checkLanding()
                if (bottleY > height + 400) gameOver()
            }
            State.LANDED -> {
                landTimer--
                if (bounceVel > 0.1f || bounceVel < -0.1f) {
                    scaleY += bounceVel
                    bounceVel *= 0.8f
                } else {
                    scaleY = 1f
                }
                
                if (landTimer <= 0) {
                    currentIdx++
                    while (currentIdx + 5 >= items.size)
                        items.add(Item(items.last().x + pickDistance(), 160f + Random.nextFloat() * 100f))
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
        if (score > best) {
            best = score
            prefs.edit().putInt("best_score", best).apply()
        }
        toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
    }

    private fun checkLanding() {
        if (bottleVY <= 0) return
        val rad = Math.toRadians(rot.toDouble())
        val bottomOff = (bottleW / 2 * abs(sin(rad)) + bottleH / 2 * abs(Math.cos(rad))).toFloat()
        val bottleBottom = bottleY + bottomOff
        if (bottleBottom < platformTopY) return

        for (i in items.indices) {
            val item = items[i]
            // Проверка, попали ли мы на предмет
            if (bottleX in (item.x - item.width / 2)..(item.x + item.width / 2)) {
                if (i == currentIdx + 1) {
                    val norm = ((rot % 360f) + 360f) % 360f
                    val upright = norm < uprightTol || norm > 360f - uprightTol
                    if (upright) {
                        snapBottle()
                        state = State.LANDED
                        landTimer = 25
                        score++
                        scaleY = 0.8f
                        bounceVel = 0.05f
                        toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 150) 
                    } else {
                        bottleY = platformTopY - bottomOff
                        bottleVY = 0f; bottleVX = 0f
                        gameOver()
                    }
                } else {
                    gameOver() // Промахнулись мимо нужного предмета
                }
                return
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        update()
        
        val theme = currentTheme
        drawBackground(canvas, theme)

        if (state == State.MENU) {
            drawMenu(canvas)
        } else {
            // Рисуем предметы
            for (i in items.indices) {
                val item = items[i]
                val pX = item.x - camX
                if (pX < -item.width || pX > width + item.width) continue
                drawItem(canvas, pX, item.width, theme)
            }

            // Тень
            val shadowY = platformTopY
            val shadowScale = (1f - (shadowY - bottleY) / 800f).coerceIn(0.3f, 1f)
            canvas.drawOval(bottleX - camX - (bottleW/2) * shadowScale, shadowY - 10f, bottleX - camX + (bottleW/2) * shadowScale, shadowY + 10f, shadowPaint)

            drawBottle(canvas)

            canvas.drawText(score.toString(), width / 2f, 180f, textPaint)
            canvas.drawText("Рекорд: $best", width / 2f, 245f, smallPaint)

            if (state == State.CHARGING) {
                val bw = width * 0.65f
                val bx = (width - bw) / 2f
                val by = height - adBannerHeight - 100f
                val bh = 34f
                canvas.drawRoundRect(bx, by, bx + bw, by + bh, 17f, 17f, powerBgPaint)
                canvas.drawRoundRect(bx, by, bx + bw * power, by + bh, 17f, 17f, powerFillPaint)
            }

            if (state == State.READY) {
                val alpha = (128 + 127 * sin(pulseTime / 300.0)).toInt()
                smallPaint.alpha = alpha
                canvas.drawText("Зажми и отпусти", width / 2f, height - adBannerHeight - 120f, smallPaint)
                smallPaint.alpha = 255
            }

            if (state == State.GAMEOVER) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
                canvas.drawText("Промах!", width / 2f, height / 2f - 60f, textPaint)
                canvas.drawText("Счёт: $score", width / 2f, height / 2f + 40f, smallPaint)
                canvas.drawText("Нажми для рестарта", width / 2f, height / 2f + 130f, smallPaint)
            }
        }

        postInvalidateOnAnimation()
    }

    private fun drawBackground(canvas: Canvas, theme: Theme) {
        val bgColorTop: Int
        val bgColorBottom: Int
        val floorColor: Int

        when (theme) {
            Theme.BACKYARD -> { bgColorTop = Color.rgb(135, 206, 235); bgColorBottom = Color.rgb(200, 240, 255); floorColor = Color.rgb(34, 139, 34) }
            Theme.LIVING_ROOM -> { bgColorTop = Color.rgb(220, 200, 180); bgColorBottom = Color.rgb(240, 220, 200); floorColor = Color.rgb(139, 69, 19) }
            Theme.KITCHEN -> { bgColorTop = Color.rgb(255, 250, 240); bgColorBottom = Color.rgb(245, 245, 220); floorColor = Color.rgb(169, 169, 169) }
            Theme.GARAGE -> { bgColorTop = Color.rgb(105, 105, 105); bgColorBottom = Color.rgb(150, 150, 150); floorColor = Color.rgb(80, 80, 80) }
            Theme.GARDEN -> { bgColorTop = Color.rgb(152, 251, 152); bgColorBottom = Color.rgb(200, 255, 200); floorColor = Color.rgb(34, 139, 34) }
            Theme.STORE -> { bgColorTop = Color.rgb(230, 230, 250); bgColorBottom = Color.rgb(255, 255, 255); floorColor = Color.rgb(200, 200, 200) }
            Theme.AIRPORT -> { bgColorTop = Color.rgb(176, 196, 222); bgColorBottom = Color.rgb(200, 220, 250); floorColor = Color.rgb(112, 128, 144) }
            Theme.SPACE -> { bgColorTop = Color.BLACK; bgColorBottom = Color.rgb(25, 25, 112); floorColor = Color.rgb(50, 50, 50) }
        }

        bgPaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), bgColorTop, bgColorBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        
        // Пол
        floorPaint.color = floorColor
        canvas.drawRect(0f, platformTopY, width.toFloat(), height.toFloat(), floorPaint)
    }

    private fun drawItem(canvas: Canvas, x: Float, w: Float, theme: Theme) {
        val topY = platformTopY
        val hw = w / 2

        when (theme) {
            Theme.BACKYARD -> {
                // Лавочка / ящик
                objectPaint.color = Color.rgb(139, 69, 19) // Дерево
                canvas.drawRect(x - hw, topY, x + hw, topY + 60f, objectPaint)
                objectDetailPaint.color = Color.rgb(100, 50, 10)
                canvas.drawRect(x - hw, topY + 20f, x + hw, topY + 30f, objectDetailPaint)
            }
            Theme.LIVING_ROOM -> {
                // Диван
                objectPaint.color = Color.rgb(200, 50, 50) // Красный диван
                canvas.drawRoundRect(x - hw, topY, x + hw, topY + 80f, 20f, 20f, objectPaint)
                objectDetailPaint.color = Color.rgb(150, 30, 30)
                canvas.drawRoundRect(x - hw + 10f, topY + 10f, x - hw + w/2 - 10f, topY + 60f, 10f, 10f, objectDetailPaint)
                canvas.drawRoundRect(x - hw + w/2 + 10f, topY + 10f, x + hw - 10f, topY + 60f, 10f, 10f, objectDetailPaint)
            }
            Theme.KITCHEN -> {
                // Стол
                objectPaint.color = Color.rgb(160, 82, 45) // Деревянный стол
                canvas.drawRect(x - hw, topY, x + hw, topY + 20f, objectPaint)
                canvas.drawRect(x - hw + 20f, topY + 20f, x - hw + 40f, topY + 120f, objectPaint) // Ножка
                canvas.drawRect(x + hw - 40f, topY + 20f, x + hw - 20f, topY + 120f, objectPaint) // Ножка
            }
            Theme.GARAGE -> {
                // Колесо / бочка
                objectPaint.color = Color.rgb(40, 40, 40)
                canvas.drawRoundRect(x - hw, topY, x + hw, topY + 100f, 30f, 30f, objectPaint)
                objectDetailPaint.color = Color.rgb(80, 80, 80)
                canvas.drawCircle(x, topY + 50f, hw * 0.6f, objectDetailPaint)
            }
            Theme.GARDEN -> {
                // Клумба / куст
                objectPaint.color = Color.rgb(34, 139, 34)
                canvas.drawOval(x - hw, topY - 20f, x + hw, topY + 60f, objectPaint)
            }
            Theme.STORE -> {
                // Коробка / стеллаж
                objectPaint.color = Color.rgb(210, 180, 140)
                canvas.drawRect(x - hw, topY, x + hw, topY + 100f, objectPaint)
                objectDetailPaint.color = Color.rgb(180, 150, 110)
                canvas.drawRect(x - hw + 10f, topY + 10f, x + hw - 10f, topY + 50f, objectDetailPaint)
            }
            Theme.AIRPORT -> {
                // Крыло самолета
                objectPaint.color = Color.rgb(200, 200, 200)
                val path = Path().apply {
                    moveTo(x - hw, topY + 30f)
                    lineTo(x + hw, topY)
                    lineTo(x + hw, topY + 40f)
                    lineTo(x - hw, topY + 70f)
                    close()
                }
                canvas.drawPath(path, objectPaint)
            }
            Theme.SPACE -> {
                // Метеорит (многоугольник)
                objectPaint.color = Color.rgb(100, 100, 100)
                val path = Path().apply {
                    moveTo(x - hw, topY + 20f)
                    lineTo(x - hw * 0.5f, topY)
                    lineTo(x + hw * 0.3f, topY + 10f)
                    lineTo(x + hw, topY + 40f)
                    lineTo(x + hw * 0.6f, topY + 80f)
                    lineTo(x - hw * 0.4f, topY + 70f)
                    close()
                }
                canvas.drawPath(path, objectPaint)
            }
        }
    }

    private fun drawMenu(canvas: Canvas) {
        canvas.drawText("BOTTLE", width / 2f, height / 2f - 200f, titlePaint)
        canvas.drawText("FLIP", width / 2f, height / 2f - 80f, titlePaint)
        
        canvas.drawText("Рекорд: $best", width / 2f, height / 2f + 20f, smallPaint)

        val btnW = width * 0.7f
        val btnH = 120f
        val btnX = width / 2f - btnW / 2
        val btnY = height / 2f + 100f
        
        val rect = RectF(btnX, btnY, btnX + btnW, btnY + btnH)
        canvas.drawRoundRect(rect, 30f, 30f, buttonPaint)
        
        val playTextPaint = Paint().apply {
            color = Color.BLACK; textSize = 60f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("ИГРАТЬ", width / 2f, btnY + btnH / 2f + 20f, playTextPaint)
    }

    private fun drawBottle(canvas: Canvas) {
        canvas.save()
        canvas.translate(bottleX - camX, bottleY)
        canvas.scale(1f, scaleY)
        canvas.rotate(rot)

        val hw = bottleW / 2
        val hh = bottleH / 2
        val comOffset = hh * 0.2f
        canvas.translate(0f, comOffset)
        
        val bodyBottom = hh - comOffset
        val bodyTop = -hh * 0.40f - comOffset
        val shoulderTop = -hh * 0.65f - comOffset
        val neckTop = -hh * 0.88f - comOffset
        val capTop = -hh - comOffset

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

        bottleBodyPaint.shader = LinearGradient(-hw, 0f, hw, 0f, intArrayOf(Color.rgb(150, 190, 220), Color.WHITE, Color.rgb(150, 190, 220)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, bottleBodyPaint)

        canvas.save()
        canvas.clipPath(path)
        val waterTop = bodyBottom - bottleH * 0.55f
        waterPaint.shader = LinearGradient(-hw, waterTop, hw, bodyBottom, Color.rgb(85, 165, 225), Color.rgb(30, 100, 170), Shader.TileMode.CLAMP)
        canvas.drawRect(-hw, waterTop, hw, bodyBottom, waterPaint)
        canvas.restore()

        canvas.drawPath(path, outlinePaint)
        canvas.drawRect(-hw * 0.55f, capTop, hw * 0.55f, neckTop, capPaint)
        canvas.drawRect(-hw * 0.45f, capTop + 4f, -hw * 0.15f, neckTop - 2f, capHiPaint)

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (state == State.MENU) {
                    val btnW = width * 0.7f
                    val btnX = width / 2f - btnW / 2
                    val btnY = height / 2f + 100f
                    if (event.x in btnX..(btnX + btnW) && event.y in btnY..(btnY + 120f)) {
                        reset()
                    }
                } else if (state == State.READY) {
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
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
    }
}
