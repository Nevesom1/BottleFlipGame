package com.example.bottleflip

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

class MainActivity : Activity() {
    private var adView: AdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Создаем контейнер, который накладывает элементы друг на друга
        val layout = FrameLayout(this)

        // 1. Добавляем игру на весь экран
        val gameView = GameView(this)
        layout.addView(gameView, FrameLayout.LayoutParams(-1, -1))

        // 2. Создаем рекламный баннер
        adView = AdView(this)
        // Тестовый ID баннера от Google
        adView?.adUnitId = "ca-app-pub-3940256099942544/6300978111"
        adView?.setAdSize(AdSize.BANNER)

        // Настраиваем позицию баннера: внизу по центру
        val adParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        layout.addView(adView, adParams)

        setContentView(layout)

        // 3. Загружаем рекламу
        val adRequest = AdRequest.Builder().build()
        adView?.loadAd(adRequest)
    }

    override fun onDestroy() {
        // Обязательно уничтожаем баннер при закрытии игры, чтобы не было утечек памяти
        adView?.destroy()
        super.onDestroy()
    }
}
