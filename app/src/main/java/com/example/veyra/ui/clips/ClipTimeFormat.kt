package com.example.veyra.ui.clips

/** "m:ss.t" (tenths), or "h:mm:ss.t" from one hour: clip points need sub-second precision. */
fun formatClipTime(ms: Long): String {
    val tenths = ms.coerceAtLeast(0) / 100
    val totalSeconds = tenths / 10
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val tenth = tenths % 10
    return if (hours > 0) {
        "%d:%02d:%02d.%d".format(hours, minutes, seconds, tenth)
    } else {
        "%d:%02d.%d".format(minutes, seconds, tenth)
    }
}
