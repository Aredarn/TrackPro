package com.yourpackage.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp



//THE CODE IS NOT MINE:
//https://medium.com/@kappdev/creating-a-seven-segment-view-in-jetpack-compose-e217209780fe

data class SegmentsState(
    val a: Boolean = false,
    val b: Boolean = false,
    val c: Boolean = false,
    val d: Boolean = false,
    val e: Boolean = false,
    val f: Boolean = false,
    val g: Boolean = false
)

val SevenSegmentNumbers = mapOf(
    0 to SegmentsState(a = true, b = true, c = true, d = true, e = true, f = true),
    1 to SegmentsState(b = true, c = true),
    2 to SegmentsState(a = true, b = true, d = true, e = true, g = true),
    3 to SegmentsState(a = true, b = true, c = true, d = true, g = true),
    4 to SegmentsState(b = true, c = true, f = true, g = true),
    5 to SegmentsState(a = true, c = true, d = true, f = true, g = true),
    6 to SegmentsState(a = true, c = true, d = true, e = true, f = true, g = true),
    7 to SegmentsState(a = true, b = true, c = true),
    8 to SegmentsState(a = true, b = true, c = true, d = true, e = true, f = true, g = true),
    9 to SegmentsState(a = true, b = true, c = true, d = true, f = true, g = true),
)

fun Int.getSegmentsState(): SegmentsState {
    return SevenSegmentNumbers.getOrElse(this) {
        throw IllegalArgumentException("The digit must be in the range from 0 to 9")
    }
}

data class SegmentData(
    val isActive: Boolean,
    val isVertical: Boolean,
    val startX: Float,
    val endX: Float,
    val startY: Float,
    val endY: Float
)


fun SegmentData.addSpacing(space: Float): SegmentData {
    return when {
        isVertical -> this.copy(
            startY = (this.startY + space),
            endY = (this.endY - space)
        )
        else -> this.copy(
            startX = (this.startX + space),
            endX = (this.endX - space)
        )
    }
}

fun Int.splitToDigits(): List<Int> {
    return this.toString().map(Char::digitToInt)
}

fun <T> List<T>.padToStart(size: Int, value: T): List<T> {
    require(size >= 0) { "Size should not be negative" }
    return if (this.size < size) {
        List(size - this.size) { value } + this
    } else {
        this
    }
}


private fun DrawScope.drawSegment(
    color: Color,
    data: SegmentData,
    halfWidth: Float // Half of the segment width
) {
    // Create a Path object to draw the segment
    val segmentPath = Path().apply {
        // Using with() for improved readability by accessing SegmentData properties directly
        with(data) {
            // Move the cursor to the start of the segment
            moveTo(startX, startY)
            if (isVertical) {
                // Drawing vertical segment path
                lineTo(startX + halfWidth, startY + halfWidth) // 1
                lineTo(startX + halfWidth, endY - halfWidth) // 2
                lineTo(endX, endY)
                lineTo(startX - halfWidth, endY - halfWidth) // 3
                lineTo(startX - halfWidth, startY + halfWidth) // 4
            } else {
                // Drawing horizontal segment path
                lineTo(startX + halfWidth, startY - halfWidth) // 1
                lineTo(endX - halfWidth, startY - halfWidth) // 2
                lineTo(endX, endY)
                lineTo(endX - halfWidth, startY + halfWidth) // 3
                lineTo(startX + halfWidth, startY + halfWidth) // 4
            }
            // Close the path
            close()
        }
    }

    // Draw the segment using the path and specified color
    drawPath(segmentPath, color)
}

@Composable
fun SingleSevenSegment(
    state: SegmentsState,
    modifier: Modifier,
    activeColor: Color,
    inactiveColor: Color,
    segmentWidth: Dp,
    segmentsSpace: Dp
) {
    Canvas(
        // This modifier ensures that the view is displayed in a correct ratio of 2:1
        modifier = modifier.aspectRatio(0.5f, matchHeightConstraintsFirst = true)
    ) {
        val halfViewHeight = (size.height / 2)
        val halfWidth = (segmentWidth.toPx() / 2)

        val rightEdge = (size.width - halfWidth)
        val bottomEdge = (size.height - halfWidth)

        // Define data for each segment based on the SegmentsState and Canvas configurations
        val segmentData = listOf(
            SegmentData(state.a, isVertical = false, halfWidth, rightEdge, halfWidth, halfWidth),
            SegmentData(state.b, isVertical = true, rightEdge, rightEdge, halfWidth, halfViewHeight),
            SegmentData(state.c, isVertical = true, rightEdge, rightEdge, halfViewHeight, bottomEdge),
            SegmentData(state.d, isVertical = false, halfWidth, rightEdge, bottomEdge, bottomEdge),
            SegmentData(state.e, isVertical = true, halfWidth, halfWidth, halfViewHeight, bottomEdge),
            SegmentData(state.f, isVertical = true, halfWidth, halfWidth, halfWidth, halfViewHeight),
            SegmentData(state.g, isVertical = false, halfWidth, rightEdge, halfViewHeight, halfViewHeight)
        )

        // Draw the segments
        segmentData.forEach { data ->
            drawSegment(
                color = if (data.isActive) activeColor else inactiveColor,
                data = data.addSpacing(segmentsSpace.toPx()),
                halfWidth = halfWidth,
            )
        }
    }
}


@Composable
fun SevenSegmentView(
    number: Int,
    modifier: Modifier,
    activeColor: Color,
    inactiveColor: Color = activeColor.copy(0.16f),
    digitsNumber: Int = 1,
    digitsSpace: Dp = 4.dp,
    segmentWidth: Dp = 4.dp,
    segmentsSpace: Dp = 0.dp
) {
    // Validate input parameters
    require(digitsNumber > 0) { "Digits number should be greater than 0" }
    require(number >= 0) { "The number has to be positive" }

    // Split the number into individual digits
    var digits = remember(number) { number.splitToDigits() }

    // Ensures the correct number of digits to display
    // Alternative behaviors could be implemented, such as throwing an exception for mismatches
    if (digits.size > digitsNumber)
        digits = digits.takeLast(digitsNumber)


    Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(digitsSpace),
    verticalAlignment = Alignment.CenterVertically
    ) {
    // Pad the digits to match the desired number of digits and display each digit using SingleSevenSegment
    // Alternatively, replacing null with 0 ensures that unfilled segments display as 0 instead of remaining
        digits.padToStart(digitsNumber, null).forEach { digit ->
        val state = digit?.getSegmentsState() ?: SegmentsState()
            SingleSevenSegment(
                state = state,
                modifier = Modifier.fillMaxHeight(),
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                segmentWidth = segmentWidth,
                segmentsSpace = segmentsSpace
            )
        }
    }
}

