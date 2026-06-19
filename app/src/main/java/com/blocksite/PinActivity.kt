package com.blocksite

import android.app.Activity
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.blocksite.data.PinRepository

class PinActivity : AppCompatActivity() {

    private lateinit var pinRepository: PinRepository
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        pinRepository = PinRepository(applicationContext)

        if (pinRepository.isDelayActive()) {
            showTimer()
        } else {
            showPinInput()
        }
    }

    private fun showPinInput() {
        val layoutPin = findViewById<View>(R.id.layoutPinInput)
        val layoutTimer = findViewById<View>(R.id.layoutTimer)
        val editPin = findViewById<EditText>(R.id.editPin)
        val btnEnter = findViewById<Button>(R.id.btnEnterPin)
        val textError = findViewById<TextView>(R.id.textPinError)

        layoutPin.visibility = View.VISIBLE
        layoutTimer.visibility = View.GONE

        btnEnter.setOnClickListener {
            val pin = editPin.text.toString()
            if (pinRepository.checkPin(pin)) {
                pinRepository.startDelay()
                showTimer()
            } else {
                textError.text = "Неверный PIN"
                editPin.text.clear()
            }
        }
    }

    private fun showTimer() {
        val layoutPin = findViewById<View>(R.id.layoutPinInput)
        val layoutTimer = findViewById<View>(R.id.layoutTimer)
        val textTimer = findViewById<TextView>(R.id.textTimer)
        val textTimerLabel = findViewById<TextView>(R.id.textTimerLabel)

        layoutPin.visibility = View.GONE
        layoutTimer.visibility = View.VISIBLE

        val remaining = pinRepository.getRemainingDelayMs()

        textTimerLabel.text = "Подождите перед входом"

        countDownTimer = object : CountDownTimer(remaining, 1000) {
            override fun onTick(millisLeft: Long) {
                val minutes = millisLeft / 60000
                val seconds = (millisLeft % 60000) / 1000
                textTimer.text = "%02d:%02d".format(minutes, seconds)
            }

            override fun onFinish() {
                textTimer.text = "00:00"
                setResult(Activity.RESULT_OK)
                finish()
            }
        }.start()
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
