package com.ascendant.sentiment.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascendant.sentiment.model.HelioHit
import com.ascendant.sentiment.model.HelioWeek
import com.ascendant.sentiment.model.MercuryWeek
import com.ascendant.sentiment.model.PlutoExact
import com.ascendant.sentiment.model.PlutoGrid
import com.ascendant.sentiment.model.PlutoWeek
import com.ascendant.sentiment.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** v7.0: one colour per aspect angle, used by the heliocentric aspect panel and by the readouts. */
val HelioAspectColors: Map<Int, Color> = mapOf(
    0 to Color(0xFFFFFFFF),     // white
    30 to Color(0xFF6B9E6B),    // dull green
    45 to Color(0xFFFACC15),    // yellow
    60 to Color(0xFFA3E635),    // bright lime
    72 to Color(0xFF38BDF8),    // sky blue
    90 to Color(0xFFFF3B30),    // bright red
    120 to Color(0xFF22C55E),   // green
    135 to Color(0xFFC62828),   // red
    144 to Color(0xFF3B6CF0),   // blue
    150 to Color(0xFF8B5A2B),   // brown
    180 to Color(0xFF800000)    // maroon
)

private val HelioLine = Color(0xFFFBBF24)
private val HelioMacdCyan = Color(0xFF22D3EE)
private val HelioMacdOrange = Color(0xFFFB923C)

private fun fmtH(v: Double): String = (if (v > 0.05) "+" else if (v < -0.05) "−" else "") + "%.1f".format(abs(v))

/** Small colour key: one chip per aspect angle. */
@Composable
fun HelioLegend() {
    val angles = listOf(0, 30, 45, 60, 72, 90, 120, 135, 144, 150, 180)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(angles.take(6), angles.drop(6)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(HelioAspectColors[a] ?: Color.White, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(3.dp))
                        Text("$a°", color = Slate400, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

/**
 * v7.0: heliocentric running total of the aspects to the Sun for the whole week, 6-hour bars.
 * Bold amber line = running total, faint MACD of the total (histogram, MACD line, signal line) behind it.
 */
@Composable
fun HelioFieldCard(
    week: HelioWeek,
    hitsNow: List<HelioHit>,
    inspectedTimeMs: Long,
    nowMs: Long,
    timeZoneId: String,
    onInspectTime: (Long) -> Unit
) {
    val bars = week.bars
    if (bars.isEmpty()) return
    val n = bars.size
    val weekStart = bars.first().startMs
    val weekEnd = weekStart + n * week.barMs
    val selIdx = ((inspectedTimeMs - weekStart) / week.barMs).toInt().coerceIn(0, n - 1)
    val cur = bars[selIdx]
    val iHi = bars.indices.maxByOrNull { bars[it].total } ?: 0
    val iLo = bars.indices.minByOrNull { bars[it].total } ?: 0
    val timeFmt = remember(timeZoneId) { SimpleDateFormat("EEE HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone(timeZoneId) } }
    val dayFmt = remember(timeZoneId) { SimpleDateFormat("EEE d", Locale.US).apply { timeZone = TimeZone.getTimeZone(timeZoneId) } }
    val onInspect = rememberUpdatedState(onInspectTime)
    val maxLine = remember(week) { niceCeil(max(1.0, bars.maxOf { abs(it.total) } * 1.1)) }
    val maxMacd = remember(week) {
        max(0.1, max(week.macd.maxOf { abs(it) }, max(week.signal.maxOf { abs(it) }, week.hist.maxOf { abs(it) }))) * 1.1
    }
    val histNow = week.hist[selIdx]

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("HELIOCENTRIC SUN FIELD · WEEK (6h bars)", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(timeFmt.format(Date(cur.startMs)) + " bar", color = Slate400, fontSize = 11.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Total " + fmtH(cur.total),
                color = if (cur.total >= 0) Emerald400 else Rose400, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Text(
                "MACD hist " + fmtH(histNow) + if (histNow > 0) " (rising)" else " (falling)",
                color = Slate100, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .weekScrub(week, n, weekStart, week.barMs, onInspect)
            ) {
                val left = 42.dp.toPx()
                val right = 12.dp.toPx()
                val top = 8.dp.toPx()
                val bottom = 16.dp.toPx()
                val plotW = size.width - left - right
                val plotH = size.height - top - bottom
                val cell = plotW / n
                fun xOf(i: Int) = left + (i + 0.5f) * cell
                fun yOf(v: Double) = (top + plotH / 2.0 * (1.0 - v / maxLine)).toFloat()
                fun yM(v: Double) = (top + plotH / 2.0 * (1.0 - v / maxMacd)).toFloat()
                val yZero = yOf(0.0)

                // MACD background
                for (i in 0 until n) {
                    val h = week.hist[i]
                    val y = yM(h)
                    val y0 = yM(0.0)
                    drawRect(
                        (if (h >= 0) Emerald500 else Rose500).copy(alpha = 0.22f),
                        Offset(left + i * cell + 1f, min(y, y0)), Size(max(cell - 2f, 1f), max(abs(y - y0), 1f))
                    )
                }
                fun macdLine(values: DoubleArray, color: Color) {
                    val p = Path()
                    for (i in 0 until n) { val x = xOf(i); val y = yM(values[i]); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                    drawPath(p, color, style = Stroke(width = 1.5f, cap = StrokeCap.Round))
                }
                macdLine(week.macd, HelioMacdCyan.copy(alpha = 0.35f))
                macdLine(week.signal, HelioMacdOrange.copy(alpha = 0.35f))

                listOf(1.0, -1.0).forEach { f ->
                    drawLine(Color(0xFF475569).copy(alpha = 0.35f), Offset(left, yOf(f * maxLine)), Offset(left + plotW, yOf(f * maxLine)), 1f)
                }
                drawLine(Color(0xFF475569), Offset(left - 4.dp.toPx(), yZero), Offset(left + plotW, yZero), 1.5f)

                var lastDay = ""
                for (i in 0 until n) {
                    val label = dayFmt.format(Date(bars[i].startMs))
                    if (label != lastDay) {
                        lastDay = label
                        drawLine(Color(0xFF334155).copy(alpha = 0.6f), Offset(left + i * cell, top), Offset(left + i * cell, top + plotH), 1f)
                        drawContext.canvas.nativeCanvas.drawText(
                            label, left + i * cell + 3.dp.toPx(), size.height - 2.dp.toPx(),
                            android.graphics.Paint().apply { isAntiAlias = true; textSize = 9.sp.toPx(); color = Slate400.toArgb() }
                        )
                    }
                }

                // the running total, bold
                val p = Path()
                for (i in 0 until n) { val x = xOf(i); val y = yOf(bars[i].total); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                drawPath(p, HelioLine, style = Stroke(width = 3.6.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(Emerald400, 4.dp.toPx(), Offset(xOf(iHi), yOf(bars[iHi].total)))
                drawCircle(Rose400, 4.dp.toPx(), Offset(xOf(iLo), yOf(bars[iLo].total)))

                if (nowMs in weekStart until weekEnd) {
                    val nx = left + ((nowMs - weekStart).toFloat() / (weekEnd - weekStart)) * plotW
                    drawLine(Color(0xFFFBBF24).copy(alpha = 0.6f), Offset(nx, top), Offset(nx, top + plotH), 1.5f)
                }
                drawLine(Color.White, Offset(xOf(selIdx), top), Offset(xOf(selIdx), top + plotH), 2f)

                val lp = android.graphics.Paint().apply { isAntiAlias = true; textSize = 9.sp.toPx(); textAlign = android.graphics.Paint.Align.RIGHT }
                fun lab(t: String, v: Double, c: Color) {
                    lp.color = c.toArgb()
                    drawContext.canvas.nativeCanvas.drawText(t, left - 7.dp.toPx(), yOf(v) + lp.textSize * 0.35f, lp)
                }
                lab("+" + fmtScale(maxLine), maxLine, Emerald400.copy(alpha = 0.8f))
                lab("0", 0.0, Slate400)
                lab("−" + fmtScale(maxLine), -maxLine, Rose400.copy(alpha = 0.8f))
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "━ running total of heliocentric aspects to the Sun · faint: MACD (cyan) / signal (orange) / histogram · dots = week high / low",
                color = Slate400, fontSize = 9.sp, lineHeight = 12.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Week high ${fmtH(bars[iHi].total)} (${timeFmt.format(Date(bars[iHi].startMs))})  ·  low ${fmtH(bars[iLo].total)} (${timeFmt.format(Date(bars[iLo].startMs))})",
                color = Slate100, fontSize = 11.sp
            )
            if (hitsNow.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Aspects to the Sun at the cursor", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                hitsNow.sortedByDescending { abs(it.value) }.take(6).forEach { h ->
                    Text(
                        "${h.glyph} ${h.planetName} ${h.aspectAngle}° ☉   ${fmtH(h.value)}",
                        color = HelioAspectColors[h.aspectAngle] ?: Slate100, fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * v7.1: like the detrended Ascendant pane, but only the Sun's aspects to the Ascendant. Bars are coloured by the aspect
 * angle (see HelioAspectColors). The background tint follows the direction of the heliocentric Sun line:
 * green while its running total is rising, red while it is falling. Shares the main chart window.
 */
@Composable
fun SunAscendantPane(
    data: com.ascendant.sentiment.model.SunAscSeries,
    firstBarMs: Long,
    inspectedTimeMs: Long,
    view: IntArray,
    zoom: Float,
    trend: DoubleArray? = null,
    marks: com.ascendant.sentiment.model.MoonMarks? = null,   // v7.4: dots where the Moon aspects this planet
    bgMarks: com.ascendant.sentiment.model.MoonMarks? = null, // v7.5: background tint where the Moon aspects Mars (colour = aspect)
    modifier: Modifier = Modifier.fillMaxWidth().height(96.dp)
) {
    val numBars = data.detr.size
    if (numBars <= 0) return
    val selIdx = ((inspectedTimeMs - firstBarMs) / 300_000L).toInt().coerceIn(0, numBars - 1)
    val start = view[0].coerceIn(0, numBars - 1)
    val count = view[1].coerceIn(1, numBars - start)
    val maxVal = remember(data) { niceCeil(max(0.25, data.detr.maxOf { abs(it) } * 1.05)) }
    val zoomKey = zoom

    Canvas(modifier = modifier) {
        val left = 42.dp.toPx()
        val top = 6.dp.toPx()
        val bottom = 6.dp.toPx()
        val plotW = size.width - left - 12.dp.toPx()
        val plotH = size.height - top - bottom
        val bw = plotW / count
        fun yOf(v: Double): Float = (top + (plotH / 2.0) * (1.0 - v / maxVal)).toFloat()
        val yZero = yOf(0.0)
        drawRect(BgDark, size = size)

        if (trend != null && trend.size == numBars && numBars > 1) {
            for (i in start until start + count) {
                val d = if (i == 0) trend[1] - trend[0] else trend[i] - trend[i - 1]
                if (d == 0.0) continue
                drawRect(
                    (if (d > 0) Emerald500 else Rose500).copy(alpha = 0.2f),
                    Offset(left + (i - start) * bw, top), Size(max(bw, 1f), plotH)
                )
            }
        }

        // v7.5: Moon-Mars aspect tint behind the bars, light enough that bars, dots and guides stay clear
        if (bgMarks != null && bgMarks.angle.size == numBars) {
            for (i in start until start + count) {
                val a = bgMarks.angle[i]
                if (a < 0) continue
                drawRect(
                    (HelioAspectColors[a] ?: Color.White).copy(alpha = if (bgMarks.exact[i]) 0.34f else 0.20f),
                    Offset(left + (i - start) * bw, top), Size(max(bw, 1f), plotH)
                )
            }
        }

        listOf(1.0, -1.0).forEach { f ->
            drawLine(Color(0xFF475569).copy(alpha = 0.35f), Offset(left, yOf(f * maxVal)), Offset(left + plotW, yOf(f * maxVal)), 1f)
        }
        drawLine(Color(0xFF475569), Offset(left - 4.dp.toPx(), yZero), Offset(left + plotW, yZero), 1.5f)

        for (i in start until start + count) {
            val v = data.detr[i]
            if (v == 0.0) continue
            val y = yOf(v)
            val c = HelioAspectColors[data.angle[i]] ?: Color(0xFF64748B)
            drawRect(
                c.copy(alpha = 0.9f),
                Offset(left + (i - start) * bw, min(yZero, y)),
                Size(max(bw - 1f, 1.5f), max(abs(y - yZero), 1f))
            )
        }

        // v7.4: a dot on the bar tip whenever the Moon aspects this planet, coloured by that aspect; larger at the exact bar
        if (marks != null && marks.angle.size == numBars) {
            for (i in start until start + count) {
                val a = marks.angle[i]
                if (a < 0) continue
                val c = HelioAspectColors[a] ?: Color.White
                val center = Offset(left + (i - start + 0.5f) * bw, yOf(data.detr[i]))
                if (marks.exact[i]) {
                    drawCircle(c, 5.dp.toPx(), center)
                    drawCircle(Color.White, 5.dp.toPx(), center, style = Stroke(width = 1.5f))
                } else {
                    drawCircle(c, 2.5.dp.toPx(), center)
                    drawCircle(BgDark, 2.5.dp.toPx(), center, style = Stroke(width = 1f))
                }
            }
        }

        val sx = left + (selIdx - start + 0.5f) * bw
        drawLine(Color.White, Offset(sx, top), Offset(sx, top + plotH), 2f)

        val lp = android.graphics.Paint().apply { isAntiAlias = true; textSize = 9.sp.toPx(); textAlign = android.graphics.Paint.Align.RIGHT }
        fun lab(t: String, v: Double, c: Color) {
            lp.color = c.toArgb()
            drawContext.canvas.nativeCanvas.drawText(t, left - 7.dp.toPx(), yOf(v) + lp.textSize * 0.35f, lp)
        }
        lab("+" + fmtScale(maxVal), maxVal, Emerald400.copy(alpha = 0.8f))
        lab("0", 0.0, Slate400)
        lab("−" + fmtScale(maxVal), -maxVal, Rose400.copy(alpha = 0.8f))
    }
}

private val MercLine = Color(0xFF94A3B8)

/**
 * v7.2: geocentric aspects to Mercury as a running total for the whole week, 6-hour bars. Bold line = running total.
 * Faint in the background: the detrended total (total minus its trailing 5-day average), as bars and a thin line.
 */
@Composable
fun MercuryFieldCard(
    week: MercuryWeek,
    hitsNow: List<HelioHit>,
    inspectedTimeMs: Long,
    nowMs: Long,
    timeZoneId: String,
    onInspectTime: (Long) -> Unit
) {
    val bars = week.bars
    if (bars.isEmpty()) return
    val n = bars.size
    val weekStart = bars.first().startMs
    val weekEnd = weekStart + n * week.barMs
    val selIdx = ((inspectedTimeMs - weekStart) / week.barMs).toInt().coerceIn(0, n - 1)
    val cur = bars[selIdx]
    val iHi = bars.indices.maxByOrNull { bars[it].total } ?: 0
    val iLo = bars.indices.minByOrNull { bars[it].total } ?: 0
    val timeFmt = remember(timeZoneId) { SimpleDateFormat("EEE HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone(timeZoneId) } }
    val dayFmt = remember(timeZoneId) { SimpleDateFormat("EEE d", Locale.US).apply { timeZone = TimeZone.getTimeZone(timeZoneId) } }
    val onInspect = rememberUpdatedState(onInspectTime)
    val maxLine = remember(week) { niceCeil(max(1.0, bars.maxOf { abs(it.total) } * 1.1)) }
    val maxDetr = remember(week) { max(0.1, week.detr.maxOf { abs(it) }) * 1.1 }
    val detrNow = week.detr[selIdx]

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("☿ ASPECTS TO MERCURY · WEEK (6h bars)", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(timeFmt.format(Date(cur.startMs)) + " bar", color = Slate400, fontSize = 11.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Total " + fmtH(cur.total),
                color = if (cur.total >= 0) Emerald400 else Rose400, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Text(
                "Detrended (vs 5-day average) " + fmtH(detrNow) + if (detrNow > 0) " (above average)" else " (below average)",
                color = Slate100, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .weekScrub(week, n, weekStart, week.barMs, onInspect)
            ) {
                val left = 42.dp.toPx()
                val right = 12.dp.toPx()
                val top = 8.dp.toPx()
                val bottom = 16.dp.toPx()
                val plotW = size.width - left - right
                val plotH = size.height - top - bottom
                val cell = plotW / n
                fun xOf(i: Int) = left + (i + 0.5f) * cell
                fun yOf(v: Double) = (top + plotH / 2.0 * (1.0 - v / maxLine)).toFloat()
                fun yD(v: Double) = (top + plotH / 2.0 * (1.0 - v / maxDetr)).toFloat()
                val yZero = yOf(0.0)

                // background: detrended total (own scale)
                for (i in 0 until n) {
                    val d = week.detr[i]
                    val y = yD(d)
                    val y0 = yD(0.0)
                    drawRect(
                        (if (d >= 0) Emerald500 else Rose500).copy(alpha = 0.22f),
                        Offset(left + i * cell + 1f, min(y, y0)), Size(max(cell - 2f, 1f), max(abs(y - y0), 1f))
                    )
                }
                val dp = Path()
                for (i in 0 until n) { val x = xOf(i); val y = yD(week.detr[i]); if (i == 0) dp.moveTo(x, y) else dp.lineTo(x, y) }
                drawPath(dp, HelioMacdCyan.copy(alpha = 0.35f), style = Stroke(width = 1.5f, cap = StrokeCap.Round))

                listOf(1.0, -1.0).forEach { f ->
                    drawLine(Color(0xFF475569).copy(alpha = 0.35f), Offset(left, yOf(f * maxLine)), Offset(left + plotW, yOf(f * maxLine)), 1f)
                }
                drawLine(Color(0xFF475569), Offset(left - 4.dp.toPx(), yZero), Offset(left + plotW, yZero), 1.5f)

                var lastDay = ""
                for (i in 0 until n) {
                    val label = dayFmt.format(Date(bars[i].startMs))
                    if (label != lastDay) {
                        lastDay = label
                        drawLine(Color(0xFF334155).copy(alpha = 0.6f), Offset(left + i * cell, top), Offset(left + i * cell, top + plotH), 1f)
                        drawContext.canvas.nativeCanvas.drawText(
                            label, left + i * cell + 3.dp.toPx(), size.height - 2.dp.toPx(),
                            android.graphics.Paint().apply { isAntiAlias = true; textSize = 9.sp.toPx(); color = Slate400.toArgb() }
                        )
                    }
                }

                val p = Path()
                for (i in 0 until n) { val x = xOf(i); val y = yOf(bars[i].total); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                drawPath(p, MercLine, style = Stroke(width = 3.6.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(Emerald400, 4.dp.toPx(), Offset(xOf(iHi), yOf(bars[iHi].total)))
                drawCircle(Rose400, 4.dp.toPx(), Offset(xOf(iLo), yOf(bars[iLo].total)))

                if (nowMs in weekStart until weekEnd) {
                    val nx = left + ((nowMs - weekStart).toFloat() / (weekEnd - weekStart)) * plotW
                    drawLine(Color(0xFFFBBF24).copy(alpha = 0.6f), Offset(nx, top), Offset(nx, top + plotH), 1.5f)
                }
                drawLine(Color.White, Offset(xOf(selIdx), top), Offset(xOf(selIdx), top + plotH), 2f)

                val lp = android.graphics.Paint().apply { isAntiAlias = true; textSize = 9.sp.toPx(); textAlign = android.graphics.Paint.Align.RIGHT }
                fun lab(t: String, v: Double, c: Color) {
                    lp.color = c.toArgb()
                    drawContext.canvas.nativeCanvas.drawText(t, left - 7.dp.toPx(), yOf(v) + lp.textSize * 0.35f, lp)
                }
                lab("+" + fmtScale(maxLine), maxLine, Emerald400.copy(alpha = 0.8f))
                lab("0", 0.0, Slate400)
                lab("−" + fmtScale(maxLine), -maxLine, Rose400.copy(alpha = 0.8f))
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "━ running total of geocentric aspects to Mercury · faint: total minus its 5-day average (bars and cyan line, own scale) · dots = week high / low",
                color = Slate400, fontSize = 9.sp, lineHeight = 12.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Week high ${fmtH(bars[iHi].total)} (${timeFmt.format(Date(bars[iHi].startMs))})  ·  low ${fmtH(bars[iLo].total)} (${timeFmt.format(Date(bars[iLo].startMs))})",
                color = Slate100, fontSize = 11.sp
            )
            if (hitsNow.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Aspects to Mercury at the cursor", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                hitsNow.sortedByDescending { abs(it.value) }.take(6).forEach { h ->
                    Text(
                        "${h.glyph} ${h.planetName} ${h.aspectAngle}° ☿   ${fmtH(h.value)}",
                        color = HelioAspectColors[h.aspectAngle] ?: Slate100, fontSize = 11.sp
                    )
                }
            }
        }
    }
}

private val ConvergenceAmber = Color(0xFFF59E0B)

/** Draws one lane per actor (Moon, Mercury, Mars, Saturn): coloured by aspect to Pluto, brighter closer to exact. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawActorLanes(
    grid: PlutoGrid,
    from: Int,
    count: Int,
    left: Float,
    top: Float,
    laneH: Float,
    cellW: Float,
    labelGapPx: Float
) {
    val glyph = android.graphics.Paint().apply {
        isAntiAlias = true; textSize = 11.sp.toPx(); color = Slate400.toArgb(); textAlign = android.graphics.Paint.Align.RIGHT
    }
    for (k in grid.actors.indices) {
        val y = top + k * laneH
        drawLine(Color(0xFF1E293B), Offset(left, y + laneH), Offset(left + cellW * count, y + laneH), 1f)
        drawContext.canvas.nativeCanvas.drawText(grid.actors[k].glyph, left - labelGapPx, y + laneH * 0.5f + glyph.textSize * 0.35f, glyph)
        for (i in from until from + count) {
            val a = grid.angle[k][i]
            if (a < 0) continue
            val closeness = (1.0 - grid.orb[k][i] / 3.0).toFloat().coerceIn(0f, 1f)
            drawRect(
                (HelioAspectColors[a] ?: Color.White).copy(alpha = 0.45f + 0.55f * closeness),
                Offset(left + (i - from) * cellW, y + 2f), Size(max(cellW, 1f), laneH - 4f)
            )
        }
    }
    // convergence: two or more actors in aspect to Pluto on the same bar
    for (i in from until from + count) {
        val lit = grid.actors.indices.count { grid.angle[it][i] >= 0 }
        if (lit >= 2) {
            drawRect(ConvergenceAmber, Offset(left + (i - from) * cellW, top - 3.dp.toPx()), Size(max(cellW, 1f), 3.dp.toPx()))
        }
    }
}

/**
 * v8.0: Pluto and its actors on the 5-minute bars (shares the main chart window). Four lanes, top to bottom:
 * Moon, Mercury, Mars, Saturn. A lit cell = that planet aspects Pluto, coloured by the aspect. Amber bar on top = two or
 * more actors aspect Pluto on the same bar (convergence).
 */
@Composable
fun PlutoActorStrip(
    grid: PlutoGrid,
    barCount: Int,
    firstBarMs: Long,
    inspectedTimeMs: Long,
    view: IntArray,
    zoom: Float,
    modifier: Modifier = Modifier.fillMaxWidth().height((12 + 15 * grid.actors.size).dp)
) {
    if (barCount <= 0 || grid.angle.isEmpty() || grid.angle[0].size != barCount) return
    val selIdx = ((inspectedTimeMs - firstBarMs) / 300_000L).toInt().coerceIn(0, barCount - 1)
    val start = view[0].coerceIn(0, barCount - 1)
    val count = view[1].coerceIn(1, barCount - start)
    val zoomKey = zoom

    Canvas(modifier = modifier) {
        val left = 42.dp.toPx()
        val top = 6.dp.toPx()
        val bottom = 3.dp.toPx()
        val plotW = size.width - left - 12.dp.toPx()
        val laneH = (size.height - top - bottom) / grid.actors.size
        val bw = plotW / count
        drawRect(BgDark, size = size)
        drawActorLanes(grid, start, count, left, top, laneH, bw, 8.dp.toPx())
        val sx = left + (selIdx - start + 0.5f) * bw
        drawLine(Color.White, Offset(sx, top), Offset(sx, size.height - bottom), 2f)
    }
}

private fun fmtDur(ms: Long): String {
    val h = abs(ms) / 3_600_000.0
    return if (h < 36) "%.1f h".format(h) else "%.1f days".format(h / 24.0)
}

/**
 * v8.0: Pluto and its actors for the whole week in 6-hour bars: one lane per actor, coloured by the aspect to Pluto and
 * brighter closer to exact, amber marks where two or more actors aspect Pluto together. Underneath, for every actor
 * currently in aspect: the aspect and how long to (or since) its exact pass.
 */
@Composable
fun PlutoWeekCard(
    week: PlutoWeek,
    exacts: List<PlutoExact>,
    inspectedTimeMs: Long,
    nowMs: Long,
    timeZoneId: String,
    onInspectTime: (Long) -> Unit,
    targetName: String = "Pluto",
    targetGlyph: String = "♇"
) {
    val grid = week.grid
    val n = grid.angle[0].size
    if (n == 0) return
    val weekStart = week.startMs
    val weekEnd = weekStart + n * week.barMs
    val dayFmt = remember(timeZoneId) { SimpleDateFormat("EEE d", Locale.US).apply { timeZone = TimeZone.getTimeZone(timeZoneId) } }
    val onInspect = rememberUpdatedState(onInspectTime)
    val selIdx = ((inspectedTimeMs - weekStart) / week.barMs).toInt().coerceIn(0, n - 1)
    val convBars = (0 until n).count { i -> grid.actors.indices.count { grid.angle[it][i] >= 0 } >= 2 }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("$targetGlyph ${targetName.uppercase()} AND ITS ACTORS · WEEK (6h bars)", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                "Lanes: ${grid.actors.joinToString(", ") { it.glyph + " " + it.name }} · amber bar = two or more at once ($convBars bars this week)",
                color = Slate400, fontSize = 9.sp, lineHeight = 12.sp
            )
            Spacer(Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((22 + 22 * grid.actors.size).dp)
                    .weekScrub(week, n, weekStart, week.barMs, onInspect)
            ) {
                val left = 42.dp.toPx()
                val top = 8.dp.toPx()
                val bottom = 14.dp.toPx()
                val plotW = size.width - left - 12.dp.toPx()
                val laneH = (size.height - top - bottom) / grid.actors.size
                val cell = plotW / n
                drawActorLanes(grid, 0, n, left, top, laneH, cell, 8.dp.toPx())

                var lastDay = ""
                for (i in 0 until n) {
                    val label = dayFmt.format(Date(weekStart + i * week.barMs))
                    if (label != lastDay) {
                        lastDay = label
                        drawLine(Color(0xFF334155).copy(alpha = 0.6f), Offset(left + i * cell, top), Offset(left + i * cell, top + laneH * grid.actors.size), 1f)
                        drawContext.canvas.nativeCanvas.drawText(
                            label, left + i * cell + 3.dp.toPx(), size.height - 2.dp.toPx(),
                            android.graphics.Paint().apply { isAntiAlias = true; textSize = 9.sp.toPx(); color = Slate400.toArgb() }
                        )
                    }
                }
                if (nowMs in weekStart until weekEnd) {
                    val nx = left + ((nowMs - weekStart).toFloat() / (weekEnd - weekStart)) * plotW
                    drawLine(Color(0xFFFBBF24).copy(alpha = 0.6f), Offset(nx, top), Offset(nx, top + laneH * grid.actors.size), 1.5f)
                }
                val sx = left + (selIdx + 0.5f) * cell
                drawLine(Color.White, Offset(sx, top), Offset(sx, top + laneH * grid.actors.size), 2f)
            }

            Spacer(Modifier.height(6.dp))
            Text("At the cursor", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            if (exacts.isEmpty()) {
                Text("None of ${grid.actors.joinToString(", ") { it.name }} is in aspect to $targetName.", color = Slate100, fontSize = 11.sp)
            }
            exacts.forEach { e ->
                val timing = e.exactMs?.let { m ->
                    val d = m - inspectedTimeMs
                    if (abs(d) < 1_800_000L) "exact now" else if (d > 0) "exact in ${fmtDur(d)}" else "exact ${fmtDur(d)} ago"
                } ?: "no exact pass nearby"
                Text(
                    "${e.glyph} ${e.actorName} ${e.angle}° $targetGlyph · orb %.1f° · $timing".format(e.orbNow),
                    color = HelioAspectColors[e.angle] ?: Slate100, fontSize = 11.sp
                )
            }
        }
    }
}
