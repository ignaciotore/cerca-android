package com.help.seguridad

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import java.util.UUID

class MedicationActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private val dayNames = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medications)
        list = findViewById(R.id.medicationList)
        status = findViewById(R.id.medicationStatus)
        findViewById<Button>(R.id.addMedicationButton).setOnClickListener { showEditor(null) }
        findViewById<Button>(R.id.medicationBackButton).setOnClickListener { finish() }
        requestNotificationPermissionIfNeeded()
        render()
    }

    override fun onResume() {
        super.onResume()
        MedicationReminderScheduler.sync(this)
        render()
    }

    private fun render() {
        list.removeAllViews()
        val items = MedicationStore.load(this)
        if (items.isEmpty()) {
            list.addView(text("Todavía no cargaste medicamentos.", 16f, false))
            status.text = "Agregá un remedio y elegí sus días y horarios."
            return
        }
        items.forEach { item -> list.addView(medicationCard(item)) }
        val active = items.count { it.reminderEnabled }
        status.text = "${items.size} medicamento(s) · $active con recordatorio activo"
    }

    private fun medicationCard(item: MedicationItem): LinearLayout {
        val pad = dp(16)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0xFFE0E9E7.toInt())
            }
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(12)) }
        card.layoutParams = params
        card.addView(text(item.name, 20f, true))
        if (item.dose.isNotBlank()) card.addView(text("Dosis: " + item.dose, 15f, false))
        card.addView(text("Días: " + formatDays(item.days), 14f, false))
        card.addView(text("Horarios: " + item.times.joinToString(" · "), 14f, false))
        card.addView(text(if (item.reminderEnabled) "🔔 Recordatorio activo" else "Recordatorio desactivado", 14f, true))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val edit = Button(this).apply { text = "EDITAR"; setOnClickListener { showEditor(item) } }
        val delete = Button(this).apply { text = "ELIMINAR"; setOnClickListener { confirmDelete(item) } }
        actions.addView(edit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(delete, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(actions)
        return card
    }

    private fun showEditor(existing: MedicationItem?) {
        val pad = dp(18)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        val name = EditText(this).apply { hint = "Nombre del remedio"; setText(existing?.name.orEmpty()) }
        val dose = EditText(this).apply { hint = "Dosis · ej. 1 comprimido / 500 mg"; setText(existing?.dose.orEmpty()) }
        body.addView(name)
        body.addView(dose)
        body.addView(text("Días", 17f, true))
        val dayChecks = (1..7).map { day ->
            CheckBox(this).apply {
                text = dayNames[day - 1]
                isChecked = existing?.days?.contains(day) ?: true
            }.also { body.addView(it) }
        }
        body.addView(text("Horarios", 17f, true))
        val times = (existing?.times ?: emptyList()).toMutableList()
        val timesText = text("", 16f, true)
        body.addView(timesText)
        fun refreshTimes() { times.sort(); timesText.text = if (times.isEmpty()) "Sin horarios" else times.joinToString(" · ") }
        refreshTimes()
        val addTime = Button(this).apply {
            text = "AGREGAR HORARIO"
            setOnClickListener {
                val initialHour = 8
                TimePickerDialog(this@MedicationActivity, { _, hour, minute ->
                    val value = String.format(Locale.US, "%02d:%02d", hour, minute)
                    if (value !in times) times.add(value)
                    refreshTimes()
                }, initialHour, 0, true).show()
            }
        }
        val clearTimes = Button(this).apply { text = "BORRAR HORARIOS"; setOnClickListener { times.clear(); refreshTimes() } }
        body.addView(addTime)
        body.addView(clearTimes)
        val reminder = CheckBox(this).apply { text = "Avisarme con una notificación en cada horario"; isChecked = existing?.reminderEnabled ?: true }
        body.addView(reminder)

        val scroll = ScrollView(this).apply { addView(body) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Agregar medicación" else "Editar medicación")
            .setView(scroll)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val medName = name.text.toString().trim()
                val selectedDays = dayChecks.mapIndexedNotNull { index, check -> if (check.isChecked) index + 1 else null }
                if (medName.isBlank()) { toast("Ingresá el nombre del remedio."); return@setOnClickListener }
                if (selectedDays.isEmpty()) { toast("Elegí al menos un día."); return@setOnClickListener }
                if (times.isEmpty()) { toast("Agregá al menos un horario."); return@setOnClickListener }
                if (reminder.isChecked) requestNotificationPermissionIfNeeded()
                MedicationStore.upsert(this, MedicationItem(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = medName,
                    dose = dose.text.toString().trim(),
                    days = selectedDays,
                    times = times.distinct().sorted(),
                    reminderEnabled = reminder.isChecked
                ))
                MedicationReminderScheduler.sync(this)
                dialog.dismiss()
                render()
                toast("Medicación guardada.")
            }
        }
        dialog.show()
    }

    private fun confirmDelete(item: MedicationItem) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar " + item.name + "?")
            .setMessage("También se cancelarán sus recordatorios.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                MedicationStore.delete(this, item.id)
                MedicationReminderScheduler.sync(this)
                render()
            }.show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
    }

    private fun formatDays(days: List<Int>): String {
        val sorted = days.distinct().sorted()
        return if (sorted.size == 7) "Todos los días" else sorted.mapNotNull { dayNames.getOrNull(it - 1) }.joinToString(" · ")
    }

    private fun text(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(0xFF34444B.toInt())
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object { private const val REQ_NOTIFICATIONS = 902 }
}
