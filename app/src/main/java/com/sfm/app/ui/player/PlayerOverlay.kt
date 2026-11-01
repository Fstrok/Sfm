package com.sfm.app.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.Dp

/** STUB: implemented by the player agent. Draws the expanded player and the mini-player above [bottomInset]. */
@Composable
fun PlayerOverlay(bottomInset: Dp) {}

/** STUB: implemented by the player agent. Content shown while the activity is in picture-in-picture. */
@Composable
fun PipPlayer() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("PiP") }
