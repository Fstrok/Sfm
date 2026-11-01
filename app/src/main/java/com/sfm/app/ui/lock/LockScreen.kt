package com.sfm.app.ui.lock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.material3.Button

// STUB: implemented by the library agent.
@Composable
fun LockScreen(onUnlocked: () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Button(onClick = onUnlocked) { Text("Войти") } }
