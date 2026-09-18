package com.example.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

data class DrawnStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

@Composable
fun DoodleCanvas(
    strokes: List<DrawnStroke>,
    currentStrokeColor: Color,
    currentStrokeWidth: Float,
    isDoodleModeEnabled: Boolean,
    onStrokeFinished: (DrawnStroke) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPoints = remember { mutableStateListOf<Offset>() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isDoodleModeEnabled) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPoints.clear()
                                currentPoints.add(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentPoints.add(change.position)
                            },
                            onDragEnd = {
                                if (currentPoints.isNotEmpty()) {
                                    onStrokeFinished(
                                        DrawnStroke(
                                            points = currentPoints.toList(),
                                            color = currentStrokeColor,
                                            strokeWidth = currentStrokeWidth
                                        )
                                    )
                                    currentPoints.clear()
                                }
                            },
                            onDragCancel = {
                                currentPoints.clear()
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        // Draw completed strokes
        strokes.forEach { stroke ->
            if (stroke.points.size > 1) {
                val path = Path().apply {
                    moveTo(stroke.points[0].x, stroke.points[0].y)
                    for (i in 1 until stroke.points.size) {
                        lineTo(stroke.points[i].x, stroke.points[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = stroke.color,
                    style = Stroke(
                        width = stroke.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            } else if (stroke.points.size == 1) {
                drawCircle(
                    color = stroke.color,
                    radius = stroke.strokeWidth / 2f,
                    center = stroke.points[0]
                )
            }
        }

        // Draw active in-progress stroke
        if (currentPoints.size > 1) {
            val livePath = Path().apply {
                moveTo(currentPoints[0].x, currentPoints[0].y)
                for (i in 1 until currentPoints.size) {
                    lineTo(currentPoints[i].x, currentPoints[i].y)
                }
            }
            drawPath(
                path = livePath,
                color = currentStrokeColor,
                style = Stroke(
                    width = currentStrokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        } else if (currentPoints.size == 1) {
            drawCircle(
                color = currentStrokeColor,
                radius = currentStrokeWidth / 2f,
                center = currentPoints[0]
            )
        }
    }
}
