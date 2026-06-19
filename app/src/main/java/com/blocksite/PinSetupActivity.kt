package com.blocksite

import android.app.Activity
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.blocksite.data.PinRepository

class PinSetupActivity : AppCompatActivity() {

    private lateinit var pinRepository: PinRepository

    private val delayOptions = listOf(
        "1 минута" to 60,
        "5 минут" to 300,
        "10 минут" to 600,
        "30 минут" to 1800
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_setup)

        pinRepository = PinRepository(applicationContext)

        val editPin = findViewById<EditText>(R.id.editPinNew)
        val editPinConfirm = findViewById<EditText>(R.id.editPinConfirm)
        val spinnerDelay = findViewById<Spinner>(R.id.spinnerDelay)
        val btnSave = findViewById<Button>(R.id.btnSavePin)
        val textError = findViewById<TextView>(R.id.textPinSetupError)

        spinnerDelay.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            delayOptions.map { it.first }
        )

        btnSave.setOnClickListener {
            val pin = editPin.text.toString()
            val confirm = editPinConfirm.text.toString()

            when {
                pin.length < 4 -> textError.text = "PIN должен быть не менее 4 цифр"
                pin != confirm -> textError.text = "PIN-коды не совпадают"
                else -> {
                    val delaySeconds = delayOptions[spinnerDelay.selectedItemPosition].second
                    pinRepository.setPin(pin)
                    pinRepository.setDelaySeconds(delaySeconds)
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
        }
    }

    override fun onBackPressed() {
        // Запрещаем закрыть экран установки без сохранения PIN
        if (!pinRepository.hasPin()) {
            Toast.makeText(this, "Сначала установите PIN", Toast.LENGTH_SHORT).show()
        } else {
            super.onBackPressed()
        }
    }
}
