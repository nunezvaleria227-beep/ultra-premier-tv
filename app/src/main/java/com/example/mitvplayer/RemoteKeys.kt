package com.example.mitvplayer

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Controles universales, el control de la tele (HDMI-CEC), teclados y mandos mandan a veces otras teclas que el
 * control del Fire TV. Aquí se cambian por las del Fire TV, así la app se maneja igual con cualquiera:
 *  - OK: botón A o "Select" de mandos y controles genéricos.
 *  - Atrás: Esc de teclados y botón B de mandos.
 *  - Flechas: las de navegación del sistema que mandan algunos controles con panel táctil.
 * El control del Fire TV no cambia en nada.
 */
object RemoteKeys {
    fun translate(event: KeyEvent): KeyEvent {
        val code = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_SELECT -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> KeyEvent.KEYCODE_DPAD_UP
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> return event
        }
        return KeyEvent(
            event.downTime, event.eventTime, event.action, code, event.repeatCount, event.metaState,
            event.deviceId, event.scanCode, event.flags, event.source
        )
    }

    private var hatX = 0
    private var hatY = 0

    /**
     * Mandos y controles que mandan la cruceta como "palanca" (eje HAT) en vez de teclas:
     * cada movimiento se convierte en la flecha correspondiente. Devuelve true si se usó.
     */
    fun hatToKeys(event: MotionEvent, send: (KeyEvent) -> Unit): Boolean {
        val fromPad = event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_GAMEPAD)
        if (!fromPad || event.actionMasked != MotionEvent.ACTION_MOVE) return false
        val x = direction(event.getAxisValue(MotionEvent.AXIS_HAT_X))
        val y = direction(event.getAxisValue(MotionEvent.AXIS_HAT_Y))
        if (x == hatX && y == hatY) return false
        if (x != hatX && x != 0) press(if (x < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT, event, send)
        if (y != hatY && y != 0) press(if (y < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN, event, send)
        hatX = x
        hatY = y
        return true
    }

    private fun direction(value: Float): Int = when {
        value < -0.5f -> -1
        value > 0.5f -> 1
        else -> 0
    }

    private fun press(code: Int, event: MotionEvent, send: (KeyEvent) -> Unit) {
        val now = SystemClock.uptimeMillis()
        send(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, 0, event.deviceId, 0, 0, InputDevice.SOURCE_DPAD))
        send(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, 0, event.deviceId, 0, 0, InputDevice.SOURCE_DPAD))
    }
}
